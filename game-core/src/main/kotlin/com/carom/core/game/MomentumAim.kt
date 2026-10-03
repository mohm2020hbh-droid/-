package com.carom.core.game

import kotlin.math.hypot
import kotlin.math.min

/**
 * Hold, move, let go: the player's control of the ball before the throw, in world coordinates.
 *
 * The finger takes hold of the ball (anywhere in its control zone) and the ball goes where the finger takes it,
 * exactly, keeping the place under the finger where it was grabbed. There is no pull and no aiming: the throw is the
 * ball's own movement. When the finger lets go the ball is not stopped: **it leaves with the velocity it had just
 * before**, in the direction it was travelling, and from then on it is physics and never follows the finger again.
 *
 *     finger movement → ball movement → release velocity → physics
 *
 * A slow movement is a weak throw, a medium one a medium throw, a fast one a strong throw, and any real movement,
 * however small, throws: there is no dead zone and no minimum pull. The only movement that throws nothing is none at
 * all (a touch that never moved, or a ball that rested before the finger lifted): its velocity is 0, so it stays
 * where it was put.
 *
 * The velocity is the movement the finger gave the ball: how far the ball was taken over the last [window] seconds
 * before the release, over that time, so a ball that was held still before the finger lifted has stopped. It is the
 * movement the ball was *taken* through, not where walls and the zone let it get: the zone is not drawn, so a quick
 * flick that runs past its edge must not lose its speed to a boundary nobody can see. (The caller still puts the ball
 * only where it can be, at [wantX], [wantY] as near as the zone and the walls allow.) The class knows nothing about
 * walls or the zone.
 *
 * Time is in seconds on one clock; events carry their own time.
 */
class MomentumAim(
    /** How far back before the release the ball's speed is measured. */
    private val window: Double = 0.08,
    /** Movements slower than this (world units per second) are no movement: a ball this slow would be dead at once. */
    private val minSpeed: Double = 0.0,
    /** World velocity per unit of the ball's own (1 = the ball leaves exactly as fast as it moved). */
    private val gain: Double = 1.0,
) {
    /** A throw: the velocity the ball leaves with, in world units per second. */
    class Release(val vx: Double, val vy: Double) {
        val speed: Double get() = hypot(vx, vy)
    }

    var isActive = false
        private set

    /** Where the ball should be now: the finger plus the way the ball sat under it when it was grabbed. */
    var wantX = 0.0
        private set
    var wantY = 0.0
        private set

    private var grabX = 0.0
    private var grabY = 0.0

    // Where the finger took the ball, recently (a ring buffer), to measure how fast it was going at release.
    private val sampleX = DoubleArray(SAMPLES)
    private val sampleY = DoubleArray(SAMPLES)
    private val sampleT = DoubleArray(SAMPLES)
    private var sampleCount = 0
    private var sampleNext = 0
    private val at = DoubleArray(2)

    /** Takes hold of the ball (at [ballX], [ballY]) with the finger at ([x], [y]) at [time]. */
    fun begin(x: Double, y: Double, ballX: Double, ballY: Double, time: Double) {
        isActive = true
        grabX = ballX - x
        grabY = ballY - y
        wantX = ballX
        wantY = ballY
        sampleCount = 0
        sampleNext = 0
        record(ballX, ballY, time)
    }

    /** The finger moved to ([x], [y]) at [time]: the ball is wanted at [wantX], [wantY]. */
    fun move(x: Double, y: Double, time: Double) {
        if (!isActive) return
        wantX = x + grabX
        wantY = y + grabY
        record(wantX, wantY, time)
    }

    private fun record(x: Double, y: Double, time: Double) {
        val last = (sampleNext - 1 + SAMPLES) % SAMPLES
        // The clock never runs backwards, so the same time is never counted twice.
        val t = if (sampleCount > 0 && time < sampleT[last]) sampleT[last] else time
        sampleX[sampleNext] = x
        sampleY[sampleNext] = y
        sampleT[sampleNext] = t
        sampleNext = (sampleNext + 1) % SAMPLES
        sampleCount = min(sampleCount + 1, SAMPLES)
    }

    fun cancel() {
        isActive = false
    }

    /**
     * The velocity the finger gave the ball at [time]: its displacement over the last [window] seconds (less if it has not been held that
     * long) divided by that time. A ball that came to rest measures 0. Written to [out] as (vx, vy).
     */
    fun velocity(time: Double, out: DoubleArray) {
        out[0] = 0.0
        out[1] = 0.0
        if (sampleCount == 0) return
        val oldest = (sampleNext - sampleCount + SAMPLES) % SAMPLES
        val newest = (sampleNext - 1 + SAMPLES) % SAMPLES
        val now = maxOf(time, sampleT[newest])
        val span = min(window, now - sampleT[oldest])
        if (span < MIN_SPAN) return
        positionAt(now, oldest)
        val x1 = at[0]
        val y1 = at[1]
        positionAt(now - span, oldest)
        out[0] = (x1 - at[0]) / span * gain
        out[1] = (y1 - at[1]) / span * gain
    }

    /** Where the ball was being taken at [t] (before the first sample it is where it started; after the last, where it ended). */
    private fun positionAt(t: Double, oldest: Int) {
        var a = oldest
        if (t <= sampleT[a]) {
            at[0] = sampleX[a]
            at[1] = sampleY[a]
            return
        }
        for (k in 1 until sampleCount) {
            val b = (oldest + k) % SAMPLES
            if (t <= sampleT[b]) {
                val span = sampleT[b] - sampleT[a]
                val f = if (span > 0.0) (t - sampleT[a]) / span else 1.0
                at[0] = sampleX[a] + (sampleX[b] - sampleX[a]) * f
                at[1] = sampleY[a] + (sampleY[b] - sampleY[a]) * f
                return
            }
            a = b
        }
        at[0] = sampleX[a]
        at[1] = sampleY[a]
    }

    private val v = DoubleArray(2)

    /**
     * The finger lifts at [time]. Returns the velocity the ball leaves with, or null when it was not moving (it simply
     * stays where it was put). After this the aim is idle: nothing follows the finger any more.
     */
    fun release(time: Double): Release? {
        if (!isActive) return null
        velocity(time, v)
        isActive = false
        val speed = hypot(v[0], v[1])
        return if (speed > minSpeed && speed > 0.0) Release(v[0], v[1]) else null
    }

    private companion object {
        /** Enough for the window even at a very high touch rate. */
        const val SAMPLES = 48

        /** Shorter spans than this are too short to measure a speed from (a touch that lifts at once). */
        const val MIN_SPAN = 0.008
    }
}
