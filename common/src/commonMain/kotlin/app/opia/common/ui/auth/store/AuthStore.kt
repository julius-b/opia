package app.opia.common.ui.auth.store

import app.opia.common.ui.auth.store.AuthStore.*
import com.arkivanov.mvikotlin.core.store.Store
import java.util.*

enum class IdentityProvider {
    Google, Apple
}

internal interface AuthStore : Store<Intent, State, Label> {
    sealed class Intent {
        data class SetUnique(val unique: String) : Intent()
        data class SetSecret(val secret: String) : Intent()
        object Login : Intent()
    }

    data class State(
        val isLoading: Boolean = false,
        val unique: String = "",
        val uniqueError: String? = null,
        val secret: String = "",
        val secretError: String? = null
    )

    sealed class Label {
        data class Authenticated(val selfId: UUID) : Label()
        object NetworkError : Label()
        object UnknownError : Label()
    }
}
