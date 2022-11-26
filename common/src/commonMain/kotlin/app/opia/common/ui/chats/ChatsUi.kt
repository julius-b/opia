package app.opia.common.ui.chats

import androidx.compose.foundation.layout.Column
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import app.opia.common.ui.chats.OpiaChats.Event
import app.opia.common.ui.component.opiaBlue
import app.opia.common.ui.component.opiaGray
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState

@Composable
fun ChatsContent(component: OpiaChats) {
    val model by component.models.subscribeAsState()

    Scaffold(topBar = {
        TopAppBar(title = { Text(text = "Chats") },
            backgroundColor = opiaBlue,
            contentColor = opiaGray,
            actions = {
                IconButton(onClick = component::logout) {
                    Icon(
                        imageVector = Icons.Default.Logout, contentDescription = "logout"
                    )
                }
            })
    }) {
        LaunchedEffect(Unit) {
            component.events.collect {
                when (it) {
                    is Event.LoggedOut -> component.back()
                }
            }
        }

        Column { }
    }
}
