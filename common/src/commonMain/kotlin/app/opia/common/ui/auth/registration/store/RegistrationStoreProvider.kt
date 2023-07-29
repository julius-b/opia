package app.opia.common.ui.auth.registration.store

import OpiaDispatchers
import app.opia.common.api.Code
import app.opia.common.api.NetworkResponse
import app.opia.common.api.model.OwnedFieldScope
import app.opia.common.api.model.OwnedFieldType
import app.opia.common.api.repository.ActorTypeUser
import app.opia.common.db.Owned_field
import app.opia.common.di.ServiceLocator
import app.opia.common.ui.auth.AuthCtx
import app.opia.common.ui.auth.registration.RegistrationState
import app.opia.common.ui.auth.registration.VERIFICATION_CODE_LENGTH
import app.opia.common.ui.auth.registration.store.RegistrationStore.Intent
import app.opia.common.ui.auth.registration.store.RegistrationStore.Label
import app.opia.common.ui.auth.registration.store.RegistrationStore.State
import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

internal class RegistrationStoreProvider(
    private val storeFactory: StoreFactory, private val dispatchers: OpiaDispatchers
) {
    fun provide(): RegistrationStore =
        object : RegistrationStore, Store<Intent, State, Label> by storeFactory.create(
            name = "RegistrationStore",
            initialState = State(),
            bootstrapper = SimpleBootstrapper(Unit),
            executorFactory = ::ExecutorImpl,
            reducer = ReducerImpl
        ) {}

    private sealed class Msg {
        data class UiStateChanged(val uiState: RegistrationState) : Msg()
        data class LoadingChanged(val loading: Boolean) : Msg()
        data class NameChanged(val name: String) : Msg()
        data class NameError(val error: String) : Msg()
        data class HandleChanged(val handle: String) : Msg()
        data class HandleError(val error: String) : Msg()
        data class DOBChanged(val dob: LocalDate) : Msg()
        data class DOBError(val error: String) : Msg()
        data class EmailChanged(val email: String) : Msg()
        data class EmailError(val error: String) : Msg()
        data class PhoneNoChanged(val phoneNo: String) : Msg()
        data class PhoneNoError(val error: String) : Msg()
        data class SecretChanged(val secret: String) : Msg()
        data class SecretError(val error: String) : Msg()
        data class SecretRepeatChanged(val secretRepeat: String) : Msg()

        //data class EnteringVerificationCode(val enteringVerificationCode: Boolean) : Msg()
        data class PhoneVerificationChanged(val phoneVerification: Owned_field?) : Msg()
        data class PhoneVerificationCodeChanged(val verificationCode: String) : Msg()
        data class PhoneVerificationCodeError(val error: String) : Msg()
        data class EmailVerificationChanged(val emailVerification: Owned_field?) : Msg()
        //object Authenticated : Msg()
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<Intent, Unit, State, Msg, Label>(dispatchers.main) {
        override fun executeAction(action: Unit, getState: () -> State) {}

        override fun executeIntent(intent: Intent, getState: () -> State) = when (intent) {
            is Intent.NextToFinalize -> nextToFinalize(getState())
            is Intent.SetUiState -> dispatch(Msg.UiStateChanged(intent.uiState))
            is Intent.SetName -> dispatch(Msg.NameChanged(intent.name))
            is Intent.SetHandle -> dispatch(Msg.HandleChanged(intent.handle))
            is Intent.SetDOB -> dispatch(Msg.DOBChanged(intent.dob))
            is Intent.SetEmail -> dispatch(Msg.EmailChanged(intent.email))
            is Intent.SetPhoneNo -> dispatch(Msg.PhoneNoChanged(intent.phoneNo))
            is Intent.SetSecret -> dispatch(Msg.SecretChanged(intent.secret))
            is Intent.SetSecretRepeat -> dispatch(Msg.SecretRepeatChanged(intent.secretRepeat))
            is Intent.SetPhoneVerificationCode -> dispatch(
                Msg.PhoneVerificationCodeChanged(intent.verificationCode)
            )

            is Intent.ConfirmPhoneVerificationDialog -> confirmPhoneVerification(getState())
            is Intent.DismissPhoneVerificationDialog -> dispatch(
                Msg.PhoneVerificationChanged(null)
            )

            is Intent.Authenticate -> register(getState())
        }

        private fun doRegister(state: State) {
            if (state.phoneVerification == null) {
                println("[!] register > illegal state, expect verificationCode to be set")
                return
            }
            println("[*] register > code: ${state.phoneVerification.verification_code}")

            dispatch(Msg.LoadingChanged(true))
            scope.launch {
                val ownedFields = mutableListOf(state.phoneVerification)
                state.emailVerification?.let { ownedFields.add(it) }
                val res = withContext(Dispatchers.IO) {
                    ServiceLocator.authRepo.register(
                        ActorTypeUser, state.handle, state.name, state.secret, ownedFields
                    )
                }

                println("[*] register > res: $res")
                when (res) {
                    is NetworkResponse.ApiSuccess -> {
                        println("[+] register > actor created: ${res.body}")
                        if (res.body.hints == null) {
                            println("[!] register > expected server hints")
                            publish(Label.UnknownError)
                            return@launch
                        }

                        // TODO should session res contain all active ownedFields? yes, for login!
                        // save session & actor
                        val asRes = withContext(Dispatchers.IO) {
                            ServiceLocator.authRepo.login(state.handle, state.secret)
                        }
                        when (asRes) {
                            is NetworkResponse.ApiSuccess -> {
                                val actorId = asRes.body.data.actor_id
                                val self =
                                    ServiceLocator.database.actorQueries.getById(actorId).asFlow()
                                        .mapToOne().first()
                                publish(
                                    Label.Authenticated(
                                        AuthCtx(
                                            installationId = asRes.body.data.installation_id,
                                            actorId = self.id,
                                            ioid = asRes.body.data.ioid,
                                            secretUpdateId = asRes.body.data.secret_update_id
                                        )
                                    )
                                )
                            }
                            // can't handle ApiError
                            is NetworkResponse.NetworkError -> publish(Label.NetworkError)
                            else -> publish(Label.UnknownError)
                        }
                    }

                    is NetworkResponse.ApiError -> {
                        println("[~] register > actor errors: ${res.body.errors}")
                        if (res.body.errors == null) {
                            println("[!] register > error response has no errors")
                            publish(Label.UnknownError)
                            return@launch
                        }

                        for ((k, v) in res.body.errors) {
                            when (k) {
                                "handle" -> {
                                    for (status in v) {
                                        when (status.code) {
                                            Code.constraint -> dispatch(Msg.HandleError("4-16 alphanumeric characters and underscores"))
                                            Code.conflict -> dispatch(Msg.HandleError("already used"))
                                            else -> dispatch(Msg.HandleError("invalid"))
                                        }
                                    }
                                    dispatch(Msg.UiStateChanged(RegistrationState.StepOne))
                                }

                                "secret" -> dispatch(Msg.SecretError("Password must contain at least 8 characters"))
                                else -> {
                                    // TODO it's possible that an owned-field has been taken -> handle conflict error here as well
                                    println("[!] register > error response has unknown field: '$k'")
                                    publish(Label.UnknownError)
                                }
                            }
                        }
                    }

                    is NetworkResponse.NetworkError -> publish(Label.NetworkError)
                    else -> publish(Label.UnknownError)
                }
            }.invokeOnCompletion {
                dispatch(Msg.LoadingChanged(false))
            }
        }

        private fun confirmPhoneVerification(state: State) {
            if (state.phoneVerification == null) {
                println("[!] register > confirm > illegal state, expect verificationCode to be set")
                return
            }
            println("[*] register > confirm > code: ${state.phoneVerification.verification_code}")

            if (state.phoneVerification.verification_code.length != VERIFICATION_CODE_LENGTH) {
                dispatch(Msg.PhoneVerificationCodeError("Code should be 6 digits"))
                return
            }

            scope.launch {
                // validate ownedField
                val res = ServiceLocator.authRepo.patchOwnedField(
                    state.phoneVerification.id, state.phoneVerification.verification_code
                )
                when (res) {
                    is NetworkResponse.ApiSuccess -> {
                        // only hide dialog & proceed when owned has been validated successfully
                        // update verificationCode -> set updated .valid = true, thereby hiding the dialog
                        // don't set it to null because actor creation could fail
                        dispatch(Msg.PhoneVerificationChanged(res.body.data))

                        // let UI trigger the Registration Intent, current `state` is tainted
                        publish(Label.OwnedFieldConfirmed)
                    }

                    is NetworkResponse.ApiError -> {
                        // TODO parse...
                        dispatch(Msg.PhoneVerificationCodeError("incorrect code"))
                    }

                    is NetworkResponse.NetworkError -> publish(Label.NetworkError)
                    else -> publish(Label.UnknownError)
                }
            }.invokeOnCompletion {
                dispatch(Msg.LoadingChanged(false))
            }
        }

        private fun register(state: State) {
            println("[*] register > handle: ${state.handle}")

            if (state.phoneNo.isBlank()) {
                dispatch(Msg.PhoneNoError("required"))
                return
            }

            dispatch(Msg.LoadingChanged(true))
            scope.launch {
                val installation =
                    withContext(Dispatchers.IO) { ServiceLocator.installationRepo.upsertInstallation() }
                println("[*] register > installation: $installation")
                if (installation == null) {
                    publish(Label.NetworkError)
                    return@launch
                }

                val ownedFieldRequests = mutableListOf<Pair<OwnedFieldType, String>>()

                // create owned phone if the current number hasn't been validated already
                if (state.phoneVerification?.valid != true) ownedFieldRequests += Pair(
                    OwnedFieldType.phone_no, state.phoneNo
                )

                // created owned email if one has been entered it hasn't been validated yet
                // NOTE: valid is never true, just ensure owned exists - `state.emailVerification?.valid != true`
                if (state.email.isNotBlank() && state.emailVerification == null) ownedFieldRequests += Pair(
                    OwnedFieldType.email, state.email
                )

                println("[*] register > ownedFieldRequests: $ownedFieldRequests")
                if (ownedFieldRequests.isEmpty()) {
                    doRegister(state)
                    return@launch
                }

                val ownedFieldResponses = mutableListOf<Owned_field>()
                for (owned in ownedFieldRequests) {
                    val ownedRes = withContext(Dispatchers.IO) {
                        ServiceLocator.authRepo.createOwnedField(
                            OwnedFieldScope.signup, owned.second
                        )
                    }
                    println("[*] register > owned: $ownedRes")
                    when (ownedRes) {
                        is NetworkResponse.ApiSuccess -> {
                            ownedFieldResponses.add(ownedRes.body.data)
                        }

                        is NetworkResponse.ApiError -> {
                            if (ownedRes.body.errors == null) {
                                println("[!] register > owned > error response has no errors")
                                publish(Label.UnknownError)
                                return@launch
                            }
                            for ((k, v) in ownedRes.body.errors) {
                                when (k) {
                                    "content" -> {
                                        for (status in v) {
                                            when (status.code) {
                                                Code.constraint -> {
                                                    when (owned.first) {
                                                        OwnedFieldType.email -> dispatch(
                                                            Msg.EmailError(
                                                                "invalid email"
                                                            )
                                                        )

                                                        OwnedFieldType.phone_no -> dispatch(
                                                            Msg.PhoneNoError(
                                                                "invalid phone number"
                                                            )
                                                        )
                                                    }
                                                }

                                                else -> {}
                                            }
                                        }
                                    }

                                    "value" -> {
                                        for (status in v) {
                                            when (status.code) {
                                                Code.conflict -> {
                                                    when (owned.first) {
                                                        OwnedFieldType.email -> dispatch(
                                                            Msg.EmailError(
                                                                "already used"
                                                            )
                                                        )

                                                        OwnedFieldType.phone_no -> dispatch(
                                                            Msg.PhoneNoError(
                                                                "already used"
                                                            )
                                                        )
                                                    }
                                                }

                                                else -> {}
                                            }
                                        }
                                    }

                                    else -> {
                                        println("[!] register > owned > error response has unknown field: '$k'")
                                        publish(Label.UnknownError)
                                    }
                                }
                            }
                        }

                        is NetworkResponse.NetworkError -> publish(Label.NetworkError)
                        else -> publish(Label.UnknownError)
                    }
                }

                if (ownedFieldRequests.size != ownedFieldResponses.size) {
                    println("[~] register > owned > some fields contained errors, aborting...")
                    return@launch
                }

                // only emit if it was actually sent (otherwise the user changes one and the other gets reset to null)
                if (ownedFieldRequests.any { it.first == OwnedFieldType.email }) {
                    val email = ownedFieldResponses.firstOrNull { it.type == OwnedFieldType.email }
                    dispatch(Msg.EmailVerificationChanged(email))
                }

                if (ownedFieldRequests.any { it.first == OwnedFieldType.phone_no }) {
                    // if new phoneNo has been added (& server requires validation) then show dialog now
                    val phoneNo =
                        ownedFieldResponses.firstOrNull { it.type == OwnedFieldType.phone_no && !it.valid }
                    dispatch(Msg.PhoneVerificationChanged(phoneNo))

                    // otherwise continue account creation (trigger doRegister)
                    if (phoneNo == null) publish(Label.OwnedFieldConfirmed)
                } else publish(Label.OwnedFieldConfirmed)
            }.invokeOnCompletion {
                dispatch(Msg.LoadingChanged(false))
            }
        }

        private fun nextToFinalize(state: State) {
            if (state.name.isBlank()) {
                dispatch(Msg.NameError("required"))
                return
            }
            if (state.handle.isBlank()) {
                dispatch(Msg.HandleError("required"))
                return
            }
            if (state.dob == null) {
                dispatch(Msg.DOBError("required"))
                return
            }
            dispatch(Msg.UiStateChanged(RegistrationState.StepTwo))
        }
    }

    private object ReducerImpl : Reducer<State, Msg> {
        override fun State.reduce(msg: Msg): State = when (msg) {
            is Msg.UiStateChanged -> copy(uiState = msg.uiState)
            is Msg.LoadingChanged -> copy(isLoading = msg.loading)
            is Msg.NameChanged -> copy(name = msg.name, nameError = null)
            is Msg.NameError -> copy(nameError = msg.error)
            is Msg.HandleChanged -> copy(handle = msg.handle, handleError = null)
            is Msg.HandleError -> copy(handleError = msg.error)
            is Msg.DOBChanged -> copy(dob = msg.dob, dobError = null)
            is Msg.DOBError -> copy(dobError = msg.error)
            is Msg.EmailChanged -> copy(
                email = msg.email, emailError = null, emailVerification = null
            )

            is Msg.EmailError -> copy(emailError = msg.error)
            is Msg.PhoneNoChanged -> copy(
                phoneNo = msg.phoneNo,
                phoneNoError = null,
                phoneVerification = null,
                phoneVerificationCode = ""
            )

            is Msg.PhoneNoError -> copy(phoneNoError = msg.error)
            is Msg.SecretChanged -> copy(secret = msg.secret, secretError = null)
            is Msg.SecretError -> copy(secretError = msg.error)
            is Msg.SecretRepeatChanged -> copy(secretRepeat = msg.secretRepeat)
            is Msg.PhoneVerificationChanged -> copy(
                phoneVerification = msg.phoneVerification,
                phoneVerificationCode = msg.phoneVerification?.verification_code ?: "",
                // reset error
                phoneVerificationCodeError = null
            )

            is Msg.PhoneVerificationCodeChanged -> copy(
                phoneVerification = phoneVerification!!.copy(content = msg.verificationCode),
                phoneVerificationCode = msg.verificationCode,
                phoneVerificationCodeError = null
            )

            is Msg.PhoneVerificationCodeError -> copy(phoneVerificationCodeError = msg.error)
            is Msg.EmailVerificationChanged -> copy(emailVerification = msg.emailVerification)
        }
    }
}
