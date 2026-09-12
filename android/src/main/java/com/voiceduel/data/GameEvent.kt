package com.voiceduel.data

import org.json.JSONObject

/** Everything the server can tell this client, already parsed. */
sealed interface GameEvent {

    data class Connected(val playerId: String) : GameEvent

    data class RoomCreated(val code: String) : GameEvent

    data class PlayersReady(val playerAId: String, val playerBId: String) : GameEvent

    data class RoundStart(
        val roundNumber: Int,
        val totalRounds: Int,
        val soundId: String,
        val soundName: String,
        val soundEmoji: String,
        val performerId: String,
        val countdownSeconds: Int,
    ) : GameEvent

    data class AudioReady(val roundNumber: Int) : GameEvent

    data class AudioReceived(val frame: AudioFrame) : GameEvent

    data class RoundResult(
        val roundNumber: Int,
        val performerId: String,
        val score: Int,
        val totalScores: Map<String, Int>,
        val timedOut: Boolean,
    ) : GameEvent

    data class GameOver(val winnerId: String?, val finalScores: Map<String, Int>) : GameEvent

    data class OpponentLeft(val reason: String) : GameEvent

    data class Failed(val reason: String) : GameEvent

    /** The transport itself dropped — distinct from a protocol-level error. */
    data class ConnectionLost(val cause: String?) : GameEvent

    data object Pong : GameEvent
}

/** Translates raw protocol frames into [GameEvent]s. */
object GameEventMapper {

    fun fromMessage(message: ServerMessage): GameEvent? {
        val payload = message.payload
        return when (message.type) {
            GameProtocol.CONNECTED -> GameEvent.Connected(payload.optString("player_id"))

            GameProtocol.ROOM_CREATED -> GameEvent.RoomCreated(payload.optString("code"))

            GameProtocol.PLAYERS_READY -> GameEvent.PlayersReady(
                playerAId = payload.optString("player_a_id"),
                playerBId = payload.optString("player_b_id"),
            )

            GameProtocol.ROUND_START -> GameEvent.RoundStart(
                roundNumber = payload.optInt("round_number"),
                totalRounds = payload.optInt("total_rounds"),
                soundId = payload.optString("sound_id"),
                soundName = payload.optString("sound_name"),
                soundEmoji = payload.optString("sound_emoji"),
                performerId = payload.optString("performer_id"),
                countdownSeconds = payload.optInt("countdown_seconds"),
            )

            GameProtocol.AUDIO_READY -> GameEvent.AudioReady(payload.optInt("round_number"))

            GameProtocol.ROUND_RESULT -> GameEvent.RoundResult(
                roundNumber = payload.optInt("round_number"),
                performerId = payload.optString("performer_id"),
                score = payload.optInt("score"),
                totalScores = payload.optJSONObject("total_scores").toIntMap(),
                timedOut = payload.optBoolean("timed_out", false),
            )

            GameProtocol.GAME_OVER -> GameEvent.GameOver(
                // A draw is reported as a JSON null.
                winnerId = if (payload.isNull("winner_id")) null else payload.optString("winner_id"),
                finalScores = payload.optJSONObject("final_scores").toIntMap(),
            )

            GameProtocol.OPPONENT_DISCONNECTED ->
                GameEvent.OpponentLeft(payload.optString("reason", "disconnected"))

            GameProtocol.ERROR -> GameEvent.Failed(payload.optString("reason"))

            GameProtocol.PONG -> GameEvent.Pong

            else -> null
        }
    }

    private fun JSONObject?.toIntMap(): Map<String, Int> {
        if (this == null) return emptyMap()
        return keys().asSequence().associateWith { optInt(it) }
    }
}
