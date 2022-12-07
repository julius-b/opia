package app.opia.common.di

import app.opia.common.api.RetrofitClient
import app.opia.common.api.repository.ActorRepo
import app.opia.common.api.repository.InstallationRepo
import app.opia.common.api.repository.KeyRepo
import app.opia.common.db.DriverFactory
import app.opia.common.db.createDatabase

class ServiceLocator(driverFactory: DriverFactory) {
    val database = createDatabase(driverFactory)
    val okHttpClient = RetrofitClient.newOkHttpClient(this)
    val retrofitClient = RetrofitClient.newRetrofitClient(okHttpClient)
    val installationRepo = InstallationRepo(
        database.installationQueries, RetrofitClient.newInstallationService(retrofitClient)
    )
    val keyRepo = KeyRepo(this, RetrofitClient.newKeyService(retrofitClient))
    val actorRepo = ActorRepo(this, RetrofitClient.newActorService(retrofitClient))
    val messagingService = RetrofitClient.newMessagingService(retrofitClient)
}
