package com.carom.core.game

import kotlin.math.hypot
import kotlin.math.min

/**
 * Drag-to-launch aiming, in world coordinates. The player puts a finger on the ball (anywhere in
 * its launch zone), drags it the way it should go and lets go: the ball flies in the direction of
 * the drag. Drag right and it goes right, drag up and it goes up.
 *
 * Power comes from how far the finger travelled (a short drag is a soft shot, [maxDrag] or more
 * is full power), plus a small boost when the finger is still moving fast as it lets go (a flick).
 * Dragging, pausing and then letting go gives exactly the power of the distance, so careful shots
 * stay repeatable.
 *
 * The drag is measured from where the finger first touched, not from the ball's centre, so a
 * touch off the ball's centre doesn't produce a jump.
 */
class DragAim(
    private val maxDrag: Double,
    /** Drags weaker than this are a cancel, so a tap never fires the ball. */
    private val minPower: Double = 0.12,
    /** Finger speed at release (world units per second) where the flick boost begins. */
    private val flickStart: Double = Double.POSITIVE_INFINITY,
    /** Finger speed at which the flick boost is complete. */
    private val flickFull: Double = Double.POSITIVE_INFINITY,
    /** Power a fast flick adds on top of the drag distance's power. */
    private val flickBoost: Double = 0.25,
) {
    var isActive = false
        private set

    private var startX = 0.0
    private var startY = 0.0
    private var currentX = 0.0
    private var currentY = 0.0

    // The finger's recent positions (a ring buffer), to measure how fast it moves at release.
    private val sampleX = DoubleArray(SAMPLES)
    private val sampleY = DoubleArray(SAMPLES)
    private val sampleT = DoubleArray(SAMPLES)
    private var sampleCount = 0
    private var sampleNext = 0

    /**
     * Starts aiming if the touch at (x, y) is within [grabRadius] of the ball at (ballX, ballY).
     * [time] is in seconds on any clock, used only to measure flicks.
     */
    fun tryBegin(x: Double, y: Double, ballX: Double, ballY: Double, grabRadius: Double, time: Double = 0.0): Boolean {
        if (hypot(x - ballX, y - ballY) > grabRadius) return false
        isActive = true
        startX = x
        startY = y
        sampleCount = 0
        sampleNext = 0
        drag(x, y, time)
        return true
    }

    fun drag(x: Double, y: Double, time: Double = 0.0) {
        if (!isActive) return
        currentX = x
        currentY = y
        sampleX[sampleNext] = x
        sampleY[sampleNext] = y
        sampleT[sampleNext] = time
        sampleNext = (sampleNext + 1) % SAMPLES
        sampleCount = min(sampleCount + 1, SAMPLES)
    }

    fun cancel() {
        isActive = false
    }

    val dragLength: Double get() = hypot(currentX - startX, currentY - startY)

    /** 0..1 from the drag distance alone. */
    val power: Double get() = (dragLength / maxDrag).coerceIn(0.0, 1.0)

    /** Launch direction: the direction of the drag (a unit vector). Zero until the finger moves. */
    val dirX: Double get() = dragLength.let { if (it > 0.0) (currentX - startX) / it else 0.0 }
    val dirY: Double get() = dragLength.let { if (it > 0.0) (currentY - startY) / it else 0.0 }

    /** Whether letting go now would fire the ball. */
    val isShotReady: Boolean get() = isActive && power >= minPower

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
        return hypot(currentX - sampleX[oldest], currentY - sampleY[oldest]) / span
    }

    /** Power of a shot let go at [time]: the drag distance's power plus any flick boost. */
    fun launchPower(time: Double): Double {
        val speed = fingerSpeed(time)
        val flick = if (speed <= flickStart) 0.0 else ((speed - flickStart) / (flickFull - flickStart)).coerceIn(0.0, 1.0)
        return min(1.0, power + flickBoost * flick)
    }

    /**
     * Ends aiming with the finger lifted at (x, y) and fires [session] if the drag was long
     * enough. Returns whether the ball was launched.
     */
    fun release(session: GameSession, x: Double = currentX, y: Double = currentY, time: Double = 0.0): Boolean {
        if (!isActive) return false
        drag(x, y, time)
        isActive = false
        return power >= minPower && session.launch(dirX, dirY, launchPower(time))
    }

    private companion object {
        const val SAMPLES = 16

        /** How far back the release speed looks. */
        const val FLICK_WINDOW = 0.08

        /** Shorter spans than this are too noisy to measure a speed from. */
        const val MIN_SPAN = 0.012
    }
}
