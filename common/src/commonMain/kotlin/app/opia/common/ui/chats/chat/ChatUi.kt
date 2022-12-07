package app.opia.common.ui.chats.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Send
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.opia.common.ui.component.opiaBlue
import app.opia.common.ui.component.opiaGray
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState

@Composable
fun ChatContent(component: OpiaChat) {
    val model by component.models.subscribeAsState()

    Scaffold(topBar = {
        TopAppBar(title = { Text(text = model.peer?.name ?: "") },
            backgroundColor = opiaBlue,
            contentColor = opiaGray,
            navigationIcon = {
                IconButton(onClick = component::onBackClicked) {
                    Icon(imageVector = Icons.Default.ArrowBackIosNew, contentDescription = "back")
                }
            })
    }, bottomBar = {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            TextEntryBox(onSendClicked = { component.onSendClicked(it) }, resetScroll = {
//                scope.launch {
//                    scrollState.scrollToItem(0)
//                }
            })
        }
    }) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            val listState = rememberLazyListState()
            LazyColumn(state = listState, reverseLayout = true, modifier = Modifier.fillMaxSize()) {
                items(model.msgs.reversed()) { msg ->
                    Item(msg)
                }
            }
        }
    }
}

@Composable
private fun Item(
    item: MessageItem
) {
    // padding before background acts as a margin
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (item.from == null) Alignment.End else Alignment.Start
    ) {
        Column(
            modifier = Modifier.padding(10.dp).clip(RoundedCornerShape(5.dp))
                .background(Color(0xffEDEDED)).padding(8.dp),
        ) {
            Text(
                text = item.text, color = opiaBlue
            )
        }
    }
}

@Composable
private fun TextEntryBox(
    //text: String, // TODO use state.msg, don't send msg in callback
    onSendClicked: (String) -> Unit, resetScroll: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    var value by rememberSaveable { mutableStateOf("") }

    Row(
        horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.weight(1f).semantics { contentDescription = "Message text field" },
            shape = RoundedCornerShape(16.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        IconButton(
            onClick = {
                onSendClicked(value)
                value = ""

                // Move scroll to bottom
                resetScroll()
                // Remove focus (which also hides keyboard)
                focusManager.clearFocus()
            }, enabled = value.isNotBlank()
        ) {
            Icon(
                imageVector = Icons.Rounded.Send,
                contentDescription = "Send",
                tint = Color.White,
                modifier = Modifier.size(48.dp).background(
                    brush = Brush.verticalGradient(
                        colors = if (value.isNotBlank()) {
                            listOf(Color(0xFFFF1278), Color(0xFFFE7168))
                        } else {
                            listOf(Color(0x80FF1278), Color(0x80FE7168))
                        }
                    ), shape = CircleShape
                ).padding(start = 8.dp, end = 4.dp)
            )
        }
    }
}
