package app.opia.common.ui.home

import app.opia.common.db.Actor
import app.opia.common.ui.chats.OpiaChats
import app.opia.common.ui.chats.chat.OpiaChat
import app.opia.common.ui.settings.OpiaSettings
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value

// isChild: don't show on nav bar, custom scaffold
enum class HomeChild(val navIndex: Int, val isChild: Boolean = false) {
    Chats(0), Chat(0, true), Settings(1)
}

interface OpiaHome {

    val childStack: Value<ChildStack<*, Child>>

    val activeChild: Value<HomeChild>

    fun onBarSelect(index: Int)

    val models: Value<HomeModel>

    data class HomeModel(
        val self: Actor?
    )

    sealed class Child(val meta: HomeChild) {
        data class Chats(val component: OpiaChats) : Child(HomeChild.Chats)
        data class Chat(val component: OpiaChat) : Child(HomeChild.Chat)
        data class Settings(val component: OpiaSettings) : Child(HomeChild.Settings)
    }

    sealed class Output {
        object Back : Output()
    }
}
