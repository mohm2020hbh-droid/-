package com.carom.core.physics

import kotlin.math.hypot

/**
 * A collision segment from A to B. It collides as a capsule: every point within [radius] of the
 * segment is solid, so a thick wall has rounded ends and a thin polygon edge has radius 0.
 *
 * Direction and normal are precomputed because segments are swept against thousands of times.
 * A segment can be moved with [set] (a moving or rotating obstacle) and switched off with
 * [enabled] (a destroyed or switched-off one); [owner] says whose it is: negative for level
 * geometry, otherwise the index of the level element that carries it.
 */
class Segment(
    ax: Double,
    ay: Double,
    bx: Double,
    by: Double,
    val radius: Double,
) {
    var ax = ax
        private set
    var ay = ay
        private set
    var bx = bx
        private set
    var by = by
        private set
    var length = 0.0
        private set

    /** Unit direction A→B (zero for a degenerate segment, which then collides as a point). */
    var ux = 0.0
        private set
    var uy = 0.0
        private set

    /** Unit normal (direction rotated 90°). Which side the ball is on is decided per query. */
    var nx = 0.0
        private set
    var ny = 0.0
        private set

    /** A disabled segment is invisible to the ball. */
    var enabled = true
    var owner = STATIC

    init {
        set(ax, ay, bx, by)
    }

    fun set(ax: Double, ay: Double, bx: Double, by: Double) {
        this.ax = ax
        this.ay = ay
        this.bx = bx
        this.by = by
        length = hypot(bx - ax, by - ay)
        ux = if (length > 0.0) (bx - ax) / length else 0.0
        uy = if (length > 0.0) (by - ay) / length else 0.0
        nx = -uy
        ny = ux
    }

    companion object {
        /** Owner of plain level geometry (walls, blocks, the level's edges). */
        const val STATIC = -1
    }
}

/** A circular sensor. The world reports when the ball's centre enters it; it never blocks motion. */
class CircleTrigger(val x: Double, val y: Double, val radius: Double)

/** Reusable output of a sweep query, so the hot path allocates nothing. */
class Contact {
    var t = 0.0
    var nx = 0.0
    var ny = 0.0
}
