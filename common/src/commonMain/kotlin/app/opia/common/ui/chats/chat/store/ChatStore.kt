package app.opia.common.ui.chats.chat.store

import app.opia.common.db.Actor
import app.opia.common.ui.chats.chat.MessageItem
import app.opia.common.ui.chats.chat.store.ChatStore.*
import com.arkivanov.mvikotlin.core.store.Store

internal interface ChatStore : Store<Intent, State, Label> {
    sealed class Intent {
        data class AddMessage(val txt: String) : Intent()
    }

    data class State(
        val self: Actor? = null, val peer: Actor? = null, val msgs: List<MessageItem> = emptyList()
    )

    sealed class Label {
        object NetworkError : Label()
        object UnknownError : Label()
    }
}
