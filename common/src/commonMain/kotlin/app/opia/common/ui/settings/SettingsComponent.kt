package app.opia.common.ui.settings

import app.opia.common.ui.auth.AuthCtx
import app.opia.common.ui.settings.OpiaSettings.Model
import app.opia.common.ui.settings.OpiaSettings.Output
import app.opia.common.ui.settings.store.SettingsStore.Intent
import app.opia.common.ui.settings.store.SettingsStore.State
import app.opia.common.ui.settings.store.SettingsStoreProvider
import app.opia.common.utils.asValue
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import com.arkivanov.mvikotlin.core.store.StoreFactory

class SettingsComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    authCtx: AuthCtx,
    private val output: (Output) -> Unit
) : OpiaSettings, ComponentContext by componentContext {
    private val store = instanceKeeper.getStore {
        SettingsStoreProvider(storeFactory = storeFactory, authCtx = authCtx).provide()
    }

    override val models: Value<Model> = store.asValue().map(stateToModel)

    override fun onDistributorChanged(distributor: String) {
        store.accept(Intent.SetDistributor(distributor))
    }

    override fun onNameChanged(name: String) {
        store.accept(Intent.SetName(name))
    }

    override fun onDescChanged(desc: String) {
        store.accept(Intent.SetDesc(desc))
    }

    override fun updateClicked() {
        store.accept(Intent.UpdateAccount)
    }

    override suspend fun logoutClicked() {
        output(Output.Logout)
    }
}

internal val stateToModel: (State) -> Model = {
    Model(
        self = it.self,
        name = it.name,
        desc = it.desc,
        distributors = it.distributors,
        distributor = it.distributor,
        endpoint = it.endpoint
    )
}
