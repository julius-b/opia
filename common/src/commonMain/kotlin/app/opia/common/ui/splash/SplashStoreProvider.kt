package app.opia.common.ui.splash

import app.opia.common.di.ServiceLocator
import app.opia.common.ui.splash.SplashStore.Label
import app.opia.common.ui.splash.SplashStore.State
import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch
import mainDispatcher

internal class SplashStoreProvider(
    private val storeFactory: StoreFactory, private val di: ServiceLocator
) {
    fun provide(): SplashStore =
        object : SplashStore, Store<Nothing, State, Label> by storeFactory.create(
            name = "SplashStore",
            initialState = State(),
            bootstrapper = SimpleBootstrapper(Action.Start),
            executorFactory = ::ExecutorImpl,
            reducer = ReducerImpl
        ) {}

    private sealed interface Action {
        object Start : Action
    }

    private sealed class Msg {
        data class Next(val next: OpiaSplash.Next) : Msg()
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<Nothing, Action, State, Msg, Label>(mainDispatcher()) {
        override fun executeAction(action: Action, getState: () -> State) {
            println("Splash > action: $action")
            when (action) {
                is Action.Start -> loadStateFromDb()
            }
        }

        private fun loadStateFromDb() {
            scope.launch {
                val sess = di.actorRepo.getLatestAuthSession()

                // events published during executeAction are not received by consumers because
                // flow collection hasn't yet started. The problem is mentioned in the docs.
                // A dirty fix: `delay(1000L)`. Alts:
                // - sending a Msg from UI LaunchedEffect _after_ collecting
                // - publish update as state, therefore new received will receive the correct value as well
                if (sess != null) {
                    publish(Label.Main)
                    dispatch(Msg.Next(OpiaSplash.Next.MAIN))
                } else {
                    publish(Label.Auth)
                    dispatch(Msg.Next(OpiaSplash.Next.AUTH))
                }
            }
        }
    }

    private object ReducerImpl : Reducer<State, Msg> {
        override fun State.reduce(msg: Msg): State = when (msg) {
            is Msg.Next -> copy(next = msg.next)
        }
    }
}
