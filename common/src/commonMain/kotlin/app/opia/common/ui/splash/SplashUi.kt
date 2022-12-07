package app.opia.common.ui.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.opia.common.ui.component.opiaBlue
import app.opia.common.ui.splash.OpiaSplash.Next
import app.opia.common.ui.splash.OpiaSplash.Event
import app.opia.common.ui.splash.OpiaSplash.Output
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SplashContent(component: OpiaSplash) {
    val model by component.models.subscribeAsState()
    var emitted: Boolean by rememberSaveable { mutableStateOf(false) }

    // too slow...
    LaunchedEffect(Unit) {
        println("[*] Splash > collecting...")
        component.events.collectLatest {
            if (emitted) return@collectLatest
            emitted = true
            println("[+] Splash > event: $it")
            when (it) {
                is Event.Auth -> component.onNext(Output.Auth)
                is Event.Main -> component.onNext(Output.Main(it.selfId))
            }
        }
    }

    println("[*] Splash > [$emitted] model.next: ${model.next}")
    when (model.next) {
        is Next.Auth -> {
            if (!emitted) component.onNext(Output.Auth)
            emitted = true
        }
        is Next.Main -> {
            val main = (model.next as Next.Main)
            if (!emitted) component.onNext(Output.Main(main.selfId))
            emitted = true
        }
        else -> {}
    }

    Box(
        contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()
    ) {
        Text(
            "OPIA", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = opiaBlue
        )
    }
}
