package app.opia.common.api.repository

import app.opia.common.api.NetworkResponse
import app.opia.common.api.endpoint.ActorApi
import app.opia.common.db.Actor
import app.opia.common.db.Actor_link
import app.opia.db.OpiaDatabase
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import kotlinx.coroutines.flow.first
import java.util.*

const val ActorTypeUser = 'u'

class ActorRepo(
    private val db: OpiaDatabase, val api: ActorApi
) {
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
}
