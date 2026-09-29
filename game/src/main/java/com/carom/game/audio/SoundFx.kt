package com.carom.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Plays the game's sound effects. Each sound is synthesised once (on a background thread, so
 * start-up never waits for it) into static [AudioTrack]s; sounds that can overlap get a few
 * tracks in rotation. The rolling sound is a seamless loop whose volume follows the ball.
 *
 * Sound is decoration: until the sounds are ready, or if the device refuses audio tracks, the game
 * simply stays silent.
 */
class SoundFx {

    private class Bank(val voices: List<Voice>) {
        private var next = 0

        fun play(volume: Float, rate: Float) {
            if (voices.isEmpty()) return
            voices[next].play(volume, rate)
            next = (next + 1) % voices.size
        }

        fun release() = voices.forEach { it.release() }
    }

    @Volatile private var impact: Bank? = null
    @Volatile private var launch: Bank? = null
    @Volatile private var shatter: Bank? = null
    @Volatile private var win: Bank? = null
    @Volatile private var tap: Bank? = null
    @Volatile private var roll: Voice? = null
    @Volatile private var released = false
    private var rolling = false

    init {
        Thread({
            val banks = listOf(
                Synth.impact() to 4, Synth.launch() to 2, Synth.shatter() to 1, Synth.win() to 1, Synth.tap() to 2,
            ).map { (pcm, count) -> Bank(List(count) { Voice.create(pcm, loop = false) }.filterNotNull()) }
            val rollVoice = Voice.create(Synth.roll(), loop = true)
            synchronized(this) {
                if (released) {
                    banks.forEach { it.release() }
                    rollVoice?.release()
                } else {
                    impact = banks[0]
                    launch = banks[1]
                    shatter = banks[2]
                    win = banks[3]
                    tap = banks[4]
                    roll = rollVoice
                }
            }
        }, "carom-sounds").start()
    }

    /**
     * A bounce. [strength] (0..1, how hard the ball hit) sets the volume; [step] is which bounce of
     * the shot this is, and picks the note, so successive bounces climb a pentatonic scale.
     */
    fun impact(strength: Double, step: Int) {
        val s = strength.coerceIn(0.0, 1.0).toFloat()
        val note = Synth.PENTATONIC[Math.floorMod(step, Synth.PENTATONIC.size)]
        impact?.play(volume = 0.35f + 0.6f * s, rate = note)
    }

    /** The throw; louder for a stronger one. */
    fun launch(power: Double) {
        launch?.play(volume = 0.3f + 0.5f * power.coerceIn(0.0, 1.0).toFloat(), rate = 0.92f + 0.16f * power.toFloat())
    }

    fun shatter() = shatter?.play(volume = 0.85f, rate = 1f)

    fun win() = win?.play(volume = 0.8f, rate = 1f)

    fun tap() = tap?.play(volume = 0.35f, rate = 1f)

    /** The rolling sound at [level] (0..1, from the ball's speed); 0 silences it. */
    fun roll(level: Float) {
        val voice = roll ?: return
        val volume = 0.3f * level.coerceIn(0f, 1f)
        if (volume > 0.002f) {
            voice.setVolume(volume)
            if (!rolling) {
                voice.resume()
                rolling = true
            }
        } else if (rolling) {
            voice.pause()
            rolling = false
        }
    }

    fun release() {
        synchronized(this) {
            released = true
            listOfNotNull(impact, launch, shatter, win, tap).forEach { it.release() }
            roll?.release()
        }
    }

    private class Voice(private val track: AudioTrack) {
        fun play(volume: Float, rate: Float) {
            try {
                track.stop()
                track.reloadStaticData()
                track.playbackRate = (Synth.SAMPLE_RATE * rate).toInt()
                track.setVolume(volume)
                track.play()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Could not play a sound", e)
            }
        }

        fun setVolume(volume: Float) {
            track.setVolume(volume)
        }

        fun resume() {
            try {
                track.play()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Could not play a sound", e)
            }
        }

        fun pause() {
            try {
                track.pause()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Could not pause a sound", e)
            }
        }

        fun release() = track.release()

        companion object {
            fun create(pcm: ShortArray, loop: Boolean): Voice? = try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(Synth.SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                if (loop) track.setLoopPoints(0, pcm.size, -1)
                if (track.state == AudioTrack.STATE_INITIALIZED) Voice(track) else null.also { track.release() }
            } catch (e: Exception) {
                Log.w(TAG, "Audio unavailable", e)
                null
            }
        }
    }

    private companion object {
        const val TAG = "Carom"
    }
}
