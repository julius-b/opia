package app.opia.common.ui.home.store

import OpiaDispatchers
import app.opia.common.db.Actor
import app.opia.common.di.ServiceLocator
import app.opia.common.ui.auth.AuthCtx
import app.opia.common.ui.home.store.HomeStore.State
import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class HomeStoreProvider(
    private val storeFactory: StoreFactory,
    private val dispatchers: OpiaDispatchers,
    private val authCtx: AuthCtx,
    private val logout: () -> Unit
) {
    private val db = ServiceLocator.database

    fun provide(): HomeStore =
        object : HomeStore, Store<Nothing, State, Nothing> by storeFactory.create(
            name = "HomeStore",
            initialState = State(),
            bootstrapper = SimpleBootstrapper(Action.Start),
            executorFactory = ::ExecutorImpl,
            reducer = ReducerImpl
        ) {}

    private sealed interface Action {
        object Start : Action
    }

    private sealed class Msg {
        data class SelfUpdated(val self: Actor) : Msg()
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<Nothing, Action, State, Msg, Nothing>(dispatchers.main) {
        override fun executeAction(action: Action, getState: () -> State) = when (action) {
            is Action.Start -> loadStateFromDb()
        }

        private fun loadStateFromDb() {
            println("[*] HomeStore - initializing...")
            if (!ServiceLocator.isAuthenticated()) {
                println("[~] HomeStore - not authenticated, expecting logout...")
                return
            }
            scope.launch {
                // register UI-specific logout
                ServiceLocator.authCtx.addLogoutHandler(logout)

                val self = withContext(ServiceLocator.dispatchers.io) {
                    db.actorQueries.getById(authCtx.actorId).executeAsOne()
                }
                dispatch(Msg.SelfUpdated(self))
            }
        }
    }

    private object ReducerImpl : Reducer<State, Msg> {
        override fun State.reduce(msg: Msg) = when (msg) {
            is Msg.SelfUpdated -> copy(self = msg.self)
        }
    }
}
