package com.voiceduel.data

import kotlinx.coroutines.flow.SharedFlow
import org.json.JSONObject

/**
 * The single place the UI talks to the server through.
 *
 * It owns the socket and exposes one intention-revealing call per protocol
 * message, so no screen or ViewModel has to know the wire format.
 */
class GameRepository(
    private val socket: GameSocket = GameSocket(),
) {

    val events: SharedFlow<GameEvent> = socket.events

    val isConnected: Boolean
        get() = socket.isConnected

    fun connect(serverUrl: String) = socket.connect(serverUrl)

    fun createRoom(): Boolean = socket.sendMessage(GameProtocol.CREATE_ROOM)

    fun joinRoom(code: String): Boolean =
        socket.sendMessage(GameProtocol.JOIN_ROOM, JSONObject().put("code", code))

    fun sendRecording(roundNumber: Int, audio: ByteArray, mime: String = GameProtocol.DEFAULT_AUDIO_MIME): Boolean =
        socket.sendAudio(roundNumber, audio, mime)

    fun submitRating(roundNumber: Int, score: Int): Boolean =
        socket.sendMessage(
            GameProtocol.RATING_SUBMITTED,
            JSONObject().put("round_number", roundNumber).put("score", score),
        )

    fun leaveRoom(): Boolean = socket.sendMessage(GameProtocol.LEAVE_ROOM)

    fun disconnect() = socket.disconnect()
}
