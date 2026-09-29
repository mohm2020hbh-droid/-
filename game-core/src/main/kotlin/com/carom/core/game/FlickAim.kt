package com.carom.core.game

import kotlin.math.hypot
import kotlin.math.min

/**
 * Hold-and-throw control, in world coordinates. The player holds the ball and moves it around its
 * launch zone with a finger (the ball follows the finger), then throws it with a stroke: the ball
 * flies the way the stroke went, right for a stroke to the right, up for a stroke upwards.
 *
 * A stroke starts where the finger last rested or turned back, so the player can move the ball
 * into place, pause, and then swipe: only the swipe counts. Power comes from the stroke's length
 * (a short stroke is a soft throw, [maxStroke] or more is full power), plus a little extra when the
 * finger is moving fast as it lets go. Letting go of a resting finger throws nothing: the ball
 * simply stays where it was put.
 */
class FlickAim(
    private val maxStroke: Double,
    /** A finger that stays within this distance for [REST_TIME] is resting. */
    private val restDistance: Double,
    /** Strokes weaker than this are not throws, so a tap or a small nudge never fires the ball. */
    private val minPower: Double = 0.15,
    /** Finger speed at release (world units per second) where the flick boost begins. */
    private val flickStart: Double = Double.POSITIVE_INFINITY,
    /** Finger speed at which the flick boost is complete. */
    private val flickFull: Double = Double.POSITIVE_INFINITY,
    /** Power a fast flick adds on top of the stroke's own power. */
    private val flickBoost: Double = 0.25,
) {
    /** A throw: a unit direction and a power in 0..1. */
    class Throw(val dirX: Double, val dirY: Double, val power: Double)

    var isActive = false
        private set

    private var downX = 0.0
    private var downY = 0.0
    private var fingerX = 0.0
    private var fingerY = 0.0
    private var lastTime = 0.0

    // Where the current stroke started, and its farthest point so far (to notice a turn back).
    private var anchorX = 0.0
    private var anchorY = 0.0
    private var farX = 0.0
    private var farY = 0.0
    private var farDistance = 0.0

    // Where the finger has been keeping still, and since when.
    private var stillX = 0.0
    private var stillY = 0.0
    private var stillSince = 0.0

    // The finger's recent positions (a ring buffer), to measure how fast it moves at release.
    private val sampleX = DoubleArray(SAMPLES)
    private val sampleY = DoubleArray(SAMPLES)
    private val sampleT = DoubleArray(SAMPLES)
    private var sampleCount = 0
    private var sampleNext = 0

    /** Starts holding the ball with the finger at (x, y) at [time] (seconds, any clock). */
    fun begin(x: Double, y: Double, time: Double) {
        isActive = true
        downX = x
        downY = y
        fingerX = x
        fingerY = y
        lastTime = time
        startStroke(x, y)
        stillX = x
        stillY = y
        stillSince = time
        sampleCount = 0
        sampleNext = 0
        record(x, y, time)
    }

    fun move(x: Double, y: Double, time: Double) {
        if (!isActive) return
        // No news from the finger for a while means it was resting where it last was.
        if (time - lastTime >= REST_TIME) startStroke(fingerX, fingerY)
        fingerX = x
        fingerY = y
        lastTime = time
        record(x, y, time)

        if (hypot(x - stillX, y - stillY) > restDistance) {
            stillX = x
            stillY = y
            stillSince = time
        } else if (time - stillSince >= REST_TIME) {
            startStroke(x, y)
        }

        val d = hypot(x - anchorX, y - anchorY)
        if (d >= farDistance) {
            farDistance = d
            farX = x
            farY = y
        } else if (farDistance - d > restDistance * TURN_FACTOR) {
            // Coming back: a new stroke starts where the finger turned.
            anchorX = farX
            anchorY = farY
            farDistance = hypot(x - farX, y - farY)
            farX = x
            farY = y
        }
    }

    fun cancel() {
        isActive = false
    }

    /** How far the finger is from where it went down: the ball follows it by this much. */
    val offsetX: Double get() = fingerX - downX
    val offsetY: Double get() = fingerY - downY

    val strokeLength: Double get() = hypot(fingerX - anchorX, fingerY - anchorY)

    /** 0..1 from the current stroke's length alone. */
    val power: Double get() = (strokeLength / maxStroke).coerceIn(0.0, 1.0)

    /** The current stroke's direction (a unit vector); zero before the finger moves. */
    val dirX: Double get() = strokeLength.let { if (it > 0.0) (fingerX - anchorX) / it else 0.0 }
    val dirY: Double get() = strokeLength.let { if (it > 0.0) (fingerY - anchorY) / it else 0.0 }

    /** Whether letting go now would throw the ball. */
    val isThrowReady: Boolean get() = isActive && power >= minPower

    /**
     * How fast the finger was moving just before [time], in world units per second: distance
     * over the last [FLICK_WINDOW] seconds. A finger that has come to rest measures 0.
     */
    fun fingerSpeed(time: Double): Double {
        var oldest = -1
        for (k in 1..sampleCount) {
            val i = (sampleNext - k + SAMPLES) % SAMPLES
            if (time - sampleT[i] > FLICK_WINDOW) break
            oldest = i
        }
        if (oldest < 0) return 0.0
        val span = time - sampleT[oldest]
        if (span < MIN_SPAN) return 0.0
        return hypot(fingerX - sampleX[oldest], fingerY - sampleY[oldest]) / span
    }

    /**
     * The finger lifts at (x, y) at [time]. Returns the throw, or null when the stroke was too
     * weak (the ball just stays where it was put).
     */
    fun release(x: Double, y: Double, time: Double): Throw? {
        if (!isActive) return null
        move(x, y, time)
        isActive = false
        if (power < minPower) return null
        val speed = fingerSpeed(time)
        val flick = if (speed <= flickStart) 0.0 else ((speed - flickStart) / (flickFull - flickStart)).coerceIn(0.0, 1.0)
        return Throw(dirX, dirY, min(1.0, power + flickBoost * flick))
    }

    private fun startStroke(x: Double, y: Double) {
        anchorX = x
        anchorY = y
        farX = x
        farY = y
        farDistance = 0.0
    }

    private fun record(x: Double, y: Double, time: Double) {
        sampleX[sampleNext] = x
        sampleY[sampleNext] = y
        sampleT[sampleNext] = time
        sampleNext = (sampleNext + 1) % SAMPLES
        sampleCount = min(sampleCount + 1, SAMPLES)
    }

    companion object {
        /** A finger still for this long (seconds) is resting, and the next movement is a new stroke. */
        const val REST_TIME = 0.12

        /** Moving back this many rest distances towards the stroke's start begins a new stroke. */
        private const val TURN_FACTOR = 3.0

        private const val SAMPLES = 16

        /** How far back the release speed looks. */
        private const val FLICK_WINDOW = 0.08

        /** Shorter spans than this are too noisy to measure a speed from. */
        private const val MIN_SPAN = 0.012
    }
}
