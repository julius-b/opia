package app.opia.android

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import org.unifiedpush.android.connector.EXTRA_BYTES_MESSAGE
import org.unifiedpush.android.connector.MessagingReceiver
import org.unifiedpush.android.connector.UnifiedPush
import java.net.URLDecoder

const val TAG = "OpiaApp"
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
