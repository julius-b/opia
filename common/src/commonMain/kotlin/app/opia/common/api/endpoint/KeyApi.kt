package app.opia.common.api.endpoint

import app.opia.common.api.HintedApiSuccess
import app.opia.common.api.PlainApiSuccess
import app.opia.common.api.model.*
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface KeyApi {
    // not yet authenticated
    @GET("key/vault")
    suspend fun getVaultKey(
        @Header(Authorization) authorization: String
    ): PlainApiSuccess<ApiVaultKey>

    // not yet authenticated
    @POST("key/vault")
    suspend fun createVaultKey(
        @Header(Authorization) authorization: String, @Body params: CreateVaultKeyParams
    ): PlainApiSuccess<ApiVaultKey>

    @POST("key")
    suspend fun createKeyPair(
        @Body params: CreateKeyPairParams
    ): PlainApiSuccess<ApiKeyPair>

    @GET("key/kex/ephemeral/outstanding")
    suspend fun listOutstandingEKexKeys(): HintedApiSuccess<OutstandingEKexKeysResponse, OutstandingEKexKeysHints>
}
