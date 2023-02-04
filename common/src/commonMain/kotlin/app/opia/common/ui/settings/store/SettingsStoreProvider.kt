package app.opia.common.ui.settings.store

import OpiaDispatchers
import app.opia.common.api.NetworkResponse
import app.opia.common.api.model.PatchActorParams
import app.opia.common.db.Actor
import app.opia.common.di.ServiceLocator
import app.opia.common.ui.auth.AuthCtx
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
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class SettingsStoreProvider(
    componentContext: AppComponentContext,
    private val storeFactory: StoreFactory,
    private val di: ServiceLocator,
    private val dispatchers: OpiaDispatchers,
    private val authCtx: AuthCtx
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
        data class DistributorsUpdated(val distributors: List<String>) : Msg()
        data class DistributorUpdated(val distributor: String?) : Msg()
        data class EndpointUpdated(val endpoint: String?) : Msg()
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<Intent, Action, State, Msg, Nothing>(dispatchers.main) {
        override fun executeAction(action: Action, getState: () -> State) = when (action) {
            is Action.Start -> loadStateFromDb(getState())
        }

        override fun executeIntent(intent: Intent, getState: () -> State) = when (intent) {
            is Intent.SetDistributor -> onDistributorChanged(intent.distributor)
            is Intent.SetName -> dispatch(Msg.NameUpdated(intent.name))
            is Intent.SetDesc -> dispatch(Msg.DescUpdated(intent.desc))
            is Intent.UpdateAccount -> updateAccount(getState())
        }

        private fun loadStateFromDb(state: State) {
            scope.launch {
                db.actorQueries.getById(authCtx.actorId).asFlow().mapToOne().collectLatest { self ->
                    println("[*] Settings > self: $self")
                    dispatch(Msg.SelfUpdated(self))
                    dispatch(Msg.NameUpdated(self.name))
                    dispatch(Msg.DescUpdated(self.desc))
                }
            }
            scope.launch {
                db.msgQueries.getNotificationConfig(authCtx.actorId, authCtx.ioid).asFlow()
                    .mapToOneOrNull().collectLatest { nc ->
                        dispatch(Msg.DistributorUpdated(nc?.provider))
                        dispatch(Msg.EndpointUpdated(nc?.endpoint))
                    }
            }
            scope.launch {
                val distributors = di.notificationRepo.listDistributors()
                println("[*] Settings > distributors: $distributors")
                dispatch(Msg.DistributorsUpdated(distributors))
            }
        }

        private fun onDistributorChanged(distributor: String) {
            dispatch(Msg.DistributorUpdated(distributor))
            di.notificationRepo.registerUnifiedPush(
                authCtx.ioid.toString(),
                if (distributor == "none") null else distributor
            )
            // TODO
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
            is Msg.DistributorsUpdated -> copy(distributors = msg.distributors)
            is Msg.DistributorUpdated -> copy(distributor = msg.distributor)
            is Msg.EndpointUpdated -> copy(endpoint = msg.endpoint)
        }
    }
}
