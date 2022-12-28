package app.opia.common.sync

import app.opia.common.api.Code
import app.opia.common.api.NetworkResponse
import app.opia.common.api.endpoint.MessagingApi
import app.opia.common.api.hasErr
import app.opia.common.api.model.*
import app.opia.common.api.repository.KeyRepo
import app.opia.common.api.repository.LinkPerm
import app.opia.common.db.Actor_link
import app.opia.common.db.Auth_session
import app.opia.common.db.Msg_payload
import app.opia.common.db.Msg_rcpt
import app.opia.common.di.ServiceLocator
import app.opia.common.utils.mutableListWithCap
import ch.oxc.nikea.*
import ch.oxc.nikea.Random
import ch.oxc.nikea.extra.toBase64
import ch.oxc.nikea.extra.toUtf8
import com.github.michaelbull.result.*
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime
import java.util.*

data class ActiveHandshake(
    val meta: ApiHandshake, val hs: SessionKeys
)

@OptIn(ExperimentalUnsignedTypes::class)
class ChatSync(
    private val di: ServiceLocator,
    private val keyRepo: KeyRepo,
    private val msgApi: MessagingApi,
    private val sess: Auth_session
) {
    private val db = di.database
    private val activeHandshakes = mutableMapOf<UUID, ActiveHandshake>()

    // TODO move some code to MessageRepository, etc.
    // TODO handle server errors: skex expired, etc.
    suspend fun sync() {
        if (!keyRepo.syncKeys(sess)) {
            println("[!] Sync > key sync failed, bailing...")
            return
        }

        // read, acquire latest state (keys, handshakes) used by peers
        if (!receiveMessages()) return

        // receive rejections, upload own receipts
        // get remote receipts (hs rejections) after receiving message so as not to delete handshakes prematurely
        // get remote receipts (hs rejections) before re-uploading rejected messages
        if (!syncReceipts()) return

        // create new hs if necessary TODO rename
        if (!uploadRejectedMessages()) return

        // use latest state to transmit new messages
        if (!transmitMessages()) return

        // upload backups (sending new messages is more important)
        backupMessages()
    }

    // peer may have opened & used multiple handshakes while we were offline
    // decrypt all messages using the handshakes available
    private suspend fun receiveMessages(): Boolean {
        // doesn't return own because it's already 'read' -- couldn't decrypt it anyway, need 2 different hs states for one ioid
        val listRes = msgApi.list()
        if (listRes !is NetworkResponse.ApiSuccess) {
            println("[!] Sync > rx > unexpected list err: $listRes")
            return false
        }
        val list = listRes.body.data
        println("[*] Sync > rx > msgs: #${list.msgs.size}, packets: #${list.packets.size}")

        val perHandshake = list.packets.groupBy { it.hs_id }
        // ioid to (hs)
        val newHandshakes = mutableMapOf<UUID, MutableList<ActiveHandshake>>()

        fun reject(hsId: UUID, packets: List<ApiMsgPacket>, reason: String) {
            db.msgQueries.transaction {
                afterRollback {
                    println("[!] Sync > rx > rollback: $hsId")
                }

                for (p in packets) {
                    val rcpt = Msg_rcpt(
                        p.msg_id, p.rcpt_ioid, p.dup, hsId, reason, null, ZonedDateTime.now(), null
                    )
                    db.msgQueries.upsertReceipt(rcpt)
                    db.msgQueries.upsertReceiptSyncStatus(
                        p.msg_id, p.rcpt_ioid, p.dup, ZonedDateTime.now()
                    )
                }
            }
        }

        for ((hsId, packets) in perHandshake) {
            val peerIO = packets[0].from_ioid
            // doesn't happen because server inserts the receipt with read_at
            if (peerIO == sess.ioid) println("[~] Sync > rx [$peerIO/$hsId] > msg from self")
            val hs: ActiveHandshake
            if (activeHandshakes[peerIO]?.meta?.id == hsId) {
                hs = activeHandshakes[peerIO]!!
            } else {
                println("[*] Sync > rx [$peerIO/$hsId] > hs not currently active, querying...")
                val hsRes = msgApi.getHandshake(hsId)
                when (hsRes) {
                    is NetworkResponse.ApiSuccess -> {
                        val apiHandshake = hsRes.body.data
                        println("[+] Sync > rx [$peerIO/$hsId] > remote lookup successful, created_at: ${apiHandshake.created_at}")
                        // TODO remove, server wouldn't return it...
                        if (apiHandshake.initiator_ioid == sess.ioid) {
                            println("[~] Sync > rx [$peerIO/$hsId] > hs is self-initiated, can't restore - rejecting...")
                            reject(hsId, packets, "rx_self_initiated_hs_lost")
                            break
                        }
                        val init = initHandshake(apiHandshake, isInitiator = false)
                        val err = init.getError()
                        if (err != null) {
                            println("[!] Sync > rx [$peerIO/$hsId] > failed to init hs: $err")
                            reject(hsId, packets, "rx_init/${err.name}")
                            break
                        }
                        hs = init.unwrap()
                        newHandshakes[peerIO] =
                            newHandshakes.getOrDefault(peerIO, mutableListOf()).apply {
                                add(hs)
                            }
                    }
                    is NetworkResponse.ApiError -> {
                        // eg. bc self-initiated: {"code":"forbidden","errors":{"initiator_ioid":[{"code":"forbidden"}]}}
                        println("[~] Sync > rx [$peerIO/$hsId] > remote lookup failed - rejecting...")
                        reject(hsId, packets, "rx_remote_lookup/${hsRes.body.code}")
                        break
                    }
                    else -> {
                        println("[!] Sync > rx [$peerIO/$hsId] > api lookup failed, assuming temporary - ignoring all #${packets.size} packets")
                        break
                    }
                }
            }
            for (p in packets.sortedBy { it.dup }) {
                val msg = list.msgs.first { it.id == p.msg_id }
                val raw = Base64.getDecoder().decode(p.payload_enc).toUByteArray()
                val n = hs.hs.rx.n.toLong()
                if (n != p.seqno) {
                    println("[*] Sync > rx [$peerIO/$hsId@${p.dup}] > n: $n, seqno: ${p.seqno}")
                    // NOTE: if it fails it's (likely) the first one
                    reject(hsId, packets, "rx_seqno/$n/${p.seqno}")
                    break
                }
                val payload = hs.hs.rx.decrypt(raw).toByteArray().toUtf8()
                println("[+] Sync > rx [$peerIO/$hsId@${p.dup}] > payload: '$payload'")
                val rcpt = Msg_rcpt(
                    p.msg_id, p.rcpt_ioid, p.dup, hsId, null, ZonedDateTime.now(), null, null
                )
                db.msgQueries.transaction {
                    afterRollback {
                        println("[!] Sync > rx [$peerIO/$hsId@${p.dup}] > rollback")
                    }
                    db.msgQueries.insert(msg)
                    db.msgQueries.insertPayload(Msg_payload(msg.id, payload))

                    db.msgQueries.upsertReceipt(rcpt)
                    db.msgQueries.upsertReceiptSyncStatus(
                        msg.id, rcpt.rcpt_ioid, rcpt.dup, ZonedDateTime.now()
                    )

                    // show group if it's not addressed to us
                    val linkActor = if (msg.rcpt_id == sess.actor_id) msg.from_id else msg.rcpt_id
                    // TODO perm completely wrong, if it's a group it needs to come from the invite
                    db.actorQueries.insertLink(
                        Actor_link(
                            sess.actor_id,
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
        }

        for ((peerIO, newPeerHandshakes) in newHandshakes) {
            for (newHs in newPeerHandshakes) {
                val currentHs = activeHandshakes[peerIO] // every loop
                if (currentHs == null || currentHs.meta.created_at.isBefore(newHs.meta.created_at)) {
                    println("[+] Sync > rx [$peerIO] > current handshake (${currentHs?.meta?.id}) outdated, using new: ${newHs.meta.id}")
                    activeHandshakes[peerIO] = newHs
                }
            }
        }

        return true
    }

    // upload receipts created locally, download receipts from peers
    private suspend fun syncReceipts(): Boolean {
        val syncStates = db.msgQueries.listUnsyncedReceipts().asFlow().mapToList().first()
        println("[*] Sync > rcpts > unsynced: #${syncStates.size}")
        for (syncStatus in syncStates) {
            val rcpt =
                db.msgQueries.getReceipt(syncStatus.msg_id, syncStatus.rcpt_ioid, syncStatus.dup)
                    .asFlow().mapToOne().first()
            assert(rcpt.hs_id != null) // only server creates rcpt with hs_id = null
            val createRcptRes = msgApi.createMsgReceipt(rcpt)
            if (createRcptRes !is NetworkResponse.ApiSuccess) {
                println("[!] Sync > rcpts > create rcpt ${rcpt.msg_id}: $createRcptRes")
                return false
            }
            db.msgQueries.deleteReceiptSyncStatus(
                syncStatus.msg_id, syncStatus.rcpt_ioid, syncStatus.dup, syncStatus.updated_at
            )
        }

        // receipts for which we still expect a server response (inefficient polling)
        //val updated = db.msgQueries.listOutstandingReceipts()

        // receipts updated by peers
        val receiptsRes = msgApi.listUpdatedReceipts()
        if (receiptsRes !is NetworkResponse.ApiSuccess) {
            println("[!] Sync > rcpts > list rcpts: $receiptsRes")
            return false
        }
        val receiptUpds = receiptsRes.body.data
        println("[*] Sync > rcpts > upds: #${receiptUpds.size}")
        for ((msgId, upds) in receiptUpds.groupBy { it.msg_id }) {
            // only apply latest update of receipt for this msg&dup
            for ((dup, updsForDup) in upds.groupBy { it.dup }) {
                val u = updsForDup.maxBy { it.created_at }
                db.msgQueries.upsertReceipt(
                    Msg_rcpt(
                        msgId, u.rcpt_ioid, dup, u.hs_id, u.cause, u.recv_at, u.rjct_at, u.read_at
                    )
                )
                // delete updates in order (ensure oldest is deleted before newest is deleted)

                for (upd in updsForDup.sortedBy { it.created_at }) {
                    // uploadRejected only sees the latest rejections, which should suffice but somehow doesn't
                    if (upd.rjct_at != null && activeHandshakes[upd.rcpt_ioid]?.meta?.id == upd.hs_id) {
                        println("[~] Sync > rcpts > got rjct, deleting active handshake: ${upd.hs_id}")
                        activeHandshakes.remove(upd.rcpt_ioid)
                    }
                    val delRes = msgApi.deleteReceiptUpdate(
                        DeleteReceiptUpdate(msgId, u.rcpt_ioid, dup, upd.created_at)
                    )
                    if (delRes is NetworkResponse.ApiSuccess) {
                        val rows = delRes.body.data.rows
                        if (rows == null || rows < 1) println("[!] Sync > rcpts > deleted no rows!")
                        else println("[+] Sync > rcpts > deleted rcpt upd: #$rows")
                    } else println("[!] Sync > rcpts > delete rcpt upd failed: $delRes")
                }
            }
        }

        return true
    }

    private suspend fun uploadRejectedMessages(): Boolean {
        val outstandingRes = msgApi.listOutstandingMsgs()
        if (outstandingRes !is NetworkResponse.ApiSuccess) {
            println("[!] Sync > rjcts > query outstanding: $outstandingRes")
            return false
        }
        val rcpts = outstandingRes.body.data
        println("[*] Sync > rjcts > outstanding: #${rcpts.size}")

        for (rjct in rcpts) {
            if (activeHandshakes[rjct.rcpt_ioid]?.meta?.id == rjct.hs_id) {
                println("[*] Sync > rjcts [${rjct.msg_id} -> ${rjct.rcpt_ioid}] > active handshake outdated")
                activeHandshakes.remove(rjct.rcpt_ioid)
            }
        }

        for (rjct in rcpts) {
            val rcptIO = rjct.rcpt_ioid
            println("[*] Sync > rjcts [${rjct.msg_id} -> $rcptIO] > re-encrypting msg for peer...")
            if (activeHandshakes[rcptIO] == null) {
                println("[*] Sync > rjcts [${rjct.msg_id} -> $rcptIO] > +hs > no active handshake, initiating...")
                val hsRes = msgApi.createHandshake(CreateHandshakeParams(rcptIO))
                if (hsRes !is NetworkResponse.ApiSuccess) {
                    println("[!] Sync > rjcts [${rjct.msg_id} -> $rcptIO] > +hs > failed to open handshake")
                    if (hsRes is NetworkResponse.ApiError) {
                        val errors = hsRes.body.errors
                        if (errors.hasErr("ekex", Code.required)) {
                            println("[~] Sync > rjcts [${rjct.msg_id} -> $rcptIO] > +hs > self out of ekex keys")
                        }
                    }
                    break
                }
                val hs = hsRes.body.data
                println("[+] Sync > rjcts [${rjct.msg_id} -> $rcptIO] > +hs > registered: $hs")

                val init = initHandshake(hs, isInitiator = true)
                val err = init.getError()
                if (err != null) {
                    println("[!] Sync > rjcts [${rjct.msg_id} -> $rcptIO] > +hs > err: $err")
                    break
                }
                activeHandshakes[rcptIO] = init.unwrap()
            }
            val msg = db.msgQueries.getById(rjct.msg_id).asFlow().mapToOne().first()
            val hs = activeHandshakes[rcptIO]!!
            println("[*] Sync > rjcts [$rcptIO] > re-encrypting msg: '${msg.payload}'")
            val payload = msg.payload.encodeToByteArray().toUByteArray()
            val enc = hs.hs.tx.encrypt(payload)
            val enc64 = enc.toByteArray().toBase64()
            println("[*] Sync > rjcts [$rcptIO] > enc: $enc64")
            val p = ApiMsgPacket(
                msg.id,
                msg.from_id,
                msg.rcpt_id,
                rcptIO,
                dup = rjct.dup + 1,
                hs.meta.id,
                seqno = hs.hs.tx.n.toLong() - 1L,
                enc64
            )
            val createMsgPacketRes = msgApi.createMsgPacket(msg.id, p)
            if (createMsgPacketRes !is NetworkResponse.ApiSuccess) {
                println("[!] Sync > rjcts [$rcptIO] > create packet failed")
                break
            }
            val rcpt = createMsgPacketRes.body.data.rcpt
            db.msgQueries.upsertReceipt(rcpt)
            println("[+] Sync > rjcts [$rcptIO] > saved new rcpt")
        }

        return true
    }

    private suspend fun transmitMessages(): Boolean {
        val unsynced = db.msgQueries.listUnsynced(sess.actor_id).asFlow().mapToList().first()
        println("[*] Sync > tx > unsynced: #${unsynced.size}")

        // need to loop per msg first for initial receipt creation
        for (msg in unsynced) {
            println("[*] Sync > tx [+${msg.id} -> ${msg.rcpt_id}] > preparing msg: '${msg.payload}'")
            // TODO don't query IOs every time if rcpt is the same...
            val rcptIOsRes = msgApi.listIOs(msg.rcpt_id)
            if (rcptIOsRes !is NetworkResponse.ApiSuccess) {
                println("[!] Sync > tx [+${msg.id} -> ${msg.rcpt_id}] > listing peer's IOs failed: $rcptIOsRes")
                return false
            }
            val keyed = rcptIOsRes.body.data.keyed
            val rcptIOs = rcptIOsRes.body.data.ios.map { it.id }
            println("[+] Sync > tx [+${msg.id} -> ${msg.rcpt_id}] > peer IOs (#${rcptIOs.size}): $rcptIOs")

            val packets = mutableListWithCap<ApiMsgPacket>(rcptIOs.size)
            rcptIO@ for ((rcptIdx, rcptIO) in rcptIOs.withIndex()) {
                println(
                    "[*] Sync > tx [-> ${msg.rcpt_id}/$rcptIdx] > active hs: ${
                        activeHandshakes.get(
                            rcptIO
                        )?.meta?.id
                    }"
                )
                if (!activeHandshakes.containsKey(rcptIO)) {
                    if (!keyed.contains(rcptIO)) {
                        println("[~] Sync > tx [-> ${msg.rcpt_id}/$rcptIdx] > peer has no keys, skipping...")
                        continue@rcptIO
                    }
                    println("[*] Sync > tx [-> ${msg.rcpt_id}/$rcptIdx] > no active handshake, initiating...")
                    val hsRes = msgApi.createHandshake(CreateHandshakeParams(rcptIO))
                    when (hsRes) {
                        is NetworkResponse.NetworkError -> {
                            println("[~] Sync > tx [-> ${msg.rcpt_id}/$rcptIdx] > +hs > no network, aborting tx...")
                            return false
                        }
                        is NetworkResponse.ApiError -> {
                            val errors = hsRes.body.errors
                            if (errors.hasErr("ekex", Code.required)) {
                                println("[~] Sync > tx [-> ${msg.rcpt_id}/$rcptIdx] > +hs > self out of ekex keys, aborting tx...")
                                return false
                            } else if (errors.hasErr("responder_ioid", Code.expired)) {
                                println("[~] Sync > tx [-> ${msg.rcpt_id}/$rcptIdx] > +hs > peer is out of keys, skipping...")
                                continue@rcptIO
                            } else {
                                println("[!] Sync > tx [-> ${msg.rcpt_id}/$rcptIdx] > +hs > unexpected api err: $hsRes")
                                return false
                            }
                        }
                        is NetworkResponse.UnknownError -> {
                            println("[!] Sync > tx [-> ${msg.rcpt_id}/$rcptIdx] > +hs > unexpected err: $hsRes")
                            return false
                        }
                        else -> {}
                    }
                    val hs = (hsRes as NetworkResponse.ApiSuccess).body.data
                    println("[+] Sync > tx [-> ${msg.rcpt_id}/$rcptIdx] > +hs > registered: $hs")

                    val init = initHandshake(hs, isInitiator = true)
                    val err = init.getError()
                    if (err != null) {
                        println("[!] Sync > tx [-> ${msg.rcpt_id}/$rcptIdx] > +hs > err: $err")
                        continue@rcptIO
                    }
                    activeHandshakes[rcptIO] = init.unwrap()
                }
                val hs = activeHandshakes[rcptIO]!!
                println("[*] Sync > tx [-> ${msg.rcpt_id}/$rcptIdx] > encrypting msg: '${msg.payload}'")
                val payload = msg.payload.encodeToByteArray().toUByteArray()
                val enc = hs.hs.tx.encrypt(payload)
                val enc64 = enc.toByteArray().toBase64()
                println("[*] Sync > tx [-> ${msg.rcpt_id}/$rcptIdx] > enc: $enc64")
                val p = ApiMsgPacket(
                    msg.id,
                    msg.from_id,
                    msg.rcpt_id,
                    rcptIO,
                    dup = 0L,
                    hs.meta.id,
                    seqno = hs.hs.tx.n.toLong() - 1L, // already encrypted
                    enc64
                )
                packets.add(p)
            }
            println("[+] Sync > tx [+${msg.id} -> ${msg.rcpt_id}] > packets: #${packets.size}")

            val msgRes = msgApi.create(CreateMsgParams(msg.id, msg.rcpt_id, packets))
            when (msgRes) {
                is NetworkResponse.ApiSuccess -> {
                    val body = msgRes.body.data
                    println("[+] Sync > tx [+${msg.id} -> ${msg.rcpt_id}] > got receipts: #${body.rcpts.size} (rjct: #${body.rcpts.count { it.rjct_at != null }})")
                    db.msgQueries.transaction {
                        for (rcpt in body.rcpts) {
                            db.msgQueries.upsertReceipt(rcpt)
                        }
                    }
                }
                is NetworkResponse.ApiError, is NetworkResponse.UnknownError -> {
                    println("[!] Sync > tx [+${msg.id} -> ${msg.rcpt_id}] > unexpected err, aborting tx...")
                    return false
                }
                is NetworkResponse.NetworkError -> {
                    println("[~] Sync > tx [+${msg.id} -> ${msg.rcpt_id}] > no network, aborting tx...")
                    return false
                }
            }
        }
        return true
    }

    private suspend fun backupMessages(): Boolean {
        val vaultKey =
            db.vaultKeyQueries.get(sess.actor_id, sess.secret_update_id).asFlow().mapToOne().first()

        // list outstanding backups
        val outstandingRes = msgApi.listOutstandingMsgBackups()
        if (outstandingRes !is NetworkResponse.ApiSuccess) {
            println("[!] Sync > bckps > unexpected list err: $outstandingRes")
            return false
        }

        for (outstanding in outstandingRes.body.data) {
            val msg = db.msgQueries.getById(outstanding).asFlow().mapToOne().first()
            println("[+] Sync > bckps [${vaultKey.id}] > encrypting msg: ${msg.id}/'${msg.payload}'")

            val nonce = Random.gen(24)
            val enc = XChaCha20Poly1305Cipher.encrypt(
                msg.payload.encodeToByteArray().toUByteArray(),
                "${sess.actor_id}:${msg.msg_id}".encodeToByteArray().toUByteArray(),
                nonce,
                vaultKey.seck_clr
            )
            val enc64 = (nonce + enc).toByteArray().toBase64()
            println("[+] Sync > bckps > enc: $enc64")

            val createRes = msgApi.createMsgBackup(
                CreateMsgBackup(
                    msg.id, vaultKey.id, enc64
                )
            )
            if (createRes !is NetworkResponse.ApiSuccess) {
                println("[!] Sync > bckps > unexpected create err: $createRes")
                return false
            }
            println("[+] Sync > bckps > created backup for msg: ${msg.id}")
        }

        return true
    }

    // client locally ensures that its latest skex key is used
    private suspend fun initHandshake(
        hs: ApiHandshake, isInitiator: Boolean
    ): Result<ActiveHandshake, CryptoError> {
        val role = if (isInitiator) "initiator" else "responder"
        val peer = if (isInitiator) hs.responder_ioid else hs.initiator_ioid
        val s = db.keyPairQueries.getSKexKey(sess.ioid).asFlow().mapToOne().first()
        val hsS = if (isInitiator) hs.initiator_skey_id else hs.responder_skey_id
        if (s.id != hsS) return Err(CryptoError.invalid_skex)
        println("[*] Sync > +hs [$role] > using skex: ${s.id}")

        val hsE = if (isInitiator) hs.initiator_ekey_id else hs.responder_ekey_id
        val e = db.keyPairQueries.getEKexKey(hsE).asFlow().mapToOneOrNull().first()
        if (e == null) return Err(CryptoError.invalid_ekex)
        println("[*] Sync > +hs [$role] > using ekex: ${e.id}")

        val rs = (if (isInitiator) hs.responder_skey else hs.initiator_skey)!!
        val re = (if (isInitiator) hs.responder_ekey else hs.initiator_ekey)!!

        println("[*] Sync > +hs [$role] > querying remote identity...")
        val identityRes = msgApi.getIdentity(peer)
        when (identityRes) {
            is NetworkResponse.ApiError, is NetworkResponse.UnknownError -> {
                println("[!] Sync > +hs [$role] > ri > unexpected err: $identityRes")
                return Err(CryptoError.unknown)
            }
            is NetworkResponse.NetworkError -> return Err(CryptoError.temporary)
            else -> {}
        }
        val ri = (identityRes as NetworkResponse.ApiSuccess).body.data

        // TODO use correct config, not nikea.DefaultConfig
        val keys = Keys(
            s = KeyPair(s.seck_clr, s.pubk),
            e = KeyPair(e.seck_clr, e.pubk),
            rs = Base64.getDecoder().decode(rs.pubk).toUByteArray(),
            re = Base64.getDecoder().decode(re.pubk).toUByteArray(),
            remoteIdentity = Identity(
                pubk = Base64.getDecoder().decode(ri.pubk).toUByteArray(),
                ssig = Base64.getDecoder().decode(rs.pubk_signed).toUByteArray(),
                esig = Base64.getDecoder().decode(re.pubk_signed).toUByteArray()
            )
        )
        val session = Handshake().run { if (isInitiator) initiate(keys) else respond(keys) }
        println("[+] Sync > +hs [$role] > session: $session")

        val active = ActiveHandshake(hs, session)
        return Ok(active)
    }

    companion object Factory {
        suspend fun init(di: ServiceLocator, keyRepo: KeyRepo, msgApi: MessagingApi): ChatSync {
            val authSession = di.database.sessionQueries.getLatest().asFlow().mapToOne().first()

            return ChatSync(di, keyRepo, msgApi, authSession)
        }
    }
}

enum class CryptoError {
    invalid_skex, invalid_ekex, unknown, temporary
}
