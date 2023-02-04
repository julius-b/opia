package app.opia.common.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Dropdown uses different APIs on Android & Desktop, need a custom common implementation
// wait until ExposedDropdownMenuBox is available?
// see: https://github.com/JetBrains/compose-jb/issues/1673
// Dropdowns are platform-specific:
// - https://github.com/androidx/androidx/blob/f28daa8e82452d013f385c270549a91d62ff6339/compose/material/material/src/desktopMain/kotlin/androidx/compose/material/DesktopMenu.desktop.kt#L76
// - https://github.com/androidx/androidx/blob/11eb0c3e15c16d03b9cc8d431032672222dec6bb/compose/material/material/src/androidMain/kotlin/androidx/compose/material/AndroidMenu.android.kt#L74
@Composable
expect fun <T : Any> DropdownButton(
    modifier: Modifier = Modifier,
    values: List<T>,
    value: T?,
    contentDescription: String?,
    onValueChanged: (value: T) -> Unit
)
