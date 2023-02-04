package app.opia.common.di

import OpiaDispatchers
import app.opia.common.NotificationRepo
import app.opia.common.api.RetrofitClient
import app.opia.common.api.repository.AuthRepo
import app.opia.common.api.repository.InstallationRepo
import app.opia.common.db.DriverFactory
import app.opia.common.db.createDatabase
import app.opia.db.OpiaDatabase
import okhttp3.OkHttpClient
import retrofit2.Retrofit

// TODO use Koin
// Only provides unauthenticated repositories
class ServiceLocator(
    driverFactory: DriverFactory,
    val dispatchers: OpiaDispatchers,
    private val _notificationRepo: (OpiaDatabase) -> NotificationRepo
) {
    val database = createDatabase(driverFactory)
    val notificationRepo = _notificationRepo(database)
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
