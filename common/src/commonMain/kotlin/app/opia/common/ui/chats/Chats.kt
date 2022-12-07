package app.opia.common.ui.chats

import app.opia.common.db.Actor
import com.arkivanov.decompose.value.Value
import kotlinx.coroutines.flow.Flow
import java.util.*

interface OpiaChats {
    val models: Value<Model>

    val events: Flow<Event>

    fun logout()

    fun onBackClicked()

    fun onChatClicked(peerId: UUID)

    fun continueToChat(selfId: UUID, peerId: UUID)

    fun onSearchUpdated(query: String)

    fun onSearchClicked()

    data class Model(
        val self: Actor?,
        val chats: List<ChatsItem>,
        val searchQuery: String,
        val searchError: String?
    )

    sealed class Event {
        object LoggedOut : Event()
        object SearchFinished : Event()
        data class ChatOpened(val selfId: UUID, val peerId: UUID) : Event()
    }

    sealed class Output {
        object Back : Output()
        data class Selected(val selfId: UUID, val peerId: UUID) : Output()
    }
}
