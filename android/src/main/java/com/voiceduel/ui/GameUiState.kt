package com.voiceduel.ui

/** Which screen the match is currently on. */
enum class GamePhase {
    HOME,
    WAITING,
    PLAYING,
    RATING,
    ROUND_RESULT,
    GAME_OVER,
}

/** Transport status, shown as a banner so the player is never left guessing. */
enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    LOST,
}

/** The round the server announced. */
data class RoundInfo(
    val number: Int,
    val totalRounds: Int,
    val soundName: String,
    val soundEmoji: String,
    val performerId: String,
    val countdownSeconds: Int,
)

/** The outcome of the round just played. */
data class RoundOutcome(
    val number: Int,
    val performerId: String,
    val score: Int,
    val timedOut: Boolean,
)

/** The end of the match. */
data class FinalOutcome(
    val winnerId: String?,
    val scores: Map<String, Int>,
)

/**
 * Everything the five screens render, in one immutable snapshot.
 *
 * Received audio is deliberately *not* held here: byte arrays make state
 * comparison meaningless, so the ViewModel keeps the recording privately and
 * exposes only [hasRecordingToRate].
 */
data class GameUiState(
    val serverUrl: String = "",
    val connection: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val phase: GamePhase = GamePhase.HOME,
    val playerId: String? = null,
    val opponentId: String? = null,
    val roomCode: String? = null,
    val joinCodeInput: String = "",
    val round: RoundInfo? = null,
    val secondsRemaining: Int = 0,
    val isRecording: Boolean = false,
    val isSendingRecording: Boolean = false,
    val hasRecordingToRate: Boolean = false,
    val isPlayingRecording: Boolean = false,
    val pendingScore: Int = 50,
    val isSubmittingRating: Boolean = false,
    val lastOutcome: RoundOutcome? = null,
    val scores: Map<String, Int> = emptyMap(),
    val finalOutcome: FinalOutcome? = null,
    val microphoneDenied: Boolean = false,
    val notice: String? = null,
) {

    /** True when this device is the one imitating the sound this round. */
    val isPerformer: Boolean
        get() = round != null && playerId != null && round.performerId == playerId

    val myScore: Int
        get() = playerId?.let { scores[it] } ?: 0

    val opponentScore: Int
        get() = opponentId?.let { scores[it] } ?: 0

    val isBusy: Boolean
        get() = connection == ConnectionStatus.CONNECTING
}
