package app.opia.common.ui.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.opia.common.ui.component.opiaBlue

@Composable
fun SplashContent(component: OpiaSplash) {
    Box(
        contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()
    ) {
        Text(
            "OPIA", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = opiaBlue
        )
    }
}
