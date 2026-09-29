package com.carom.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Plays the game's sound effects. Each sound is synthesised once into a static [AudioTrack];
 * impacts get a few tracks in rotation so bounces in quick succession can overlap.
 *
 * Sound is decoration: if the device refuses to create audio tracks, the game simply stays silent.
 */
class SoundFx {

    private val impacts: List<Voice>
    private val shatter: Voice?
    private var nextImpact = 0

    init {
        val impactPcm = Synth.impact()
        impacts = List(IMPACT_VOICES) { Voice.create(impactPcm) }.filterNotNull()
        shatter = Voice.create(Synth.shatter())
    }

    /**
     * A bounce. [strength] (0..1, how hard the ball hit) sets the volume within a calm range,
     * and lifts the pitch a touch for harder hits.
     */
    fun impact(strength: Double) {
        if (impacts.isEmpty()) return
        val s = strength.coerceIn(0.0, 1.0).toFloat()
        val voice = impacts[nextImpact]
        nextImpact = (nextImpact + 1) % impacts.size
        voice.play(volume = 0.4f + 0.5f * s, rate = 0.97f + 0.06f * s)
    }

    fun shatter() {
        shatter?.play(volume = 0.8f, rate = 1f)
    }

    fun release() {
        impacts.forEach { it.release() }
        shatter?.release()
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
        const val IMPACT_VOICES = 3
    }
}
