package com.voiceduel.data

import java.nio.ByteBuffer
import org.json.JSONObject

/**
 * The wire protocol, mirroring `backend/app/models/messages.py`.
 *
 * Text frames are JSON objects shaped `{"type": ..., "payload": {...}}`.
 * Binary frames carry one recording:
 *
 * ```
 * [4 bytes big-endian header length][UTF-8 JSON header][raw audio bytes]
 * ```
 */
object GameProtocol {

    // Client -> Server
    const val CREATE_ROOM = "create_room"
    const val JOIN_ROOM = "join_room"
    const val RATING_SUBMITTED = "rating_submitted"
    const val LEAVE_ROOM = "leave_room"
    const val PING = "ping"

    // Server -> Client
    const val CONNECTED = "connected"
    const val ROOM_CREATED = "room_created"
    const val PLAYERS_READY = "players_ready"
    const val ROUND_START = "round_start"
    const val AUDIO_READY = "audio_ready"
    const val ROUND_RESULT = "round_result"
    const val GAME_OVER = "game_over"
    const val OPPONENT_DISCONNECTED = "opponent_disconnected"
    const val ERROR = "error"
    const val PONG = "pong"

    // Error reasons
    const val ERR_INVALID_CODE = "invalid_code"
    const val ERR_ROOM_FULL = "room_full"
    const val ERR_NOT_YOUR_TURN = "not_your_turn"
    const val ERR_WRONG_PHASE = "wrong_phase"
    const val ERR_STALE_ROUND = "stale_round"
    const val ERR_INVALID_SCORE = "invalid_score"
    const val ERR_AUDIO_TOO_LARGE = "audio_too_large"

    const val DEFAULT_AUDIO_MIME = "audio/mp4"

    private const val HEADER_LENGTH_BYTES = 4
    private const val MAX_HEADER_BYTES = 4096

    /** Build a `{type, payload}` text frame. */
    fun encodeMessage(type: String, payload: JSONObject = JSONObject()): String =
        JSONObject().put("type", type).put("payload", payload).toString()

    /** Parse a text frame; returns `null` when the frame is not valid protocol. */
    fun decodeMessage(raw: String): ServerMessage? {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val type = root.optString("type").takeIf { it.isNotEmpty() } ?: return null
        return ServerMessage(type, root.optJSONObject("payload") ?: JSONObject())
    }

    /** Frame a recording for the wire. */
    fun encodeAudioFrame(roundNumber: Int, audio: ByteArray, mime: String = DEFAULT_AUDIO_MIME): ByteArray {
        val header = JSONObject()
            .put("round_number", roundNumber)
            .put("mime", mime)
            .toString()
            .toByteArray(Charsets.UTF_8)
        require(header.size <= MAX_HEADER_BYTES) { "audio header too large" }

        return ByteBuffer.allocate(HEADER_LENGTH_BYTES + header.size + audio.size)
            .putInt(header.size)
            .put(header)
            .put(audio)
            .array()
    }

    /** Split a received binary frame; returns `null` when it is malformed. */
    fun decodeAudioFrame(frame: ByteArray): AudioFrame? {
        if (frame.size < HEADER_LENGTH_BYTES) return null

        val headerLength = ByteBuffer.wrap(frame, 0, HEADER_LENGTH_BYTES).int
        if (headerLength <= 0 || headerLength > MAX_HEADER_BYTES) return null

        val headerEnd = HEADER_LENGTH_BYTES + headerLength
        if (frame.size < headerEnd) return null

        val header = runCatching {
            JSONObject(String(frame, HEADER_LENGTH_BYTES, headerLength, Charsets.UTF_8))
        }.getOrNull() ?: return null

        return AudioFrame(
            roundNumber = header.optInt("round_number", -1),
            mime = header.optString("mime").ifEmpty { DEFAULT_AUDIO_MIME },
            audio = frame.copyOfRange(headerEnd, frame.size),
        )
    }
}

/** A decoded text frame from the server. */
data class ServerMessage(val type: String, val payload: JSONObject)

/** A decoded recording from the server. */
data class AudioFrame(val roundNumber: Int, val mime: String, val audio: ByteArray) {

    // `audio` is a ByteArray, so the generated equals/hashCode would compare by
    // identity; these compare by content instead.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioFrame) return false
        return roundNumber == other.roundNumber &&
            mime == other.mime &&
            audio.contentEquals(other.audio)
    }

    override fun hashCode(): Int {
        var result = roundNumber
        result = 31 * result + mime.hashCode()
        result = 31 * result + audio.contentHashCode()
        return result
    }
}
