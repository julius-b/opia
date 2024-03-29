package app.opia.common.ui.auth.registration

import app.opia.common.db.Owned_field
import app.opia.common.ui.auth.AuthCtx
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

const val VERIFICATION_CODE_LENGTH = 6

sealed class RegistrationState : Parcelable {
    @Parcelize
    object StepOne : RegistrationState()

    @Parcelize
    object StepTwo : RegistrationState()
}

interface OpiaRegistration {

    val models: Value<Model>

    val events: Flow<Event>

    fun onNameChanged(name: String)

    fun onHandleChanged(handle: String)

    fun onDOBChanged(dob: LocalDate)

    fun onEmailChanged(email: String)

    fun onPhoneNoChanged(phoneNo: String)

    fun onSecretChanged(secret: String)

    fun onSecretRepeatChanged(secretRepeat: String)

    // only accessible from StepOne, back to auth
    fun onBackToAuthClicked()

    fun onNextToFinalizeClicked()

    fun onBackToRegistrationClicked()

    // NOTE: could accept `type: email|phoneNo`
    fun onPhoneVerificationCodeChanged(verificationCode: String)

    fun confirmPhoneVerificationDialog()

    fun dismissPhoneVerificationDialog()

    fun onAuthenticate()

    fun onAuthenticated(authCtx: AuthCtx)

    data class Model(
        val uiState: RegistrationState,
        val isLoading: Boolean,
        val name: String,
        val nameError: String?,
        val handle: String,
        val handleError: String?,
        val dob: LocalDate?,
        val dobError: String?,
        val email: String,
        val emailError: String?,
        val phoneNo: String,
        val phoneNoError: String?,
        val secret: String,
        val secretError: String?,
        val secretRepeat: String,
        val phoneVerification: Owned_field?,
        val phoneVerificationCode: String,
        val phoneVerificationCodeError: String?,
        val emailVerification: Owned_field?
    )

    sealed class Event {
        data object OwnedFieldConfirmed : Event()
        data class Authenticated(val authCtx: AuthCtx) : Event()
        data object NetworkError : Event()
        data object UnknownError : Event()
    }

    sealed class Output {
        data class Authenticated(val authCtx: AuthCtx) : Output()
        data object BackToAuth : Output()
    }
}
