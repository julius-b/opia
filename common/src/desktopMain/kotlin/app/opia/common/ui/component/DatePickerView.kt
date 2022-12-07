package app.opia.common.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import java.time.LocalDate
import java.util.*

// wait until ExposedDropdownMenuBox is available?
// see: https://github.com/JetBrains/compose-jb/issues/1673
@Composable
fun <T: Any> DropdownButton(
    values: List<T>,
    value: T,
    onValueChanged: (value: T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(
        onClick = { expanded = true }
    ) {
        Text(value.toString())
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = "expand day select"
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        for (v in values) {
            DropdownMenuItem(
                onClick = {
                    expanded = false
                    onValueChanged(v)
                }
            ) {
                Text(v.toString())
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
actual fun DatePickerView(
    onValueChange: (date: LocalDate?) -> Unit
) {
    val currentDate = Calendar.getInstance()
    val currentYear = currentDate.get(Calendar.YEAR)

    var day by remember { mutableStateOf(1) }
    var month by remember { mutableStateOf(1) }
    var year by remember { mutableStateOf(currentYear) }

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Enter a date") },
        confirmButton = {
            Button(onClick = {
                println("[*] DatePicker > date: $day/$month/$year")
                val date = LocalDate.of(year, month, day)
                onValueChange(date)
            }) {
                Text("Confirm")
            }
        },
        //dismissButton = { Button(onClick = {}) { Text("Dismiss") } },
        text = {
            Column {
                Row {
                    DropdownButton(
                        (0..31).toList(),
                        day
                    ) { day = it }
                    DropdownButton(
                        (0..12).toList(),
                        month
                    ) { month = it }
                    DropdownButton(
                        (1900..currentYear).toList(),
                        year
                    ) { year = it }
                }
            }
        }
    )
}
