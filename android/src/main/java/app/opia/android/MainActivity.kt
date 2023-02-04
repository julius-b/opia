package app.opia.android

import DefaultDispatchers
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
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
import java.io.Serializable
import java.util.*

const val TAG = "OpiaApp"

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val di = ServiceLocator(
            DriverFactory(context = this), DefaultDispatchers
        ) { db -> AndroidNotificationRepo(this, db) }
        val root = opiaRoot(defaultComponentContext(), di)

        setContent {
            ComposeAppTheme {
                Surface(color = MaterialTheme.colors.background) {
                    OpiaRootContent(root)
                }
            }
        }

        registerReceiver(
            LocalUPReceiver(Handler(Looper.getMainLooper()), di),
            IntentFilter("app.opia.broadcast.up.NEW_ENDPOINT")
        )
    }

    private fun opiaRoot(componentContext: ComponentContext, di: ServiceLocator): OpiaRoot =
        OpiaRootComponent(
            componentContext = componentContext,
            storeFactory = LoggingStoreFactory(TimeTravelStoreFactory()),
            di = di,
            DefaultDispatchers
        )

    private class LocalUPReceiver(val handler: Handler, val di: ServiceLocator) :
        BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "LocalUPReceiver > received intent: $intent")
            handler.post {
                if (intent.action != "app.opia.broadcast.up.NEW_ENDPOINT") {
                    Log.e(TAG, "LocalUPReceiver > unknown action: ${intent.action}")
                    return@post
                }
                Toast.makeText(context, "Received UP", Toast.LENGTH_SHORT).show()

                val updateId = intent.getSerializableExtraSafe("update_id", UUID::class.java)
                val actorId = intent.getSerializableExtraSafe("actor_id", UUID::class.java)!!
                val ioid = intent.getSerializableExtraSafe("ioid", UUID::class.java)!!

                Log.i(TAG, "LocalUPReceiver > re-inserting NoUpd to trigger observer: $updateId")
                di.database.msgQueries.triggerNotificationConfig(actorId, ioid)
            }
        }
    }
}

fun <T : Serializable> Intent.getSerializableExtraSafe(key: String, clazz: Class<T>): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        this.extras?.getSerializable(key, clazz)
    } else this.extras?.getSerializable(key) as? T?
}
