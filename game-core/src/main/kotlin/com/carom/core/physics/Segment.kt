package com.carom.core.physics

import kotlin.math.hypot

/**
 * A static collision segment from A to B. It collides as a capsule: every point within [radius]
 * of the segment is solid, so a thick wall has rounded ends and a thin polygon edge has radius 0.
 *
 * Direction and normal are precomputed because segments are swept against thousands of times.
 */
class Segment(
    val ax: Double,
    val ay: Double,
    val bx: Double,
    val by: Double,
    val radius: Double,
) {
    val length: Double = hypot(bx - ax, by - ay)

    /** Unit direction A→B (zero for a degenerate segment, which then collides as a point). */
    val ux: Double = if (length > 0.0) (bx - ax) / length else 0.0
    val uy: Double = if (length > 0.0) (by - ay) / length else 0.0

    /** Unit normal (direction rotated 90°). Which side the ball is on is decided per query. */
    val nx: Double = -uy
    val ny: Double = ux
}

/** A circular sensor. The world reports when the ball's centre enters it; it never blocks motion. */
class CircleTrigger(val x: Double, val y: Double, val radius: Double)

/** Reusable output of a sweep query, so the hot path allocates nothing. */
class Contact {
    var t = 0.0
    var nx = 0.0
    var ny = 0.0
}
