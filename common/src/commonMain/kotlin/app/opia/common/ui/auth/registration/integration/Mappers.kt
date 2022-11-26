package app.opia.common.ui.auth.registration.integration

import app.opia.common.ui.auth.registration.OpiaRegistration.Event
import app.opia.common.ui.auth.registration.OpiaRegistration.Model
import app.opia.common.ui.auth.registration.store.RegistrationStore.Label
import app.opia.common.ui.auth.registration.store.RegistrationStore.State

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
        is Label.Authenticated -> Event.Authenticated
        is Label.NetworkError -> Event.NetworkError
        is Label.UnknownError -> Event.UnknownError
    }
}
