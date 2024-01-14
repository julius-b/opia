package app.opia.common.sync

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.opia.common.di.ServiceLocator
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Objects.isNull
import java.util.UUID
import kotlin.coroutines.coroutineContext

const val SYNC_TIMEOUT = 60_000L

// in Android: keeper of ApplicationContext
interface Notifier {

}

suspend fun notifyUnread(actorID: UUID, notifier: Notifier) {
    ServiceLocator.database.msgQueries.listUnread(actorID).asFlow()
        .mapToList(coroutineContext + ServiceLocator.dispatchers.io).collectLatest {
            Logger.i("notify-unread: notify=$it")
        }
}

// ws conn when in foreground TODO stop ws for all actors in background if FCM is active
// TODO on token-refresh: UnregisterActor, RegisterActor
class MsgSync(private val notifier: Notifier) {

    private val mutex = Mutex()

    private val authJobs = mutableMapOf<UUID, Job>()

    // eg. background / foreground
    private var showNotifications = true;

    suspend fun onNotification(actorID: UUID) {
        // TODO tell Cord if that's how Cord works...
    }

    suspend fun registerActor(actorID: UUID, ioid: UUID) {
        mutex.withLock {
            if (authJobs.containsKey(actorID)) {
                Logger.w("msg-sync/register") { "already registered: $actorID" }
                return
            }
            val job = CoroutineScope(ServiceLocator.dispatchers.main).launch {
                val cord = CordClient()
                // TODO share active handshakes - but then both have to lock
                launch { notifyUnread(actorID, notifier) }
                launch { pushLocalMsg(actorID, ioid) }
                launch { pushLocalRcpt() }
                launch { cord.loopCord() }

                launch(ServiceLocator.dispatchers.io) {
                    while (isActive) {
                        Logger.d("msg-sync/push-reg") { "syncing..." }/*if (sync.syncPushCfg()) { // TODO
                            Logger.i("msg-sync/push-reg") { "synced" }
                            break
                        }*/
                        Logger.e("msg-sync/push-reg") { "sync failed, retrying..." }
                        delay(SYNC_TIMEOUT)
                    }
                }
            }
            job.invokeOnCompletion {
                Logger.i("msg-sync/register") { "sync job completed for: $actorID" }
            }
            authJobs[actorID] = job
        }
    }

    suspend fun unregisterActor(actorID: UUID) {
        mutex.withLock {
            val jobs = authJobs[actorID]
            if (isNull(jobs)) {
                Logger.e("msg-sync/unregister") { "unknown=$actorID" }
                return
            }
            Logger.i("msg-sync/unregister") { "logging out: $actorID" }
            jobs!!.cancelAndJoin()
            Logger.i("msg-sync/unregister") { "logged out: $actorID" }
        }
    }

    suspend fun unregisterAll() {
        mutex.withLock {
            for ((k, _) in authJobs) {
                Logger.i("msg-sync/unregister-all") { "logging out: $k" }
                authJobs[k]!!.cancelAndJoin()
                Logger.i("msg-sync/unregister-all") { "logged out: $k" }
            }
            authJobs.clear()
        }
    }

    // TODO just send msg to chat-sync to do a new GET
    suspend fun syncActor(actorID: UUID) {
        mutex.withLock {
            //authJobs[actorID].wsConn?.
        }
    }
}
