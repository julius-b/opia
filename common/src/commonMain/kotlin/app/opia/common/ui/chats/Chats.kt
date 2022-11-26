package app.opia.common.ui.chats

import com.arkivanov.decompose.value.Value
import kotlinx.coroutines.flow.Flow

interface OpiaChats {
    val models: Value<Model>

    val events: Flow<Event>

    fun logout()

    fun back()

    data class Model(
        val items: List<ChatsItem>
    )

    sealed class Event {
        object LoggedOut : Event()
    }

    sealed class Output {
        object Back : Output()
        data class Selected(val id: Long) : Output()
    }
}
