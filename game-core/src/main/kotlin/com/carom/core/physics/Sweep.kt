package com.carom.core.physics

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Continuous collision queries. A moving circle of radius r against a capsule of radius w is the
 * same problem as a moving point against a capsule of radius r + w, so every query below works on
 * the ball's centre travelling from P to P + D, with the time of impact t expressed as a fraction
 * of D (0 = start, 1 = end). Because impacts are solved analytically rather than by stepping and
 * testing overlap, a fast ball cannot tunnel through a thin wall.
 */
internal object Sweep {
    /** A ball this close to a surface counts as touching it rather than as penetrating it. */
    const val TOUCH_TOLERANCE = 1e-6

    /**
     * Earliest time the ball touches [seg] while moving towards it, with the contact normal
     * pointing from the wall to the ball. Returns false if there is no such contact in [0, 1].
     */
    fun circleVsSegment(
        px: Double, py: Double,
        dx: Double, dy: Double,
        r: Double,
        seg: Segment,
        out: Contact,
    ): Boolean {
        val rr = r + seg.radius
        var bestT = Double.POSITIVE_INFINITY
        var bnx = 0.0
        var bny = 0.0

        // Flat face: the ball's centre reaches the line offset by rr, within the segment's extent.
        if (seg.length > 0.0) {
            val s0 = (px - seg.ax) * seg.nx + (py - seg.ay) * seg.ny
            val side = if (s0 >= 0.0) 1.0 else -1.0
            val dist = s0 * side
            val approach = (dx * seg.nx + dy * seg.ny) * side
            if (approach < 0.0 && dist >= rr - TOUCH_TOLERANCE) {
                val t = max(0.0, (dist - rr) / -approach)
                if (t <= 1.0) {
                    val along = (px + dx * t - seg.ax) * seg.ux + (py + dy * t - seg.ay) * seg.uy
                    if (along >= 0.0 && along <= seg.length) {
                        bestT = t
                        bnx = seg.nx * side
                        bny = seg.ny * side
                    }
                }
            }
        }

        // Rounded ends: the ball's centre reaches the circle of radius rr around each endpoint.
        val ta = circleTime(px, py, dx, dy, seg.ax, seg.ay, rr, touchCounts = true)
        if (ta < bestT) {
            bestT = ta
            bnx = px + dx * ta - seg.ax
            bny = py + dy * ta - seg.ay
        }
        val tb = circleTime(px, py, dx, dy, seg.bx, seg.by, rr, touchCounts = true)
        if (tb < bestT) {
            bestT = tb
            bnx = px + dx * tb - seg.bx
            bny = py + dy * tb - seg.by
        }

        if (bestT == Double.POSITIVE_INFINITY) return false
        val len = sqrt(bnx * bnx + bny * bny)
        if (len == 0.0) return false
        out.t = bestT
        out.nx = bnx / len
        out.ny = bny / len
        return true
    }

    /** Earliest time the ball's centre enters [trigger], or +∞ if it doesn't (or is already inside). */
    fun triggerEntry(px: Double, py: Double, dx: Double, dy: Double, trigger: CircleTrigger): Double =
        circleTime(px, py, dx, dy, trigger.x, trigger.y, trigger.radius, touchCounts = false)

    /**
     * Earliest t in [0, 1] where |P + tD - C| = radius while moving inwards. A start point already
     * within [TOUCH_TOLERANCE] of the boundary yields 0 when [touchCounts]; a start point deeper
     * inside yields +∞ (penetration is resolved separately, and triggers only fire on entry).
     */
    private fun circleTime(
        px: Double, py: Double,
        dx: Double, dy: Double,
        cx: Double, cy: Double,
        radius: Double,
        touchCounts: Boolean,
    ): Double {
        val fx = px - cx
        val fy = py - cy
        val b = fx * dx + fy * dy // half of the quadratic's b term
        if (b >= 0.0) return Double.POSITIVE_INFINITY // moving away or tangent
        val a = dx * dx + dy * dy
        val c = fx * fx + fy * fy - radius * radius
        if (c <= 0.0) {
            val touching = sqrt(fx * fx + fy * fy) >= radius - TOUCH_TOLERANCE
            return if (touchCounts && touching) 0.0 else Double.POSITIVE_INFINITY
        }
        val disc = b * b - a * c
        if (disc < 0.0) return Double.POSITIVE_INFINITY
        val t = (-b - sqrt(disc)) / a
        return if (t in 0.0..1.0) t else Double.POSITIVE_INFINITY
    }

    /**
     * Distance from P to the closest point of [seg]'s centre line; writes the unit direction from
     * that point to P into [out] (falling back to the segment normal when P lies on the line).
     */
    fun distanceToSegment(px: Double, py: Double, seg: Segment, out: Contact): Double {
        var cx = seg.ax
        var cy = seg.ay
        if (seg.length > 0.0) {
            val along = ((px - seg.ax) * seg.ux + (py - seg.ay) * seg.uy).coerceIn(0.0, seg.length)
            cx = seg.ax + seg.ux * along
            cy = seg.ay + seg.uy * along
        }
        val ox = px - cx
        val oy = py - cy
        val d = sqrt(ox * ox + oy * oy)
        if (d > 0.0) {
            out.nx = ox / d
            out.ny = oy / d
        } else {
            out.nx = seg.nx
            out.ny = seg.ny
        }
        return d
    }
}
