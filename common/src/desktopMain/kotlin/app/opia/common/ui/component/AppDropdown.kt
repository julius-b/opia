package app.opia.common.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.toSize

@Composable
actual fun <T : Any> DropdownButton(
    modifier: Modifier,
    values: List<T>,
    value: T?,
    contentDescription: String?,
    onValueChanged: (value: T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    // Box: without it, DropdownMenu parent is the whole view and it appears in the upper-left/bottom-left corner
    // selectWidth: when the button uses the complete width, so should the dropdown. Butten size defined by parent.
    var selectWidth by remember { mutableStateOf(Size.Zero) }
    Box {
        TextButton(modifier = modifier.onGloballyPositioned {
            selectWidth = it.size.toSize()
        }, onClick = { expanded = !expanded }) {
            Text(value?.toString() ?: "")
            Icon(
                imageVector = Icons.Default.ArrowDropDown, contentDescription = contentDescription
            )
        }
        DropdownMenu(
            modifier = modifier.width(with(LocalDensity.current) { selectWidth.width.toDp() }),
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            for (v in values) {
                DropdownMenuItem(onClick = {
                    expanded = false
                    onValueChanged(v)
                }) {
                    Text(v.toString())
                }
            }
        }
    }
}
