package app.opia.common.ui.splash

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
import com.badoo.reaktive.base.Consumer
import com.badoo.reaktive.base.invoke
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal val stateToModel: (State) -> Model = {
    Model(
        next = it.next
    )
}

internal val labelToEvent: (Label) -> Event = {
    when (it) {
        is Label.Auth -> Event.Auth
        is Label.Main -> Event.Main
    }
}

class SplashComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    di: ServiceLocator,
    private val output: Consumer<Output>
) : OpiaSplash, ComponentContext by componentContext {

    private val store = instanceKeeper.getStore {
        SplashStoreProvider(
            storeFactory = storeFactory, di = di
        ).provide()
    }

    override val models: Value<Model> = store.asValue().map(stateToModel)

    override val events: Flow<Event> = store.labels.map(transform = labelToEvent)

    override fun onNext(to: Output) {
        output(to)
    }
}
