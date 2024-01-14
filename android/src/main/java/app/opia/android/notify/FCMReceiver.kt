package app.opia.android.notify

import android.util.Log
import app.opia.android.TAG
import app.opia.common.di.AuthStatus
import app.opia.common.di.ServiceLocator
import app.opia.common.ui.auth.AuthCtx
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.messaging.ktx.messaging
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException

class FCMReceiver : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Log.i(TAG, "FCMReceiver > new token: $token")

        // fcm token is queried directly
        //ReceiverUtil.updateDB(this, ProviderFCMNoUP, token)
    }

    override fun onMessageReceived(msg: RemoteMessage) = runBlocking {
        Log.i(TAG, "FCMReceiver > msg: [${msg.messageId}] ${msg.data}")

        // ensure cord is connected
        var auth = ServiceLocator.getAuth()
        if (auth is AuthStatus.Unauthenticated) {
            Log.i(TAG, "FCMReceiver > msg - authenticating...")
            val sess = withContext(ServiceLocator.dispatchers.io) {
                ServiceLocator.database.sessionQueries.getLatest().executeAsOneOrNull()
            }
            if (sess == null) {
                Log.e(TAG, "FCMReceiver > msg - no authentication available")
                // TODO save opia-fcm-unreg msg
                return@runBlocking
            }
            ServiceLocator.login(
                AuthCtx(
                    sess.installation_id,
                    sess.actor_id,
                    sess.ioid,
                    sess.secret_update_id,
                    sess.refresh_token,
                    sess.access_token,
                    sess.created_at
                )
            )
            auth = ServiceLocator.getAuth()
            Log.i(TAG, "FCMReceiver > msg - authenticated: ${auth is AuthStatus.Authenticated}")
        }

        Log.w(TAG, "FCMReceiver > msg - done looping")
    }

    companion object {
        suspend fun getToken(): String? {
            return try {
                val token = Firebase.messaging.token.await()
                token
            } catch (e: IOException) {
                // java.io.IOException: java.util.concurrent.ExecutionException: java.io.IOException: MISSING_INSTANCEID_SERVICE (no google)
                // java.io.IOException: java.util.concurrent.ExecutionException: java.io.IOException: SERVICE_NOT_AVAILABLE (no internet)
                Log.e(TAG, "token: $e")
                null
            }
        }
    }
}
