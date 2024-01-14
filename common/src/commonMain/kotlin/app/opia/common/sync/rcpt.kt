package app.opia.common.sync

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.opia.common.api.NetworkResponse
import app.opia.common.api.model.DeleteReceiptUpdate
import app.opia.common.db.Msg_rcpt
import app.opia.common.di.ServiceLocator
import co.touchlab.kermit.Logger
import kotlin.coroutines.coroutineContext

suspend fun pullRcpt(): Boolean {
    val ltag = "pull-rcpt"
    val db = ServiceLocator.database
    val msgApi = ServiceLocator.msgRepo.api

    val receiptsRes = msgApi.listUpdatedReceipts()
    if (receiptsRes !is NetworkResponse.ApiSuccess) {
        Logger.e(ltag) { "list err: $receiptsRes" }
        return false
    }
    val receiptUpds = receiptsRes.body.data
    Logger.i(ltag) { "got upds: #${receiptUpds.size}" }

    for ((msgId, upds) in receiptUpds.groupBy { it.msg_id }) {
        // only apply latest update of receipt for this msg&dup
        for ((dup, updsForDup) in upds.groupBy { it.dup }) {
            // delete updates in order (ensure oldest is deleted before newest is deleted)
            for (upd in updsForDup.sortedBy { it.created_at }) {
                val lctx = "[${upd.hs_id}]"
                // uploadRejected only sees the latest rejections, which should suffice but somehow doesn't
                //if (upd.rjct_at != null && activeHandshakes[upd.rcpt_ioid]?.meta?.id == upd.hs_id) {
                //    Logger.i(ltag) { "$lctx got rjct, deleting active handshake..." }
                //    activeHandshakes.remove(upd.rcpt_ioid)
                //}
                val delRes = msgApi.deleteReceiptUpdate(
                    DeleteReceiptUpdate(msgId, upd.rcpt_ioid, dup, upd.created_at)
                )
                // TODO still insert in db when remote delete fails?
                if (delRes is NetworkResponse.ApiSuccess) {
                    val rows = delRes.body.data.rows
                    if (rows == null || rows < 1) Logger.e(ltag) { "$lctx deleted no rows!" }
                    else Logger.d(ltag) { "$lctx deleted rcpt upd: #$rows" }
                } else Logger.e(ltag) { "$lctx delete rcpt upd failed: $delRes" }
            }

            val u = updsForDup.maxBy { it.created_at }
            db.msgQueries.upsertReceipt(
                Msg_rcpt(
                    msgId, u.rcpt_ioid, dup, u.hs_id, u.cause, u.recv_at, u.rjct_at, u.read_at
                )
            )
        }
    }

    return true
}

suspend fun pushLocalRcpt() {
    val ltag = "push-local-rcpt"
    val db = ServiceLocator.database
    val msgApi = ServiceLocator.msgRepo.api

    db.msgQueries.listUnsyncedReceipts().asFlow()
        .mapToList(coroutineContext + ServiceLocator.dispatchers.io).collect { syncStates ->
            Logger.i(ltag) { "unsynced: #${syncStates.size}" }
            for (syncStatus in syncStates) {
                val lctx = "[${syncStatus.msg_id}]"
                val rcpt = db.msgQueries.getReceipt(
                    syncStatus.msg_id, syncStatus.rcpt_ioid, syncStatus.dup
                ).executeAsOne()
                val createRcptRes = msgApi.createMsgReceipt(rcpt)
                if (createRcptRes !is NetworkResponse.ApiSuccess) {
                    Logger.e(ltag) { "$lctx create err: $createRcptRes" }
                    return@collect
                }
                db.msgQueries.deleteReceiptSyncStatus(
                    syncStatus.msg_id, syncStatus.rcpt_ioid, syncStatus.dup, syncStatus.updated_at
                )
            }
        }
}
