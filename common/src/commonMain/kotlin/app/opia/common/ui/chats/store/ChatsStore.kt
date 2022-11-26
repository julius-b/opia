package app.opia.common.ui.chats.store

import app.opia.common.ui.chats.ChatsItem
import app.opia.common.ui.chats.store.ChatsStore.*
import com.arkivanov.mvikotlin.core.store.Store
import java.util.*

interface ChatsStore : Store<Intent, State, Label> {
    sealed class Intent {
        object Logout : Intent()
        data class DeleteItem(val id: UUID) : Intent()
    }

    data class State(
        val items: List<ChatsItem> = emptyList(), val text: String = ""
    )

    sealed class Label {
        object LoggedOut : Label()
    }
}
