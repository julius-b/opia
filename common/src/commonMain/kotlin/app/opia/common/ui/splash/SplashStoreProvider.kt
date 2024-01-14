package app.opia.common.ui.splash

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
    private val storeFactory: StoreFactory, private val onNext: (to: Output) -> Unit
) {
    private val dispatchers = ServiceLocator.dispatchers
    private val db = ServiceLocator.database

    fun provide(): SplashStore =
        object : SplashStore, Store<Nothing, Unit, Nothing> by storeFactory.create(
            name = "SplashStore",
            initialState = Unit,
            bootstrapper = SimpleBootstrapper(Action.Start),
            executorFactory = ::ExecutorImpl
        ) {}

    private sealed interface Action {
        data object Start : Action
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<Nothing, Action, Unit, Nothing, Nothing>(dispatchers.main) {
        override fun executeAction(action: Action, getState: () -> Unit) = when (action) {
            is Action.Start -> loadStateFromDb()
        }

        private fun loadStateFromDb() {
            println("[*] Splash > init")
            scope.launch {
                val sess = withContext(dispatchers.io) {
                    db.sessionQueries.getLatest().executeAsOneOrNull()
                }

                // events published during executeAction are not received by consumers because
                // flow collection hasn't yet started. The problem is mentioned in the docs.
                // A dirty fix: `delay(1000L)`. Alts:
                // - sending a Msg from UI LaunchedEffect _after_ collecting
                // - publish update as state, therefore new received will receive the correct value as well
                if (sess != null) {
                    val authCtx = AuthCtx(
                        installationId = sess.installation_id,
                        actorId = sess.actor_id,
                        ioid = sess.ioid,
                        secretUpdateId = sess.secret_update_id,
                        refreshToken = sess.refresh_token,
                        accessToken = sess.access_token,
                        sessCreatedAt = sess.created_at
                    )
                    withContext(dispatchers.io) {
                        ServiceLocator.login(authCtx)
                    }
                    onNext(Output.Main(authCtx))
                } else {
                    // Auth expects an empty db
                    ServiceLocator.authRepo.logout()
                    onNext(Output.Auth)
                }
            }
        }
    }
}
