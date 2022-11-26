package app.opia.common.ui.auth.integration

import app.opia.common.di.ServiceLocator
import app.opia.common.ui.auth.OpiaAuth
import app.opia.common.ui.auth.OpiaAuth.*
import app.opia.common.ui.auth.store.AuthStore.Intent
import app.opia.common.ui.auth.store.AuthStoreProvider
import app.opia.common.ui.auth.store.IdentityProvider
import app.opia.common.utils.asValue
import app.opia.common.utils.getStore
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.labels
import com.badoo.reaktive.base.Consumer
import com.badoo.reaktive.base.invoke
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AuthComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    di: ServiceLocator,
    private val output: Consumer<Output>
) : OpiaAuth, ComponentContext by componentContext {

    private val store = instanceKeeper.getStore {
        AuthStoreProvider(
            storeFactory = storeFactory, di = di
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

    override fun onLoginClicked() {
        store.accept(Intent.Login)
    }

    override fun onRegisterClicked() {
        output(Output.Register)
    }

    override fun onContinueWithProviderClicked(provider: IdentityProvider) {
        output(Output.ContinueWithProvider(provider))
    }

    override fun onAuthenticated() {
        output(Output.Authenticated)
    }
}
