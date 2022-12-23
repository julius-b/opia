package app.opia.common.ui.auth

import OpiaDispatchers
import app.opia.common.di.ServiceLocator
import app.opia.common.ui.auth.OpiaAuth.Event
import app.opia.common.ui.auth.OpiaAuth.Model
import app.opia.common.ui.auth.OpiaAuth.Output
import app.opia.common.ui.auth.store.AuthStore.*
import app.opia.common.ui.auth.store.AuthStoreProvider
import app.opia.common.ui.auth.store.IdentityProvider
import app.opia.common.utils.asValue
import app.opia.common.utils.getStore
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.labels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*

class AuthComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    di: ServiceLocator,
    private val dispatchers: OpiaDispatchers,
    private val output: (Output) -> Unit
) : OpiaAuth, ComponentContext by componentContext {
    private val store = instanceKeeper.getStore {
        AuthStoreProvider(
            storeFactory = storeFactory, di = di, dispatchers = dispatchers
        ).provide()
    }

    override val models: Value<Model> = store.asValue().map(stateToModel)

    override val events: Flow<Event> = store.labels.map(transform = labelToEvent)

    override fun onUniqueChanged(unique: String) {
        store.accept(Intent.SetUnique(unique))
    }

    override fun onSecretChanged(secret: String) {
        store.accept(Intent.SetSecret(secret))
    }

    override fun onLogin() {
        store.accept(Intent.Login)
    }

    override fun onRegister() {
        output(Output.Register)
    }

    override fun onContinueWithProvider(provider: IdentityProvider) {
        output(Output.ContinueWithProvider(provider))
    }

    override fun onAuthenticated(selfId: UUID) {
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
        is Label.Authenticated -> Event.Authenticated(it.selfId)
        is Label.NetworkError -> Event.NetworkError
        is Label.UnknownError -> Event.UnknownError
    }
}
