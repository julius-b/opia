package app.opia.common.api.repository

import app.opia.common.api.HintedApiSuccess
import app.opia.common.api.NetworkResponse
import app.opia.common.api.PlainApiSuccess
import app.opia.common.api.endpoint.ActorApi
import app.opia.common.api.model.*
import app.opia.common.db.Actor
import app.opia.common.db.Actor_link
import app.opia.common.db.Auth_session
import app.opia.common.db.Owned_field
import app.opia.common.di.ServiceLocator
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneNotNull
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import kotlinx.coroutines.flow.first
import java.util.*

const val ActorTypeUser = 'u'

class ActorRepo(
    private val di: ServiceLocator, val api: ActorApi
) {
    private val db = di.database

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

        val vaultKey = di.keyRepo.syncVaultKey(authSession, secret)
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

    suspend fun getActor(id: UUID, cache: Boolean = true): Actor? {
        val res = api.get(id)
        if (res is NetworkResponse.ApiSuccess) {
            val actor = res.body.data
            db.actorQueries.insert(actor)
            return actor
        }
        if (res.httpCode == 401) return null
        return db.actorQueries.getById(id).asFlow().mapToOneOrNull().first()
    }

    suspend fun getActorByHandle(handle: String): Actor? {
        val res = api.getByHandle(handle)
        if (res is NetworkResponse.ApiSuccess) {
            val actor = res.body.data
            db.actorQueries.insert(actor)
            return actor
        }
        if (res.httpCode == 401) return null
        return db.actorQueries.getByHandle(handle).asFlow().mapToOneOrNull().first()
    }

    // if null
    suspend fun getLatestAuthSession(
        refreshIfExpired: Boolean = false
    ): Auth_session? {
        val authSession = db.sessionQueries.getLatest().asFlow().mapToOneOrNull().first()
        return authSession
    }

    suspend fun listLinks(): List<Actor_link>? {
        val linksRes = api.listLinks()
        return when (linksRes) {
            is NetworkResponse.ApiSuccess -> {
                val links = linksRes.body.data
                db.actorQueries.transaction {
                    for (link in links) {
                        db.actorQueries.insertLink(link)
                    }
                }
                links
            }
            else -> null
        }
    }

    suspend fun logout() {
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
