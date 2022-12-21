package app.opia.common.ui.auth

import OpiaDispatchers
import app.opia.common.di.ServiceLocator
import app.opia.common.ui.auth.AuthView.*
import app.opia.common.ui.auth.store.AuthStore.*
import app.opia.common.ui.auth.store.AuthStoreProvider
import app.opia.common.ui.auth.store.IdentityProvider
import app.opia.common.utils.getStore
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.mvikotlin.core.binder.BinderLifecycleMode
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.bind
import com.arkivanov.mvikotlin.extensions.coroutines.events
import com.arkivanov.mvikotlin.extensions.coroutines.labels
import com.arkivanov.mvikotlin.extensions.coroutines.states
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import java.util.*

class AuthComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    di: ServiceLocator,
    private val dispatchers: OpiaDispatchers,
    private val output: (Output) -> Unit
) : ComponentContext by componentContext {
    private val store = instanceKeeper.getStore {
        AuthStoreProvider(
            storeFactory = storeFactory, di = di, dispatchers = dispatchers
        ).provide()
    }

    //val errors: Flow<Any>

    init {
        lifecycle.doOnDestroy(store::dispose)

        bind(lifecycle, BinderLifecycleMode.CREATE_DESTROY, dispatchers.unconfined) {
            store.labels.bindTo { label ->
                when (label) {
                    is Label.Authenticated -> output(Output.Authenticated(label.selfId))
                    is Label.NetworkError -> onError()
                    is Label.UnknownError -> onError()
                }
            }
        }
    }

    fun onViewCreated(view: AuthView, viewLifecycle: Lifecycle) {
        bind(viewLifecycle, BinderLifecycleMode.CREATE_DESTROY, dispatchers.unconfined) {
            view.events.mapNotNull(viewEventToIntent) bindTo store
        }

        bind(viewLifecycle, BinderLifecycleMode.START_STOP, dispatchers.unconfined) {
            store.states.map(stateToModel) bindTo view
            view.events bindTo ::onEvent
        }
    }

    // viewEvent
    private fun onEvent(ev: Event) {
        when (ev) {
            is Event.ContinueWithProviderClicked -> output(Output.Register)
            else -> {} // irrelevant
        }
    }

    fun onUniqueChanged(unique: String) {
        store.accept(Intent.SetUnique(unique))
    }

    fun onSecretChanged(secret: String) {
        store.accept(Intent.SetSecret(secret))
    }

    fun onLoginClicked() {
        store.accept(Intent.Login)
    }

    fun onRegisterClicked() {
        output(Output.Register)
    }

    fun onContinueWithProviderClicked(provider: IdentityProvider) {
        output(Output.ContinueWithProvider(provider))
    }

    fun onAuthenticated(selfId: UUID) {
        output(Output.Authenticated(selfId))
    }
}

internal val stateToModel: (State) -> Model = {
    Model(
        isLoading = it.isLoading,
        unique = it.unique,
        uniqueError = it.uniqueError,
        secret = it.secret,
        secretError = it.secretError
    )
}

internal val labelToEvent: (Label) -> Event = {
    when (it) {
        is Label.Authenticated -> Intent.Authenticated(it.selfId)
        is Label.NetworkError -> Event.NetworkError
        is Label.UnknownError -> Event.UnknownError
    }
}

internal val viewEventToIntent: (AuthView.Event) -> Intent? = {
    when (it) {
        is AuthView.Event.UniqueChanged -> Intent.SetUnique(it.unique)
        is AuthView.Event.SecretChanged -> Intent.SetSecret(it.secret)
        is AuthView.Event.LoginClicked -> Intent.Login
        is AuthView.Event.RegisterClicked -> null
        is AuthView.Event.ContinueWithProviderClicked -> null
    }
}
