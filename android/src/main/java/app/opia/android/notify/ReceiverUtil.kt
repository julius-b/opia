package app.opia.android.notify

import android.content.Context
import android.content.Intent
import android.util.Log
import app.opia.android.TAG
import app.opia.android.TopicPushCfg
import app.opia.common.db.DriverFactory
import app.opia.common.db.createDatabase
import app.opia.common.utils.catching
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.Serializable
import java.util.UUID

object ReceiverUtil {

    data class Account(
        val actor_id: UUID, val ioid: UUID
    ) : Serializable

    fun updateDB(
        ctx: Context, provider: String, endpoint: String, ioidS: String? = null
    ) = runBlocking(Dispatchers.IO) {
        val driverFactory = DriverFactory(ctx)
        val db = createDatabase(driverFactory) // TODO consider

        // accounts to update notification_config for
        // FCM updates all accounts (one token per device, but api requires individual update per ioid)
        // UP only updates the selected account (ioid not null)
        val accounts = when (provider) {
            ProviderFCMNoUP -> db.sessionQueries.getLatestByDistinctIOID().asFlow().mapToList()
                .first().map { Account(it.actor_id, it.ioid) }

            else -> {
                val ioid =
                    runCatching { UUID.fromString(ioidS) }.catching(IllegalArgumentException::class) {
                        Log.e(TAG, "updateDB [$provider:$ioidS] - malformed instance, ignoring")
                        return@runBlocking
                    }.getOrThrow()
                val sess = db.sessionQueries.getLatestByIOID(ioid).asFlow().mapToOneOrNull().first()
                if (sess == null) null
                else listOf(Account(sess.actor_id, sess.ioid))
            }
        }

        Log.i(TAG, "updateDB [$provider:$ioidS] - accounts: $accounts, new endpoint: '$endpoint'")

        if (accounts.isNullOrEmpty()) {
            Log.w(TAG, "updateDB [$provider:$ioidS] - nothing to do")
            return@runBlocking
        }

        // one intent with list of ioids and updateIDs

        accounts.forEach { acc ->
            Log.d(TAG, "updateDB [$provider:$ioidS] - new acc: $acc")

            db.msgQueries.transaction {
                afterCommit { Log.d(TAG, "updateDB [$provider:$ioidS] - done") }
                afterRollback {
                    Log.e(TAG, "updateDB [$provider:$ioidS] - rollback")
                }
                if (provider != ProviderFCMNoUP)
                    db.msgQueries.upsertNotificationReg(acc.ioid, provider, endpoint, false)
                // TODO use Factory, but Moshi isn't available
                // TODO this isn't optimal, but db doesn't know about nc-sync args either

            }

        }

        // inform the UI about the db change
        // TODO test that fcm and UP give correct ctx
        Intent().also { intent ->
            intent.action = TopicPushCfg
            intent.putExtra("id", accounts.map { "${it.ioid}" }.toTypedArray())
            //intent.putExtras(Bundle().apply { putSerializable("updates", ArrayList(accounts)) })
            ctx.sendBroadcast(intent)
        }
    }
}
