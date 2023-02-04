package app.opia.android

import android.content.Context
import android.util.Log
import app.opia.common.NotificationRepo
import app.opia.db.OpiaDatabase
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.unifiedpush.android.connector.UnifiedPush
import java.util.*

class AndroidNotificationRepo(private val context: Context, private val db: OpiaDatabase) :
    NotificationRepo {
    override fun listDistributors() = UnifiedPush.getDistributors(context) + listOf("none")

    override fun init(instance: String) {
        Log.d(TAG, "registerUP [$instance] > init")

        // triggers BroadcastReceiver -> save current state in db
        UnifiedPush.registerApp(context, instance)
    }

    // distributor from db
    override fun registerUnifiedPush(instance: String, distributor: String?) {
        Log.d(TAG, "registerUP [$instance] > distributor: $distributor")
        val currentDist = UnifiedPush.getDistributor(context)
        if (currentDist.isNotEmpty()) {
            Log.d(TAG, "registerUP [$instance] > currentDist: $currentDist")
            if (currentDist != distributor) {
                Log.w(TAG, "registerUP [$instance] > mismatch, removing current...")
                UnifiedPush.forceRemoveDistributor(context) // forceRemove causes ConcurrentModificationException
                UnifiedPush.unregisterApp(context, instance)
                if (distributor == null) {
                    runBlocking(Dispatchers.IO) {
                        val ioid: UUID
                        try {
                            ioid = UUID.fromString(instance)
                        } catch (e: IllegalArgumentException) {
                            Log.e(TAG, "registerUP [$instance] > malformed instance, ignoring")
                            return@runBlocking
                        }
                        val sess = db.sessionQueries.getLatest().asFlow().mapToOneOrNull().first()
                        if (sess == null) {
                            Log.e(TAG, "registerUP [$instance] > no active session, ignoring")
                            return@runBlocking
                        }
                        if (sess.ioid != ioid) {
                            Log.e(TAG, "registerUP [$instance] > unknown instance, ignoring")
                            return@runBlocking
                        }
                        val updateId = UUID.randomUUID()
                        Log.i(TAG, "registerUP [$instance] > resetting, updateId: $updateId")
                        db.msgQueries.transaction {
                            db.msgQueries.resetNotificationConfig(sess.actor_id, sess.ioid)
                            db.msgQueries.createNCUpdate(updateId, sess.actor_id, sess.ioid)
                        }
                    }
                } else {
                    Log.i(TAG, "registerUP [$instance] > registering new: $distributor...")
                    UnifiedPush.saveDistributor(context, distributor)
                }
            }
            // register, whether distributor changed or not, so long as it isn't null
            UnifiedPush.registerApp(context, instance) // if (distributor != null)
            return
        }
        // none registered, none requested
        if (distributor == null) return
        val distributors = UnifiedPush.getDistributors(context)
        Log.d(TAG, "registerUP [$instance] > distributors: $distributors")
        if (!distributors.contains(distributor)) {
            Log.w(TAG, "registerUP [$instance] > requested distributor not available: $distributor")
            return
        }
        Log.i(TAG, "registerUP [$instance] > registering new: $distributor...")
        UnifiedPush.saveDistributor(context, distributor)
        UnifiedPush.registerApp(context, instance)
    }
}
