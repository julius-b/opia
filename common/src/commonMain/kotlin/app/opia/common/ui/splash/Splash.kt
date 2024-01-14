package app.opia.common.ui.splash

import app.opia.common.ui.auth.AuthCtx

interface OpiaSplash {

    fun onNext(to: Output)

    sealed class Output {
        data object Auth : Output()
        data class Main(val authCtx: AuthCtx) : Output()
    }
}
