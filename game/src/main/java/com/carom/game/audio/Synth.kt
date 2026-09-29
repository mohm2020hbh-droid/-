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
 * a few decaying partials, shaped noise and a touch of room (a small reverb), so they are soft,
 * musical and tiny. Bounces are marimba-like notes that climb a pentatonic scale, so a shot plays
 * a little melody.
 */
object Synth {
    const val SAMPLE_RATE = 44100

    /** Pitch steps for successive bounces: a major pentatonic scale, as playback-rate ratios. */
    val PENTATONIC = floatArrayOf(1f, 9f / 8f, 5f / 4f, 3f / 2f, 5f / 3f, 2f)

    /**
     * A bounce: a warm mallet note (C5) with the bright short overtone of a wooden bar, a soft
     * felt attack and a short room tail.
     */
    fun impact(): ShortArray {
        val out = FloatArray(seconds(0.9))
        val f = 523.25
        partial(out, 0.0, f, 1.0, 0.32, attack = 0.0015)
        partial(out, 0.0, f * 3.98, 0.22, 0.06, attack = 0.001)
        partial(out, 0.0, f * 9.1, 0.05, 0.02, attack = 0.001)
        noiseBurst(out, 0.0, amp = 0.12, decay = 0.004, cutoff = 2500.0, seed = 3)
        reverb(out, mix = 0.18)
        return finish(out, peak = 0.9)
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

    /** A few milliseconds of low-passed noise: the soft contact of a mallet. */
    private fun noiseBurst(out: FloatArray, start: Double, amp: Double, decay: Double, cutoff: Double, seed: Int) {
        val rnd = Random(seed)
        val filter = Svf()
        val first = seconds(start)
        for (k in 0 until min(out.size - first, seconds(decay * 8))) {
            val t = k.toDouble() / SAMPLE_RATE
            out[first + k] += (amp * exp(-t / decay) * filter.lowpass(rnd.nextDouble(-1.0, 1.0), cutoff, q = 0.7)).toFloat()
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
