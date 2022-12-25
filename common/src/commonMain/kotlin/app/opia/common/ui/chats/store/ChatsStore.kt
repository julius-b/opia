package app.opia.common.ui.chats.store

import app.opia.common.db.Actor
import app.opia.common.ui.chats.ChatsItem
import app.opia.common.ui.chats.store.ChatsStore.*
import com.arkivanov.mvikotlin.core.store.Store
import java.util.*

interface ChatsStore : Store<Intent, State, Label> {
    sealed class Intent {
        data class SetSearchQuery(val query: String) : Intent()
        object Search : Intent()
        data class OpenChat(val id: UUID) : Intent()
        data class DeleteItem(val id: UUID) : Intent()
    }

    data class State(
        val self: Actor? = null,
        val chats: List<ChatsItem> = emptyList(),
        val searchQuery: String = "",
        val searchError: String? = null
    )

    sealed class Label {
        object SearchFinished : Label()
        data class ChatOpened(val selfId: UUID, val peerId: UUID) : Label()
    }
}
