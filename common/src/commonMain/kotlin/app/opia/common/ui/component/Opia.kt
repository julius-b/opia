package app.opia.common.ui.component

import androidx.compose.material.Snackbar
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val opiaBlue = Color(0xff133052)
val opiaGray = Color(0xffededed)

@Composable
fun OpiaErrorSnackbarHost(
    snackbarHostState: SnackbarHostState
) {
    SnackbarHost(snackbarHostState) { data ->
        Snackbar(
            data,
            backgroundColor = opiaBlue,
        )
    }
}
