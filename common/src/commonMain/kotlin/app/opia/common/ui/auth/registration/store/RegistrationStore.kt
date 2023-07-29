package app.opia.common.ui.auth.registration.store

import app.opia.common.db.Owned_field
import app.opia.common.ui.auth.AuthCtx
import app.opia.common.ui.auth.registration.RegistrationState
import app.opia.common.ui.auth.registration.store.RegistrationStore.Intent
import app.opia.common.ui.auth.registration.store.RegistrationStore.Label
import app.opia.common.ui.auth.registration.store.RegistrationStore.State
import com.arkivanov.mvikotlin.core.store.Store
import java.time.LocalDate

internal interface RegistrationStore : Store<Intent, State, Label> {
    sealed class Intent {
        object NextToFinalize : Intent()
        data class SetUiState(val uiState: RegistrationState) : Intent()
        data class SetName(val name: String) : Intent()
        data class SetHandle(val handle: String) : Intent()
        data class SetDOB(val dob: LocalDate) : Intent()
        data class SetEmail(val email: String) : Intent()
        data class SetPhoneNo(val phoneNo: String) : Intent()
        data class SetSecret(val secret: String) : Intent()
        data class SetSecretRepeat(val secretRepeat: String) : Intent()
        data class SetPhoneVerificationCode(val verificationCode: String) : Intent()
        object ConfirmPhoneVerificationDialog : Intent()
        object DismissPhoneVerificationDialog : Intent()
        object Authenticate : Intent()
    }

    data class State(
        val uiState: RegistrationState = RegistrationState.StepOne,
        val isLoading: Boolean = false,
        val name: String = "",
        val nameError: String? = null,
        val handle: String = "",
        val handleError: String? = null,
        val dob: LocalDate? = null,
        val dobError: String? = null,
        val email: String = "",
        val emailError: String? = null,
        val phoneNo: String = "",
        val phoneNoError: String? = null,
        val secret: String = "",
        val secretError: String? = null,
        val secretRepeat: String = "",
        //val enteringVerificationCode: Boolean = false,
        val phoneVerification: Owned_field? = null,
        val phoneVerificationCode: String = "", // update inside Owned_field is not understood
        val phoneVerificationCodeError: String? = null,
        val emailVerification: Owned_field? = null
    )

    sealed class Label {
        object OwnedFieldConfirmed : Label()
        data class Authenticated(val authCtx: AuthCtx) : Label()
        object NetworkError : Label()
        object UnknownError : Label()
    }
}
