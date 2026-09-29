package com.carom.game.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The game's sounds, synthesised once at start-up instead of shipped as files. Each is built from
 * a few decaying partials, shaped noise and a touch of room (a small reverb), so they are clean
 * and tiny.
 */
object Synth {
    const val SAMPLE_RATE = 44100

    /**
     * A bounce: a short, clear knock of a hard ball on something solid. A sharp click on
     * contact, a brief resonant "tok" and a firm low thump underneath, over almost at once.
     */
    fun impact(): ShortArray {
        val out = FloatArray(seconds(0.3))
        val rnd = Random(3)
        val click = Svf()
        for (k in 0 until seconds(0.03)) {
            val t = k.toDouble() / SAMPLE_RATE
            out[k] += (0.6 * min(1.0, t / 0.0003) * exp(-t / 0.0025) * click.bandpass(rnd.nextDouble(-1.0, 1.0), 3500.0, q = 1.2)).toFloat()
        }
        for (k in 0 until seconds(0.12)) {
            val t = k.toDouble() / SAMPLE_RATE
            val body = sin(2 * PI * 950.0 * t + 6.0 * (1 - exp(-t / 0.004)))
            out[k] += (0.55 * min(1.0, t / 0.0005) * exp(-t / 0.018) * body).toFloat()
            out[k] += (0.2 * min(1.0, t / 0.0005) * exp(-t / 0.01) * sin(2 * PI * 2350.0 * t)).toFloat()
        }
        var phase = 0.0
        for (k in 0 until seconds(0.2)) {
            val t = k.toDouble() / SAMPLE_RATE
            phase += 2 * PI * (110.0 + 40.0 * exp(-t / 0.01)) / SAMPLE_RATE
            out[k] += (0.7 * min(1.0, t / 0.001) * exp(-t / 0.04) * sin(phase)).toFloat()
        }
        reverb(out, mix = 0.08)
        return finish(out, peak = 0.95)
    }

    /**
     * The ball turning into a fan and spinning up: a small motor winding up from a low hum to a
     * high whine, with the whoosh of the blades pulsing faster and faster. Ends abruptly, where
     * the fan explodes.
     */
    fun spin(): ShortArray {
        val length = 1.1
        val out = FloatArray(seconds(length))
        val rnd = Random(13)
        val air = Svf()
        var motor = 0.0
        var blades = 0.0
        for (k in out.indices) {
            val t = k.toDouble() / SAMPLE_RATE
            val p = t / length
            val pitch = 90.0 + 700.0 * p.pow(1.4)
            motor += 2 * PI * pitch / SAMPLE_RATE
            // Four blades: the whoosh pulses four times per turn, from 3 to 60 turns a second.
            blades += 2 * PI * 4.0 * (3.0 + 57.0 * p.pow(1.6)) / SAMPLE_RATE
            var tone = 0.0
            for (h in 1..6) tone += sin(motor * h) / h
            val whoosh = air.bandpass(rnd.nextDouble(-1.0, 1.0), 800.0 + 2500.0 * p, q = 1.5) * (0.5 + 0.5 * sin(blades))
            val env = 0.3 + 0.7 * p
            out[k] = (env * (0.35 * tone + 0.6 * whoosh) * min(1.0, t / 0.01)).toFloat()
        }
        reverb(out, mix = 0.1)
        return finish(out, peak = 0.8)
    }

    /**
     * The fan exploding: a sharp crack, a deep boom whose rumble closes down from bright to dark,
     * a falling sub-bass drop and a scatter of crackles. Clearly unlike a wall knock.
     */
    fun explosion(): ShortArray {
        val out = FloatArray(seconds(1.5))
        val rnd = Random(17)
        val bright = Svf()
        for (k in 0 until seconds(0.06)) {
            val t = k.toDouble() / SAMPLE_RATE
            out[k] += (0.8 * min(1.0, t / 0.0003) * exp(-t / 0.008) * bright.lowpass(rnd.nextDouble(-1.0, 1.0), 5000.0, q = 0.7)).toFloat()
        }
        val rumble = Svf()
        for (k in 0 until seconds(1.3)) {
            val t = k.toDouble() / SAMPLE_RATE
            val cutoff = 150.0 + 1650.0 * exp(-t / 0.15)
            out[k] += (1.0 * min(1.0, t / 0.002) * exp(-t / 0.35) * rumble.lowpass(rnd.nextDouble(-1.0, 1.0), cutoff, q = 0.9)).toFloat()
        }
        var phase = 0.0
        for (k in 0 until seconds(1.0)) {
            val t = k.toDouble() / SAMPLE_RATE
            phase += 2 * PI * (35.0 + 35.0 * exp(-t / 0.12)) / SAMPLE_RATE
            out[k] += (0.9 * min(1.0, t / 0.003) * exp(-t / 0.4) * sin(phase)).toFloat()
        }
        repeat(40) {
            val start = 0.05 + 0.75 * rnd.nextDouble().pow(1.5)
            val amp = 0.25 * (1.0 - start / 0.9)
            val filter = Svf()
            val first = seconds(start)
            for (k in 0 until min(out.size - first, seconds(0.006))) {
                val t = k.toDouble() / SAMPLE_RATE
                out[first + k] += (amp * exp(-t / 0.0015) * filter.bandpass(rnd.nextDouble(-1.0, 1.0), 2000.0, q = 1.0)).toFloat()
            }
        }
        reverb(out, mix = 0.3)
        return finish(out, peak = 0.95)
    }

    /** A new ball appearing at the start: a quick, light rising "pop". */
    fun respawn(): ShortArray {
        val out = FloatArray(seconds(0.35))
        var phase = 0.0
        for (k in 0 until seconds(0.15)) {
            val t = k.toDouble() / SAMPLE_RATE
            phase += 2 * PI * (380.0 * (880.0 / 380.0).pow(min(1.0, t / 0.06))) / SAMPLE_RATE
            out[k] += (min(1.0, t / 0.003) * exp(-t / 0.05) * sin(phase)).toFloat()
        }
        reverb(out, mix = 0.12)
        return finish(out, peak = 0.6)
    }

    /** The ball running out of speed: a soft falling "whoo", deflating. */
    fun fizzle(): ShortArray {
        val out = FloatArray(seconds(0.5))
        var phase = 0.0
        val rnd = Random(19)
        val breath = Svf()
        for (k in 0 until seconds(0.35)) {
            val t = k.toDouble() / SAMPLE_RATE
            phase += 2 * PI * (520.0 * (160.0 / 520.0).pow(min(1.0, t / 0.3))) / SAMPLE_RATE
            val env = min(1.0, t / 0.01) * exp(-t / 0.12)
            out[k] += (env * (sin(phase) + 0.25 * breath.lowpass(rnd.nextDouble(-1.0, 1.0), 900.0, q = 0.7))).toFloat()
        }
        reverb(out, mix = 0.12)
        return finish(out, peak = 0.5)
    }

    /** The throw: a soft air "whoosh" rising and settling, over a gentle low puff. */
    fun launch(): ShortArray {
        val n = seconds(0.55)
        val out = FloatArray(n)
        val rnd = Random(5)
        val filter = Svf()
        for (i in 0 until seconds(0.36)) {
            val t = i.toDouble() / SAMPLE_RATE
            val centre = if (t < 0.16) 350.0 + (1500.0 - 350.0) * (t / 0.16) else 1500.0 - 600.0 * min(1.0, (t - 0.16) / 0.2)
            val env = min(1.0, t / 0.05).pow(2.0) * exp(-max(0.0, t - 0.06) / 0.09)
            out[i] += (0.9 * env * filter.bandpass(rnd.nextDouble(-1.0, 1.0), centre, q = 1.3)).toFloat()
        }
        for (i in 0 until seconds(0.12)) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = 90.0 - 30.0 * min(1.0, t / 0.08)
            out[i] += (0.35 * exp(-t / 0.04) * sin(2 * PI * freq * t) * min(1.0, t / 0.004)).toFloat()
        }
        reverb(out, mix = 0.12)
        return finish(out, peak = 0.8)
    }

    /**
     * The ball rolling: a soft, low, felt-like rumble with a faint breath of air and a slow
     * wobble. It loops seamlessly; its volume follows the ball's speed.
     */
    fun roll(): ShortArray {
        val loop = seconds(2.0)
        val fade = seconds(0.25)
        val raw = FloatArray(loop + fade)
        val rnd = Random(9)
        var brown = 0.0
        val low = Svf()
        val air = Svf()
        for (i in raw.indices) {
            val t = i.toDouble() / SAMPLE_RATE
            val white = rnd.nextDouble(-1.0, 1.0)
            brown = (brown + 0.02 * white) * 0.995
            val body = low.lowpass(brown * 8.0, 380.0, q = 0.8)
            val breath = air.bandpass(white, 1300.0, q = 0.9) * 0.05
            val wobble = 1.0 + 0.15 * sin(2 * PI * 7.0 * t)
            raw[i] = ((body + breath) * wobble).toFloat()
        }
        // Cross-fade the tail into the head so the loop has no seam.
        val out = FloatArray(loop)
        for (i in 0 until loop) out[i] = raw[i]
        for (k in 0 until fade) {
            val w = k.toFloat() / fade
            out[k] = raw[loop + k] * (1 - w) + raw[k] * w
        }
        return finish(out, peak = 0.5, edges = false)
    }

    /**
     * Scoring: a bright bell arpeggio (C6, E6, G6, C7) over a soft warm chord, with a shimmer of
     * tiny sparkles as the ball swirls into the ring.
     */
    fun win(): ShortArray {
        val out = FloatArray(seconds(2.2))
        val notes = doubleArrayOf(1046.5, 1318.5, 1568.0, 2093.0)
        val starts = doubleArrayOf(0.0, 0.08, 0.16, 0.27)
        for (k in notes.indices) {
            val amp = if (k == notes.size - 1) 0.9 else 0.7
            partial(out, starts[k], notes[k], amp, 0.9, attack = 0.003)
            partial(out, starts[k], notes[k] * 2.0, amp * 0.3, 0.5, attack = 0.003)
            partial(out, starts[k], notes[k] * 3.0, amp * 0.1, 0.3, attack = 0.003)
            partial(out, starts[k], notes[k] * 4.2, amp * 0.06, 0.15, attack = 0.002)
        }
        partial(out, 0.0, 523.25, 0.14, 0.9, attack = 0.06)
        partial(out, 0.0, 783.99, 0.1, 0.9, attack = 0.06)
        val rnd = Random(21)
        repeat(22) {
            val start = 0.05 + 0.85 * rnd.nextDouble()
            partial(out, start, 4000.0 + 4000.0 * rnd.nextDouble(), 0.04 + 0.04 * rnd.nextDouble(), 0.025 + 0.035 * rnd.nextDouble(), attack = 0.001)
        }
        reverb(out, mix = 0.3)
        return finish(out, peak = 0.85)
    }

    /**
     * The ball breaking: a crisp crack and a soft knock, then small glass-like pieces ringing out,
     * dense at first and thinning, with a few settling taps. Clear, but not a crash.
     */
    fun shatter(): ShortArray {
        val out = FloatArray(seconds(1.3))
        val rnd = Random(7)
        crack(out, 0.0, 0.5, rnd)
        crack(out, 0.024, 0.25, rnd)
        for (i in 0 until seconds(0.2)) {
            val t = i.toDouble() / SAMPLE_RATE
            out[i] += (0.3 * exp(-t / 0.06) * sin(2 * PI * 150.0 * t) * min(1.0, t / 0.002)).toFloat()
        }
        repeat(18) {
            val start = 0.005 + 0.5 * rnd.nextDouble().pow(1.8)
            val f = 1800.0 + 2400.0 * rnd.nextDouble()
            val amp = (0.08 + 0.1 * rnd.nextDouble()) * (1.0 - start / 0.7)
            val decay = 0.12 + 0.13 * rnd.nextDouble()
            partial(out, start, f, amp, decay, attack = 0.001)
            partial(out, start, f * 2.76, amp * 0.4, decay * 0.5, attack = 0.001)
            partial(out, start, f * 5.4, amp * 0.15, decay * 0.3, attack = 0.001)
        }
        repeat(4) {
            partial(out, 0.25 + 0.3 * rnd.nextDouble(), 600.0 + 300.0 * rnd.nextDouble(), 0.05, 0.03, attack = 0.001)
        }
        reverb(out, mix = 0.25)
        return finish(out, peak = 0.85)
    }

    /** A button: a tiny, soft, rounded tick. */
    fun tap(): ShortArray {
        val out = FloatArray(seconds(0.12))
        partial(out, 0.0, 1760.0, 0.6, 0.012, attack = 0.001)
        partial(out, 0.0, 880.0, 0.4, 0.02, attack = 0.001)
        return finish(out, peak = 0.6)
    }

    // ---------------------------------------------------------------- building blocks

    private fun seconds(s: Double) = (s * SAMPLE_RATE).toInt()

    /** Adds a sine at [freq] starting at [start] s, fading in over [attack] and out with time constant [decay]. */
    private fun partial(out: FloatArray, start: Double, freq: Double, amp: Double, decay: Double, attack: Double) {
        val first = seconds(start)
        val last = min(out.size, first + seconds(decay * 9))
        val w = 2 * PI * freq / SAMPLE_RATE
        for (i in first until last) {
            val k = i - first
            val t = k.toDouble() / SAMPLE_RATE
            out[i] += (amp * min(1.0, t / attack) * exp(-t / decay) * sin(w * k)).toFloat()
        }
    }

    /** A crack: a burst of bright band-passed noise, gone in a few tens of milliseconds. */
    private fun crack(out: FloatArray, start: Double, amp: Double, rnd: Random) {
        val filter = Svf()
        val first = seconds(start)
        for (k in 0 until min(out.size - first, seconds(0.12))) {
            val t = k.toDouble() / SAMPLE_RATE
            val ramp = min(1.0, t / 0.0005)
            out[first + k] += (amp * ramp * exp(-t / 0.018) * filter.bandpass(rnd.nextDouble(-1.0, 1.0), 3000.0, q = 0.8)).toFloat()
        }
    }

    /**
     * A small room: four damped comb filters into two all-passes (the classic Schroeder/Freeverb
     * layout), mixed in at [mix] of the dry sound's energy.
     */
    private fun reverb(out: FloatArray, mix: Double) {
        val combs = intArrayOf(1557, 1617, 1491, 1422).map { DoubleArray(it) }
        val combIndex = IntArray(4)
        val combStore = DoubleArray(4)
        val allpasses = intArrayOf(556, 441).map { DoubleArray(it) }
        val allIndex = IntArray(2)
        val wet = FloatArray(out.size)
        for (i in out.indices) {
            val x = out[i] * 0.25
            var sum = 0.0
            for (c in 0 until 4) {
                val buf = combs[c]
                val y = buf[combIndex[c]]
                combStore[c] = y * 0.8 + combStore[c] * 0.2
                buf[combIndex[c]] = x + combStore[c] * 0.8
                combIndex[c] = (combIndex[c] + 1) % buf.size
                sum += y
            }
            var s = sum
            for (a in 0 until 2) {
                val buf = allpasses[a]
                val b = buf[allIndex[a]]
                buf[allIndex[a]] = s + b * 0.5
                allIndex[a] = (allIndex[a] + 1) % buf.size
                s = b - s
            }
            wet[i] = s.toFloat()
        }
        val gain = mix * rms(out) / max(rms(wet), 1e-9)
        for (i in out.indices) out[i] += (wet[i] * gain).toFloat()
    }

    private fun rms(a: FloatArray): Double {
        var sum = 0.0
        for (v in a) sum += v.toDouble() * v
        return sqrt(sum / max(1, a.size))
    }

    /** Short fades at both ends (no clicks), scaled to [peak], as 16-bit PCM. */
    private fun finish(samples: FloatArray, peak: Double, edges: Boolean = true): ShortArray {
        if (edges) {
            val tail = min(samples.size, SAMPLE_RATE / 50)
            for (k in 0 until tail) samples[samples.size - 1 - k] *= k.toFloat() / tail
        }
        var max = 1e-6f
        for (v in samples) max = max(max, abs(v))
        val gain = (peak / max * Short.MAX_VALUE).toFloat()
        return ShortArray(samples.size) { (samples[it] * gain).toInt().toShort() }
    }

    /** A state-variable filter (Chamberlin): low-pass and band-pass outputs from one structure. */
    private class Svf {
        private var low = 0.0
        private var band = 0.0

        private fun step(x: Double, cutoff: Double, q: Double) {
            val f = 2 * sin(PI * min(cutoff, SAMPLE_RATE / 6.0) / SAMPLE_RATE)
            low += f * band
            val high = x - low - band / q
            band += f * high
        }

        fun lowpass(x: Double, cutoff: Double, q: Double): Double {
            step(x, cutoff, q)
            return low
        }

        fun bandpass(x: Double, cutoff: Double, q: Double): Double {
            step(x, cutoff, q)
            return band
        }
    }
}
