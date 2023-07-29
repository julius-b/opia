package app.opia.common.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opia.common.ui.component.DropdownButton
import app.opia.common.ui.component.opiaBlue
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import kotlinx.coroutines.launch

@Composable
fun SettingsContent(component: OpiaSettings) {
    val model by component.models.subscribeAsState()

    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Hi, ${model.self?.name}",
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            color = opiaBlue,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = model.name,
            onValueChange = component::onNameChanged,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 12.dp),
            // enabled = !model.isLoading, // TODO
            label = { Text("Name") },
            placeholder = { Text("Update name") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.AccountCircle, contentDescription = null)
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            singleLine = true,
            maxLines = 1
        )


        OutlinedTextField(
            value = model.desc,
            onValueChange = component::onDescChanged,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 12.dp),
            // enabled = !model.isLoading, // TODO
            label = { Text("Desc") },
            placeholder = { Text("Update description") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            singleLine = false,
            maxLines = 5
        )

        Button(
            onClick = component::updateClicked,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = opiaBlue, contentColor = Color.White
            )
        ) {
            Text("Update", modifier = Modifier.padding(2.dp))
        }

        Divider(modifier = Modifier.padding(10.dp).fillMaxWidth())

        Button(
            onClick = { coroutineScope.launch { component.logoutClicked() } },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = opiaBlue, contentColor = Color.White
            )
        ) {
            Text("Logout", modifier = Modifier.padding(2.dp))
        }
    }
}
