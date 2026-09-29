package com.carom.game.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * The game's sounds, synthesised at start-up instead of shipped as files: a few decaying
 * sine partials each, which keeps them soft, clean and tiny.
 */
object Synth {
    const val SAMPLE_RATE = 44100

    /**
     * The ball meeting a wall: a short, rounded "tock" like a marble on polished wood. A low
     * fundamental that dips in pitch as it strikes, two quieter inharmonic partials for body, and
     * a very short breath of noise for the contact itself.
     */
    fun impact(): ShortArray {
        val n = (0.3 * SAMPLE_RATE).toInt()
        val out = FloatArray(n)
        val base = 392.0
        val partials = arrayOf(
            doubleArrayOf(1.0, 1.0, 0.11),
            doubleArrayOf(2.32, 0.28, 0.045),
            doubleArrayOf(4.25, 0.1, 0.02),
        )
        for ((ratio, amp, decay) in partials) {
            var phase = 0.0
            for (i in 0 until n) {
                val t = i.toDouble() / SAMPLE_RATE
                val f = base * ratio * (1.0 + 0.04 * exp(-t / 0.012))
                phase += 2 * PI * f / SAMPLE_RATE
                out[i] += (amp * exp(-t / decay) * sin(phase)).toFloat()
            }
        }
        val rnd = Random(3)
        var low = 0.0
        for (i in 0 until min(n, SAMPLE_RATE / 50)) {
            val t = i.toDouble() / SAMPLE_RATE
            low += 0.35 * (rnd.nextDouble(-1.0, 1.0) - low)
            out[i] += (0.18 * exp(-t / 0.004) * low).toFloat()
        }
        return finish(out, peak = 0.9, attackSeconds = 0.0015)
    }

    /**
     * The ball breaking: a brief crack of filtered noise with a soft low knock under it, then a
     * scatter of small bright chimes, dense at first and thinning out, like porcelain pieces
     * settling. Short and restrained rather than a crash.
     */
    fun shatter(): ShortArray {
        val n = (0.75 * SAMPLE_RATE).toInt()
        val out = FloatArray(n)
        val rnd = Random(7)

        // The crack: noise, band-limited to its bright middle, fading in a few tens of ms.
        var lp = 0.0
        var hpIn = 0.0
        var hpOut = 0.0
        for (i in 0 until n / 4) {
            val t = i.toDouble() / SAMPLE_RATE
            val x = rnd.nextDouble(-1.0, 1.0)
            lp += 0.55 * (x - lp)
            hpOut = 0.8 * (hpOut + lp - hpIn)
            hpIn = lp
            out[i] += (0.55 * exp(-t / 0.035) * hpOut).toFloat()
        }
        // The knock.
        for (i in 0 until n / 3) {
            val t = i.toDouble() / SAMPLE_RATE
            out[i] += (0.35 * exp(-t / 0.05) * sin(2 * PI * 170.0 * t)).toFloat()
        }
        // The pieces.
        repeat(14) {
            val start = 0.012 + 0.4 * rnd.nextDouble().pow(1.7)
            val f = 2300.0 + 3200.0 * rnd.nextDouble()
            val amp = (0.1 + 0.16 * rnd.nextDouble()) * (1.0 - start / 0.6)
            val decay = 0.02 + 0.05 * rnd.nextDouble()
            val first = (start * SAMPLE_RATE).toInt()
            val last = min(n, first + (decay * 8 * SAMPLE_RATE).toInt())
            for (i in first until last) {
                val t = (i - first).toDouble() / SAMPLE_RATE
                val env = amp * exp(-t / decay) * min(1.0, t / 0.001)
                out[i] += (env * (sin(2 * PI * f * t) + 0.35 * sin(2 * PI * f * 2.76 * t))).toFloat()
            }
        }
        return finish(out, peak = 0.85, attackSeconds = 0.001)
    }

    /** Fades in over [attackSeconds] (no click), scales to [peak] and converts to 16-bit PCM. */
    private fun finish(samples: FloatArray, peak: Double, attackSeconds: Double): ShortArray {
        val attack = max(1, (attackSeconds * SAMPLE_RATE).toInt())
        for (i in 0 until min(attack, samples.size)) samples[i] *= i.toFloat() / attack
        // A short fade at the end so the tail never stops on a click.
        val tail = min(samples.size, SAMPLE_RATE / 100)
        for (k in 0 until tail) samples[samples.size - 1 - k] *= k.toFloat() / tail
        val max = samples.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-6f)
        val gain = (peak / max * Short.MAX_VALUE).toFloat()
        return ShortArray(samples.size) { (samples[it] * gain).toInt().toShort() }
    }
}
