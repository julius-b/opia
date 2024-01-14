package app.opia.android

import DefaultDispatchers
import android.app.Application
import app.opia.android.notify.FCMNotificationRepo
import app.opia.common.db.DriverFactory
import app.opia.common.db.createDatabase
import app.opia.common.di.ServiceLocator
import app.opia.common.sync.Notifier
import kotlinx.coroutines.runBlocking

class ApplicationContext : Application() {

    override fun onCreate() = runBlocking {
        super.onCreate()

        val db = createDatabase(DriverFactory(context = this@ApplicationContext))
        ServiceLocator.init(DefaultDispatchers, db, FCMNotificationRepo(), object : Notifier {})

        // init for service
        // idea: SL.initialLogin.await() -> after that it's available and service can test
        // splash & service could both wait for it, but it would also have to be provided by -desktop
    }
}
