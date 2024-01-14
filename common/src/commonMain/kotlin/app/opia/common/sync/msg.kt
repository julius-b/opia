package app.opia.common.sync

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.opia.common.api.NetworkResponse
import app.opia.common.api.model.ApiMsgPacket
import app.opia.common.api.repository.LinkPerm
import app.opia.common.db.Actor_link
import app.opia.common.db.Msg_payload
import app.opia.common.db.Msg_rcpt
import app.opia.common.di.AuthStatus
import app.opia.common.di.ServiceLocator
import app.opia.common.utils.mutableListWithCap
import ch.oxc.nikea.extra.toBase64
import ch.oxc.nikea.extra.toUtf8
import co.touchlab.kermit.Logger
import java.time.ZonedDateTime
import java.util.Base64
import java.util.UUID
import kotlin.coroutines.coroutineContext

suspend fun pullMsg(): Boolean {
    val ltag = "pull-msg"
    val sess = ServiceLocator.getAuth()
    if (sess !is AuthStatus.Authenticated) return false

    val db = ServiceLocator.database
    val msgApi = ServiceLocator.msgRepo.api

    val listRes = msgApi.list()
    if (listRes !is NetworkResponse.ApiSuccess) {
        Logger.i(ltag) { "querying msgs failed: $listRes" }
        return false
    }
    val list = listRes.body.data
    Logger.d(ltag) { "got #${list.size} msgs" }

    for (p in list) {
        val payload = Base64.getDecoder().decode(p.payload_enc).toUtf8()
        val lctx = "[msg=${p.id}@${p.dup}|${p.hs_id}, from=${p.from_id}@${p.from_ioid}]"
        Logger.d(ltag) { "$lctx payload: '$payload'" }
        val rcpt = Msg_rcpt(
            p.id, p.rcpt_ioid, p.dup, null, null, ZonedDateTime.now(), null, null
        )
        db.msgQueries.transaction {
            afterRollback {
                Logger.e(ltag) { "$lctx rollback" }
            }
            val msgPayload = Msg_payload(p.id, p.from_id, p.rcpt_id, payload, p.timestamp, null)
            db.msgQueries.insertPayload(msgPayload)

            db.msgQueries.upsertReceipt(rcpt)
            db.msgQueries.upsertReceiptSyncStatus(
                p.id, rcpt.rcpt_ioid, rcpt.dup, ZonedDateTime.now()
            )

            // show group if it's not addressed to us
            val linkActor = if (p.rcpt_id == sess.actorId) p.from_id else p.rcpt_id
            // TODO perm completely wrong, if it's a group it needs to come from the invite
            db.actorQueries.insertLink(
                Actor_link(
                    sess.actorId,
                    linkActor,
                    LinkPerm.write.ordinal.toLong(),
                    ZonedDateTime.now(),
                    null,
                    null,
                    null,
                    null
                )
            )
        }
    }
    return true
}

// returns: continue
suspend fun pushMsg(ioid: UUID, msg: Msg_payload): Boolean {
    val ltag = "push-msg"
    val lctx = "[msg=${msg.msg_id}, rcpt=${msg.rcpt_id}]"
    val db = ServiceLocator.database
    val msgApi = ServiceLocator.msgRepo.api

    val rcptIOsRes = msgApi.listIOs(msg.rcpt_id)
    if (rcptIOsRes !is NetworkResponse.ApiSuccess) {
        Logger.i(ltag) { "$lctx listing peer's IOs failed: $rcptIOsRes" }
        return true
    }

    // use ios directly, not keyed, since most clients don't upload any keys in pt mode
    val rcptIOs = rcptIOsRes.body.data.ios.map { it.id }
    Logger.d(ltag) { "$lctx peer IOs (#${rcptIOs.size}): $rcptIOs" }

    val packets = mutableListWithCap<ApiMsgPacket>(rcptIOs.size)
    Logger.d(ltag) { "$lctx encrypting msg: '${msg.payload.replace('\n', '|')}'" }
    rcptIO@ for ((rcptIdx, rcptIO) in rcptIOs.withIndex()) {
        val lctx = "[msg=${msg.msg_id}, rcpt=${msg.rcpt_id}, idx=$rcptIdx]"
        val payload = msg.payload.encodeToByteArray().toBase64()
        Logger.d(ltag) { "$lctx payload: $payload" }
        val p = ApiMsgPacket(
            msg.msg_id, ioid, msg.rcpt_id, rcptIO, dup = 0L, null, seqno = 0, msg.timestamp, payload
        )
        packets.add(p)
    }
    Logger.d(ltag) { "$lctx packets: #${packets.size}" }
    when (val msgRes = msgApi.create(packets)) {
        is NetworkResponse.ApiSuccess -> {
            val body = msgRes.body.data
            Logger.i(ltag) { "$lctx got receipts: #${body.size} (rjct: #${body.count { it.rjct_at != null }})" }
            db.msgQueries.transaction {
                for (rcpt in body) {
                    db.msgQueries.upsertReceipt(rcpt)
                }
            }
        }

        is NetworkResponse.ApiError, is NetworkResponse.UnknownError -> {
            Logger.e(ltag) { "$lctx unexpected err (aborting tx): $msgRes" }
            return false
        }

        is NetworkResponse.NetworkError -> {
            Logger.w(ltag) { "$lctx no network (aborting tx): $msgRes" }
            return false
        }
    }
    return true
}

// TODO re-emit last value every now & then for retry / trigger on network change
// TODO integrate in Cord (send via Cord), LocalMsg.Push
suspend fun pushLocalMsg(actorID: UUID, ioid: UUID) {
    val ltag = "push-local-msg"

    // TODO want collect-latest but don't want to cancel (since query always returns all)
    // NOTE: it's ok to re-verify message hasn't been synced yet anyway
    // TODO msgApi/Repo: don't query IOs every time if rcpt is the same... server will notify if not enough packets
    ServiceLocator.database.msgQueries.listUnsynced(actorID).asFlow()
        .mapToList(coroutineContext + ServiceLocator.dispatchers.io).collect { unsynced ->
            Logger.i(ltag) { "pushing unsynced... msgs=(${unsynced.size})$unsynced" }

            for (msg in unsynced) {
                if (!pushMsg(ioid, msg)) {
                    Logger.w(ltag) { "aborting..." }
                    return@collect
                }
            }
        }
}
