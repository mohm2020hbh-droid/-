package com.carom.core.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Finds the beats in a piece of music, to feed the pictures (a pulse in the exit's glow, a lift in the
 * background). It only ever drives visuals: nothing in the physics or the rules reads it.
 *
 * The method is an onset detector. The signal is split into a low band (kicks, bass) and the rest (snares,
 * hats, plucks); each is measured as its energy in short frames; a beat is where the energy of either band
 * rises sharply above what it has been lately. Beats are picked from that curve where it is a local maximum,
 * above an adaptive threshold (the recent average plus a multiple of the recent spread), and never closer
 * together than [minGap] seconds.
 */
object BeatDetector {

    class Beats(val times: DoubleArray, val strengths: DoubleArray) {
        val count: Int get() = times.size

        /** Beats per minute from the median gap between beats, or 0 with fewer than 4 beats. */
        fun tempo(): Double {
            if (times.size < 4) return 0.0
            val gaps = DoubleArray(times.size - 1) { times[it + 1] - times[it] }
            gaps.sort()
            val median = gaps[gaps.size / 2]
            return if (median > 0) 60.0 / median else 0.0
        }
    }

    fun detect(
        samples: FloatArray,
        sampleRate: Int,
        frame: Int = 1024,
        hop: Int = 512,
        sensitivity: Double = 1.4,
        minGap: Double = 0.2,
    ): Beats {
        val frames = if (samples.size < frame) 0 else (samples.size - frame) / hop + 1
        if (frames < 3) return Beats(DoubleArray(0), DoubleArray(0))

        // Split into low band (one-pole low-pass near 200 Hz) and the rest, then measure each frame's energy.
        val k = 1.0 - exp(-2.0 * Math.PI * 200.0 / sampleRate)
        val low = FloatArray(samples.size)
        var y = 0.0
        for (i in samples.indices) {
            y += k * (samples[i] - y)
            low[i] = y.toFloat()
        }
        val lowE = DoubleArray(frames)
        val highE = DoubleArray(frames)
        for (f in 0 until frames) {
            var l = 0.0
            var h = 0.0
            val start = f * hop
            for (i in start until start + frame) {
                l += low[i] * low[i]
                val r = samples[i] - low[i]
                h += r * r
            }
            lowE[f] = sqrt(l / frame)
            highE[f] = sqrt(h / frame)
        }

        // Onset strength: how much each band's energy rose since the frame before.
        val onset = DoubleArray(frames)
        for (f in 1 until frames) onset[f] = max(0.0, lowE[f] - lowE[f - 1]) + max(0.0, highE[f] - highE[f - 1])

        // Peaks above the local threshold.
        val frameSeconds = hop.toDouble() / sampleRate
        val around = max(1, (0.75 / frameSeconds).toInt())
        val gapFrames = max(1, (minGap / frameSeconds).toInt())
        val times = ArrayList<Double>()
        val strengths = ArrayList<Double>()
        var lastBeat = -gapFrames
        for (f in 1 until frames - 1) {
            if (onset[f] < onset[f - 1] || onset[f] < onset[f + 1]) continue
            val from = max(0, f - around)
            val to = minOf(frames - 1, f + around)
            var mean = 0.0
            for (i in from..to) mean += onset[i]
            mean /= (to - from + 1)
            var spread = 0.0
            for (i in from..to) spread += abs(onset[i] - mean)
            spread /= (to - from + 1)
            val threshold = mean + sensitivity * spread + 1e-6
            if (onset[f] > threshold && f - lastBeat >= gapFrames) {
                times.add((f * hop + frame / 2).toDouble() / sampleRate)
                strengths.add(onset[f])
                lastBeat = f
            }
        }
        val top = strengths.maxOrNull() ?: 1.0
        return Beats(times.toDoubleArray(), DoubleArray(strengths.size) { strengths[it] / top })
    }

    /**
     * A 0..1 pulse for a picture, at [time] seconds into the music: 1 at a beat, fading away over [decay] seconds.
     * [beats] must be for the whole loop; [loopLength] wraps the time round.
     */
    fun pulse(beats: Beats, time: Double, loopLength: Double, decay: Double = 0.25): Double {
        if (beats.count == 0 || loopLength <= 0.0) return 0.0
        val t = ((time % loopLength) + loopLength) % loopLength
        // The most recent beat at or before t (binary search); the beat before the loop's first wraps from the end.
        var lo = 0
        var hi = beats.count - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (beats.times[mid] <= t) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        val since = if (found >= 0) t - beats.times[found] else t + loopLength - beats.times[beats.count - 1]
        val strength = if (found >= 0) beats.strengths[found] else beats.strengths[beats.count - 1]
        return (exp(-since / decay) * (0.5 + 0.5 * strength)).coerceIn(0.0, 1.0)
    }
}
