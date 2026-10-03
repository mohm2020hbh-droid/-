package com.carom.core.game

import kotlin.math.hypot

/**
 * The reference game's touch control: the finger does not carry the ball anywhere. A swipe is read as a
 * delta, the delta times the sensibility is an impulse, and the impulse gives the ball a velocity:
 *
 * `Touch → Drag → Delta → Impulse → Velocity`
 *
 * Positions are in dp (density-independent pixels) so the same swipe feels the same on every screen; times
 * are in seconds. The class knows nothing about the ball: it turns touches into a [Result], and the
 * session decides whether the ball may take it.
 *
 * Swipes, taps and double taps are told apart by [GameTuning.swipeTolerance], [GameTuning.tapThreshold],
 * [GameTuning.tapWait] and [GameTuning.doubleTapWait].
 */
class TouchControl(var tuning: GameTuning = GameTuning.DEFAULT) {

    enum class Gesture { NONE, SWIPE, TAP, DOUBLE_TAP }

    /** What a finished touch turned out to be; for a swipe, (dx, dy) is the drag in dp. */
    class Result(val gesture: Gesture, val dx: Double, val dy: Double) {
        val length: Double get() = hypot(dx, dy)
    }

    private var down = false
    private var startX = 0.0
    private var startY = 0.0
    private var startTime = 0.0
    private var curX = 0.0
    private var curY = 0.0
    private var lastTapEnd = NEVER
    private var lastTapStart = NEVER

    val isDown: Boolean get() = down

    /** The drag so far, in dp (zero when no touch is down). */
    val deltaX: Double get() = if (down) curX - startX else 0.0
    val deltaY: Double get() = if (down) curY - startY else 0.0

    /** Whether the drag so far is long enough to be a swipe. */
    val isSwipe: Boolean get() = down && hypot(deltaX, deltaY) >= tuning.swipeTolerance

    /**
     * How hard the swipe so far would throw the ball, 0..1 (1 = the top speed). It fills the ring around the ball
     * while the finger is down, and 0 for a drag that is not yet a swipe.
     */
    val power: Double
        get() = if (isSwipe) (hypot(deltaX, deltaY) * tuning.touchSensibility / tuning.maxSpeedRef).coerceIn(0.0, 1.0) else 0.0

    /** The impulse of a drag of (dx, dy) dp, in ref units per second (mass 1: it is also the velocity change). */
    fun impulseRef(dx: Double, dy: Double): Double = hypot(dx, dy) * tuning.touchSensibility

    fun begin(x: Double, y: Double, time: Double) {
        down = true
        startX = x
        startY = y
        curX = x
        curY = y
        startTime = time
    }

    fun move(x: Double, y: Double) {
        if (!down) return
        curX = x
        curY = y
    }

    fun cancel() {
        down = false
    }

    /** The finger lifted at (x, y) at [time]. */
    fun end(x: Double, y: Double, time: Double): Result {
        if (!down) return Result(Gesture.NONE, 0.0, 0.0)
        curX = x
        curY = y
        down = false
        val dx = curX - startX
        val dy = curY - startY
        if (hypot(dx, dy) >= tuning.swipeTolerance) return Result(Gesture.SWIPE, dx, dy)
        if (time - startTime > tuning.tapThreshold) return Result(Gesture.NONE, 0.0, 0.0) // a long press, not a tap
        // A tap. A second one soon after the first (but not a bounce of the same touch) is a double tap.
        val gap = startTime - lastTapEnd
        val double = lastTapEnd != NEVER && gap <= tuning.doubleTapWait && startTime - lastTapStart >= tuning.tapWait
        if (double) {
            lastTapEnd = NEVER
            lastTapStart = NEVER
            return Result(Gesture.DOUBLE_TAP, 0.0, 0.0)
        }
        lastTapStart = startTime
        lastTapEnd = time
        return Result(Gesture.TAP, 0.0, 0.0)
    }

    private companion object {
        const val NEVER = -1e9
    }
}
