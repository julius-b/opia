package app.opia.common.ui.home

import app.opia.common.ui.chats.OpiaChats
import app.opia.common.ui.chats.chat.OpiaChat
import app.opia.common.ui.settings.OpiaSettings
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value

enum class HomeChild {
    Chats, Settings
}

interface OpiaHome {

    val childStack: Value<ChildStack<*, Child>>

    val activeChildIndex: Value<Int>

    fun onBarSelect(index: Int)

    sealed class Child(val index: Int?) {
        data class Chats(val component: OpiaChats) : Child(HomeChild.Chats.ordinal)
        data class Chat(val component: OpiaChat) : Child(null)
        data class Settings(val component: OpiaSettings) : Child(HomeChild.Settings.ordinal)
    }

    sealed class Output {
        object Back : Output()
    }
}
