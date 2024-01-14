package app.opia.common.sync

import app.opia.common.ApiPushReg
import app.opia.common.api.Code
import app.opia.common.api.NetworkResponse
import app.opia.common.api.hasErr
import app.opia.common.api.model.ApiHandshake
import app.opia.common.api.model.ApiMsgPacket
import app.opia.common.api.model.CreateHandshakeParams
import app.opia.common.api.model.CreateMsgBackup
import app.opia.common.api.model.DeleteReceiptUpdate
import app.opia.common.db.Auth_session
import app.opia.common.db.Msg_rcpt
import app.opia.common.db.Notification_registration
import app.opia.common.di.ServiceLocator
import app.opia.common.utils.mutableListWithCap
import ch.oxc.nikea.Handshake
import ch.oxc.nikea.Identity
import ch.oxc.nikea.KeyPair
import ch.oxc.nikea.Keys
import ch.oxc.nikea.Random
import ch.oxc.nikea.SessionKeys
import ch.oxc.nikea.XChaCha20Poly1305Cipher
import ch.oxc.nikea.extra.toBase64
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64
import java.util.UUID
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

const val EE_MODE_NONE = 0 // plain
const val EE_MODE_IDENT = 1
const val EE_MODE_FULL = 2
const val EE_MODE = EE_MODE_NONE;

data class ActiveHandshake(
    val meta: ApiHandshake, val hs: SessionKeys
)

data class SyncStats(
    val recvCnt: Int,
    val sendCnt: Int
)

@OptIn(ExperimentalUnsignedTypes::class)
internal class ChatSync(
    private val sess: Auth_session
) {
    private val db by lazy { ServiceLocator.database }
    private val keyRepo by lazy { ServiceLocator.keyRepo }
    private val msgApi by lazy { ServiceLocator.msgRepo.api }
    private val actorApi by lazy { ServiceLocator.actorRepo.api }
    private val activeHandshakes = mutableMapOf<UUID, ActiveHandshake>()

    // TODO move some code to MessageRepository, etc.
    // TODO handle server errors: skex expired, etc.
    suspend fun sync(): Result<SyncStats, String> {
        // TODO upload notificationThing


        if (EE_MODE >= EE_MODE_IDENT && !keyRepo.syncKeys(sess)) {
            println("[!] Sync > key sync failed, bailing...")
            return Err("")
        }

        // read, acquire latest state (keys, handshakes) used by peers
        //if (!(if (EE_MODE == EE_MODE_FULL) receiveMessages() else rxMessagesPlain())) return Err("")

        // receive rejections, upload own receipts
        // get remote receipts (hs rejections) after receiving message so as not to delete handshakes prematurely
        // get remote receipts (hs rejections) before re-uploading rejected messages
        //if (!syncReceipts()) return Err("")

        // create new hs if necessary TODO rename
        if (!uploadRejectedMessages()) return Err("")

        // use latest state to transmit new messages
        //if (!(if (EE_MODE == EE_MODE_FULL) transmitMessages() else txMessagesPlain())) return Err("")

        // upload backups (sending new messages is more important)
        //backupMessages() // LibSodium not initialized?

        return Ok(SyncStats(0, 0))
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
            val msg = db.msgQueries.getById(rjct.msg_id).executeAsOne()
            val hs = activeHandshakes[rcptIO]!!
            println("[*] Sync > rjcts [$rcptIO] > re-encrypting msg: '${msg.payload}'")
            val payload = msg.payload.encodeToByteArray().toUByteArray()
            val enc = hs.hs.tx.encrypt(payload)
            val enc64 = enc.toByteArray().toBase64()
            println("[*] Sync > rjcts [$rcptIO] > enc: $enc64")
            val seqno = hs.hs.tx.n.toLong() - 1L
            val p = ApiMsgPacket(
                msg.msg_id, sess.ioid, msg.rcpt_id, rcptIO, rjct.dup + 1, hs.meta.id, seqno, msg.timestamp, enc64
            )
            val createMsgPacketRes = msgApi.createMsgPacket(msg.msg_id, p)
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
        val unsynced = db.msgQueries.listUnsynced(sess.actor_id).executeAsList()
        println("[*] Sync > tx > unsynced: #${unsynced.size}")

        // need to loop per msg first for initial receipt creation
        for (msg in unsynced) {
            println("[*] Sync > tx [+${msg.msg_id} -> ${msg.rcpt_id}] > preparing msg: '${msg.payload}'")
            // TODO don't query IOs every time if rcpt is the same...
            val rcptIOsRes = msgApi.listIOs(msg.rcpt_id)
            if (rcptIOsRes !is NetworkResponse.ApiSuccess) {
                println("[!] Sync > tx [+${msg.msg_id} -> ${msg.rcpt_id}] > listing peer's IOs failed: $rcptIOsRes")
                return false
            }
            val keyed = rcptIOsRes.body.data.keyed
            val rcptIOs = rcptIOsRes.body.data.ios.map { it.id }
            println("[+] Sync > tx [+${msg.msg_id} -> ${msg.rcpt_id}] > peer IOs (#${rcptIOs.size}): $rcptIOs")

            val packets = mutableListWithCap<ApiMsgPacket>(rcptIOs.size)
            rcptIO@ for ((rcptIdx, rcptIO) in rcptIOs.withIndex()) {
                println("[*] Sync > tx [-> ${msg.rcpt_id}/$rcptIdx] > active hs: ${activeHandshakes[rcptIO]?.meta?.id}")
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
                            return if (errors.hasErr("ekex", Code.required)) {
                                println("[~] Sync > tx [-> ${msg.rcpt_id}/$rcptIdx] > +hs > self out of ekex keys, aborting tx...")
                                false
                            } else if (errors.hasErr("responder_ioid", Code.expired)) {
                                println("[~] Sync > tx [-> ${msg.rcpt_id}/$rcptIdx] > +hs > peer is out of keys, skipping...")
                                continue@rcptIO
                            } else {
                                println("[!] Sync > tx [-> ${msg.rcpt_id}/$rcptIdx] > +hs > unexpected api err: $hsRes")
                                false
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
                // already encrypted
                val seqno = hs.hs.tx.n.toLong() - 1L
                val p = ApiMsgPacket(
                    msg.msg_id, sess.ioid, msg.rcpt_id, rcptIO, dup = 0L, hs.meta.id, seqno, msg.timestamp, enc64
                )
                packets.add(p)
            }
            println("[+] Sync > tx [+${msg.msg_id} -> ${msg.rcpt_id}] > packets: #${packets.size}")

            when (val msgRes = msgApi.create(packets)) {
                is NetworkResponse.ApiSuccess -> {
                    val body = msgRes.body.data
                    println("[+] Sync > tx [+${msg.msg_id} -> ${msg.rcpt_id}] > got receipts: #${body.size} (rjct: #${body.count { it.rjct_at != null }})")
                    db.msgQueries.transaction {
                        for (rcpt in body) {
                            db.msgQueries.upsertReceipt(rcpt)
                        }
                    }
                }

                is NetworkResponse.ApiError, is NetworkResponse.UnknownError -> {
                    println("[!] Sync > tx [+${msg.msg_id} -> ${msg.rcpt_id}] > unexpected err, aborting tx...")
                    return false
                }

                is NetworkResponse.NetworkError -> {
                    println("[~] Sync > tx [+${msg.msg_id} -> ${msg.rcpt_id}] > no network, aborting tx...")
                    return false
                }
            }
        }
        return true
    }

    private suspend fun backupMessages(): Boolean {
        val vaultKey = db.vaultKeyQueries.get(sess.actor_id, sess.secret_update_id).executeAsOne()

        // list outstanding backups
        val outstandingRes = msgApi.listOutstandingMsgBackups()
        if (outstandingRes !is NetworkResponse.ApiSuccess) {
            println("[!] Sync > bckps > unexpected list err: $outstandingRes")
            return false
        }

        for (outstanding in outstandingRes.body.data) {
            val msg = db.msgQueries.getById(outstanding).executeAsOne()
            println("[+] Sync > bckps [${vaultKey.id}] > encrypting msg: ${msg.msg_id}/'${msg.payload}'")

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
                    msg.msg_id, vaultKey.id, enc64
                )
            )
            if (createRes !is NetworkResponse.ApiSuccess) {
                println("[!] Sync > bckps > unexpected create err: $createRes")
                return false
            }
            println("[+] Sync > bckps > created backup for msg: ${msg.msg_id}")
        }

        return true
    }

    // client locally ensures that its latest skex key is used
    private suspend fun initHandshake(
        hs: ApiHandshake, isInitiator: Boolean
    ): Result<ActiveHandshake, CryptoError> {
        val role = if (isInitiator) "initiator" else "responder"
        val peer = if (isInitiator) hs.responder_ioid else hs.initiator_ioid
        val s = db.keyPairQueries.getSKexKey(sess.ioid).executeAsOne()
        val hsS = if (isInitiator) hs.initiator_skey_id else hs.responder_skey_id
        if (s.id != hsS) return Err(CryptoError.invalid_skex)
        println("[*] Sync > +hs [$role] > using skex: ${s.id}")

        val hsE = if (isInitiator) hs.initiator_ekey_id else hs.responder_ekey_id
        val e = db.keyPairQueries.getEKexKey(hsE).executeAsOneOrNull() ?: return Err(
            CryptoError.invalid_ekex
        )
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

    // for now: loop (:/) until successful
    // TODO: check if local token changed, check if server lost token (ws notice)
    // returns: idempotent "done"
    suspend fun syncPushCfg(ioid: UUID): Boolean = withContext(ServiceLocator.dispatchers.io) {
        //val ioid = ServiceLocator.authCtx.ioid
        var curr = db.msgQueries.getNotificationReg(ioid).executeAsOneOrNull()
        if (curr == null) {
            println("[*] Sync > push-reg - creating new reg")
            val reg = ServiceLocator.notificationRepo.getCurrentLocalReg(ioid)
            if (reg == null) {
                println("[-] Sync > push-reg - failed to get external/system reg, exiting")
                return@withContext false
            }
            // save when synced = true :) (only UP needs to be saved immediately)
            curr = Notification_registration(ioid, reg.provider, reg.endpoint, false)
            println("[+] Sync > push-reg - got reg: ${reg.provider}/${reg.endpoint}")
        }
        val res = actorApi.postNotifyReg(ApiPushReg(curr.provider, curr.endpoint, ioid))
        if (res is NetworkResponse.ApiSuccess) {
            db.msgQueries.upsertNotificationReg(ioid, curr.provider, curr.endpoint, true)
            println("[+] Sync > push-reg - registered")
            return@withContext true
        }
        println("[-] Sync > push-reg - post failed: $res")
        return@withContext false
    }

    companion object Factory {
        suspend fun init(): ChatSync {
            val sess = withContext(Dispatchers.IO) {
                ServiceLocator.database.sessionQueries.getLatest().executeAsOne()
            }
            return ChatSync(sess)
        }
    }
}

enum class CryptoError {
    invalid_skex, invalid_ekex, unknown, temporary
}
