package app.opia.common.ui.settings.store

import OpiaDispatchers
import app.opia.common.db.Actor
import app.opia.common.di.ServiceLocator
import com.arkivanov.mvikotlin.core.store.Reducer
import app.opia.common.ui.settings.store.SettingsStore.Intent
import app.opia.common.ui.settings.store.SettingsStore.State
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*

internal class SettingsStoreProvider(
    private val storeFactory: StoreFactory,
    private val di: ServiceLocator,
    private val dispatchers: OpiaDispatchers,
    private val selfId: UUID
) {
    private val db = di.database

    fun provide(): SettingsStore =
        object : SettingsStore, Store<Intent, State, Nothing> by storeFactory.create(
            name = "SettingsStore",
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
        CoroutineExecutor<Intent, Action, State, Msg, Nothing>(dispatchers.main) {
        override fun executeAction(action: Action, getState: () -> State) = when (action) {
            is Action.Start -> loadStateFromDb(getState())
        }

        private fun loadStateFromDb(state: State) {
            scope.launch {
                val self = db.actorQueries.getById(selfId).asFlow().mapToOne().first()
                dispatch(Msg.SelfUpdated(self))
            }
        }

        override fun executeIntent(intent: Intent, getState: () -> State) = when (intent) {
            else -> {}
        }
    }

    private object ReducerImpl : Reducer<State, Msg> {
        override fun State.reduce(msg: Msg) = when (msg) {
            is Msg.SelfUpdated -> copy(self = msg.self)
        }
    }
}
