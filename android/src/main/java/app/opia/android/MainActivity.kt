package app.opia.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import app.opia.common.db.DriverFactory
import app.opia.common.di.ServiceLocator
import app.opia.common.ui.OpiaRoot
import app.opia.common.ui.OpiaRootComponent
import app.opia.common.ui.OpiaRootContent
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.defaultComponentContext
import com.arkivanov.mvikotlin.logging.store.LoggingStoreFactory
import com.arkivanov.mvikotlin.timetravel.store.TimeTravelStoreFactory

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = opiaRoot(defaultComponentContext())

        setContent {
            ComposeAppTheme {
                Surface(color = MaterialTheme.colors.background) {
                    OpiaRootContent(root)
                }
            }
        }
    }

    private fun opiaRoot(componentContext: ComponentContext): OpiaRoot = OpiaRootComponent(
        componentContext = componentContext,
        storeFactory = LoggingStoreFactory(TimeTravelStoreFactory()),
        di = ServiceLocator(DriverFactory(context = this))
    )
}
