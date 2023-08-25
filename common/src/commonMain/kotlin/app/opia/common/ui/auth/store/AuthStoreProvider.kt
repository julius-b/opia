package app.opia.common.ui.auth.store

import app.opia.common.api.Code
import app.opia.common.api.NetworkResponse
import app.opia.common.di.ServiceLocator
import app.opia.common.ui.auth.AuthCtx
import app.opia.common.ui.auth.store.AuthStore.Intent
import app.opia.common.ui.auth.store.AuthStore.Label
import app.opia.common.ui.auth.store.AuthStore.State
import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AuthStoreProvider(
    private val storeFactory: StoreFactory
) {
    private val dispatchers = ServiceLocator.dispatchers
    private val db = ServiceLocator.database
    private val installationRepo = ServiceLocator.installationRepo
    private val authRepo = ServiceLocator.authRepo

    fun provide(): AuthStore =
        object : AuthStore, Store<Intent, State, Label> by storeFactory.create(
            name = "AuthStore",
            initialState = State(),
            executorFactory = ::ExecutorImpl,
            reducer = ReducerImpl
        ) {}

    private sealed class Msg {
        data class LoadingChanged(val loading: Boolean) : Msg()
        data class UniqueChanged(val unique: String) : Msg()
        data class SecretChanged(val secret: String) : Msg()
        data class UniqueError(val error: String) : Msg()
        data class SecretError(val error: String) : Msg()
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<Intent, Unit, State, Msg, Label>(dispatchers.main) {

        override fun executeIntent(intent: Intent, getState: () -> State) = when (intent) {
            is Intent.SetUnique -> dispatch(Msg.UniqueChanged(intent.unique))
            is Intent.SetSecret -> dispatch(Msg.SecretChanged(intent.secret))
            is Intent.Login -> login(getState())
        }

        private fun login(state: State) {
            println("[+] login > unique: ${state.unique}")
            dispatch(Msg.LoadingChanged(true))

            scope.launch {
                // withContext: fix stuttering
                val installation = withContext(dispatchers.io) {
                    installationRepo.upsertInstallation()
                }
                println("[*] login > installation: $installation")
                if (installation == null) {
                    publish(Label.NetworkError)
                    return@launch
                }

                // save session & actor
                val authRes = withContext(dispatchers.io) {
                    authRepo.login(state.unique, state.secret)
                }
                when (authRes) {
                    is NetworkResponse.ApiSuccess -> {
                        val actorId = authRes.body.data.actor_id
                        val self = withContext(dispatchers.io) {
                            db.actorQueries.getById(actorId).executeAsOne()
                        }
                        publish(
                            Label.Authenticated(
                                AuthCtx(
                                    installationId = authRes.body.data.installation_id,
                                    actorId = self.id,
                                    ioid = authRes.body.data.ioid,
                                    secretUpdateId = authRes.body.data.secret_update_id
                                )
                            )
                        )
                    }

                    is NetworkResponse.ApiError -> {
                        if (authRes.body.errors == null) {
                            println("[!] login > error response has no errors")
                            publish(Label.UnknownError)
                            return@launch
                        }
                        // TODO expired: secret needs to be updated
                        for ((k, v) in authRes.body.errors) {
                            when (k) {
                                "unique" -> {
                                    // find first error
                                    for (status in v) {
                                        when (status.code) {
                                            Code.required -> {
                                                dispatch(Msg.UniqueError("Username required"))
                                                return@launch
                                            }

                                            Code.reference -> {
                                                dispatch(Msg.UniqueError("No account for this username"))
                                                return@launch
                                            }

                                            else -> {}
                                        }
                                    }
                                    dispatch(Msg.UniqueError(v[0].code.toString()))
                                }

                                "secret" -> {
                                    for (status in v) {
                                        when (status.code) {
                                            Code.unauthenticated -> {
                                                dispatch(Msg.SecretError("Incorrect password"))
                                                return@launch
                                            }

                                            else -> {}
                                        }
                                    }
                                    dispatch(Msg.SecretError(v[0].code.toString()))
                                }

                                else -> {
                                    println("[!] login > error response has unknown field: '$k'")
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
    }

    private object ReducerImpl : Reducer<State, Msg> {
        override fun State.reduce(msg: Msg) = when (msg) {
            is Msg.LoadingChanged -> copy(isLoading = msg.loading)
            is Msg.UniqueChanged -> copy(unique = msg.unique, uniqueError = null)
            is Msg.UniqueError -> copy(uniqueError = msg.error)
            is Msg.SecretChanged -> copy(secret = msg.secret, secretError = null)
            is Msg.SecretError -> copy(secretError = msg.error)
        }
    }
}
