package app.opia.common.ui.component

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

@Composable
actual fun DatePickerView(
    onValueChange: (date: LocalDate?) -> Unit
) {
    val activity = LocalContext.current as AppCompatActivity

    showDatePicker(activity, onValueChange)
}

private fun showDatePicker(
    activity: AppCompatActivity,
    onValueChange: (LocalDate?) -> Unit
) {
    val calendar = Calendar.getInstance()
    calendar.set(1910, 1, 1)
    val startFrom = calendar.timeInMillis

    val picker = MaterialDatePicker.Builder
        .datePicker().setCalendarConstraints(
            CalendarConstraints.Builder()
                .setEnd(Instant.now().toEpochMilli())
                .setStart(startFrom)
                .setValidator(DateValidatorPointBackward.now())
                .build()
        )
        .build()

    picker.show(activity.supportFragmentManager, picker.toString())
    picker.addOnPositiveButtonClickListener {
        val i = Instant.ofEpochMilli(it)
        val d = i.atZone(ZoneId.systemDefault())
        //val d = i.atZone(ZoneId.of("UTC"))
        onValueChange(d.toLocalDate())
    }
    picker.addOnNegativeButtonClickListener {
        onValueChange(null)
    }
    picker.addOnDismissListener {
        onValueChange(null)
    }
}
