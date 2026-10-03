package com.carom.game.audio

import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import android.os.SystemClock
import android.util.Log
import com.carom.core.audio.AudioCue
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
class SoundFx(private val bounceFile: () -> AssetFileDescriptor) : SoundOutput {

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
                "switch" to (Synth.tap() to 1),
                "restart1" to (Synth.restartTap1() to 1),
                "restart2" to (Synth.restartTap2() to 1),
                "restart3" to (Synth.restartTap3() to 1),
                "spin" to (Synth.spin() to 1),
                "explosion" to (Synth.explosion() to 1),
                "fizzle" to (Synth.fizzle() to 1),
                "uiBack" to (Synth.uiBack() to 1),
                "uiConfirm" to (Synth.uiConfirm() to 1),
                "uiLevel" to (Synth.uiLevel() to 1),
                "uiWorld" to (Synth.uiWorld() to 1),
                "unlock" to (Synth.unlock() to 1),
                "transition" to (Synth.transition() to 1),
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

    /** Plays [cue] at [volume] (already mixed by the audio manager: master x bus x the cue's level). */
    override fun play(cue: AudioCue, volume: Float) {
        when (cue) {
            // The recorded bounce, exactly as it is (its own pitch and length), started afresh for every collision.
            AudioCue.BOUNCE -> bounce?.play(volume = volume, rate = pitch)
            else -> bankOf(cue)?.let { playBank(it, volume, priority = cue.priority) }
        }
    }

    override fun stop(cue: AudioCue) {
        if (cue == AudioCue.SUCCESS_SPIN) banks["spin"]?.stop()
    }

    /** The synthesised bank a cue plays, or null for the two recordings. */
    private fun bankOf(cue: AudioCue): String? = when (cue) {
        AudioCue.SUCCESS -> "win"
        AudioCue.SUCCESS_SPIN -> "spin"
        AudioCue.SUCCESS_BURST -> "explosion"
        AudioCue.FAIL_BREAK -> "shatter"
        AudioCue.FAIL_STOP -> "fizzle"
        AudioCue.CLOCK -> "clock"
        AudioCue.PORTAL -> "portal"
        AudioCue.SWITCH -> "switch"
        AudioCue.EXIT_PARTIAL -> "exitPartial"
        AudioCue.UI_PRESS -> "tap"
        AudioCue.UI_LEVEL_SELECT -> "uiLevel"
        AudioCue.UI_WORLD_SELECT -> "uiWorld"
        AudioCue.UI_UNLOCK -> "unlock"
        AudioCue.UI_BACK -> "uiBack"
        AudioCue.UI_CONFIRM -> "uiConfirm"
        AudioCue.LEVEL_TRANSITION -> "transition"
        AudioCue.RESTART_TAP_1 -> "restart1"
        AudioCue.RESTART_TAP_2 -> "restart2"
        AudioCue.RESTART_TAP_3 -> "restart3"
        AudioCue.BOUNCE -> null
    }

    /**
     * Plays a sound from its bank if the shared pool has room for it (or something less important to give up).
     * [priority] is a [com.carom.core.audio.VoicePool.Priority].
     */
    private fun playBank(name: String, volume: Float, rate: Float = 1f, priority: Int) {
        val bank = banks[name] ?: return
        val voice = bank.peek() ?: return
        val slot = pool.acquire(now(), (bank.seconds / (rate * pitch)).toDouble(), priority)
        if (slot < 0) return // a busy moment: this one is not important enough to take a source
        if (pool.lastStolen >= 0) slots[slot]?.stop()
        slots[slot] = voice
        bank.play(volume, rate, pitch)
    }

    /**
     * Sets the playback speed of every sound, the ones already playing included (1 is normal).
     */
    override fun setPitch(scale: Float) {
        if (scale == pitch) return
        pitch = scale
        for (v in slots) v?.applyPitch(scale)
        bounce?.setRate(scale)
    }

    override fun release() {
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
