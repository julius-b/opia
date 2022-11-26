package app.opia.common.ui.splash

import com.arkivanov.decompose.value.Value
import kotlinx.coroutines.flow.Flow

interface OpiaSplash {

    val models: Value<Model>

    val events: Flow<Event>

    fun onNext(to: Output)

    // NOTE: remove once publish solution works
    data class Model(
        val next: Next?
    )

    enum class Next {
        AUTH, MAIN
    }

    sealed class Event {
        object Auth : Event()
        object Main : Event()
    }

    sealed class Output {
        object Auth : Output()
        object Main : Output()
    }
}
