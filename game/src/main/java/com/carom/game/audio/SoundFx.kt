package com.carom.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import com.carom.core.audio.VoicePool
import com.carom.core.audio.WavFile

/**
 * Plays the game's sound effects. Each sound is synthesised once (on a background thread, so
 * start-up never waits for it) into static [AudioTrack]s; sounds that can overlap get a few
 * tracks in rotation. The rolling sound is a seamless loop whose volume follows the ball.
 *
 * The sources are a fixed pool: nothing is created while playing. On top of each sound's own
 * tracks a shared [VoicePool] caps how many play at once and lets the important sounds (a ball
 * exploding, the exit) take over from the small ones (bounces) when a busy moment fills it.
 * [setPitch] scales the playback speed of everything, playing or not, for slow motion.
 *
 * The launch sound is a recorded file ([launchSound] gives its bytes); it plays at its own sample rate, exactly as
 * recorded. Every other sound is synthesised.
 *
 * Sound is decoration: until the sounds are ready, or if the device refuses audio tracks, the game
 * simply stays silent.
 */
class SoundFx(private val launchSound: () -> ByteArray) {

    private class Bank(val voices: List<Voice>, val seconds: Float) {
        private var next = 0

        /** The track the next [play] will use. */
        fun peek(): Voice? = voices.getOrNull(next)

        fun play(volume: Float, rate: Float, pitch: Float) {
            if (voices.isEmpty()) return
            voices[next].play(volume, rate, pitch)
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

    /** The playback speed everything plays at: 1 normally, lower in slow motion. */
    private var pitch = 1f

    private val pool = VoicePool(POOL_SIZE)
    private val slots = arrayOfNulls<Voice>(POOL_SIZE)

    init {
        Thread({
            val made = linkedMapOf(
                "impact" to (Synth.impact() to 4),
                "container" to (Synth.containerHit() to 2),
                "shatter" to (Synth.shatter() to 2),
                "win" to (Synth.win() to 1),
                "tap" to (Synth.tap() to 2),
                "spin" to (Synth.spin() to 1),
                "explosion" to (Synth.explosion() to 1),
                "respawn" to (Synth.respawn() to 2),
                "fizzle" to (Synth.fizzle() to 1),
                "portal" to (Synth.portal() to 2),
                "slowIn" to (Synth.slowIn() to 1),
                "slowOut" to (Synth.slowOut() to 1),
                "exitPartial" to (Synth.exitPartial() to 1),
            ).mapValues { (_, v) ->
                Bank(List(v.second) { Voice.create(v.first, loop = false) }.filterNotNull(), v.first.size.toFloat() / Synth.SAMPLE_RATE)
            }.toMutableMap()
            loadLaunch()?.let { pcm ->
                made["launch"] = Bank(List(2) { Voice.create(pcm.samples, loop = false, sampleRate = pcm.sampleRate) }.filterNotNull(), pcm.seconds.toFloat())
            }
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

    private fun now(): Double = SystemClock.elapsedRealtime() / 1000.0

    private fun loadLaunch(): WavFile.Pcm? = try {
        WavFile.decode(launchSound())
    } catch (e: Exception) {
        Log.w(TAG, "Could not read the launch sound", e)
        null
    }

    /**
     * Plays a sound from its bank if the shared pool has room for it (or something less important to give up).
     * [priority] is a [VoicePool.Priority].
     */
    private fun play(name: String, volume: Float, rate: Float = 1f, priority: Int = VoicePool.Priority.UI) {
        val bank = banks[name] ?: return
        val voice = bank.peek() ?: return
        val slot = pool.acquire(now(), (bank.seconds / (rate * pitch)).toDouble(), priority)
        if (slot < 0) return // a busy moment: this one is not important enough to take a source
        if (pool.lastStolen >= 0) slots[slot]?.stop()
        slots[slot] = voice
        bank.play(volume, rate, pitch)
    }

    /**
     * A wall hit: louder for a harder hit ([strength] 0..1). [pitch] is the bounce's place in the ball's life
     * (low on the first bounce, rising towards the last); a slight change from one knock to the next keeps a
     * quick run of bounces from sounding mechanical.
     */
    fun impact(strength: Double, pitch: Float = 1f) {
        val s = strength.coerceIn(0.0, 1.0).toFloat()
        val wobble = IMPACT_PITCHES[impactCount++ % IMPACT_PITCHES.size]
        play("impact", volume = 0.45f + 0.55f * s, rate = pitch * wobble * (0.96f + 0.08f * s), priority = VoicePool.Priority.BOUNCE)
    }

    /** A hit on a ball container: its own, brighter clack, with the same rising pitch. */
    fun containerHit(strength: Double, pitch: Float = 1f) {
        val s = strength.coerceIn(0.0, 1.0).toFloat()
        play("container", volume = 0.5f + 0.5f * s, rate = pitch * (0.96f + 0.08f * s), priority = VoicePool.Priority.BOUNCE + 1)
    }

    /** The throw: the recorded launch sound at its own pitch, louder for a stronger throw. */
    fun launch(power: Double) {
        play("launch", volume = 0.3f + 0.5f * power.coerceIn(0.0, 1.0).toFloat(), priority = VoicePool.Priority.LAUNCH)
    }

    fun shatter() = play("shatter", 0.85f, priority = VoicePool.Priority.EXPLOSION)

    fun win() = play("win", 0.9f, priority = VoicePool.Priority.EXIT_COMPLETE)

    fun tap() = play("tap", 0.35f, priority = VoicePool.Priority.UI)

    fun spin() = play("spin", 0.8f, priority = VoicePool.Priority.EXIT_COMPLETE)

    /** Stops a spin-up that was cut short (the level was restarted or left). */
    fun stopSpin() = banks["spin"]?.stop()

    fun explosion() = play("explosion", 0.95f, priority = VoicePool.Priority.EXIT_COMPLETE)

    fun respawn() = play("respawn", 0.5f, priority = VoicePool.Priority.UI)

    fun fizzle() = play("fizzle", 0.6f, priority = VoicePool.Priority.UI)

    fun portal() = play("portal", 0.7f, priority = VoicePool.Priority.PORTAL)

    fun slowIn() = play("slowIn", 0.7f, priority = VoicePool.Priority.SLOWMO)

    fun slowOut() = play("slowOut", 0.6f, priority = VoicePool.Priority.SLOWMO)

    fun exitPartial() = play("exitPartial", 0.6f, priority = VoicePool.Priority.EXIT_PARTIAL)

    /**
     * Sets the playback speed of every sound, the ones already playing included (slow motion drops it to
     * about a third, and it comes back to 1 when time does). The rolling sound follows too.
     */
    fun setPitch(scale: Float) {
        if (scale == pitch) return
        pitch = scale
        for (v in slots) v?.applyPitch(scale)
        roll?.applyPitch(scale)
    }

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

    private class Voice(private val track: AudioTrack, private val sampleRate: Int) {
        /** The playback speed this sound was started at, before the global pitch. */
        private var baseRate = 1f

        fun play(volume: Float, rate: Float, pitch: Float) {
            try {
                track.stop()
                track.reloadStaticData()
                baseRate = rate
                track.playbackRate = (sampleRate * rate * pitch).toInt().coerceAtLeast(MIN_RATE)
                track.setVolume(volume)
                track.play()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Could not play a sound", e)
            }
        }

        fun applyPitch(pitch: Float) {
            try {
                track.playbackRate = (sampleRate * baseRate * pitch).toInt().coerceAtLeast(MIN_RATE)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Could not change a sound's pitch", e)
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
            fun create(pcm: ShortArray, loop: Boolean, sampleRate: Int = Synth.SAMPLE_RATE): Voice? = try {
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
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                if (loop) track.setLoopPoints(0, pcm.size, -1)
                if (track.state == AudioTrack.STATE_INITIALIZED) Voice(track, sampleRate) else null.also { track.release() }
            } catch (e: Exception) {
                Log.w(TAG, "Audio unavailable", e)
                null
            }
        }
    }

    private companion object {
        const val TAG = "Carom"

        /** Sounds that can play at once. */
        const val POOL_SIZE = 10

        /** AudioTrack refuses very low rates. */
        const val MIN_RATE = 4000

        /** Tiny pitch differences between successive knocks. */
        val IMPACT_PITCHES = floatArrayOf(1f, 0.97f, 1.03f, 0.99f, 1.02f)
    }
}
