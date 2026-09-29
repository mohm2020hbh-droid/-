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

        fun stop() = voices.forEach { it.stop() }

        fun release() = voices.forEach { it.release() }
    }

    @Volatile private var banks: Map<String, Bank> = emptyMap()
    @Volatile private var roll: Voice? = null
    @Volatile private var released = false
    private var rolling = false
    private var impactCount = 0

    init {
        Thread({
            val made = linkedMapOf(
                "impact" to (Synth.impact() to 4),
                "launch" to (Synth.launch() to 2),
                "shatter" to (Synth.shatter() to 1),
                "win" to (Synth.win() to 1),
                "tap" to (Synth.tap() to 2),
                "spin" to (Synth.spin() to 1),
                "explosion" to (Synth.explosion() to 1),
                "respawn" to (Synth.respawn() to 2),
                "fizzle" to (Synth.fizzle() to 1),
            ).mapValues { (_, v) -> Bank(List(v.second) { Voice.create(v.first, loop = false) }.filterNotNull()) }
            val rollVoice = Voice.create(Synth.roll(), loop = true)
            synchronized(this) {
                if (released) {
                    made.values.forEach { it.release() }
                    rollVoice?.release()
                } else {
                    banks = made
                    roll = rollVoice
                }
            }
        }, "carom-sounds").start()
    }

    private fun play(name: String, volume: Float, rate: Float = 1f) = banks[name]?.play(volume, rate)

    /**
     * A wall hit: louder for a harder hit ([strength] 0..1), with a slight change of pitch from one
     * knock to the next so a quick run of bounces never sounds mechanical.
     */
    fun impact(strength: Double) {
        val s = strength.coerceIn(0.0, 1.0).toFloat()
        val wobble = IMPACT_PITCHES[impactCount++ % IMPACT_PITCHES.size]
        play("impact", volume = 0.45f + 0.55f * s, rate = wobble * (0.96f + 0.08f * s))
    }

    /** The throw; louder for a stronger one. */
    fun launch(power: Double) {
        play("launch", volume = 0.3f + 0.5f * power.coerceIn(0.0, 1.0).toFloat(), rate = 0.92f + 0.16f * power.toFloat())
    }

    fun shatter() = play("shatter", 0.85f)

    fun win() = play("win", 0.6f)

    fun tap() = play("tap", 0.35f)

    fun spin() = play("spin", 0.8f)

    /** Stops a spin-up that was cut short (the level was restarted or left). */
    fun stopSpin() = banks["spin"]?.stop()

    fun explosion() = play("explosion", 0.95f)

    fun respawn() = play("respawn", 0.5f)

    fun fizzle() = play("fizzle", 0.6f)

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
            banks.values.forEach { it.release() }
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

        fun stop() {
            try {
                track.stop()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Could not stop a sound", e)
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

        /** Tiny pitch differences between successive knocks. */
        val IMPACT_PITCHES = floatArrayOf(1f, 0.97f, 1.03f, 0.99f, 1.02f)
    }
}
