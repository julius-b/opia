package app.opia.common.ui.splash

import app.opia.common.ui.splash.SplashStore.Label
import app.opia.common.ui.splash.SplashStore.State
import com.arkivanov.mvikotlin.core.store.Store

internal interface SplashStore : Store<Nothing, State, Label> {

    // NOTE: remove or `object` once publish solution works
    data class State(
        val next: OpiaSplash.Next? = null
    )

    sealed class Label {
        object Auth : Label()
        object Main : Label()
    }
}
