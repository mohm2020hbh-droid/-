package com.fliperror.web

import kotlinx.browser.window
import kotlin.math.pow

/**
 * Prototype audio. Synthesised with WebAudio so the slice stays asset-free and
 * offline. GDD 12.4: the jump note climbs with every clean jump and resets on
 * death, and the death sound is short enough not to sit in front of a retry.
 */
object Audio {
    private var ctx: dynamic = null
    private var master: dynamic = null
    private var musicOn = false
    private var streak = 0

    private fun ensure(): Boolean {
        if (ctx == null) {
            val C = window.asDynamic().AudioContext ?: window.asDynamic().webkitAudioContext
            if (C == null) return false
            ctx = js("new C()")
            master = ctx.createGain()
            master.gain.value = 0.22
            master.connect(ctx.destination)
        }
        return ctx != null
    }

    fun resume() {
        if (!ensure()) return
        if (ctx.state == "suspended") ctx.resume()
        if (!musicOn) { musicOn = true; startMusic() }
    }

    private fun blip(freq: Double, dur: Double, type: String, gain: Double) {
        if (!ensure()) return
        val t = ctx.currentTime as Double
        val osc = ctx.createOscillator()
        val g = ctx.createGain()
        osc.type = type
        osc.frequency.setValueAtTime(freq, t)
        g.gain.setValueAtTime(gain, t)
        g.gain.exponentialRampToValueAtTime(0.0001, t + dur)
        osc.connect(g); g.connect(master)
        osc.start(t); osc.stop(t + dur)
    }

    /** Pitch ladder: every clean jump is a semitone up, capped at an octave. */
    fun jump() {
        val step = if (streak > 12) 12 else streak
        blip(330.0 * 2.0.pow(step / 12.0), 0.10, "square", 0.16)
        streak++
    }

    fun death() {
        streak = 0
        if (!ensure()) return
        val t = ctx.currentTime as Double
        val osc = ctx.createOscillator()
        val g = ctx.createGain()
        osc.type = "sawtooth"
        osc.frequency.setValueAtTime(220.0, t)
        osc.frequency.exponentialRampToValueAtTime(48.0, t + 0.22)
        g.gain.setValueAtTime(0.22, t)
        g.gain.exponentialRampToValueAtTime(0.0001, t + 0.24)
        osc.connect(g); g.connect(master)
        osc.start(t); osc.stop(t + 0.25)
    }

    fun finish() {
        streak = 0
        listOf(523.25, 659.25, 783.99, 1046.5).forEachIndexed { i, f ->
            window.setTimeout({ blip(f, 0.22, "triangle", 0.18) }, i * 90)
        }
    }

    /** A 140 BPM two-bar loop: kick on the beat, bass on the eighths. */
    private fun startMusic() {
        if (!ensure()) return
        val beat = 60.0 / 140.0
        val notes = doubleArrayOf(82.41, 82.41, 110.0, 82.41, 98.0, 82.41, 73.42, 82.41)
        var step = 0
        fun tick() {
            if (ctx.state != "closed") {
                val t = ctx.currentTime as Double
                // kick
                val k = ctx.createOscillator(); val kg = ctx.createGain()
                k.type = "sine"
                k.frequency.setValueAtTime(140.0, t)
                k.frequency.exponentialRampToValueAtTime(42.0, t + 0.11)
                kg.gain.setValueAtTime(0.45, t)
                kg.gain.exponentialRampToValueAtTime(0.0001, t + 0.16)
                k.connect(kg); kg.connect(master); k.start(t); k.stop(t + 0.17)
                // bass
                val b = ctx.createOscillator(); val bg = ctx.createGain()
                b.type = "sawtooth"
                b.frequency.setValueAtTime(notes[step % notes.size], t)
                bg.gain.setValueAtTime(0.10, t)
                bg.gain.exponentialRampToValueAtTime(0.0001, t + beat * 0.85)
                b.connect(bg); bg.connect(master); b.start(t); b.stop(t + beat)
                step++
            }
            window.setTimeout({ tick() }, (beat * 1000).toInt())
        }
        tick()
    }
}
