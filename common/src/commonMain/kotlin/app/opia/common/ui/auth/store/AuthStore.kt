package app.opia.common.ui.auth.store

import app.opia.common.ui.auth.AuthCtx
import app.opia.common.ui.auth.store.AuthStore.Intent
import app.opia.common.ui.auth.store.AuthStore.Label
import app.opia.common.ui.auth.store.AuthStore.State
import com.arkivanov.mvikotlin.core.store.Store

enum class IdentityProvider {
    Google, Apple
}

internal interface AuthStore : Store<Intent, State, Label> {
    sealed class Intent {
        data class SetUnique(val unique: String) : Intent()
        data class SetSecret(val secret: String) : Intent()
        data object Login : Intent()
    }

    data class State(
        val isLoading: Boolean = false,
        val unique: String = "",
        val uniqueError: String? = null,
        val secret: String = "",
        val secretError: String? = null
    )

    sealed class Label {
        data class Authenticated(val authCtx: AuthCtx) : Label()
        data object NetworkError : Label()
        data object UnknownError : Label()
    }
}
