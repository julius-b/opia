package app.opia.common.ui.splash

import java.util.*

interface OpiaSplash {

    fun onNext(to: Output)

    sealed class Output {
        object Auth : Output()
        data class Main(val selfId: UUID) : Output()
    }
}
