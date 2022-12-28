package app.opia.common.api.endpoint

import app.opia.common.api.HintedApiSuccess
import app.opia.common.api.PlainApiSuccess
import app.opia.common.api.model.*
import app.opia.common.db.Actor
import app.opia.common.db.Actor_link
import app.opia.common.db.Auth_session
import app.opia.common.db.Owned_field
import retrofit2.http.*
import java.util.*

interface ActorApi {
    @POST("actor/owned")
    suspend fun createOwned(
        @Header(InstallationId) installationId: UUID, @Body params: CreateOwnedFieldParams
    ): PlainApiSuccess<Owned_field>

    @PATCH("actor/owned/{ownedFieldId}")
    suspend fun patchOwned(
        @Path("ownedFieldId") id: UUID, @Body params: PatchOwnedFieldParams
    ): PlainApiSuccess<Owned_field>

    @GET("actor")
    suspend fun list(): PlainApiSuccess<List<Actor>>

    @PATCH("actor")
    suspend fun patch(@Body params: PatchActorParams): PlainApiSuccess<Actor>

    @GET("actor/{id}")
    suspend fun getUnauthenticated(
        @Header(Authorization) authorization: String, @Path("id") id: UUID
    ): PlainApiSuccess<Actor>

    // (possibly) not yet authenticated
    @GET("actor/{id}")
    suspend fun get(@Path("id") id: UUID): PlainApiSuccess<Actor>

    @POST("actor")
    suspend fun create(
        @Header(InstallationId) installationId: UUID,
        @Header(ChallengeResponse) challengeResponses: List<String>,
        @Body params: CreateActorParams
    ): HintedApiSuccess<Actor, AuthHints>

    @GET("actor/by-handle/{handle}")
    suspend fun getByHandle(@Path("handle") handle: String): PlainApiSuccess<Actor>

    @POST("auth_session")
    suspend fun createAuthSession(
        @Header(InstallationId) installationId: UUID, @Body params: CreateAuthSessionParams
    ): HintedApiSuccess<Auth_session, AuthHints>

    // don't use Auth header since server doesn't like invalid tokens
    @POST("auth_session/refresh")
    suspend fun refreshAuthSession(@Header(RefreshToken) authorization: String): PlainApiSuccess<Auth_session>

    @GET("actor/link")
    suspend fun listLinks(): PlainApiSuccess<List<Actor_link>>
}
