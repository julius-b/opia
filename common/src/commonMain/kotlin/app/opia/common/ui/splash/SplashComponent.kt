package app.opia.common.ui.splash

import app.opia.common.ui.splash.OpiaSplash.Output
import app.opia.common.utils.getStore
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.mvikotlin.core.store.StoreFactory

class SplashComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    private val output: (Output) -> Unit
) : OpiaSplash, ComponentContext by componentContext {
    private val store = instanceKeeper.getStore {
        SplashStoreProvider(storeFactory = storeFactory, onNext = ::onNext).provide()
    }

    override fun onNext(to: Output) {
        output(to)
    }
}
