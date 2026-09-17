package com.fliperror.web

import kotlinx.browser.window
import kotlin.math.pow

/**
 * Synthesised with WebAudio so the slice stays asset-free and offline.
 *
 * Two rules shape everything here. The voice is round, not metal: triangles and
 * sines with fast attacks and very short tails, so a cute square never sounds
 * like a machine tool. And the bed is a drum-and-bass engine, not a loop - a
 * 16th-note scheduler with a lookahead, so the groove stays locked while the
 * game is dropping frames, and four intensity tiers the level itself moves
 * through: it opens on drums, builds, drops at 70%, and runs flat out for the
 * last stretch. Nothing in the mix is allowed to sit in front of a retry.
 */
object Audio {
    private var ctx: dynamic = null
    private var master: dynamic = null
    private var musicBus: dynamic = null
    private var noise: dynamic = null
    private var started = false
    private var streak = 0

    var sfxEnabled = true
    var musicEnabled = true
        set(v) { field = v; musicBus?.gain?.value = if (v) MUSIC_LEVEL else 0.0 }

    private const val MUSIC_LEVEL = 0.62

    /** Beats per minute of the level being played. Set before the run starts. */
    var bpm = 140.0
    /** 0 intro, 1 build, 2 drop, 3 final drive. Driven by level progress. */
    var intensity = 0

    // --- graph ---------------------------------------------------------------

    private fun ensure(): Boolean {
        if (ctx == null) {
            val C = window.asDynamic().AudioContext ?: window.asDynamic().webkitAudioContext
            if (C == null) return false
            ctx = js("new C()")
            master = ctx.createGain()
            master.gain.value = 0.85
            // A limiter, not a loudness trick: it lets the mix sit high enough for
            // a phone speaker while a jump landing on top of a drop cannot clip.
            val comp = ctx.createDynamicsCompressor()
            comp.threshold.value = -14.0
            comp.knee.value = 26.0
            comp.ratio.value = 14.0
            comp.attack.value = 0.003
            comp.release.value = 0.14
            master.connect(comp)
            comp.connect(ctx.destination)
            musicBus = ctx.createGain()
            musicBus.gain.value = if (musicEnabled) MUSIC_LEVEL else 0.0
            musicBus.connect(master)
        }
        return ctx != null
    }

    fun resume() {
        if (!ensure()) return
        if (ctx.state == "suspended") ctx.resume()
        if (!started) { started = true; runScheduler() }
    }

    private fun noiseBuffer(): dynamic {
        if (noise == null) {
            val rate = ctx.sampleRate as Double
            val len = (rate * 1.0).toInt()
            val buf = ctx.createBuffer(1, len, rate)
            val data = buf.getChannelData(0)
            for (i in 0 until len) data[i] = (kotlin.random.Random.nextDouble() * 2.0 - 1.0).toFloat()
            noise = buf
        }
        return noise
    }

    // --- one-shot voices -------------------------------------------------------

    /** [to] slides the pitch across the note, which is what makes a blip a direction. */
    private fun tone(
        from: Double, to: Double, dur: Double, type: String, gain: Double,
        delay: Double = 0.0, bus: dynamic = null, attack: Double = 0.005,
    ) {
        if (!ensure()) return
        val t = (ctx.currentTime as Double) + delay
        val osc = ctx.createOscillator()
        val g = ctx.createGain()
        osc.type = type
        osc.frequency.setValueAtTime(from, t)
        if (to != from) osc.frequency.exponentialRampToValueAtTime(to, t + dur)
        g.gain.setValueAtTime(0.0001, t)
        g.gain.exponentialRampToValueAtTime(gain, t + attack)
        g.gain.exponentialRampToValueAtTime(0.0001, t + dur)
        osc.connect(g); g.connect(bus ?: master)
        osc.start(t); osc.stop(t + dur + 0.02)
    }

    private fun noiseHit(
        at: Double, dur: Double, gain: Double, centre: Double, q: Double,
        type: String = "bandpass", bus: dynamic = null, sweepTo: Double = 0.0,
    ) {
        val src = ctx.createBufferSource()
        src.buffer = noiseBuffer()
        val f = ctx.createBiquadFilter()
        f.type = type
        f.frequency.setValueAtTime(centre, at)
        if (sweepTo > 0.0) f.frequency.exponentialRampToValueAtTime(sweepTo, at + dur)
        f.Q.value = q
        val g = ctx.createGain()
        g.gain.setValueAtTime(0.0001, at)
        g.gain.exponentialRampToValueAtTime(gain, at + 0.004)
        g.gain.exponentialRampToValueAtTime(0.0001, at + dur)
        src.connect(f); f.connect(g); g.connect(bus ?: master)
        src.start(at); src.stop(at + dur + 0.02)
    }

    // --- the bed ----------------------------------------------------------------
    //
    // Sixteen steps to the bar. The patterns are deliberately sparse at the
    // bottom and busy at the top, so the four tiers are a real arrangement and
    // not just a volume knob.

    private val kickSteps = intArrayOf(0, 10)
    private val snareSteps = intArrayOf(4, 12)
    private val bassRoots = doubleArrayOf(55.0, 55.0, 73.42, 65.41)     // A1 A1 D2 C2

    private fun kick(at: Double) {
        val o = ctx.createOscillator(); val g = ctx.createGain()
        o.type = "sine"
        o.frequency.setValueAtTime(155.0, at)
        o.frequency.exponentialRampToValueAtTime(42.0, at + 0.10)
        g.gain.setValueAtTime(0.0001, at)
        g.gain.exponentialRampToValueAtTime(0.95, at + 0.004)
        g.gain.exponentialRampToValueAtTime(0.0001, at + 0.20)
        o.connect(g); g.connect(musicBus); o.start(at); o.stop(at + 0.22)
        noiseHit(at, 0.02, 0.30, 2600.0, 0.8, "highpass", musicBus)   // the click
    }

    private fun snare(at: Double) {
        noiseHit(at, 0.13, 0.55, 1900.0, 0.9, "bandpass", musicBus, sweepTo = 900.0)
        val o = ctx.createOscillator(); val g = ctx.createGain()
        o.type = "triangle"
        o.frequency.setValueAtTime(220.0, at)
        o.frequency.exponentialRampToValueAtTime(150.0, at + 0.08)
        g.gain.setValueAtTime(0.0001, at)
        g.gain.exponentialRampToValueAtTime(0.32, at + 0.004)
        g.gain.exponentialRampToValueAtTime(0.0001, at + 0.10)
        o.connect(g); g.connect(musicBus); o.start(at); o.stop(at + 0.12)
    }

    private fun hat(at: Double, open: Boolean, gain: Double) {
        noiseHit(at, if (open) 0.11 else 0.030, gain, 9000.0, 0.7, "highpass", musicBus)
    }

    /** Two detuned saws under a moving low-pass: the sound the genre is built on. */
    private fun bass(at: Double, freq: Double, dur: Double, gain: Double) {
        val lp = ctx.createBiquadFilter()
        lp.type = "lowpass"
        lp.frequency.setValueAtTime(freq * 10.0, at)
        lp.frequency.exponentialRampToValueAtTime(freq * 3.0, at + dur)
        lp.Q.value = 6.0
        val g = ctx.createGain()
        g.gain.setValueAtTime(0.0001, at)
        g.gain.exponentialRampToValueAtTime(gain, at + 0.012)
        g.gain.exponentialRampToValueAtTime(0.0001, at + dur)
        lp.connect(g); g.connect(musicBus)
        for (detune in doubleArrayOf(-7.0, 7.0)) {
            val o = ctx.createOscillator()
            o.type = "sawtooth"
            o.frequency.value = freq
            o.detune.value = detune
            o.connect(lp); o.start(at); o.stop(at + dur + 0.02)
        }
        // a clean sub underneath, so it still reads on a phone speaker
        val sub = ctx.createOscillator(); val sg = ctx.createGain()
        sub.type = "sine"; sub.frequency.value = freq / 2
        sg.gain.setValueAtTime(0.0001, at)
        sg.gain.exponentialRampToValueAtTime(gain * 0.8, at + 0.012)
        sg.gain.exponentialRampToValueAtTime(0.0001, at + dur)
        sub.connect(sg); sg.connect(musicBus); sub.start(at); sub.stop(at + dur + 0.02)
    }

    private fun lead(at: Double, freq: Double, gain: Double) {
        val o = ctx.createOscillator(); val g = ctx.createGain()
        o.type = "square"
        o.frequency.value = freq
        g.gain.setValueAtTime(0.0001, at)
        g.gain.exponentialRampToValueAtTime(gain, at + 0.006)
        g.gain.exponentialRampToValueAtTime(0.0001, at + 0.12)
        o.connect(g); g.connect(musicBus); o.start(at); o.stop(at + 0.14)
    }

    private var step = 0
    private var nextStepTime = 0.0

    private fun playStep(i: Int, at: Double) {
        val inBar = i % 16
        val bar = (i / 16) % 4
        val tier = intensity

        if (inBar in kickSteps) kick(at)
        if (tier >= 2 && inBar == 6) kick(at)                    // the drop's extra kick
        if (inBar in snareSteps) snare(at)
        if (tier >= 3 && inBar == 14) snare(at)

        // hats: eighths, then sixteenths once the level is moving
        if (tier >= 1 && inBar % 4 == 2) hat(at, open = false, gain = 0.20)
        if (tier >= 2 && inBar % 2 == 1) hat(at, open = false, gain = 0.13)
        if (tier >= 3 && inBar % 2 == 0 && inBar % 4 != 0) hat(at, open = true, gain = 0.10)

        val root = bassRoots[bar]
        if (inBar == 0 || inBar == 10) bass(at, root, 0.30, 0.42)
        if (tier >= 2 && inBar == 6) bass(at, root * 1.5, 0.16, 0.30)
        if (tier >= 3 && inBar == 13) bass(at, root * 2.0, 0.14, 0.26)

        if (tier >= 1 && inBar % 8 == 0) lead(at, root * 4.0, 0.14)
        if (tier >= 2) {
            val arp = intArrayOf(0, 3, 7, 10)
            if (inBar % 2 == 0) lead(at, root * 4.0 * 2.0.pow(arp[(inBar / 2) % 4] / 12.0), 0.11)
        }
        if (tier >= 3 && inBar % 4 == 3) lead(at, root * 8.0, 0.09)
    }

    /**
     * Lookahead scheduler. Notes are queued into WebAudio's own clock a fraction
     * of a second early, so a stutter in the animation loop cannot move the beat.
     */
    private fun runScheduler() {
        if (!ensure()) return
        nextStepTime = (ctx.currentTime as Double) + 0.08
        fun tick() {
            if (ctx.state != "closed") {
                val stepDur = 60.0 / bpm / 4.0
                val now = ctx.currentTime as Double
                var guard = 0
                while (nextStepTime < now + 0.12 && guard++ < 32) {
                    if (musicEnabled) playStep(step, nextStepTime)
                    step++
                    nextStepTime += stepDur
                }
                if (nextStepTime < now) nextStepTime = now + 0.02
            }
            window.setTimeout({ tick() }, 25)
        }
        tick()
    }

    /** Called each frame with 0..1 through the level; moves the arrangement. */
    fun setProgress(p: Double) {
        intensity = when {
            p >= 0.90 -> 3
            p >= 0.70 -> 2
            p >= 0.25 -> 1
            else -> 0
        }
    }

    fun restartMusic() { step = 0; intensity = 0 }

    // --- the cues ----------------------------------------------------------------

    private fun jumpRoot(): Double {
        val s = if (streak > 4) 4 else streak
        return 440.0 * 2.0.pow(s / 12.0)
    }

    fun jump() {
        val root = jumpRoot()
        tone(root * 0.75, root * 1.32, 0.080, "triangle", 0.40)
        tone(root * 1.5, root * 2.5, 0.055, "sine", 0.16)
        streak++
    }

    /** A fifth above the first jump and split in two, so it reads as "up again". */
    fun doubleJump() {
        val root = jumpRoot() * 1.5
        tone(root, root * 1.20, 0.055, "triangle", 0.40)
        tone(root * 1.34, root * 1.95, 0.095, "triangle", 0.42, delay = 0.045)
        tone(root * 3.0, root * 3.7, 0.075, "sine", 0.18, delay = 0.045)
        if (sfxEnabled && ensure()) noiseHit((ctx.currentTime as Double), 0.09, 0.18, 5200.0, 1.2, "highpass")
    }

    fun death() {
        streak = 0
        tone(520.0, 88.0, 0.18, "triangle", 0.50)
        tone(260.0, 60.0, 0.20, "sine", 0.32, delay = 0.012)
        if (sfxEnabled && ensure()) noiseHit((ctx.currentTime as Double), 0.13, 0.26, 1200.0, 0.8, "bandpass", sweepTo = 200.0)
    }

    fun land() = tone(185.0, 95.0, 0.065, "sine", 0.24)

    fun nearMiss() {
        if (!sfxEnabled || !ensure()) return
        noiseHit((ctx.currentTime as Double), 0.11, 0.20, 2600.0, 6.0, "bandpass", sweepTo = 1100.0)
    }

    fun star() {
        tone(1318.5, 1318.5, 0.070, "triangle", 0.34)
        tone(1975.5, 1975.5, 0.130, "triangle", 0.32, delay = 0.055)
        tone(2637.0, 2637.0, 0.090, "sine", 0.16, delay = 0.055)
    }

    fun finish() {
        streak = 0
        listOf(523.25, 659.25, 783.99, 1046.5).forEachIndexed { i, f ->
            tone(f, f, 0.20, "triangle", 0.42, delay = i * 0.080)
            tone(f * 2, f * 2, 0.14, "sine", 0.16, delay = i * 0.080)
        }
        tone(1046.5, 1046.5, 0.50, "triangle", 0.34, delay = 0.34)
    }

    fun uiConfirm() {
        tone(700.0, 1050.0, 0.070, "triangle", 0.32)
        tone(1400.0, 1760.0, 0.090, "sine", 0.16, delay = 0.05)
    }

    fun uiDenied() = tone(220.0, 165.0, 0.10, "triangle", 0.28)
}
