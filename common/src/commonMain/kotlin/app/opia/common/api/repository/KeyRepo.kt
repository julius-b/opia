package app.opia.common.api.repository

import app.opia.common.api.NetworkResponse
import app.opia.common.api.endpoint.KeyApi
import app.opia.common.api.model.CreateKeyPairParams
import app.opia.common.db.Auth_session
import app.opia.common.db.KeyPairQueries
import app.opia.common.db.Key_pair
import ch.oxc.nikea.extra.IdentityKey
import ch.oxc.nikea.extra.KexKey
import java.time.ZonedDateTime
import java.util.UUID

enum class KeyType {
    signature, kex
}

enum class KeyLifetime {
    static, ephemeral
}

enum class KeyAlgo {
    ed25519, ed448, x25519, x448
}

fun IdentityKey.toModel(actorId: UUID, installationId: UUID, ioid: UUID) = Key_pair(
    UUID.randomUUID(),
    actorId,
    installationId,
    ioid,
    KeyType.signature,
    KeyLifetime.static,
    KeyAlgo.valueOf(this.algo.name.lowercase()),
    this.keys.pubk,
    this.pubkSigned,
    null, // self-signed
    this.keys.seck,
    used = false,
    synced = false,
    ZonedDateTime.now(),
    null
)

fun KexKey.toModel(
    actorId: UUID, installationId: UUID, ioid: UUID, lifetime: KeyLifetime, signingKeyId: UUID?
) = Key_pair(
    UUID.randomUUID(),
    actorId,
    installationId,
    ioid,
    KeyType.kex,
    lifetime,
    KeyAlgo.valueOf(this.algo.name.lowercase()),
    this.keys.pubk,
    this.pubkSigned,
    signingKeyId,
    this.keys.seck,
    used = false,
    synced = false,
    ZonedDateTime.now(),
    null
)

@OptIn(ExperimentalUnsignedTypes::class)
class KeyRepo(
    private val keyDB: KeyPairQueries, val api: KeyApi
) {
    // TODO handle if GET returns 'expired' or 'rejected' (ie. create new)
    suspend fun syncKeys(sess: Auth_session): Boolean {
        var currentIK = keyDB.getIdentKey(sess.ioid).executeAsOneOrNull()
        if (currentIK == null) {
            // invalidate the chain of keys that depend on this identity
            // since this client only supports one algo, all keys are invalid
            keyDB.truncate()

            // delete actively or let the server do it when creating new?

            val newIK = IdentityKey.new().toModel(sess.actor_id, sess.installation_id, sess.ioid)
            println("[*] SyncKeys > IdentityKey > generate > newIK: $newIK")
            val newIKRes = api.createKeyPair(CreateKeyPairParams.fromKeyPair(newIK))
            when (newIKRes) {
                is NetworkResponse.ApiSuccess -> {
                    // currentIK has empty `synced`, newIK has empty `seck_clr`
                    currentIK = newIKRes.body.data.toKeyPair().copy(seck_clr = newIK.seck_clr)
                    keyDB.insert(currentIK)
                }

                is NetworkResponse.ApiError, is NetworkResponse.NetworkError -> {
                    println("[!] SyncKeys > IdentityKey > generate > unexpected err: $newIKRes")
                    return false
                }

                else -> return false
            }
        }

        var currentSKex = keyDB.getSKexKey(sess.ioid).executeAsOneOrNull()
        if (currentSKex == null) {
            // nothing to delete

            val newSKex = KexKey.new(currentIK.seck_clr).toModel(
                sess.actor_id, sess.installation_id, sess.ioid, KeyLifetime.static, currentIK.id
            )
            println("[*] SyncKeys > SKex > generate > newSKex: $newSKex")
            when (val newSKexRes = api.createKeyPair(CreateKeyPairParams.fromKeyPair(newSKex))) {
                is NetworkResponse.ApiSuccess -> {
                    currentSKex = newSKexRes.body.data.toKeyPair().copy(seck_clr = newSKex.seck_clr)
                    keyDB.insert(currentSKex)
                }

                is NetworkResponse.ApiError, is NetworkResponse.UnknownError -> {
                    println("[!] SyncKeys > SKex > generate > unexpected err: $newSKexRes")
                    return false
                }

                else -> return false
            }
        }

        when (val ekexOutstandingRes = api.listOutstandingEKexKeys()) {
            is NetworkResponse.ApiSuccess -> {
                // server may have deleted keys for some reason
                val hints = ekexOutstandingRes.body.hints!!
                if (hints.identity_key != currentIK.id) {
                    println("[!] SyncKeys > EKex > currentIK: ${currentIK.id}, ServerIK: ${hints.identity_key}")
                    keyDB.truncate()
                    return syncKeys(sess)
                }
                if (hints.skex_key != currentSKex.id) {
                    println("[!] SyncKeys > EKex > currentSKex: ${currentSKex.id}, ServerSKex: ${hints.skex_key}")
                    keyDB.delete(currentSKex.id)
                    return syncKeys(sess)
                }

                val outstanding = ekexOutstandingRes.body.data
                println("[*] SyncKeys > EKex > generate > outstanding keys: $outstanding...")

                repeat(outstanding.outstanding) {
                    val ekex = KexKey.new(currentIK.seck_clr).toModel(
                        sess.actor_id,
                        sess.installation_id,
                        sess.ioid,
                        KeyLifetime.ephemeral,
                        currentIK.id
                    )
                    val ekexRes = api.createKeyPair(CreateKeyPairParams.fromKeyPair(ekex))
                    when (ekexRes) {
                        is NetworkResponse.ApiSuccess -> {
                            val keyPair =
                                ekexRes.body.data.toKeyPair().copy(seck_clr = ekex.seck_clr)
                            println("[+] SyncKeys > EKex > generate > registered: ${keyPair.id}")
                            keyDB.insert(keyPair)
                        }

                        is NetworkResponse.ApiError, is NetworkResponse.UnknownError -> {
                            println("[!] SyncKeys > EKex > generate > unexpected err: $ekexOutstandingRes")
                            return false
                        }

                        else -> return false
                    }
                }
            }

            is NetworkResponse.ApiError, is NetworkResponse.UnknownError -> {
                println("[!] SyncKeys > EKex > query > unexpected err: $ekexOutstandingRes")
                return false
            }

            else -> return false
        }
        return true
    }
}
