package app.opia.common.api.endpoint

import app.opia.common.api.PlainApiSuccess
import app.opia.common.api.model.*
import app.opia.common.db.Installation_ownership
import app.opia.common.db.Msg_rcpt
import retrofit2.http.*
import java.util.*

interface MessagingApi {
    @GET("io/{ioid}/identity")
    suspend fun getIdentity(@Path("ioid") ioid: UUID): PlainApiSuccess<ApiKeyPair>

    @GET("actor/{actor_id}/io")
    suspend fun listIOs(@Path("actor_id") actorId: UUID): PlainApiSuccess<ListIOsRes>

    // only returns initiator keys
    @GET("hs/{hs_id}")
    suspend fun getHandshake(@Path("hs_id") hsID: UUID): PlainApiSuccess<ApiHandshake>

    @POST("hs")
    suspend fun createHandshake(@Body params: CreateHandshakeParams): PlainApiSuccess<ApiHandshake>

    @GET("message")
    suspend fun list(): PlainApiSuccess<ListMsgsRes>

    @POST("message")
    suspend fun create(@Body params: CreateMsgParams): PlainApiSuccess<CreateMsgRes>

    @POST("message/{message_id}/packet")
    suspend fun createMsgPacket(
        @Path("message_id") messageID: UUID, @Body params: ApiMsgPacket
    ): PlainApiSuccess<CreateMsgPacketResponse>

    @GET("message/receipt")
    suspend fun listUpdatedReceipts(): PlainApiSuccess<List<ApiUpdatedReceipt>>

    @GET("message/{message_id}/receipt")
    suspend fun listMsgReceipts(@Path("message_id") messageID: UUID): PlainApiSuccess<List<Msg_rcpt>>

    @POST("message/receipt")
    suspend fun createMsgReceipt(@Body params: Msg_rcpt): PlainApiSuccess<Msg_rcpt>

    // IllegalArgumentException: Non-body HTTP method cannot contain @Body
    //@DELETE("message/receipt/update")
    @HTTP(method = "DELETE", path = "message/receipt/update", hasBody = true)
    suspend fun deleteReceiptUpdate(@Body params: DeleteReceiptUpdate): PlainApiSuccess<GenericDeleteResponse>

    @GET("message/outstanding")
    suspend fun listOutstandingMsgs(): PlainApiSuccess<List<Msg_rcpt>>

    @GET("message/backup/outstanding")
    suspend fun listOutstandingMsgBackups(): PlainApiSuccess<List<UUID>>

    @POST("message/backup")
    suspend fun createMsgBackup(@Body params: CreateMsgBackup): PlainApiSuccess<ApiMsgBackup>
}
