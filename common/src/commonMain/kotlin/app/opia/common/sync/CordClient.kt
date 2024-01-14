package app.opia.common.sync

import app.opia.common.api.RetrofitClient
import app.opia.common.api.UUIDAdapter
import app.opia.common.api.ZonedDateTimeAdapter
import app.opia.common.api.model.Authorization
import app.opia.common.di.AuthStatus
import app.opia.common.di.ServiceLocator
import co.touchlab.kermit.Logger
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.Objects.isNull

// Cord is self-contained, it observes the db & network and passes messages in-between
// TODO does auto-recon except when auth-reject then silently "wait" to be killed
class CordClient {
    // has a connection to the server
    // one channel returns all events, broadcastChan, including conn/disconn - maybe UI wants that
    // connection re-estab is done automatically, channel stays open

    // TODO use PolymorphicAdapter
    @JsonClass(generateAdapter = true)
    data class CordPacket(
        val action: Char, val type: String, val data: Any
    )

    sealed class CordEvent {
        data class RemoteEvent(val payload: String) : CordEvent()
        data class LocalEvent(val i: Int) : CordEvent()
    }

    enum class ConnectionEvent {
        CONNECT, DISCONNECT
    }

    private var wsConn: WebSocket? = null

    // TODO public broadcast channel, UI can determine network state :)
    private var connectionState = Channel<ConnectionEvent>()

    suspend fun loopCord() = coroutineScope {
        Logger.i(TAG) { "entering loop" }

        var mainEventLoop = Channel<CordEvent> { }

        // reconnect loop
        launch { connectCord(mainEventLoop) }
        launch {
            for (event in connectionState) {
                when (event) {
                    ConnectionEvent.CONNECT -> {
                        Logger.i(TAG) { "availability: connected" }
                    }

                    ConnectionEvent.DISCONNECT -> {
                        Logger.w(TAG) { "availability: disconnect, reconnecting soon..." }
                        delay(2500)
                        // TODO do exp backoff
                        connectCord(mainEventLoop)
                    }
                }
            }
        }

        // db observers
        // TODO init tx chan, etc.

        // main event loop
        // single-threaded: concurrent handshake map access, single "receipt" state, etc.
        for (ev in mainEventLoop) {
            when (ev) {
                is CordEvent.RemoteEvent -> {
                    parseMessage(ev.payload)
                }

                is CordEvent.LocalEvent -> {

                }
            }
        }
    }

    private suspend fun parseMessage(msg: String) {
        // TODO polymorphic, make data generic
        val moshi = Moshi.Builder().add(ZonedDateTimeAdapter).add(UUIDAdapter).build()
        val packetAdapter = moshi.adapter(CordPacket::class.java)
        val packet = packetAdapter.fromJson(msg)
        if (packet == null) {
            Logger.e(TAG) { "parse: unknown failure" }
            return
        }

        when (packet.type) {
            "notify" -> {
                Logger.i(TAG) { "parse: received 'notify', querying rcpt & msg..." }
                // TODO emit local event
                if (pullRcpt()) Logger.i(TAG) { "parse: successfully pulled rcpt" }
                else Logger.e(TAG) { "parse: pull-rcpt failed" }

                if (pullMsg()) Logger.i(TAG) { "parse: successfully pulled msg" }
                else Logger.e(TAG) { "parse: pull-msg failed" }
            }

            "msg-packet" -> {
                /*val msgPacketAdapter = moshi.adapter(Array<ApiRecvMsgPacket>::class.java)
                val msgPacket = msgPacketAdapter.fromJson(packet.data)
                if (msgPacket == null) {
                    Logger.e(TAG) { "parse: msg-packet bad schema" }
                    return
                }
                Logger.i(TAG) { "parse: received msg-packet=$msgPacket" }*/
            }

            else -> {
                Logger.w(TAG) { "parse: unsupported type, ignoring (compatibility mode)" }
                return
            }
        }
    }

    private fun connectCord(mainEventLoop: SendChannel<CordEvent>) {
        Logger.i(TAG) { "connecting..." }

        var accessToken: String? = null
        // treat no-auth here same as: "connection closed (failure) - ... '401 Unauthorized'"
        runBlocking {
            val auth = ServiceLocator.getAuth()
            if (auth is AuthStatus.Authenticated) {
                accessToken = auth.accessToken
            }
        }
        if (isNull(accessToken)) {
            connectionState.trySendBlocking(ConnectionEvent.DISCONNECT)
            return
        }

        val request = Request.Builder().url(RetrofitClient.mode.config.host + "cord")
            .header(Authorization, "Bearer $accessToken").build()
        wsConn = OkHttpClient().newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                Logger.d(TAG) { "recv: text=$text" }
                // TODO disconnect when failed
                mainEventLoop.trySend(CordEvent.RemoteEvent(text))
            }

            // TODO consider
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Logger.w(TAG) { "received bytes, ignoring - bytes=$bytes" }
            }

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Logger.i(TAG) { "connection established" }
                connectionState.trySendBlocking(ConnectionEvent.CONNECT)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Logger.w(TAG) { "connection closed - code=$code, reason=$reason" }
                connectionState.trySendBlocking(ConnectionEvent.DISCONNECT)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Logger.e(TAG) { "connection closed (failure) - err=$t, resp=$response" }
                connectionState.trySendBlocking(ConnectionEvent.DISCONNECT)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Logger.w(TAG) { "connection closing - code=$code, reason=$reason" }
            }
        })
    }

    companion object {
        private const val TAG = "CordClient"
    }
}
