package app.opia.android

import DefaultDispatchers
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.LaunchedEffect
import app.opia.common.di.ServiceLocator
import app.opia.common.ui.OpiaRoot
import app.opia.common.ui.OpiaRootComponent
import app.opia.common.ui.OpiaRootContent
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.defaultComponentContext
import com.arkivanov.mvikotlin.logging.store.LoggingStoreFactory
import com.arkivanov.mvikotlin.timetravel.store.TimeTravelStoreFactory
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import java.io.Serializable
import java.util.*

const val TAG = "OpiaApp"

// temporary solution
const val TopicPushCfg = "app.opia.broadcast.push.CONFIG_UPDATE"

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//        val di = ServiceLocator(
//            DriverFactory(context = this), DefaultDispatchers
//        ) { db -> FCMNotificationRepo() } // AndroidNotificationRepo(this, db)
        val root = opiaRoot(defaultComponentContext())

        setContent {
            LaunchedEffect(Unit) {
                androidInit()
            }
            ComposeAppTheme {
                Surface(color = MaterialTheme.colors.background) {
                    OpiaRootContent(root)
                }
            }
        }
    }

    private fun androidInit() {
        registerReceiver(
            LocalKTaskReceiver(Handler(Looper.getMainLooper())), IntentFilter(TopicPushCfg)
        )

        val fcmAvailability =
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this@MainActivity)
        val fcmAvailable = fcmAvailability == ConnectionResult.SUCCESS
        // start loop if available
        if (!fcmAvailable) Log.w(TAG, "android-init - no fcm available: $fcmAvailability")

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()

        val connectivityManager =
            getSystemService(ConnectivityManager::class.java) as ConnectivityManager
        connectivityManager.registerDefaultNetworkCallback(object :
            ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "network-cb > network available")
                // TODO save "online" event in db
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                //val unmetered = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }

            override fun onLost(network: Network) {
                Log.i(TAG, "network-cb > network lost")
            }
        })
    }

    private fun opiaRoot(componentContext: ComponentContext): OpiaRoot =
        OpiaRootComponent(
            componentContext = componentContext,
            storeFactory = LoggingStoreFactory(TimeTravelStoreFactory()),
            DefaultDispatchers
        )

    private class LocalKTaskReceiver(val handler: Handler) :
        BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "LocalKTaskReceiver > received intent: $intent")
            handler.post {
                if (intent.action != TopicPushCfg) {
                    Log.e(TAG, "LocalKTaskReceiver > unknown action: ${intent.action}")
                    return@post
                }

                val ids = intent.getSerializableExtraSafe("ids", Array<UUID>::class.java)
                Log.i(TAG, "LocalKTaskReceiver > re-inserting RunCfg to trigger observer: $ids")
                ids!!.forEach(ServiceLocator.database.msgQueries::triggerNotificationReg)
            }
        }
    }
}

fun <T : Serializable> Intent.getSerializableExtraSafe(key: String, clazz: Class<T>): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        this.extras?.getSerializable(key, clazz)
    } else this.extras?.getSerializable(key) as? T?
}
