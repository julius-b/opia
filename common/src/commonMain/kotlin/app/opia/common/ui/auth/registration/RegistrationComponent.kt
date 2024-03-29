package app.opia.common.ui.auth.registration

import app.opia.common.ui.auth.AuthCtx
import app.opia.common.ui.auth.registration.OpiaRegistration.Event
import app.opia.common.ui.auth.registration.OpiaRegistration.Model
import app.opia.common.ui.auth.registration.OpiaRegistration.Output
import app.opia.common.ui.auth.registration.store.RegistrationStore.Intent
import app.opia.common.ui.auth.registration.store.RegistrationStore.Label
import app.opia.common.ui.auth.registration.store.RegistrationStore.State
import app.opia.common.ui.auth.registration.store.RegistrationStoreProvider
import app.opia.common.utils.asValue
import app.opia.common.utils.getStore
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.labels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RegistrationComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    private val output: (Output) -> Unit
) : OpiaRegistration, ComponentContext by componentContext {
    private val store = instanceKeeper.getStore {
        RegistrationStoreProvider(storeFactory = storeFactory).provide()
    }

    override val models: Value<Model> = store.asValue().map(stateToModel)

    override val events: Flow<Event> = store.labels.map(transform = labelToEvent)

    override fun onNameChanged(name: String) {
        store.accept(Intent.SetName(name))
    }

    override fun onHandleChanged(handle: String) {
        store.accept(Intent.SetHandle(handle))
    }

    override fun onDOBChanged(dob: LocalDate) {
        store.accept(Intent.SetDOB(dob))
    }

    override fun onEmailChanged(email: String) {
        store.accept(Intent.SetEmail(email))
    }

    override fun onPhoneNoChanged(phoneNo: String) {
        store.accept(Intent.SetPhoneNo(phoneNo))
    }

    override fun onSecretChanged(secret: String) {
        store.accept(Intent.SetSecret(secret))
    }

    override fun onSecretRepeatChanged(secretRepeat: String) {
        store.accept(Intent.SetSecretRepeat(secretRepeat))
    }

    override fun onBackToAuthClicked() {
        output(Output.BackToAuth)
    }

    override fun onNextToFinalizeClicked() {
        store.accept(Intent.NextToFinalize)
    }

    override fun onBackToRegistrationClicked() {
        store.accept(Intent.SetUiState(RegistrationState.StepOne))
    }

    override fun onPhoneVerificationCodeChanged(verificationCode: String) {
        store.accept(Intent.SetPhoneVerificationCode(verificationCode))
    }

    override fun confirmPhoneVerificationDialog() {
        store.accept(Intent.ConfirmPhoneVerificationDialog)
    }

    override fun dismissPhoneVerificationDialog() {
        store.accept(Intent.DismissPhoneVerificationDialog)
    }

    override fun onAuthenticate() {
        store.accept(Intent.Authenticate)
    }

    override fun onAuthenticated(authCtx: AuthCtx) {
        output(Output.Authenticated(authCtx))
    }
}

internal val stateToModel: (State) -> Model = {
    Model(
        uiState = it.uiState,
        isLoading = it.isLoading,
        name = it.name,
        nameError = it.nameError,
        handle = it.handle,
        handleError = it.handleError,
        dob = it.dob,
        dobError = it.dobError,
        email = it.email,
        emailError = it.emailError,
        phoneNo = it.phoneNo,
        phoneNoError = it.phoneNoError,
        secret = it.secret,
        secretError = it.secretError,
        secretRepeat = it.secretRepeat,
        phoneVerification = it.phoneVerification,
        phoneVerificationCode = it.phoneVerificationCode,
        phoneVerificationCodeError = it.phoneVerificationCodeError,
        emailVerification = it.emailVerification
    )
}

internal val labelToEvent: (Label) -> Event = {
    when (it) {
        is Label.OwnedFieldConfirmed -> Event.OwnedFieldConfirmed
        is Label.Authenticated -> Event.Authenticated(it.authCtx)
        is Label.NetworkError -> Event.NetworkError
        is Label.UnknownError -> Event.UnknownError
    }
}
