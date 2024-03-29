package app.opia.desktop

import DefaultDispatchers
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.opia.common.DefaultNotificationRepo
import app.opia.common.db.DriverFactory
import app.opia.common.db.createDatabase
import app.opia.common.di.ServiceLocator
import app.opia.common.sync.Notifier
import app.opia.common.ui.OpiaRoot
import app.opia.common.ui.OpiaRootComponent
import app.opia.common.ui.OpiaRootContent
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.jetbrains.lifecycle.LifecycleController
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalDecomposeApi::class)
fun main(args: Array<String>) {
    if (args.size == 2 && args[1] == "--sh") {
        println("[+] launching shell...")
        while (true) {
            readln()
        }
        return
    }

    runBlocking {
        ServiceLocator.init(DefaultDispatchers,
            createDatabase(DriverFactory()),
            DefaultNotificationRepo,
            object : Notifier {})
    }

    val lifecycle = LifecycleRegistry()
    val root = opiaRoot(DefaultComponentContext(lifecycle = lifecycle))

    application {
        val windowState = rememberWindowState()
        LifecycleController(lifecycle, windowState)

        Window(
            onCloseRequest = ::exitApplication, state = windowState, title = "Opia"
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                MaterialTheme {
                    OpiaRootContent(root)
                }
            }
        }
    }
}

private fun opiaRoot(componentContext: ComponentContext): OpiaRoot = OpiaRootComponent(
    componentContext = componentContext,
    storeFactory = DefaultStoreFactory()
)
