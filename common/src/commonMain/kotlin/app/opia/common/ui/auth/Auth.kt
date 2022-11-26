package app.opia.common.ui.auth

import app.opia.common.ui.auth.store.IdentityProvider
import com.arkivanov.decompose.value.Value
import kotlinx.coroutines.flow.Flow

interface OpiaAuth {

    val models: Value<Model>

    // only consumed once and never reproduced/recreated, similar to BroadcastChannel
    val events: Flow<Event>

    fun onUniqueChanged(unique: String)

    fun onSecretChanged(secret: String)

    fun onLoginClicked()

    fun onRegisterClicked()

    fun onContinueWithProviderClicked(provider: IdentityProvider)

    fun onAuthenticated()

    data class Model(
        val isLoading: Boolean,
        //val generalError: String?,
        val unique: String,
        val uniqueError: String?,
        val secret: String,
        val secretError: String?
    )

    sealed class Event {
        object Authenticated : Event()
        object NetworkError : Event()
        object UnknownError : Event()
    }

    sealed class Output {
        object Authenticated : Output()
        data class ContinueWithProvider(val provider: IdentityProvider) : Output()
        object Register : Output()
    }
}
