package app.opia.common.di

import app.opia.common.api.RetrofitClient
import app.opia.common.api.repository.ActorRepo
import app.opia.common.api.repository.InstallationRepo
import app.opia.common.db.DriverFactory
import app.opia.common.db.createDatabase
import app.opia.db.OpiaDatabase

class ServiceLocator(driverFactory: DriverFactory) {
    val database: OpiaDatabase = createDatabase(driverFactory)
    val installationRepo: InstallationRepo by lazy {
        InstallationRepo(
            database.installationQueries, RetrofitClient.installationService
        )
    }
    val actorRepo: ActorRepo by lazy {
        ActorRepo(
            database, RetrofitClient.actorService
        )
    }
}
