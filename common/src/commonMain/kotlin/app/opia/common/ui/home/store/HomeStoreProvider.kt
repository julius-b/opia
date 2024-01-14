package app.opia.common.ui.home.store

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
    private val authCtx: AuthCtx
) {
    private val dispatchers = ServiceLocator.dispatchers
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
        data object Start : Action
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
            scope.launch {
                // TODO TODO on logout, this is deleted -> is it an update?
                // TODO TODO if yes, if null -> publish(Logout)
                val self = withContext(dispatchers.io) {
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
