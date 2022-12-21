package app.opia.android

import android.os.Bundle
import android.util.Log
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
import org.unifiedpush.android.connector.INSTANCE_DEFAULT
import org.unifiedpush.android.connector.UnifiedPush

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

        registerUnifiedPush()
    }

    // TODO need user account id
    private fun registerUnifiedPush() {
        val currentDist = UnifiedPush.getDistributor(this)
        if (currentDist.isNotEmpty()) {
            Log.d(TAG, "registerUP > currentDist: $currentDist")
            UnifiedPush.registerApp(this)
            return
        }
        val distributors = UnifiedPush.getDistributors(this)
        Log.d(TAG, "registerUP > distributors: $distributors")
        if (distributors.isEmpty()) {
            Log.e(TAG, "registerUP > no push notifications :[")
            return
        }
        val userDistrib = distributors[0]
        UnifiedPush.saveDistributor(this, userDistrib)
        UnifiedPush.registerApp(this, INSTANCE_DEFAULT)
    }

    private fun opiaRoot(componentContext: ComponentContext): OpiaRoot = OpiaRootComponent(
        componentContext = componentContext,
        storeFactory = LoggingStoreFactory(TimeTravelStoreFactory()),
        di = ServiceLocator(DriverFactory(context = this))
    )
}
