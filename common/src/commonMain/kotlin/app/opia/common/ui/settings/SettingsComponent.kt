package app.opia.common.ui.settings

import OpiaDispatchers
import app.opia.common.di.ServiceLocator
import app.opia.common.ui.home.AppComponentContext
import app.opia.common.ui.settings.OpiaSettings.Model
import app.opia.common.ui.settings.store.SettingsStore.Intent
import app.opia.common.ui.settings.store.SettingsStore.State
import app.opia.common.ui.settings.store.SettingsStoreProvider
import app.opia.common.utils.asValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import com.arkivanov.mvikotlin.core.store.StoreFactory
import java.util.*

class SettingsComponent(
    componentContext: AppComponentContext,
    storeFactory: StoreFactory,
    di: ServiceLocator,
    dispatchers: OpiaDispatchers,
    selfId: UUID
) : OpiaSettings, AppComponentContext by componentContext {
    private val store = instanceKeeper.getStore {
        SettingsStoreProvider(
            componentContext = componentContext,
            storeFactory = storeFactory,
            di = di,
            dispatchers = dispatchers,
            selfId = selfId
        ).provide()
    }

    override val models: Value<Model> = store.asValue().map(stateToModel)

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
        logout()
    }
}

internal val stateToModel: (State) -> Model = {
    Model(
        self = it.self, name = it.name, desc = it.desc
    )
}
