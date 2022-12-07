package app.opia.common.ui.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.opia.common.ui.auth.TextFieldError
import app.opia.common.ui.chats.OpiaChats.Event
import app.opia.common.ui.component.opiaBlue
import app.opia.common.ui.component.opiaGray
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import java.util.*

@Composable
@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeUiApi::class)
fun ChatsContent(component: OpiaChats) {
    val model by component.models.subscribeAsState()

    var showSearch by rememberSaveable { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(title = { Text(text = "Chats of ${model.self?.name}") },
            backgroundColor = opiaBlue,
            contentColor = opiaGray,
            actions = {
                IconButton(onClick = component::logout) {
                    Icon(
                        imageVector = Icons.Default.Logout, contentDescription = "logout"
                    )
                }
            })
    }, floatingActionButton = {
        FloatingActionButton(onClick = { showSearch = true }) {
            Icon(imageVector = Icons.Default.Search, contentDescription = "search")
        }
    }) {
        LaunchedEffect(Unit) {
            component.events.collect {
                when (it) {
                    is Event.LoggedOut -> component.onBackClicked()
                    is Event.SearchFinished -> showSearch = false
                    is Event.ChatOpened -> component.continueToChat(it.selfId, it.peerId)
                }
            }
        }

        val listState = rememberLazyListState()
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(model.chats) { chat ->
                Item(chat) { id ->
                    component.onChatClicked(id)
                }
            }
        }

        if (showSearch) {
            AlertDialog(onDismissRequest = {
                showSearch = false
            }, confirmButton = {
                TextButton(onClick = {
                    component.onSearchClicked()
                }) {
                    Text("Confirm")
                }
            }, dismissButton = {
                TextButton(onClick = {
                    showSearch = false
                }) {
                    Text("Dismiss")
                }
            }, title = { Text("Add contact") }, text = {
                Column {
                    Text("Enter someone's handle to add them as a contact:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = model.searchQuery,
                        onValueChange = { component.onSearchUpdated(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("handle") },
                        isError = model.searchError != null,
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false
                        ),
                        singleLine = true,
                        maxLines = 1
                    )
                    model.searchError?.let { error -> TextFieldError(error) }
                    Spacer(modifier = Modifier.height(2.dp))
                }
            })
        }
    }
}

@Composable
private fun Item(
    item: ChatsItem, onItemClicked: (id: UUID) -> Unit
) {
    Column(modifier = Modifier.clickable(onClick = { onItemClicked(item.id) }).fillMaxWidth()) {
        Text(
            item.name, maxLines = 1, overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            item.text, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}
