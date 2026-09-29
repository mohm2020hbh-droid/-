package com.carom.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.carom.core.audio.BeatDetector
import com.carom.core.audio.MusicLibrary
import com.carom.core.audio.TrackSpec
import java.util.concurrent.Executors

/**
 * The music, apart from the sound effects. A track is a recipe ([TrackSpec]) that is synthesised once on a
 * background thread and kept in a small cache; it plays as a seamless loop on one of two reused sources.
 * Changing tracks crossfades from one source to the other, and asking for the track that is already playing
 * does nothing, so the music never stops or restarts between levels of the same world.
 *
 * It also finds the beats of the loop that is playing ([beatPulse]) so pictures can pulse with it. Nothing in
 * the rules or the physics ever reads that.
 *
 * Like the effects, music is decoration: if the device refuses audio tracks the game stays quiet.
 */
class MusicPlayer {

    private class Loaded(val spec: TrackSpec, val pcm: ShortArray, val beats: BeatDetector.Beats)

    /** One of the two reused sources. */
    private class Deck {
        var track: AudioTrack? = null
        var loaded: Loaded? = null
        var volume = 0f
        var target = 0f
        var startedAt = 0L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "carom-music") }
    private val cache = object : LinkedHashMap<String, Loaded>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Loaded>?) = size > CACHE_TRACKS
    }
    private val decks = arrayOf(Deck(), Deck())
    private var live = -1
    private var wanted: TrackSpec? = null
    private var pitch = 1f
    private var enabled = true
    private var paused = false
    private var released = false
    private var ticking = false

    private val tick = object : Runnable {
        override fun run() {
            ticking = false
            var moving = false
            for (d in decks) {
                if (d.volume == d.target) continue
                val step = STEP_MS / 1000f / fadeSeconds
                d.volume = if (d.target > d.volume) minOf(d.target, d.volume + step) else maxOf(d.target, d.volume - step)
                d.track?.setVolume(d.volume * MUSIC_VOLUME)
                if (d.volume != d.target) moving = true else if (d.target == 0f) empty(d)
            }
            if (moving) schedule()
        }
    }
    private var fadeSeconds = 1.5f

    /** Plays [spec], fading over [fade] seconds. Does nothing if it is already the one playing (or on its way). */
    fun play(spec: TrackSpec, fade: Float = 1.5f) {
        if (released) return
        when (val change = MusicLibrary.change(wanted, spec, fade.toDouble())) {
            MusicLibrary.Change.Continue -> return
            is MusicLibrary.Change.Crossfade -> fadeSeconds = change.seconds.toFloat()
            is MusicLibrary.Change.Start -> fadeSeconds = change.fadeInSeconds.toFloat()
        }
        wanted = spec
        if (!enabled) return
        worker.execute {
            val loaded = synchronized(cache) { cache[spec.id] } ?: run {
                val pcm = Synth.music(spec)
                val floats = FloatArray(pcm.size) { pcm[it] / 32768f }
                Loaded(spec, pcm, BeatDetector.detect(floats, Synth.SAMPLE_RATE)).also { synchronized(cache) { cache[spec.id] = it } }
            }
            handler.post { if (!released && wanted?.id == spec.id) start(loaded) }
        }
    }

    /** Music on or off (it follows the sound setting). Turning it off fades what plays; turning it on brings the wanted track back. */
    fun setEnabled(on: Boolean) {
        if (enabled == on) return
        enabled = on
        if (!on) {
            fadeSeconds = 0.4f
            for (d in decks) d.target = 0f
            schedule()
        } else {
            val spec = wanted
            wanted = null
            if (spec != null) play(spec, 0.6f)
        }
    }

    /** The window went to the background or came back. */
    fun setPaused(value: Boolean) {
        if (paused == value) return
        paused = value
        for (d in decks) {
            try {
                if (value) d.track?.pause() else if (d.track != null) d.track?.play()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Could not pause the music", e)
            }
        }
    }

    /** All sound slows with time in a slow-motion zone: the music too. */
    fun setPitch(scale: Float) {
        pitch = scale
        for (d in decks) {
            try {
                d.track?.playbackRate = (Synth.SAMPLE_RATE * scale).toInt().coerceAtLeast(MIN_RATE)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Could not change the music's pitch", e)
            }
        }
    }

    /** 0..1, 1 on a beat of the playing track and fading away after it; 0 when nothing plays. */
    fun beatPulse(): Float {
        val d = decks.getOrNull(live) ?: return 0f
        val loaded = d.loaded ?: return 0f
        if (d.track == null || paused || !enabled) return 0f
        val elapsed = (SystemClock.elapsedRealtime() - d.startedAt) / 1000.0 * pitch
        return BeatDetector.pulse(loaded.beats, elapsed, loaded.spec.loopSeconds).toFloat()
    }

    fun release() {
        released = true
        handler.removeCallbacks(tick)
        worker.shutdownNow()
        for (d in decks) empty(d)
    }

    // ---------------------------------------------------------------- the two sources

    private fun start(loaded: Loaded) {
        val old = live
        val next = if (old == 0) 1 else 0
        val deck = decks[next]
        empty(deck)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(Synth.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(loaded.pcm.size * 2)
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "Music unavailable", e)
            return
        }
        try {
            track.write(loaded.pcm, 0, loaded.pcm.size)
            track.setLoopPoints(0, loaded.pcm.size, -1)
            track.setVolume(0f)
            track.playbackRate = (Synth.SAMPLE_RATE * pitch).toInt().coerceAtLeast(MIN_RATE)
            if (!paused) track.play()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Could not start the music", e)
            track.release()
            return
        }
        deck.track = track
        deck.loaded = loaded
        deck.volume = 0f
        deck.target = 1f
        deck.startedAt = SystemClock.elapsedRealtime()
        live = next
        if (old >= 0) decks[old].target = 0f
        schedule()
    }

    /** Stops and frees a source so it can be used again. */
    private fun empty(d: Deck) {
        try {
            d.track?.stop()
        } catch (e: IllegalStateException) {
            // already stopped
        }
        d.track?.release()
        d.track = null
        d.loaded = null
        d.volume = 0f
        d.target = 0f
    }

    private fun schedule() {
        if (ticking || released) return
        ticking = true
        handler.postDelayed(tick, STEP_MS)
    }

    private companion object {
        const val TAG = "Carom"

        /** Music sits well under the effects. */
        const val MUSIC_VOLUME = 0.22f
        const val STEP_MS = 50L
        const val CACHE_TRACKS = 3
        const val MIN_RATE = 4000
    }
}
