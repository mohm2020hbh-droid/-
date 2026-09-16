package com.fliperror.web

import kotlinx.browser.window
import kotlin.math.pow

/**
 * Synthesised with WebAudio so the slice stays asset-free and offline.
 *
 * The voice is deliberately round, not metal: triangles and sines with fast
 * attacks and very short tails, and no sawtooth anywhere, because a buzzy edge
 * makes a cute square sound like a machine. Pitch slides carry the meaning, so
 * every cue reads as a direction. Nothing runs past a fifth of a second, so no
 * sound ever sits in front of a retry (GDD 2.3).
 */
object Audio {
    private var ctx: dynamic = null
    private var master: dynamic = null
    private var musicOn = false
    private var streak = 0
    private var noise: dynamic = null

    /** Player switches. Music keeps running but silent, so the beat never drifts. */
    var sfxEnabled = true
    var musicEnabled = true
        set(v) { field = v; musicGain?.gain?.value = if (v) 1.0 else 0.0 }
    private var musicGain: dynamic = null

    private fun ensure(): Boolean {
        if (ctx == null) {
            val C = window.asDynamic().AudioContext ?: window.asDynamic().webkitAudioContext
            if (C == null) return false
            ctx = js("new C()")
            master = ctx.createGain()
            master.gain.value = 0.62
            // A limiter, not a loudness trick: it lets the master sit much higher
            // for a phone speaker while a jump landing on top of the music still
            // cannot clip. Everything below is mixed against this ceiling.
            val comp = ctx.createDynamicsCompressor()
            comp.threshold.value = -12.0
            comp.knee.value = 24.0
            comp.ratio.value = 12.0
            comp.attack.value = 0.003
            comp.release.value = 0.16
            master.connect(comp)
            comp.connect(ctx.destination)
            musicGain = ctx.createGain()
            musicGain.gain.value = if (musicEnabled) 1.0 else 0.0
            musicGain.connect(master)
        }
        return ctx != null
    }

    fun resume() {
        if (!ensure()) return
        if (ctx.state == "suspended") ctx.resume()
        if (!musicOn) { musicOn = true; startMusic() }
    }

    /**
     * One note. [to] slides the pitch across the note's life, which is what
     * makes a blip read as a direction rather than a beep.
     */
    private fun tone(
        from: Double, to: Double, dur: Double, type: String, gain: Double,
        delay: Double = 0.0, attack: Double = 0.006,
    ) {
        if (!sfxEnabled || !ensure()) return
        val t = (ctx.currentTime as Double) + delay
        val osc = ctx.createOscillator()
        val g = ctx.createGain()
        osc.type = type
        osc.frequency.setValueAtTime(from, t)
        if (to != from) osc.frequency.exponentialRampToValueAtTime(to, t + dur)
        g.gain.setValueAtTime(0.0001, t)
        g.gain.exponentialRampToValueAtTime(gain, t + attack)
        g.gain.exponentialRampToValueAtTime(0.0001, t + dur)
        osc.connect(g); g.connect(master)
        osc.start(t); osc.stop(t + dur + 0.02)
    }

    /** Two seconds of white noise, generated once and re-triggered as buffers. */
    private fun noiseBuffer(): dynamic {
        if (noise == null) {
            val rate = ctx.sampleRate as Double
            val len = (rate * 0.5).toInt()
            val buf = ctx.createBuffer(1, len, rate)
            val data = buf.getChannelData(0)
            for (i in 0 until len) data[i] = (kotlin.random.Random.nextDouble() * 2.0 - 1.0).toFloat()
            noise = buf
        }
        return noise
    }

    /** A band of air moving past. Used only for the near miss, and kept quiet. */
    private fun whoosh(centre: Double, dur: Double, gain: Double) {
        if (!sfxEnabled || !ensure()) return
        val t = ctx.currentTime as Double
        val src = ctx.createBufferSource()
        src.buffer = noiseBuffer()
        val bp = ctx.createBiquadFilter()
        bp.type = "bandpass"
        bp.frequency.setValueAtTime(centre, t)
        bp.frequency.exponentialRampToValueAtTime(centre * 0.45, t + dur)
        bp.Q.value = 6.0
        val g = ctx.createGain()
        g.gain.setValueAtTime(0.0001, t)
        g.gain.exponentialRampToValueAtTime(gain, t + 0.02)
        g.gain.exponentialRampToValueAtTime(0.0001, t + dur)
        src.connect(bp); bp.connect(g); g.connect(master)
        src.start(t); src.stop(t + dur + 0.02)
    }

    /** The note the current jump sits on. A clean streak climbs, gently. */
    private fun jumpRoot(): Double {
        val step = if (streak > 4) 4 else streak
        return 440.0 * 2.0.pow(step / 12.0)
    }

    /** Light, round, and upward: a hop, not a launch. */
    fun jump() {
        val root = jumpRoot()
        tone(root * 0.75, root * 1.30, 0.095, "triangle", 0.34)
        tone(root * 1.5, root * 2.4, 0.060, "sine", 0.12)      // a little air on top
        streak++
    }

    /**
     * The second jump, a fifth above the first and split into two quick notes so
     * it reads as "up again" even under the music. Distinct by pitch and by
     * shape, not merely louder.
     */
    fun doubleJump() {
        val root = jumpRoot() * 1.5
        tone(root, root * 1.18, 0.060, "triangle", 0.34)
        tone(root * 1.34, root * 1.9, 0.100, "triangle", 0.36, delay = 0.045)
        tone(root * 3.0, root * 3.6, 0.075, "sine", 0.13, delay = 0.045)
    }

    /** A short rubbery drop. Comic, not catastrophic, and out of the way fast. */
    fun death() {
        streak = 0
        tone(520.0, 90.0, 0.18, "triangle", 0.44)
        tone(260.0, 62.0, 0.20, "sine", 0.28, delay = 0.012)
    }

    /** Barely there: a soft tap so the ground has weight. */
    fun land() {
        tone(180.0, 96.0, 0.070, "sine", 0.20)
    }

    /** A spike cleared with nothing to spare. */
    fun nearMiss() {
        whoosh(2400.0, 0.11, 0.14)
    }

    /** Menu feedback. Quieter than anything in the run, and out of the way fast. */
    fun uiConfirm() {
        tone(700.0, 1050.0, 0.075, "triangle", 0.30)
        tone(1400.0, 1760.0, 0.095, "sine", 0.14, delay = 0.05)
    }

    fun uiDenied() {
        tone(220.0, 165.0, 0.10, "triangle", 0.26)
    }

    fun star() {
        tone(1318.5, 1318.5, 0.075, "triangle", 0.30)
        tone(1975.5, 1975.5, 0.13, "triangle", 0.28, delay = 0.06)
    }

    fun finish() {
        streak = 0
        listOf(523.25, 659.25, 783.99, 1046.5).forEachIndexed { i, f ->
            tone(f, f, 0.21, "triangle", 0.38, delay = i * 0.085)
            tone(f * 2, f * 2, 0.15, "sine", 0.14, delay = i * 0.085)
        }
    }

    /** A 140 BPM loop: kick on the beat, a soft bass on the eighths. */
    private fun startMusic() {
        if (!ensure()) return
        val beat = 60.0 / 140.0
        val notes = doubleArrayOf(82.41, 82.41, 110.0, 82.41, 98.0, 82.41, 73.42, 82.41)
        var step = 0
        fun tick() {
            if (ctx.state != "closed") {
                val t = ctx.currentTime as Double
                val k = ctx.createOscillator(); val kg = ctx.createGain()
                k.type = "sine"
                k.frequency.setValueAtTime(140.0, t)
                k.frequency.exponentialRampToValueAtTime(42.0, t + 0.11)
                kg.gain.setValueAtTime(0.62, t)
                kg.gain.exponentialRampToValueAtTime(0.0001, t + 0.16)
                k.connect(kg); kg.connect(musicGain); k.start(t); k.stop(t + 0.17)
                // Triangle, not sawtooth: the bed should carry the pulse without
                // putting a metal edge on every eighth note.
                val b = ctx.createOscillator(); val bg = ctx.createGain()
                b.type = "triangle"
                b.frequency.setValueAtTime(notes[step % notes.size], t)
                bg.gain.setValueAtTime(0.20, t)
                bg.gain.exponentialRampToValueAtTime(0.0001, t + beat * 0.85)
                b.connect(bg); bg.connect(musicGain); b.start(t); b.stop(t + beat)
                step++
            }
            window.setTimeout({ tick() }, (beat * 1000).toInt())
        }
        tick()
    }
}
