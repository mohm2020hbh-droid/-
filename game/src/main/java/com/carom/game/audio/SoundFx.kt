package com.carom.game.audio

import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import android.os.SystemClock
import android.util.Log
import com.carom.core.audio.VoicePool

/**
 * Plays the game's sound effects. Each sound is synthesised once (on a background thread, so
 * start-up never waits for it) into static [AudioTrack]s; sounds that can overlap get a few
 * tracks in rotation. The one exception is the collision sound, the recorded `ball_bounce_exact.ogg`
 * ([bounceFile] opens it), which plays exactly as recorded. The ball itself is silent: nothing plays
 * for the throw or while it flies, only for a collision.
 *
 * The sources are a fixed pool: nothing is created while playing. On top of each sound's own
 * tracks a shared [VoicePool] caps how many play at once and lets the important sounds (a ball
 * exploding, the exit) take over from the small ones when a busy moment fills it.
 * [setPitch] scales the playback speed of everything, playing or not, for slow motion.
 *
 * Sound is decoration: until the sounds are ready, or if the device refuses audio tracks, the game
 * simply stays silent.
 */
class SoundFx(private val bounceFile: () -> AssetFileDescriptor) {

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

    /**
     * The collision sound: the recorded bounce, exactly as it is (its own pitch and length), started afresh for every
     * collision so quick successive ones each sound. A [SoundPool] decodes the file once when it loads.
     */
    private class Bounce(private val pool: SoundPool) {
        /** The loaded sample's id once the pool has decoded it; 0 until then (and so silence). */
        @Volatile private var ready = 0
        private val streams = IntArray(STREAMS)
        private var next = 0

        init {
            pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) ready = id }
        }

        fun play(volume: Float, rate: Float) {
            val id = ready
            if (id == 0) return
            val stream = pool.play(id, volume, volume, 1, 0, rate.coerceIn(MIN_BOUNCE_RATE, MAX_BOUNCE_RATE))
            if (stream != 0) {
                streams[next] = stream
                next = (next + 1) % STREAMS
            }
        }

        /** Slow motion slows what is still ringing too. */
        fun setRate(rate: Float) {
            for (s in streams) if (s != 0) pool.setRate(s, rate.coerceIn(MIN_BOUNCE_RATE, MAX_BOUNCE_RATE))
        }

        fun release() = pool.release()

        companion object {
            /** Loads the recorded file, or null (and so silence) if the device or the file will not have it. */
            fun create(file: () -> AssetFileDescriptor): Bounce? = try {
                val pool = SoundPool.Builder()
                    .setMaxStreams(STREAMS)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .build()
                Bounce(pool).also { file().use { f -> pool.load(f, 1) } }
            } catch (e: Exception) {
                Log.w(TAG, "Could not load the bounce sound", e)
                null
            }
        }
    }

    @Volatile private var banks: Map<String, Bank> = emptyMap()
    @Volatile private var bounce: Bounce? = null
    @Volatile private var released = false

    /** The playback speed everything plays at (1 normally). */
    private var pitch = 1f

    private val pool = VoicePool(POOL_SIZE)
    private val slots = arrayOfNulls<Voice>(POOL_SIZE)

    init {
        Thread({
            val made = linkedMapOf(
                "shatter" to (Synth.shatter() to 2),
                "win" to (Synth.win() to 1),
                "tap" to (Synth.tap() to 2),
                "spin" to (Synth.spin() to 1),
                "explosion" to (Synth.explosion() to 1),
                "respawn" to (Synth.respawn() to 2),
                "fizzle" to (Synth.fizzle() to 1),
                "portal" to (Synth.portal() to 2),
                "clock" to (Synth.clock() to 2),
                "exitPartial" to (Synth.exitPartial() to 1),
            ).mapValues { (_, v) ->
                Bank(List(v.second) { Voice.create(v.first) }.filterNotNull(), v.first.size.toFloat() / Synth.SAMPLE_RATE)
            }
            val recorded = Bounce.create(bounceFile)
            synchronized(this) {
                if (released) {
                    made.values.forEach { it.release() }
                    recorded?.release()
                } else {
                    banks = made
                    bounce = recorded
                }
            }
        }, "carom-sounds").start()
    }

    private fun now(): Double = SystemClock.elapsedRealtime() / 1000.0

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
     * A collision of the ball with anything: the recorded bounce, exactly as it is, a little louder for a harder hit
     * ([strength] 0..1). Every collision starts its own.
     */
    fun impact(strength: Double) {
        bounce?.play(volume = 0.7f + 0.3f * strength.coerceIn(0.0, 1.0).toFloat(), rate = pitch)
    }

    fun shatter() = play("shatter", 0.85f, priority = VoicePool.Priority.EXPLOSION)

    fun win() = play("win", 0.9f, priority = VoicePool.Priority.EXIT_COMPLETE)

    fun tap() = play("tap", 0.35f, priority = VoicePool.Priority.UI)

    fun spin() = play("spin", 0.8f, priority = VoicePool.Priority.EXIT_COMPLETE)

    /** Stops a spin-up that was cut short (the level was restarted or left). */
    fun stopSpin() = banks["spin"]?.stop()

    fun explosion() = play("explosion", 0.95f, priority = VoicePool.Priority.EXIT_COMPLETE)

    /** The ball being recharged at its start, whether a try was lost or the player started again. */
    fun respawn() = play("respawn", 0.62f, priority = VoicePool.Priority.UI)

    fun fizzle() = play("fizzle", 0.6f, priority = VoicePool.Priority.UI)

    fun portal() = play("portal", 0.7f, priority = VoicePool.Priority.PORTAL)

    /** A ball losing speed in a clock; louder the faster it was going. */
    fun clock(strength: Double) = play("clock", (0.35 + 0.4 * strength).toFloat(), priority = VoicePool.Priority.CLOCK)

    fun exitPartial() = play("exitPartial", 0.6f, priority = VoicePool.Priority.EXIT_PARTIAL)

    /**
     * Sets the playback speed of every sound, the ones already playing included (1 is normal).
     */
    fun setPitch(scale: Float) {
        if (scale == pitch) return
        pitch = scale
        for (v in slots) v?.applyPitch(scale)
        bounce?.setRate(scale)
    }

    fun release() {
        synchronized(this) {
            released = true
            banks.values.forEach { it.release() }
            bounce?.release()
        }
    }

    private class Voice(private val track: AudioTrack) {
        /** The playback speed this sound was started at, before the global pitch. */
        private var baseRate = 1f

        fun play(volume: Float, rate: Float, pitch: Float) {
            try {
                track.stop()
                track.reloadStaticData()
                baseRate = rate
                track.playbackRate = (Synth.SAMPLE_RATE * rate * pitch).toInt().coerceAtLeast(MIN_RATE)
                track.setVolume(volume)
                track.play()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Could not play a sound", e)
            }
        }

        fun applyPitch(pitch: Float) {
            try {
                track.playbackRate = (Synth.SAMPLE_RATE * baseRate * pitch).toInt().coerceAtLeast(MIN_RATE)
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

        fun release() = track.release()

        companion object {
            fun create(pcm: ShortArray): Voice? = try {
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
                if (track.state == AudioTrack.STATE_INITIALIZED) Voice(track) else null.also { track.release() }
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

        /** Collisions that can ring at once, and the range of speeds a [SoundPool] plays at. */
        const val STREAMS = 6
        const val MIN_BOUNCE_RATE = 0.5f
        const val MAX_BOUNCE_RATE = 2f
    }
}
