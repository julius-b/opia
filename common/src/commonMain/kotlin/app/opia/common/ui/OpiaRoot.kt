package app.opia.common.ui

import app.opia.common.ui.auth.OpiaAuth
import app.opia.common.ui.auth.registration.OpiaRegistration
import app.opia.common.ui.chats.OpiaChats
import app.opia.common.ui.chats.chat.OpiaChat
import app.opia.common.ui.splash.OpiaSplash
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value

interface OpiaRoot {

    val childStack: Value<ChildStack<*, Child>>

    sealed class Child {
        data class Splash(val component: OpiaSplash) : Child()
        data class Auth(val component: OpiaAuth) : Child()
        data class Registration(val component: OpiaRegistration) : Child()
        data class Chats(val component: OpiaChats) : Child()
        data class Chat(val component: OpiaChat) : Child()
    }
}
