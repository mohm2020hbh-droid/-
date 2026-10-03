package com.carom.game.audio

import com.carom.core.audio.AudioCue
import com.carom.core.audio.TrackSpec

/** What plays the game's sound effects. The audio manager decides what and how loud; this only makes the noise. */
interface SoundOutput {
    /** Plays [cue] at [volume] (0..1, already mixed). */
    fun play(cue: AudioCue, volume: Float)

    /** Cuts [cue] short if it is playing (the fan's spin-up when the level is left). */
    fun stop(cue: AudioCue)

    /** The playback speed of every sound (1 normally). */
    fun setPitch(scale: Float)

    fun release()
}

/** What plays the music. Play, pause, resume, stop, loop and fades, apart from the effects. */
interface MusicController {
    /** Plays [track] as a loop, fading in over [fade] seconds (crossfading from the one that plays); nothing happens if it already plays. */
    fun play(track: TrackSpec, fade: Float = 1.5f)

    /** Fades the music out over [fade] seconds and forgets the track, so the next [play] starts it afresh. */
    fun stop(fade: Float = 0.6f)

    /** Holds the music where it is (the game went to the background). */
    fun pause()

    /** Carries on from where [pause] held it. */
    fun resume()

    /** The loudness of the music against its own level, 0..1 (master x music volume). */
    fun setGain(gain: Float)

    /** The music on or off; off fades what plays, on brings the wanted track back. */
    fun setEnabled(on: Boolean)

    fun setPitch(scale: Float)

    /** 0..1, 1 on a beat of the playing track and fading after it: a pulse for pictures. */
    fun beatPulse(): Float

    fun release()
}
