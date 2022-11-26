package app.opia.common.ui.component

import androidx.compose.runtime.Composable
import java.time.LocalDate

@Composable
expect fun DatePickerView(
    onValueChange: (date: LocalDate?) -> Unit
)
