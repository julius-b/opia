package app.opia.common.ui.chats.chat

import app.opia.common.db.Actor
import com.arkivanov.decompose.value.Value
import kotlinx.coroutines.flow.Flow

interface OpiaChat {
    val models: Value<Model>

    val events: Flow<Event>

    fun onBackClicked()

    fun onSendClicked(txt: String)

    data class Model(
        val self: Actor?,
        val peer: Actor?,
        val msgs: List<MessageItem>
    )

    sealed class Event {
        object NetworkError : Event()
        object UnknownError : Event()
    }

    sealed class Output {
        object Back : Output()
    }
}
