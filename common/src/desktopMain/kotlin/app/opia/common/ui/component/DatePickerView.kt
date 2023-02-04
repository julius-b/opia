package app.opia.common.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import java.time.LocalDate
import java.util.*

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
                        Modifier,
                        (0..31).toList(),
                        day,
                        "expand day select"
                    ) { day = it }
                    DropdownButton(
                        Modifier,
                        (0..12).toList(),
                        month,
                        "expand day select"
                    ) { month = it }
                    DropdownButton(
                        Modifier,
                        (1900..currentYear).toList(),
                        year,
                        "expand day select"
                    ) { year = it }
                }
            }
        }
    )
}
