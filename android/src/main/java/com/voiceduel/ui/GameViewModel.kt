package com.voiceduel.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voiceduel.audio.AudioPlayer
import com.voiceduel.audio.AudioRecorder
import com.voiceduel.data.GameEvent
import com.voiceduel.data.GameProtocol
import com.voiceduel.data.GameRepository
import com.voiceduel.data.ServerConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the whole match state and is the only component that talks to the
 * repository, the recorder, and the player.
 *
 * The server drives the flow: every screen change is a reaction to an event,
 * never a decision made locally.
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GameRepository()
    private val serverConfig = ServerConfig(application)
    private val recorder = AudioRecorder(application)
    private val player = AudioPlayer(application)

    private val _state = MutableStateFlow(GameUiState(serverUrl = serverConfig.serverUrl))
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    /** The recording waiting to be rated; kept out of the UI state on purpose. */
    private var incomingRecording: ByteArray? = null

    /** The next round, buffered so the result screen stays up long enough to read. */
    private var pendingRound: GameEvent.RoundStart? = null
    private var pendingGameOver: GameEvent.GameOver? = null

    private var countdownJob: Job? = null
    private var resultDwellJob: Job? = null

    init {
        viewModelScope.launch {
            repository.events.collect(::onEvent)
        }
    }

    // ------------------------------------------------------------------
    // Player intents
    // ------------------------------------------------------------------
    fun onServerUrlChange(url: String) {
        _state.update { it.copy(serverUrl = url, notice = null) }
    }

    fun onJoinCodeChange(code: String) {
        // The server only ever issues 4-digit codes.
        val digits = code.filter(Char::isDigit).take(ROOM_CODE_LENGTH)
        _state.update { it.copy(joinCodeInput = digits, notice = null) }
    }

    fun onCreateRoom() = withConnection { repository.createRoom() }

    fun onJoinRoom() {
        val code = _state.value.joinCodeInput
        if (code.length != ROOM_CODE_LENGTH) {
            showNotice("أدخل كودًا مكوّنًا من $ROOM_CODE_LENGTH أرقام")
            return
        }
        withConnection { repository.joinRoom(code) }
    }

    /**
     * Called by the play screen once the microphone permission is settled.
     *
     * A denied microphone is not fatal (C5): the round simply goes unrecorded
     * and the server closes it automatically.
     */
    fun onMicrophonePermission(granted: Boolean) {
        if (!granted) {
            _state.update { it.copy(microphoneDenied = true) }
            showNotice("لا يمكن التسجيل بدون إذن الميكروفون")
            return
        }
        _state.update { it.copy(microphoneDenied = false) }
        startPerformance()
    }

    /** The performer chose to send before the countdown ran out. */
    fun onSendRecordingNow() {
        if (!_state.value.isRecording) return
        countdownJob?.cancel()
        finishPerformance()
    }

    fun onScoreChange(score: Int) {
        _state.update { it.copy(pendingScore = score.coerceIn(MIN_SCORE, MAX_SCORE)) }
    }

    fun onReplayRecording() {
        val audio = incomingRecording ?: return
        playIncoming(audio)
    }

    fun onSubmitRating() {
        val current = _state.value
        val round = current.round ?: return
        if (current.isSubmittingRating) return

        _state.update { it.copy(isSubmittingRating = true) }
        if (!repository.submitRating(round.number, current.pendingScore)) {
            _state.update { it.copy(isSubmittingRating = false) }
            showNotice("تعذّر إرسال التقييم، تحقق من الاتصال")
        }
    }

    /** Back to the main menu, telling the server so the opponent is not left hanging. */
    fun onLeaveMatch() {
        if (repository.isConnected && _state.value.roomCode != null) {
            repository.leaveRoom()
        }
        resetToHome(notice = null)
    }

    fun onDismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    // ------------------------------------------------------------------
    // Server events
    // ------------------------------------------------------------------
    private fun onEvent(event: GameEvent) {
        when (event) {
            is GameEvent.Connected ->
                _state.update {
                    it.copy(playerId = event.playerId, connection = ConnectionStatus.CONNECTED)
                }

            is GameEvent.RoomCreated ->
                _state.update {
                    it.copy(roomCode = event.code, phase = GamePhase.WAITING, notice = null)
                }

            is GameEvent.PlayersReady -> {
                val me = _state.value.playerId
                val opponent = listOf(event.playerAId, event.playerBId).firstOrNull { it != me }
                _state.update {
                    it.copy(
                        opponentId = opponent,
                        scores = mapOf(event.playerAId to 0, event.playerBId to 0),
                        notice = null,
                    )
                }
            }

            is GameEvent.RoundStart -> onRoundStart(event)

            is GameEvent.AudioReady ->
                _state.update { it.copy(phase = GamePhase.RATING, hasRecordingToRate = false) }

            is GameEvent.AudioReceived -> onAudioReceived(event)

            is GameEvent.RoundResult -> onRoundResult(event)

            is GameEvent.GameOver -> {
                pendingGameOver = event
                if (_state.value.phase != GamePhase.ROUND_RESULT) applyPendingTransition()
            }

            is GameEvent.OpponentLeft -> {
                stopEverything()
                resetToHome(notice = "انسحب الخصم من المباراة")
            }

            is GameEvent.Failed -> onServerError(event.reason)

            is GameEvent.ConnectionLost -> {
                stopEverything()
                _state.update {
                    it.copy(
                        phase = GamePhase.HOME,
                        connection = ConnectionStatus.LOST,
                        roomCode = null,
                        round = null,
                        notice = "انقطع الاتصال بالخادم",
                    )
                }
            }

            GameEvent.Pong -> Unit
        }
    }

    private fun onRoundStart(event: GameEvent.RoundStart) {
        pendingRound = event
        // While a result is on screen, hold the next round back so the player
        // has time to read it.
        if (_state.value.phase != GamePhase.ROUND_RESULT) applyPendingTransition()
    }

    private fun onAudioReceived(event: GameEvent.AudioReceived) {
        val round = _state.value.round
        if (round != null && event.frame.roundNumber != round.number) {
            Log.w(TAG, "ignoring audio for round ${event.frame.roundNumber}")
            return
        }
        incomingRecording = event.frame.audio
        _state.update {
            it.copy(
                phase = GamePhase.RATING,
                hasRecordingToRate = true,
                pendingScore = DEFAULT_SCORE,
                isSubmittingRating = false,
            )
        }
        playIncoming(event.frame.audio)
    }

    private fun onRoundResult(event: GameEvent.RoundResult) {
        stopEverything()
        incomingRecording = null
        _state.update {
            it.copy(
                phase = GamePhase.ROUND_RESULT,
                scores = event.totalScores,
                lastOutcome = RoundOutcome(
                    number = event.roundNumber,
                    performerId = event.performerId,
                    score = event.score,
                    timedOut = event.timedOut,
                ),
                isRecording = false,
                isSendingRecording = false,
                isSubmittingRating = false,
                hasRecordingToRate = false,
                secondsRemaining = 0,
            )
        }

        resultDwellJob?.cancel()
        resultDwellJob = viewModelScope.launch {
            delay(RESULT_DWELL_MILLIS)
            applyPendingTransition()
        }
    }

    /** Moves on from the result screen to whatever the server queued next. */
    private fun applyPendingTransition() {
        resultDwellJob?.cancel()
        resultDwellJob = null

        pendingGameOver?.let { over ->
            pendingGameOver = null
            pendingRound = null
            _state.update {
                it.copy(
                    phase = GamePhase.GAME_OVER,
                    finalOutcome = FinalOutcome(over.winnerId, over.finalScores),
                    scores = over.finalScores,
                    round = null,
                )
            }
            return
        }

        pendingRound?.let { round ->
            pendingRound = null
            _state.update {
                it.copy(
                    phase = GamePhase.PLAYING,
                    round = RoundInfo(
                        number = round.roundNumber,
                        totalRounds = round.totalRounds,
                        soundName = round.soundName,
                        soundEmoji = round.soundEmoji,
                        performerId = round.performerId,
                        countdownSeconds = round.countdownSeconds,
                    ),
                    secondsRemaining = round.countdownSeconds,
                    isRecording = false,
                    isSendingRecording = false,
                    hasRecordingToRate = false,
                    lastOutcome = null,
                    notice = null,
                )
            }
        }
    }

    private fun onServerError(reason: String) {
        val message = when (reason) {
            GameProtocol.ERR_INVALID_CODE -> "الكود غير صحيح، تأكد منه وحاول مجددًا"
            GameProtocol.ERR_ROOM_FULL -> "الغرفة ممتلئة"
            GameProtocol.ERR_AUDIO_TOO_LARGE -> "التسجيل طويل جدًا"
            GameProtocol.ERR_NOT_YOUR_TURN -> "ليس دورك في هذه الجولة"
            GameProtocol.ERR_STALE_ROUND, GameProtocol.ERR_WRONG_PHASE ->
                "انتهت هذه الجولة بالفعل"
            GameProtocol.ERR_INVALID_SCORE -> "قيمة التقييم غير صالحة"
            else -> "تعذّر تنفيذ الطلب"
        }
        _state.update {
            it.copy(notice = message, isSubmittingRating = false, isSendingRecording = false)
        }
    }

    // ------------------------------------------------------------------
    // Recording
    // ------------------------------------------------------------------
    private fun startPerformance() {
        val current = _state.value
        val round = current.round ?: return
        if (!current.isPerformer || current.isRecording) return

        if (!recorder.start()) {
            showNotice("تعذّر فتح الميكروفون")
            return
        }

        _state.update { it.copy(isRecording = true, secondsRemaining = round.countdownSeconds) }

        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = round.countdownSeconds
            while (remaining > 0) {
                delay(1_000)
                remaining -= 1
                _state.update { it.copy(secondsRemaining = remaining) }
            }
            finishPerformance()
        }
    }

    private fun finishPerformance() {
        val round = _state.value.round ?: return
        if (!_state.value.isRecording) return

        _state.update { it.copy(isRecording = false, isSendingRecording = true) }

        val audio = recorder.stop()
        if (audio == null) {
            _state.update { it.copy(isSendingRecording = false) }
            showNotice("لم يُسجَّل أي صوت في هذه الجولة")
            return
        }

        if (!repository.sendRecording(round.number, audio, GameProtocol.DEFAULT_AUDIO_MIME)) {
            _state.update { it.copy(isSendingRecording = false) }
            showNotice("تعذّر إرسال التسجيل، تحقق من الاتصال")
        }
    }

    private fun playIncoming(audio: ByteArray) {
        _state.update { it.copy(isPlayingRecording = true) }
        val started = player.play(audio) {
            _state.update { it.copy(isPlayingRecording = false) }
        }
        if (!started) {
            _state.update { it.copy(isPlayingRecording = false) }
            showNotice("تعذّر تشغيل التسجيل")
        }
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------
    private fun withConnection(action: () -> Boolean) {
        val url = _state.value.serverUrl.trim()
        if (!ServerConfig.isValidUrl(url)) {
            showNotice("عنوان الخادم غير صالح، مثال: ws://192.168.1.5:8000/ws")
            return
        }

        if (!repository.isConnected) {
            serverConfig.serverUrl = url
            _state.update { it.copy(connection = ConnectionStatus.CONNECTING, notice = null) }
            repository.connect(url)
            // The command is replayed once the server answers with `connected`.
            viewModelScope.launch {
                if (awaitConnection() && !action()) {
                    showNotice("تعذّر الاتصال بالخادم")
                }
            }
            return
        }

        if (!action()) showNotice("تعذّر الاتصال بالخادم")
    }

    private suspend fun awaitConnection(): Boolean {
        var waited = 0L
        while (waited < CONNECT_TIMEOUT_MILLIS) {
            if (_state.value.connection == ConnectionStatus.CONNECTED) return true
            if (_state.value.connection == ConnectionStatus.LOST) return false
            delay(CONNECT_POLL_MILLIS)
            waited += CONNECT_POLL_MILLIS
        }
        _state.update {
            it.copy(connection = ConnectionStatus.DISCONNECTED, notice = "انتهت مهلة الاتصال بالخادم")
        }
        return false
    }

    private fun showNotice(message: String) {
        _state.update { it.copy(notice = message) }
    }

    private fun stopEverything() {
        countdownJob?.cancel()
        countdownJob = null
        resultDwellJob?.cancel()
        resultDwellJob = null
        recorder.cancel()
        player.stop()
    }

    private fun resetToHome(notice: String?) {
        pendingRound = null
        pendingGameOver = null
        incomingRecording = null
        _state.update {
            GameUiState(
                serverUrl = it.serverUrl,
                connection = if (repository.isConnected) {
                    ConnectionStatus.CONNECTED
                } else {
                    ConnectionStatus.DISCONNECTED
                },
                playerId = it.playerId,
                notice = notice,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopEverything()
        repository.disconnect()
    }

    companion object {
        private const val TAG = "GameViewModel"
        const val ROOM_CODE_LENGTH = 4
        const val MIN_SCORE = 0
        const val MAX_SCORE = 100
        private const val DEFAULT_SCORE = 50
        private const val RESULT_DWELL_MILLIS = 3_000L
        private const val CONNECT_TIMEOUT_MILLIS = 10_000L
        private const val CONNECT_POLL_MILLIS = 50L
    }
}
