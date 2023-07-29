package app.opia.common.ui.splash

import OpiaDispatchers
import app.opia.common.di.ServiceLocator
import app.opia.common.ui.auth.AuthCtx
import app.opia.common.ui.splash.OpiaSplash.Output
import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class SplashStoreProvider(
    private val storeFactory: StoreFactory,
    private val dispatchers: OpiaDispatchers,
    private val onNext: (to: Output) -> Unit
) {
    fun provide(): SplashStore =
        object : SplashStore, Store<Nothing, Unit, Nothing> by storeFactory.create(
            name = "SplashStore",
            initialState = Unit,
            bootstrapper = SimpleBootstrapper(Action.Start),
            executorFactory = ::ExecutorImpl
        ) {}

    private sealed interface Action {
        object Start : Action
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<Nothing, Action, Unit, Nothing, Nothing>(dispatchers.main) {
        override fun executeAction(action: Action, getState: () -> Unit) = when (action) {
            is Action.Start -> loadStateFromDb()
        }

        private fun loadStateFromDb() {
            println("[*] Splash > init")
            scope.launch {
                withContext(ServiceLocator.dispatchers.io) { ch.oxc.nikea.initCrypto() }

                val sess = withContext(ServiceLocator.dispatchers.io) {
                    ServiceLocator.database.sessionQueries.getLatest().executeAsOneOrNull()
                }

                // events published during executeAction are not received by consumers because
                // flow collection hasn't yet started. The problem is mentioned in the docs.
                // A dirty fix: `delay(1000L)`. Alts:
                // - sending a Msg from UI LaunchedEffect _after_ collecting
                // - publish update as state, therefore new received will receive the correct value as well
                if (sess != null) {
                    val self = withContext(ServiceLocator.dispatchers.io) {
                        ServiceLocator.database.actorQueries.getById(sess.actor_id).executeAsOne()
                    }
                    onNext(
                        Output.Main(
                            AuthCtx(
                                installationId = sess.installation_id,
                                actorId = self.id,
                                ioid = sess.ioid,
                                secretUpdateId = sess.secret_update_id
                            )
                        )
                    )
                } else {
                    // Auth expects an empty db
                    ServiceLocator.authRepo.logout()
                    onNext(Output.Auth)
                }
            }
        }
    }
}
