package app.opia.common.ui.settings

import OpiaDispatchers
import app.opia.common.di.ServiceLocator
import com.arkivanov.decompose.value.Value
import app.opia.common.ui.settings.store.SettingsStoreProvider
import com.arkivanov.decompose.ComponentContext
import app.opia.common.ui.settings.OpiaSettings.Model
import app.opia.common.ui.settings.store.SettingsStore.Intent
import app.opia.common.ui.settings.store.SettingsStore.State
import app.opia.common.utils.asValue
import com.arkivanov.decompose.value.operator.map
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import com.arkivanov.mvikotlin.core.store.StoreFactory
import java.util.*

class SettingsComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    di: ServiceLocator,
    dispatchers: OpiaDispatchers,
    selfId: UUID
) : OpiaSettings, ComponentContext by componentContext {
    private val store = instanceKeeper.getStore {
        SettingsStoreProvider(
            storeFactory = storeFactory, di = di, dispatchers = dispatchers, selfId = selfId
        ).provide()
    }

    override val models: Value<Model> = store.asValue().map(stateToModel)
}

internal val stateToModel: (State) -> Model = {
    Model(
        self = it.self
    )
}
