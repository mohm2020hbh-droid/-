package com.carom.game.audio

import android.os.SystemClock
import com.carom.core.audio.AudioCue
import com.carom.core.audio.AudioSettings
import com.carom.core.audio.CueGate
import com.carom.core.audio.TrackSpec

/**
 * The one door every sound of the game goes through. Screens ask for a cue ([play]) or the music ([playMusic]); the manager works out the
 * gain from the player's settings ([AudioSettings]: master x bus volume x the cue's own level, nothing at all when the bus is switched off),
 * lets the [CueGate] drop a pile-up of the same sound, and hands the result to the output. Changing a setting reaches what is playing at once
 * ([apply]).
 *
 * Nothing else in the game plays a sound, so the balance, the switches and the volumes live in exactly one place.
 */
class AudioManager(
    private val settings: AudioSettings,
    private val sfx: SoundOutput,
    private val music: MusicController,
    private val clock: () -> Double = { SystemClock.elapsedRealtime() / 1000.0 },
) {
    private val gate = CueGate()

    init {
        settings.onChange = { apply() } // a slider or a switch reaches the music at once
        apply()
    }

    /** Plays [cue]. [strength] (0..1) is how hard: a collision's loudness, a clock's depth. */
    fun play(cue: AudioCue, strength: Double = 1.0) {
        val gain = settings.gain(cue)
        if (gain <= 0.0) return
        if (!gate.allow(cue, clock())) return
        val s = strength.coerceIn(0.0, 1.0)
        val shaped = when (cue) {
            AudioCue.BOUNCE -> 0.7 + 0.3 * s
            AudioCue.CLOCK -> 0.5 + 0.5 * s
            else -> 1.0
        }
        sfx.play(cue, (gain * shaped).toFloat().coerceIn(0f, 1f))
    }

    fun stop(cue: AudioCue) = sfx.stop(cue)

    // ---------------------------------------------------------------- music

    /** The track for the level being shown; crossfades from the one playing, and does nothing if it is the same. */
    fun playMusic(track: TrackSpec, fade: Float = 1.5f) = music.play(track, fade)

    fun stopMusic(fade: Float = 0.6f) = music.stop(fade)

    fun pauseMusic() = music.pause()

    fun resumeMusic() = music.resume()

    /** The settings changed: the music follows (its volume and its switch). The effects read the settings on every cue. */
    fun apply() {
        music.setGain(settings.musicGain().toFloat())
        music.setEnabled(settings.musicEnabled)
    }

    fun setPitch(scale: Float) {
        sfx.setPitch(scale)
        music.setPitch(scale)
    }

    /** 0..1, a pulse on the music's beat for pictures; nothing when the music is off. */
    val beatPulse: Float get() = if (settings.musicGain() > 0.0) music.beatPulse() else 0f

    fun release() {
        sfx.release()
        music.release()
    }
}
