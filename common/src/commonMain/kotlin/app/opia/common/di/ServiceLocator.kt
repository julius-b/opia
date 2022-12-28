package app.opia.common.di

import OpiaDispatchers
import app.opia.common.api.RetrofitClient
import app.opia.common.api.repository.AuthRepo
import app.opia.common.api.repository.InstallationRepo
import app.opia.common.db.DriverFactory
import app.opia.common.db.createDatabase
import okhttp3.OkHttpClient
import retrofit2.Retrofit

// TODO use Koin
// Only provides unauthenticated repositories
class ServiceLocator(driverFactory: DriverFactory, val dispatchers: OpiaDispatchers) {
    val database = createDatabase(driverFactory)
    private val okHttpClient: OkHttpClient = RetrofitClient.newOkHttpClient(this) {}
    private val retrofitClient: Retrofit = RetrofitClient.newRetrofitClient(okHttpClient)
    val installationRepo = InstallationRepo(
        database.installationQueries, RetrofitClient.newInstallationService(retrofitClient)
    )
    val authRepo = AuthRepo(
        database,
        RetrofitClient.newActorService(retrofitClient),
        RetrofitClient.newKeyService(retrofitClient)
    )
}
