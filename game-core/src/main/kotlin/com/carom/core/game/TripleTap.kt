package com.carom.core.game

import kotlin.math.hypot

/**
 * Three quick taps, one straight after another, anywhere on the screen: the player's way to start the attempt over
 * without a button. It only watches the finger; what a triple tap does (put the ball back at its start) is the
 * caller's.
 *
 * A tap is a touch that lifts within [maxHold] seconds of landing without having travelled more than [slop]. Each
 * tap must land within [maxGap] seconds of the one before lifting, or the count starts again from that tap. A touch
 * that is held, or moves, is not a tap and forgets the taps before it, so a throw is never taken for one.
 * Coordinates are in any one unit (dp), and time is in seconds on one clock.
 */
class TripleTap(
    private val maxHold: Double = 0.30,
    private val maxGap: Double = 0.45,
    private val slop: Double = 14.0,
) {
    private var taps = 0
    private var downTime = 0.0
    private var downX = 0.0
    private var downY = 0.0
    private var lastUp = -1e9
    private var down = false
    private var moved = false

    /** Whether a finger landing at [time] begins a new set of taps: none counted yet, or too long since the last one lifted. */
    fun startsSet(time: Double): Boolean = taps == 0 || time - lastUp > maxGap

    /** A finger lands at ([x], [y]) at [time]. */
    fun down(x: Double, y: Double, time: Double) {
        if (time - lastUp > maxGap) taps = 0
        down = true
        moved = false
        downTime = time
        downX = x
        downY = y
    }

    /** The finger moves to ([x], [y]): a touch that travels is a drag, not a tap. */
    fun move(x: Double, y: Double) {
        if (down && hypot(x - downX, y - downY) > slop) moved = true
    }

    /**
     * The finger lifts at [time]. True exactly when this tap is the third in a row: the count starts again after it, so a
     * fourth tap begins a new set.
     */
    fun up(time: Double): Boolean {
        if (!down) return false
        down = false
        if (moved || time - downTime > maxHold) {
            taps = 0
            lastUp = -1e9
            return false
        }
        taps++
        lastUp = time
        if (taps >= 3) {
            taps = 0
            lastUp = -1e9
            return true
        }
        return false
    }

    /** The touch was taken away (a system gesture, a second finger): forget everything. */
    fun cancel() {
        down = false
        taps = 0
        lastUp = -1e9
    }
}
