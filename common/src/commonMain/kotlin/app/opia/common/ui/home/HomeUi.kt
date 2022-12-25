package app.opia.common.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Abc
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.opia.common.ui.chats.ChatsContent
import app.opia.common.ui.chats.chat.ChatContent
import app.opia.common.ui.component.opiaBlue
import app.opia.common.ui.component.opiaGray
import app.opia.common.ui.home.OpiaHome.Child
import app.opia.common.ui.navbar
import app.opia.common.ui.settings.SettingsContent
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.Children
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.plus
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.scale
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState

@Composable
fun HomeContent(component: OpiaHome) {
    val activeIndex by component.activeChildIndex.subscribeAsState()

    Scaffold(topBar = {
        TopAppBar(
            title = {
                when (activeIndex) {
                    0 -> Text(text = "Messages")
                    1 -> Text(text = "Settings")
                }
            },
            backgroundColor = opiaBlue,
            contentColor = opiaGray
        )
    }, bottomBar = {
        BottomNavigation {
            navbar.forEachIndexed { k, v ->
                BottomNavigationItem(
                    selected = k == activeIndex,
                    onClick = { component.onBarSelect(k) },
                    label = { Text(v) },
                    icon = {
                        Icon(imageVector = Icons.Default.Abc, contentDescription = null)
                    }
                )
            }
        }
    }) {
        HomeBody(component, Modifier.padding(it))
    }
}

@Composable
fun HomeBody(component: OpiaHome, modifier: Modifier) {
    Children(
        modifier = modifier,
        stack = component.childStack,
        animation = stackAnimation(fade() + scale()),
    ) {
        when (val child = it.instance) {
            is Child.Chats -> ChatsContent(child.component)
            is Child.Chat -> ChatContent(child.component)
            is Child.Settings -> SettingsContent(child.component)
        }
    }
}
