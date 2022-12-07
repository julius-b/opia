package app.opia.common.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import app.opia.common.ui.OpiaRoot.Child
import app.opia.common.ui.auth.AuthContent
import app.opia.common.ui.auth.registration.RegistrationContent
import app.opia.common.ui.chats.ChatsContent
import app.opia.common.ui.chats.chat.ChatContent
import app.opia.common.ui.splash.SplashContent
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.Children
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.plus
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.scale
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.stackAnimation

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun OpiaRootContent(component: OpiaRoot) {
    Children(
        stack = component.childStack,
        animation = stackAnimation(fade() + scale()),
    ) {
        when (val child = it.instance) {
            is Child.Splash -> SplashContent(child.component)
            is Child.Auth -> AuthContent(child.component)
            is Child.Registration -> RegistrationContent(child.component)
            is Child.Chats -> ChatsContent(child.component)
            is Child.Chat -> ChatContent(child.component)
        }
    }
}