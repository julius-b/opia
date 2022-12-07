package app.opia.common.ui.splash

import com.arkivanov.decompose.value.Value
import kotlinx.coroutines.flow.Flow
import java.util.*

interface OpiaSplash {

    val models: Value<Model>

    val events: Flow<Event>

    fun onNext(to: Output)

    // NOTE: remove once publish solution works
    data class Model(
        val next: Next?
    )

    sealed class Next {
        object Auth : Next()
        data class Main(val selfId: UUID) : Next()
    }

    sealed class Event {
        object Auth : Event()
        data class Main(val selfId: UUID) : Event()
    }

    sealed class Output {
        object Auth : Output()
        data class Main(val selfId: UUID) : Output()
    }
}
