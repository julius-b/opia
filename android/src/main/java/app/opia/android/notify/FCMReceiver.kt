package app.opia.android.notify

import android.util.Log
import androidx.core.app.NotificationCompat.PRIORITY_DEFAULT
import app.opia.android.TAG
import app.opia.common.di.ServiceLocator
import app.opia.common.sync.Message
import app.opia.common.sync.SyncStats
import app.opia.common.ui.auth.AuthCtx
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.messaging.ktx.messaging
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
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

    /*
    * TODO the current implementation only works by disabling E2EE
    * Current implementation doesn't touch the db to prevent state
    * Problems:
    * - msg could be inserted conflictingly when both rx are writing the same msg
    * - receipts, etc. could be overridden after already being synced in tx loop at the same time
    * Solution:
    * - register with sync (start it first if it isn't active yet) and wait for a run to start & finish after registration was done (to ensure fcm-referenced msgs are available)
    * */
    override fun onMessageReceived(msg: RemoteMessage) = runBlocking {
        Log.i(TAG, "FCMReceiver > msg: [${msg.messageId}] ${msg.data}")

        val sess = withContext(ServiceLocator.dispatchers.io) {
            ServiceLocator.database.sessionQueries.getLatest().executeAsOneOrNull()
        }
        if (sess == null) {
            Log.e(TAG, "FCMReceiver > msg - not authenticated")
            // TODO save opia-fcm-unreg msg
            return@runBlocking
        }

        // TODO not thread-safe!
        if (!ServiceLocator.isAuthenticated())
            ServiceLocator.initAuth(
                AuthCtx(sess.installation_id, sess.actor_id, sess.ioid, sess.secret_update_id)
            )

        val deferred = CompletableDeferred<SyncStats>()
        launch { ServiceLocator.msgChan.send(Message.WaitOne(deferred)) }
        val stats = deferred.await()
        // stats.recvCnt == 0: still check unread
        Log.i(TAG, "FCMReceiver > msg - got #${stats.recvCnt} new msgs")

        // query all unreceived messages (sync may even have read those messages before fcm notification arrived)
        var unread = withContext(ServiceLocator.dispatchers.io) {
            ServiceLocator.database.msgQueries.listUnread(sess.actor_id).executeAsList()
        }

        if (unread.isEmpty()) {
            Log.w(TAG, "FCMReceiver > msg - no unread messages")
        } else {
            Log.i(TAG, "FCMReceiver > msg - unread messages: ${unread.size}")
            val title = "${unread.size} unread messages"
            if (unread.size > 5) unread = unread.subList(0, 4)
            //val text = unread.joinToString("\n") { it.payload }

            Notifier(this@FCMReceiver).notifyUnread(title, unread, PRIORITY_DEFAULT)
        }
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
