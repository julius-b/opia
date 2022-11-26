package app.opia.common.api.endpoint

import app.opia.common.api.HintedApiSuccess
import app.opia.common.api.PlainApiSuccess
import app.opia.common.api.model.*
import app.opia.common.db.Actor
import app.opia.common.db.Auth_session
import app.opia.common.db.Owned_field
import retrofit2.http.*
import java.util.*

interface ActorApi {
    @POST("actor/owned")
    suspend fun postOwned(
        @Header(InstallationId) installationId: UUID, @Body params: CreateOwnedFieldParams
    ): PlainApiSuccess<Owned_field>

    @PATCH("actor/owned/{ownedFieldId}")
    suspend fun patchOwned(
        @Path("ownedFieldId") id: UUID, @Body params: PatchOwnedFieldParams
    ): PlainApiSuccess<Owned_field>

    @GET("actor")
    suspend fun list(@Header(Authorization) authorization: String): PlainApiSuccess<List<Actor>>

    @GET("actor/{id}")
    suspend fun get(
        @Header(Authorization) authorization: String, @Path("id") id: UUID
    ): PlainApiSuccess<Actor>

    @POST("actor")
    suspend fun post(
        @Header(InstallationId) installationId: UUID,
        @Header(ChallengeResponse) challengeResponses: List<String>,
        @Body params: CreateActorParams
    ): HintedApiSuccess<Actor, AuthHints>

    @GET("actor/by-handle/{handle}")
    suspend fun getByHandle(
        @Header(Authorization) authorization: String, @Path("handle") handle: String
    ): PlainApiSuccess<Actor>

    @POST("auth_session")
    suspend fun postAuthSession(
        @Header(InstallationId) installationId: UUID, @Body params: CreateAuthSessionParams
    ): HintedApiSuccess<Auth_session, AuthHints>
}
