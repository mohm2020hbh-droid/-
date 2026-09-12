package com.voiceduel.data

import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * The WebSocket transport.
 *
 * OkHttp delivers callbacks on its own threads, so events are published through
 * a buffered [MutableSharedFlow] that the ViewModel collects on the main
 * dispatcher.
 */
class GameSocket(
    private val client: OkHttpClient = defaultClient(),
) {

    private val _events = MutableSharedFlow<GameEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    private var webSocket: WebSocket? = null

    /** True once [connect] has opened a socket that has not been closed yet. */
    val isConnected: Boolean
        get() = webSocket != null

    fun connect(url: String) {
        disconnect()
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, Listener())
    }

    fun sendMessage(type: String, payload: JSONObject = JSONObject()): Boolean {
        val socket = webSocket ?: return false
        return socket.send(GameProtocol.encodeMessage(type, payload))
    }

    fun sendAudio(roundNumber: Int, audio: ByteArray, mime: String = GameProtocol.DEFAULT_AUDIO_MIME): Boolean {
        val socket = webSocket ?: return false
        return socket.send(GameProtocol.encodeAudioFrame(roundNumber, audio, mime).toByteString())
    }

    fun disconnect() {
        webSocket?.close(NORMAL_CLOSURE, null)
        webSocket = null
    }

    private inner class Listener : WebSocketListener() {

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = GameProtocol.decodeMessage(text)
            if (message == null) {
                Log.w(TAG, "dropping unparsable frame")
                return
            }
            GameEventMapper.fromMessage(message)?.let { _events.tryEmit(it) }
                ?: Log.w(TAG, "dropping unknown message type: ${message.type}")
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            val frame = GameProtocol.decodeAudioFrame(bytes.toByteArray())
            if (frame == null) {
                Log.w(TAG, "dropping malformed audio frame")
                return
            }
            _events.tryEmit(GameEvent.AudioReceived(frame))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "socket failure", t)
            this@GameSocket.webSocket = null
            _events.tryEmit(GameEvent.ConnectionLost(t.message))
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(NORMAL_CLOSURE, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (this@GameSocket.webSocket != null) {
                this@GameSocket.webSocket = null
                _events.tryEmit(GameEvent.ConnectionLost(reason.ifEmpty { null }))
            }
        }
    }

    companion object {
        private const val TAG = "GameSocket"
        private const val NORMAL_CLOSURE = 1000

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            // A duel is mostly silence between rounds; ping frames keep the
            // connection alive through NATs and mobile carriers.
            .pingInterval(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
}
