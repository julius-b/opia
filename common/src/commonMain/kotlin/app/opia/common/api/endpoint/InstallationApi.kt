package app.opia.common.api.endpoint

import app.opia.common.api.PlainApiSuccess
import app.opia.common.api.model.ApiInstallation
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

interface InstallationApi {
    @GET("installation")
    suspend fun list(): PlainApiSuccess<List<ApiInstallation>>

    @PUT("installation")
    suspend fun put(@Body installation: ApiInstallation): PlainApiSuccess<ApiInstallation>
}
