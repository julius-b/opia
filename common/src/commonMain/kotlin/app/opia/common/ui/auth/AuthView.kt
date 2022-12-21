package app.opia.common.ui.auth

import app.opia.common.ui.auth.AuthView.Event
import app.opia.common.ui.auth.AuthView.Model
import app.opia.common.ui.auth.store.IdentityProvider
import com.arkivanov.mvikotlin.core.view.MviView
import java.util.*

interface AuthView : MviView<Model, Event> {
    //val generalError: String?,
    data class Model(
        val isLoading: Boolean,
        val unique: String,
        val uniqueError: String?,
        val secret: String,
        val secretError: String?
    )

    // Intent / ViewEvent
    sealed class Event {
        data class UniqueChanged(val unique: String) : Event()
        data class SecretChanged(val secret: String) : Event()
        object LoginClicked : Event()
        object RegisterClicked : Event()
        data class ContinueWithProviderClicked(val provider: IdentityProvider) : Event()
    }

    sealed class Output {
        data class Authenticated(val selfId: UUID) : Output()
        data class ContinueWithProvider(val provider: IdentityProvider) : Output()
        object Register : Output()
    }
}
