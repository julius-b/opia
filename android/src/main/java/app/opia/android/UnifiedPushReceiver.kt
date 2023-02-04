package app.opia.android

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import app.opia.common.db.DriverFactory
import app.opia.common.db.createDatabase
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.unifiedpush.android.connector.EXTRA_BYTES_MESSAGE
import org.unifiedpush.android.connector.MessagingReceiver
import org.unifiedpush.android.connector.UnifiedPush
import java.net.URLDecoder
import java.util.*

const val DEBUG = true

class UnifiedPushReceiver : MessagingReceiver() {
    // instance: accountId/subscription?
    override fun onMessage(context: Context, message: ByteArray, instance: String) {
        Log.d(TAG, "UPReceiver [$instance] > message: $message")
        val dict = URLDecoder.decode(String(message), "UTF-8").split("&")
        val params = dict.associate {
            try {
                it.split("=")[0] to it.split("=")[1]
            } catch (e: Exception) {
                "" to ""
            }
        }
        val text = params["message"] ?: "New notification"
        val priority = params["priority"]?.toInt() ?: 8
        val title = params["title"] ?: context.getString(R.string.app_name)
        Notifier(context).sendNotification(title, text, priority)
    }

    override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
        Log.d(TAG, "UPReceiver [$instance] > new endpoint: $endpoint - syncing...")
        val currentDist = UnifiedPush.getDistributor(context)
        if (currentDist.isEmpty()) {
            Log.e(TAG, "UPReceiver [$instance] > new endpoint for unknown distributor, ignoring")
            return
        }

        val updateId = UUID.randomUUID()
        runBlocking(Dispatchers.IO) {
            // same logic as in Splash
            val driverFactory = DriverFactory(context)
            val db = createDatabase(driverFactory)
            val ioid: UUID
            try {
                ioid = UUID.fromString(instance)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "UPReceiver [$instance] > malformed instance, ignoring")
                return@runBlocking
            }
            // NOTE: not multi-account compatible
            val sess = db.sessionQueries.getLatest().asFlow().mapToOneOrNull().first()
            if (sess == null) {
                Log.e(TAG, "UPReceiver [$instance] > no active session, ignoring")
                return@runBlocking
            }
            if (sess.ioid != ioid) {
                Log.e(TAG, "UPReceiver [$instance] > unknown instance, ignoring")
                return@runBlocking
            }
            Log.d(TAG, "UPReceiver [$instance] > saving: $endpoint via $currentDist ($updateId)")
            db.msgQueries.transaction {
                afterCommit { Log.d(TAG, "UPReceiver [$instance] > saved endpoint: $endpoint") }
                afterRollback { Log.e(TAG, "UPReceiver [$instance] > rollback") }
                db.msgQueries.upsertNotificationConfig(
                    sess.actor_id, sess.ioid, currentDist, endpoint, true, true
                )
                db.msgQueries.createNCUpdate(updateId, sess.actor_id, sess.ioid)
            }
            // inform the UI about the db change
            Intent().also { intent ->
                intent.action = "app.opia.broadcast.up.NEW_ENDPOINT"
                intent.putExtra("update_id", updateId)
                intent.putExtra("actor_id", sess.actor_id)
                intent.putExtra("ioid", sess.ioid)
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onRegistrationFailed(context: Context, instance: String) {
        Log.e(TAG, "UPReceiver [$instance] > registration failed")
        Toast.makeText(context, "Registration Failed", Toast.LENGTH_SHORT).show()
        UnifiedPush.forceRemoveDistributor(context)
    }

    override fun onUnregistered(context: Context, instance: String) {
        Log.w(TAG, "UPReceiver [$instance] > unregistered")
        val appName = context.getString(R.string.app_name)
        Toast.makeText(context, "$appName is unregistered", Toast.LENGTH_SHORT).show()
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "UPReceiver > event received")
        if (DEBUG) {
            Log.d(TAG,
                "raw bytes: " + intent.getByteArrayExtra(EXTRA_BYTES_MESSAGE)
                    ?.joinToString("") { byte -> "%02x".format(byte) })
        }
        super.onReceive(context, intent)
    }
}
