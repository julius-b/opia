package app.opia.common.api.repository

import app.opia.common.api.NetworkResponse
import app.opia.common.api.endpoint.KeyApi
import app.opia.common.api.model.CreateKeyPairParams
import app.opia.common.api.model.CreateVaultKeyParams
import app.opia.common.db.Auth_session
import app.opia.common.db.Key_pair
import app.opia.common.db.Vault_key
import app.opia.common.di.ServiceLocator
import ch.oxc.nikea.extra.IdentityKey
import ch.oxc.nikea.extra.KexKey
import ch.oxc.nikea.extra.VaultKey
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime
import java.util.*

enum class KeyType {
    signature, kex
}

enum class KeyLifetime {
    static, ephemeral
}

enum class KeyAlgo {
    ed25519, ed448, x25519, x448
}

// vaultKeyId is set when the key could be recovered
fun VaultKey.toModel(actorId: UUID, secretUpdateId: UUID, id: UUID = UUID.randomUUID()) = Vault_key(
    id,
    actorId,
    this.algo.name,
    null,
    UByteArray(0),
    this.seckEnc,
    this.args,
    this.seckClr,
    secretUpdateId,
    ZonedDateTime.now(),
    null
)

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
    private val di: ServiceLocator, private val api: KeyApi
) {
    private val db = di.database

    /**
     * Only works during authentication as the secret is never stored.
     *
     * TODO query for previous vaultKey locally (to encrypt) & server-side (to recover locally)
     */
    suspend fun syncVaultKey(authSession: Auth_session, secret: String): Vault_key? {
        val authHeader = "Bearer ${authSession.access_token}"

        // authenticated associated data for the encrypted VaultKey
        val ad = "${authSession.actor_id}@${authSession.secret_update_id}".encodeToByteArray()
            .toUByteArray()

        suspend fun genVaultKey(): Vault_key? {
            println("[*] VaultKey > gen > generating...")
            val newVK =
                VaultKey.new(secret, ad).toModel(authSession.actor_id, authSession.secret_update_id)

            val createdVKRes =
                api.createVaultKey(authHeader, CreateVaultKeyParams.fromVaultKey(newVK))
            when (createdVKRes) {
                is NetworkResponse.ApiSuccess -> {
                    // does not contain seck_clr
                    val createdVK = createdVKRes.body.data
                    println("[*] VaultKey > gen > newVK.created_at    : ${newVK.created_at}")
                    println("[*] VaultKey > gen > createdVK.created_at: ${createdVK.created_at}")

                    return newVK
                }
                else -> return null
            }
        }

        val remoteVKRes = api.getVaultKey(authHeader)
        return when (remoteVKRes) {
            is NetworkResponse.ApiSuccess -> {
                val remoteVK = remoteVKRes.body.data.toVaultKey()
                if (remoteVK.secret_update_id != authSession.secret_update_id) {
                    println("[~] VaultKey > recover > unknown secret_update, can't recover current key")
                    return genVaultKey()
                }

                println("[*] VaultKey > recover > recovering...")
                // TODO catch possible decrypt exceptions; if another client uploaded rubbish, best create a new one
                val recoveredVK = VaultKey.recover(secret, remoteVK.args, remoteVK.seck_enc, ad)
                    .toModel(authSession.actor_id, authSession.secret_update_id, remoteVK.id)

                println("[+] VaultKey > recover > recovered successfully")
                recoveredVK
            }
            // TODO query first?
            is NetworkResponse.ApiError -> genVaultKey()
            else -> null
        }
    }

    // TODO handle if GET returns 'expired' or 'rejected' (ie. create new)
    suspend fun syncKeys(sess: Auth_session): Boolean {
        var currentIK = db.keyPairQueries.getIdentKey(sess.ioid).asFlow().mapToOneOrNull().first()
        if (currentIK == null) {
            // invalidate the chain of keys that depend on this identity
            // since this client only supports one algo, all keys are invalid
            db.keyPairQueries.truncate()

            // delete actively or let the server do it when creating new?

            val newIK = IdentityKey.new().toModel(sess.actor_id, sess.installation_id, sess.ioid)
            println("[*] SyncKeys > IdentityKey > generate > newIK: $newIK")
            val newIKRes = api.createKeyPair(CreateKeyPairParams.fromKeyPair(newIK))
            when (newIKRes) {
                is NetworkResponse.ApiSuccess -> {
                    // currentIK has empty `synced`, newIK has empty `seck_clr`
                    currentIK = newIKRes.body.data.toKeyPair()
                        .copy(seck_clr = newIK.seck_clr)
                    db.keyPairQueries.insert(currentIK)
                }
                is NetworkResponse.ApiError, is NetworkResponse.NetworkError -> {
                    println("[!] SyncKeys > IdentityKey > generate > unexpected err: $newIKRes")
                    return false
                }
                else -> return false
            }
        }

        var currentSKex = db.keyPairQueries.getSKexKey(sess.ioid).asFlow().mapToOneOrNull().first()
        if (currentSKex == null) {
            // nothing to delete

            val newSKex = KexKey.new(currentIK.seck_clr).toModel(
                sess.actor_id,
                sess.installation_id,
                sess.ioid,
                KeyLifetime.static,
                currentIK.id
            )
            println("[*] SyncKeys > SKex > generate > newSKex: $newSKex")
            val newSKexRes = api.createKeyPair(CreateKeyPairParams.fromKeyPair(newSKex))
            when (newSKexRes) {
                is NetworkResponse.ApiSuccess -> {
                    currentSKex = newSKexRes.body.data.toKeyPair()
                        .copy(seck_clr = newSKex.seck_clr)
                    db.keyPairQueries.insert(currentSKex)
                }
                is NetworkResponse.ApiError, is NetworkResponse.UnknownError -> {
                    println("[!] SyncKeys > SKex > generate > unexpected err: $newSKexRes")
                    return false
                }
                else -> return false
            }
        }

        val ekexOutstandingRes = api.listOutstandingEKexKeys()
        when (ekexOutstandingRes) {
            is NetworkResponse.ApiSuccess -> {
                // server may have deleted keys for some reason
                val hints = ekexOutstandingRes.body.hints!!
                if (hints.identity_key != currentIK.id) {
                    println("[!] SyncKeys > EKex > currentIK: ${currentIK.id}, ServerIK: ${hints.identity_key}")
                    db.keyPairQueries.truncate()
                    return syncKeys(sess)
                }
                if (hints.skex_key != currentSKex.id) {
                    println("[!] SyncKeys > EKex > currentSKex: ${currentSKex.id}, ServerSKex: ${hints.skex_key}")
                    db.keyPairQueries.delete(currentSKex.id)
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
                            val keyPair = ekexRes.body.data.toKeyPair()
                                .copy(seck_clr = ekex.seck_clr)
                            println("[+] Synckeys > EKex > generate > registered: ${keyPair.id}")
                            db.keyPairQueries.insert(keyPair)
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
