package app.opia.common.ui.splash

import app.opia.common.ui.splash.OpiaSplash.Next
import app.opia.common.ui.splash.SplashStore.Label
import app.opia.common.ui.splash.SplashStore.State
import com.arkivanov.mvikotlin.core.store.Store
import java.util.*

internal interface SplashStore : Store<Nothing, State, Label> {

    // NOTE: remove or `object` once publish solution works
    data class State(
        val next: Next? = null
    )

    sealed class Label {
        object Auth : Label()
        data class Main(val selfId: UUID) : Label()
    }
}
