package app.opia.common.api.model

import app.opia.common.db.Installation_ownership
import app.opia.common.db.Msg
import app.opia.common.db.Msg_rcpt
import java.time.ZonedDateTime
import java.util.*

data class ListIOsRes(
    val ios: List<Installation_ownership>,
    val keyed: List<UUID>
)

data class CreateHandshakeParams(
    val responder_ioid: UUID
)

// key nullable for GET
data class ApiHandshake(
    val id: UUID,
    val initiator_id: UUID,
    val initiator_ioid: UUID,
    val responder_id: UUID,
    val responder_ioid: UUID,
    val initiator_skey_id: UUID,
    val initiator_skey: ApiKeyPair?,
    val initiator_ekey_id: UUID,
    val initiator_ekey: ApiKeyPair?,
    val responder_skey_id: UUID,
    val responder_skey: ApiKeyPair?,
    val responder_ekey_id: UUID,
    val responder_ekey: ApiKeyPair?,
    val created_at: ZonedDateTime,
    val deleted_at: ZonedDateTime?
)

data class ListMsgsRes(
    val msgs: List<Msg>, val packets: List<ApiMsgPacket>
)

data class CreateMsgParams(
    val id: UUID, val rcpt_id: UUID, val packets: List<ApiMsgPacket>
)

data class CreateMsgRes(
    val msg: Msg, val rcpts: List<Msg_rcpt>
)

data class ApiMsgPacket(
    val msg_id: UUID,
    val from_ioid: UUID,
    val rcpt_id: UUID,
    val rcpt_ioid: UUID,
    val dup: Long,
    val hs_id: UUID,
    val seqno: Long,
    val payload_enc: String // Base64 encoded UByteArray
)

data class ApiUpdatedReceipt(
    // Msg_rcpt
    val msg_id: UUID,
    val rcpt_ioid: UUID,
    val dup: Long,
    val hs_id: UUID?,
    val recv_at: ZonedDateTime?,
    val rjct_at: ZonedDateTime?,
    val read_at: ZonedDateTime?,
    // upd
    val for_ioid: UUID,
    val created_at: ZonedDateTime
) {
    fun toMsgRcpt() = Msg_rcpt(
        msg_id, rcpt_ioid, dup, hs_id, recv_at, rjct_at, read_at
    )
}

data class CreateMsgPacketResponse(
    val packet: ApiMsgPacket, val rcpt: Msg_rcpt
)

data class DeleteReceiptUpdate(
    val msg_id: UUID, val rcpt_ioid: UUID, val dup: Long, val created_at: ZonedDateTime
)

data class CreateMsgBackup(
    val msg_id: UUID,
    val vault_key_id: UUID,
    val payload_enc: String // base64 encoded UByteArray
)

data class ApiMsgBackup(
    val msg_id: UUID,
    val actor_id: UUID,
    val vault_key_id: UUID,
    val payload_enc: String, // base64 encoded UByteArray
    val created_at: ZonedDateTime,
    val deleted_at: ZonedDateTime?
)
