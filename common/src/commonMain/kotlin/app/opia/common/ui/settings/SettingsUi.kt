package app.opia.common.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.opia.common.ui.component.opiaBlue
import kotlinx.coroutines.launch

@Composable
fun SettingsContent(component: OpiaSettings) {
    val coroutineScope = rememberCoroutineScope()

    Box {
        // change Name (state should update from database)
        // change notification provider

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
