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
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState

@Composable
fun HomeContent(component: OpiaHome) {
    val activeChild by component.activeChild.subscribeAsState()

    println("[*] Home > activeChild: $activeChild")
    Scaffold(topBar = {
        if (!activeChild.isChild) TopAppBar(
            title = { Text(navbar[activeChild.navIndex]) },
            backgroundColor = opiaBlue,
            contentColor = opiaGray
        )
    }, bottomBar = {
        if (!activeChild.isChild) BottomNavigation {
            navbar.forEachIndexed { k, v ->
                BottomNavigationItem(
                    selected = k == activeChild.navIndex,
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
fun HomeBody(component: OpiaHome, modifier: Modifier = Modifier) {
    Children(
        modifier = modifier,
        stack = component.childStack,
        animation = stackAnimation(slide()),
    ) {
        when (val child = it.instance) {
            is Child.Chats -> ChatsContent(child.component)
            is Child.Chat -> ChatContent(child.component)
            is Child.Settings -> SettingsContent(child.component)
        }
    }
}
