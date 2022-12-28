package app.opia.common.ui.settings.store

import OpiaDispatchers
import app.opia.common.api.NetworkResponse
import app.opia.common.api.model.PatchActorParams
import app.opia.common.db.Actor
import app.opia.common.di.ServiceLocator
import app.opia.common.ui.home.AppComponentContext
import app.opia.common.ui.settings.store.SettingsStore.Intent
import app.opia.common.ui.settings.store.SettingsStore.State
import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

internal class SettingsStoreProvider(
    componentContext: AppComponentContext,
    private val storeFactory: StoreFactory,
    private val di: ServiceLocator,
    private val dispatchers: OpiaDispatchers,
    private val selfId: UUID
) {
    private val db = di.database
    private val actorApi = componentContext.actorRepo.api

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
        data class NameUpdated(val name: String) : Msg()
        data class DescUpdated(val desc: String) : Msg()
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<Intent, Action, State, Msg, Nothing>(dispatchers.main) {
        override fun executeAction(action: Action, getState: () -> State) = when (action) {
            is Action.Start -> loadStateFromDb(getState())
        }

        override fun executeIntent(intent: Intent, getState: () -> State) = when (intent) {
            is Intent.SetName -> dispatch(Msg.NameUpdated(intent.name))
            is Intent.SetDesc -> dispatch(Msg.DescUpdated(intent.desc))
            is Intent.UpdateAccount -> updateAccount(getState())
        }

        private fun loadStateFromDb(state: State) {
            scope.launch {
                db.actorQueries.getById(selfId).asFlow().mapToOne().collectLatest { self ->
                    println("[*] Settings > self: $self")
                    dispatch(Msg.SelfUpdated(self))
                    dispatch(Msg.NameUpdated(self.name))
                    dispatch(Msg.DescUpdated(self.desc))
                }
            }
        }

        private fun updateAccount(state: State) {
            scope.launch {
                val patchRes = withContext(dispatchers.io) {
                    actorApi.patch(PatchActorParams(name = state.name, desc = state.desc))
                }
                println("[*] Settings > update > res: $patchRes")
                if (patchRes is NetworkResponse.ApiSuccess) {
                    db.actorQueries.insert(patchRes.body.data)
                }
                // TODO publish SnackBar error
            }
        }
    }

    private object ReducerImpl : Reducer<State, Msg> {
        override fun State.reduce(msg: Msg) = when (msg) {
            is Msg.SelfUpdated -> copy(self = msg.self)
            is Msg.NameUpdated -> copy(name = msg.name)
            is Msg.DescUpdated -> copy(desc = msg.desc)
        }
    }
}
