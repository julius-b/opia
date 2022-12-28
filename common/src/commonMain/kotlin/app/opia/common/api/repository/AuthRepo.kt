package app.opia.common.api.repository

import app.opia.common.api.HintedApiSuccess
import app.opia.common.api.NetworkResponse
import app.opia.common.api.PlainApiSuccess
import app.opia.common.api.endpoint.ActorApi
import app.opia.common.api.endpoint.KeyApi
import app.opia.common.api.model.*
import app.opia.common.db.Actor
import app.opia.common.db.Auth_session
import app.opia.common.db.Owned_field
import app.opia.common.db.Vault_key
import app.opia.db.OpiaDatabase
import ch.oxc.nikea.extra.VaultKey
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime
import java.util.*

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

// AuthRepo handles unauthenticated requests
// this includes token refresh, which only requires a refreshToken
class AuthRepo(
    private val db: OpiaDatabase, val api: ActorApi, val keyApi: KeyApi
) {
    suspend fun createOwnedField(
        scope: OwnedFieldScope, content: String
    ): PlainApiSuccess<Owned_field> {
        val installation = db.installationQueries.getSelf().asFlow().mapToOneOrNull().first()
            ?: throw IllegalStateException("installation required for owned field")

        return api.createOwned(installation.id, CreateOwnedFieldParams(scope, content))
    }

    suspend fun patchOwnedField(
        id: UUID, verificationCode: String
    ): PlainApiSuccess<Owned_field> {
        val res = api.patchOwned(id, PatchOwnedFieldParams(verificationCode))
        return res
    }

    // login does not just create an authSession but also saves it & the actor in the dbt
    suspend fun login(
        unique: String, secret: String
    ): HintedApiSuccess<Auth_session, AuthHints> {
        val installation = db.installationQueries.getSelf().asFlow().mapToOneOrNull().first()
            ?: throw IllegalStateException("installation required for auth_session")

        // TODO query for existing io's? expect caller to truncate db beforehand otherwise
        val asRes = api.createAuthSession(
            installation.id, CreateAuthSessionParams(unique, secret, true, null)
        )
        if (asRes !is NetworkResponse.ApiSuccess) return asRes

        val authSession = asRes.body.data
        val authHeader = "Bearer ${authSession.access_token}"

        // internet/other failure still possible - user needs to try again, a new session is created
        val actorRes = api.getUnauthenticated(authHeader, authSession.actor_id)
        if (actorRes !is NetworkResponse.ApiSuccess) {
            println("[!] ActorRepo > login > get-actor > bad res: $actorRes")
            return NetworkResponse.UnknownError()
        }
        val actor = actorRes.body.data

        val vaultKey = syncVaultKey(authSession, secret)
        println("[+] ActorRepo > login > vaultKey: $vaultKey")
        if (vaultKey == null) {
            println("[!] ActorRepo > login > vault-key > no vault key")
            return NetworkResponse.UnknownError()
        }

        // ensure actor is available in db when session is queries
        db.transaction {
            afterCommit { println("[+] ActorRepo > login > committed") }
            afterRollback { println("[!] ActorRepo > login > rollback") }

            db.vaultKeyQueries.insert(vaultKey)
            db.sessionQueries.insert(authSession)
            db.actorQueries.insert(actor)
            for (ownedField in asRes.body.hints!!.owned_fields) {
                db.ownedFieldQueries.insert(ownedField)
            }
        }

        return asRes
    }

    // register creates an actor and saves it locally
    // NOTE: the app does not care about actors lying around in the database, if login fails then it's just a pre-cached actor
    suspend fun register(
        type: Char, handle: String, name: String, secret: String, ownedFields: List<Owned_field>
    ): HintedApiSuccess<Actor, AuthHints> {
        val installation = db.installationQueries.getSelf().asFlow().mapToOneOrNull().first()
            ?: throw IllegalStateException("installation required for actor")

        val params = CreateActorParams(type, handle, name, secret)

        // "${it.id},${it.verification_code}"
        val res = api.create(installation.id, ownedFields.map { it.id.toString() }, params)
        if (res !is NetworkResponse.ApiSuccess) return res

        db.transaction {
            afterCommit { println("[*] ActorRepo > register > committed") }
            afterRollback { println("[!] ActorRepo > register > rollback") }
            db.actorQueries.insert(res.body.data)
            for (ownedField in res.body.hints!!.owned_fields) {
                db.ownedFieldQueries.insert(ownedField)
            }
        }

        return res
    }

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
                keyApi.createVaultKey(authHeader, CreateVaultKeyParams.fromVaultKey(newVK))
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

        val remoteVKRes = keyApi.getVaultKey(authHeader)
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

    fun logout() {
        db.transaction {
            afterCommit { println("[*] ActorRepo > logout > committed") }
            afterRollback { println("[!] ActorRepo > logout > rollback") }
            db.msgQueries.truncateReceiptSyncStatus()
            db.msgQueries.truncateReceipts()
            db.msgQueries.truncatePayloads()
            db.msgQueries.truncate()
            db.keyPairQueries.truncate()
            db.vaultKeyQueries.truncate()
            db.ownedFieldQueries.truncate()
            db.actorQueries.truncateLinks()
            db.sessionQueries.truncate()
            db.actorQueries.truncate()
            db.installationQueries.truncateOwnerships() // not installation itself
        }
    }
}
