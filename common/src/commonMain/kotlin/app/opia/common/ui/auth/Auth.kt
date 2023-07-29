package app.opia.common.ui.auth

import app.opia.common.ui.auth.store.IdentityProvider
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface OpiaAuth {

    val models: Value<Model>

    // only consumed once and never reproduced/recreated, similar to BroadcastChannel
    val events: Flow<Event>

    fun onUniqueChanged(unique: String)

    fun onSecretChanged(secret: String)

    fun onLogin()

    fun onRegister()

    fun onContinueWithProvider(provider: IdentityProvider)

    fun onAuthenticated(authCtx: AuthCtx)

    data class Model(
        val isLoading: Boolean,
        val unique: String,
        val uniqueError: String?,
        val secret: String,
        val secretError: String?
    )

    sealed class Event {
        data class Authenticated(val authCtx: AuthCtx) : Event()
        object NetworkError : Event()
        object UnknownError : Event()
    }

    sealed class Output {
        data class Authenticated(val authCtx: AuthCtx) : Output()
        data class ContinueWithProvider(val provider: IdentityProvider) : Output()
        object Register : Output()
    }
}

// info that remains constant per refreshToken
@Parcelize
data class AuthCtx(
    val installationId: UUID, val actorId: UUID, val ioid: UUID, val secretUpdateId: UUID
) : Parcelable
