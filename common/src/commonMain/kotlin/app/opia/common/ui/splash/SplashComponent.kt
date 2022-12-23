package app.opia.common.ui.splash

import OpiaDispatchers
import app.opia.common.di.ServiceLocator
import app.opia.common.ui.splash.OpiaSplash.*
import app.opia.common.ui.splash.SplashStore.Label
import app.opia.common.ui.splash.SplashStore.State
import app.opia.common.utils.asValue
import app.opia.common.utils.getStore
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.labels
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SplashComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    di: ServiceLocator,
    dispatchers: OpiaDispatchers,
    private val output: (Output) -> Unit
) : OpiaSplash, ComponentContext by componentContext {
    private val store = instanceKeeper.getStore {
        SplashStoreProvider(
            storeFactory = storeFactory, di = di, dispatchers = dispatchers
        ).provide()
    }

    override val models: Value<Model> = store.asValue().map(stateToModel)

    override val events: Flow<Event> = store.labels.map(transform = labelToEvent)

    override fun onNext(to: Output) {
        output(to)
    }
}

internal val stateToModel: (State) -> Model = {
    Model(
        next = it.next
    )
}

internal val labelToEvent: (Label) -> Event = {
    when (it) {
        is Label.Auth -> Event.Auth
        is Label.Main -> Event.Main(it.selfId)
    }
}
