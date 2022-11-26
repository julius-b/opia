package app.opia.common.api.repository

import app.opia.common.api.ApiResponse
import app.opia.common.api.HintedApiSuccess
import app.opia.common.api.NetworkResponse
import app.opia.common.api.PlainApiSuccess
import app.opia.common.api.endpoint.ActorApi
import app.opia.common.api.model.*
import app.opia.common.db.Actor
import app.opia.common.db.Auth_session
import app.opia.common.db.Owned_field
import app.opia.db.OpiaDatabase
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import kotlinx.coroutines.flow.first
import java.util.*

const val ActorTypeUser = 'u'

class ActorRepo(
    private val db: OpiaDatabase, private val api: ActorApi
) {
    suspend fun getAuthHeader(accessToken: String? = null): String {
        if (accessToken == null) {
            // NOTE: fail is no session exists
            // TODO handle outdated session here?
            val authSession = db.sessionQueries.getLatest().asFlow().mapToOne().first()
            return "Bearer ${authSession.access_token}"
        }
        return "Bearer $accessToken"
    }

    suspend fun createOwnedField(
        scope: OwnedFieldScope, content: String
    ): PlainApiSuccess<Owned_field> {
        val installation = db.installationQueries.getSelf().asFlow().mapToOneOrNull().first()
            ?: throw IllegalStateException("installation required for owned field")

        return api.postOwned(installation.id, CreateOwnedFieldParams(scope, content))
    }

    suspend fun patchOwnedField(
        id: UUID, verificationCode: String
    ): PlainApiSuccess<Owned_field> {
        val res = api.patchOwned(id, PatchOwnedFieldParams(verificationCode))
        return res
    }

    // login does not just create an authSession but also saves it & the actor in the db
    suspend fun login(
        unique: String, secret: String
    ): HintedApiSuccess<Auth_session, AuthHints> {
        val installation = db.installationQueries.getSelf().asFlow().mapToOneOrNull().first()
            ?: throw IllegalStateException("installation required for auth_session")

        // TODO query for existing io's? expect caller to truncate db beforehand otherwise
        val asRes = api.postAuthSession(
            installation.id, CreateAuthSessionParams(unique, secret, true, null)
        )
        if (asRes !is NetworkResponse.ApiSuccess) return asRes

        val authSession = asRes.body.data

        // internet/other failure still possible - user needs to try again, a new session is created
        val actorRes = api.get(getAuthHeader(authSession.access_token), authSession.actor_id)
        if (actorRes !is NetworkResponse.ApiSuccess) {
            println("[!] ActorRepo > get-actor > bad res: $actorRes")
            return NetworkResponse.UnknownError()
        }

        // ensure actor is available in db when session is queries
        db.transaction {
            db.sessionQueries.insert(authSession)
            db.actorQueries.insert(actorRes.body.data)
            asRes.body.hints!!.owned_fields.forEach {
                db.owned_fieldQueries.insert(it)
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
        val res = api.post(installation.id, ownedFields.map { it.id.toString() }, params)
        if (res !is NetworkResponse.ApiSuccess) return res

        db.actorQueries.insert(res.body.data)
        res.body.hints!!.owned_fields.forEach {
            db.owned_fieldQueries.insert(it)
        }

        return res
    }

    // TODO: cache, clearCache on auth
    suspend fun getActor(id: UUID) {
        val res = api.get(getAuthHeader(), id)
    }

    suspend fun getActorByHandle(handle: String) {}

    // if null
    suspend fun getLatestAuthSession(
        refreshIfExpired: Boolean = false
    ): Auth_session? {
        val authSession = db.sessionQueries.getLatest().asFlow().mapToOneOrNull().first()
        return authSession
    }

    suspend fun logout() {
        db.transaction {
            db.owned_fieldQueries.truncate()
            db.sessionQueries.truncate()
            db.actorQueries.truncate()
            db.installationQueries.truncateOwnerships() // not installation itself
        }
    }
}
