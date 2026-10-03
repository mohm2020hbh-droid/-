package com.carom.core.game

import com.carom.core.level.Clock
import com.carom.core.level.Element
import com.carom.core.level.ElementKind
import com.carom.core.level.LevelDefaults
import com.carom.core.level.Shape
import com.carom.core.level.Wave
import com.carom.core.physics.Segment
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

/**
 * A level [Element] while the level is being played: where it is now, whether it is on, how many hits it has
 * left, and (for a barrier) the collision segments that follow it around. [reset] puts all of it back the way
 * the level starts, which is what a retry does.
 *
 * The runtime is allocated once per session; playing allocates nothing here.
 */
class ElementRuntime(val data: Element, val index: Int) {
    var x = data.x
        private set
    var y = data.y
        private set

    /** Degrees, clockwise. */
    var rotation = data.rotation
        private set
    var sx = data.scaleX
        private set
    var sy = data.scaleY
        private set

    /** Off = ignored by the ball (a barrier switched off, a broken one). */
    var active = data.active
        private set

    /** Destructible: hits left before it breaks. */
    var hitsLeft = 0
        private set

    /** Ball container: it has already let its balls out. */
    var spent = false

    /** The velocity of the element's centre (units/s) and its turn (radians/s), for bouncing off a moving barrier. */
    var vx = 0.0
        private set
    var vy = 0.0
        private set
    var omega = 0.0
        private set

    /** A barrier's collision segments (four for a rectangle, a ring of them for a circle). Empty for a zone. */
    val segments: List<Segment>
    private val rounding: Double

    // The sine and cosine of the current rotation, kept up to date so that the per-step tests need no trigonometry.
    private var cosR = 1.0
    private var sinR = 0.0
    private val cornerX = DoubleArray(4)
    private val cornerY = DoubleArray(4)

    private val place = DoubleArray(2)

    init {
        val physical = data.physical
        rounding = if (physical) min(LevelDefaults.BLOCK_ROUNDING, min(data.scaleX, data.scaleY) / 2 * 0.999) else 0.0
        val count = if (!physical) 0 else if (data.shape == Shape.RECT) 4 else RING
        segments = List(count) { Segment(0.0, 0.0, 0.0, 0.0, rounding).also { s -> s.owner = index } }
        reset()
    }

    val radius: Double get() = sx / 2

    /** Back to the level's start. */
    fun reset() {
        x = data.x
        y = data.y
        rotation = data.rotation
        sx = data.scaleX
        sy = data.scaleY
        active = data.active
        hitsLeft = if (data.kind == ElementKind.DESTRUCTIBLE) maxOf(1, data.value.toInt()) else 0
        spent = false
        vx = 0.0
        vy = 0.0
        omega = 0.0
        if (data.animated) pose(0.0, 0.0, 0.0)
        updateSegments()
    }

    fun setActive(on: Boolean) {
        active = on
        for (s in segments) s.enabled = on
    }

    /** A destructible loses a hit; returns true if that broke it. */
    fun hit(): Boolean {
        if (data.kind != ElementKind.DESTRUCTIBLE || !active) return false
        hitsLeft--
        if (hitsLeft > 0) return false
        setActive(false)
        return true
    }

    /**
     * Moves the element to where it is now; [t] is game time (since the first throw) and [levelTime] is the time since the level
     * began (a motion on the level clock follows that one); [dt] is the time since the last call, to get its velocity.
     */
    fun animate(t: Double, levelTime: Double, dt: Double) {
        if (!data.animated) return
        pose(t, levelTime, dt)
        updateSegments()
    }

    /** Whether the element moves on the level clock, so it keeps going while the player aims. */
    val runsOnLevelClock: Boolean get() = data.moving?.clock == Clock.LEVEL

    private fun pose(t: Double, levelTime: Double, dt: Double) {
        val oldX = x
        val oldY = y
        val oldRot = rotation
        data.moving?.let { m ->
            m.at(if (m.clock == Clock.LEVEL) levelTime else t, data.x, data.y, place)
            x = place[0]
            y = place[1]
        }
        data.snap?.let { g ->
            if (g.x > 0.0) x = round(x / g.x) * g.x
            if (g.y > 0.0) y = round(y / g.y) * g.y
        }
        data.scaling?.let { s ->
            val f = wave(s.wave, t / s.period + s.phase)
            sx = data.scaleX + (s.toX - data.scaleX) * f
            sy = data.scaleY + (s.toY - data.scaleY) * f
        }
        data.rotating?.let { r -> rotation = data.rotation + r.phase + r.speed * t }
        if (dt > 0.0) {
            vx = (x - oldX) / dt
            vy = (y - oldY) / dt
            omega = Math.toRadians(rotation - oldRot) / dt
        } else {
            vx = 0.0
            vy = 0.0
            omega = 0.0
        }
    }

    /** Whether (px, py) is inside the element's shape. */
    fun contains(px: Double, py: Double): Boolean {
        val dx = px - x
        val dy = py - y
        if (data.shape == Shape.CIRCLE) return hypot(dx, dy) <= radius
        return abs(dx * cosR + dy * sinR) <= sx / 2 && abs(-dx * sinR + dy * cosR) <= sy / 2
    }

    /** Whether the straight path from (x0, y0) to (x1, y1) touches the element's shape (so a fast ball cannot skip over it). */
    fun crosses(x0: Double, y0: Double, x1: Double, y1: Double): Boolean {
        if (data.shape == Shape.CIRCLE) return distanceToSegment(x, y, x0, y0, x1, y1) <= radius
        val ax = x0 - x
        val ay = y0 - y
        val bx = x1 - x
        val by = y1 - y
        return segmentHitsBox(
            ax * cosR + ay * sinR, -ax * sinR + ay * cosR,
            bx * cosR + by * sinR, -bx * sinR + by * cosR,
            sx / 2, sy / 2,
        )
    }

    /** ([vx0], [vy0]) turned with the element's rotation, written into [out]. */
    fun rotated(vx0: Double, vy0: Double, out: DoubleArray) {
        out[0] = vx0 * cosR - vy0 * sinR
        out[1] = vx0 * sinR + vy0 * cosR
    }

    private fun updateSegments() {
        val r = Math.toRadians(rotation)
        cosR = cos(r)
        sinR = sin(r)
        if (segments.isEmpty()) return
        val c = cosR
        val s = sinR
        if (data.shape == Shape.RECT) {
            val hw = sx / 2 - rounding
            val hh = sy / 2 - rounding
            cornerX[0] = -hw; cornerX[1] = hw; cornerX[2] = hw; cornerX[3] = -hw
            cornerY[0] = -hh; cornerY[1] = -hh; cornerY[2] = hh; cornerY[3] = hh
            for (i in 0 until 4) {
                val j = (i + 1) % 4
                segments[i].set(
                    x + cornerX[i] * c - cornerY[i] * s, y + cornerX[i] * s + cornerY[i] * c,
                    x + cornerX[j] * c - cornerY[j] * s, y + cornerX[j] * s + cornerY[j] * c,
                )
            }
        } else {
            val rad = sx / 2 - rounding
            for (i in 0 until RING) {
                val a0 = 2 * Math.PI * i / RING
                val a1 = 2 * Math.PI * (i + 1) / RING
                segments[i].set(x + cos(a0) * rad, y + sin(a0) * rad, x + cos(a1) * rad, y + sin(a1) * rad)
            }
        }
        for (seg in segments) seg.enabled = active
    }

    private companion object {
        const val RING = 14

        fun wave(w: Wave, u: Double): Double = when (w) {
            Wave.SINE -> 0.5 - 0.5 * cos(2 * Math.PI * u)
            Wave.LINEAR -> {
                val fr = u - floor(u)
                if (fr < 0.5) 2 * fr else 2 * (1 - fr)
            }
            Wave.ONCE -> {
                val v = u.coerceIn(0.0, 1.0)
                v * v * (3 - 2 * v)
            }
        }

        fun distanceToSegment(px: Double, py: Double, x0: Double, y0: Double, x1: Double, y1: Double): Double {
            val dx = x1 - x0
            val dy = y1 - y0
            val len2 = dx * dx + dy * dy
            val t = if (len2 > 0.0) (((px - x0) * dx + (py - y0) * dy) / len2).coerceIn(0.0, 1.0) else 0.0
            return hypot(px - (x0 + dx * t), py - (y0 + dy * t))
        }

        /** Slab test: does the segment (a → b) meet the box [−hw, hw] × [−hh, hh]? (No allocation.) */
        fun segmentHitsBox(ax: Double, ay: Double, bx: Double, by: Double, hw: Double, hh: Double): Boolean {
            var t0 = 0.0
            var t1 = 1.0
            val dx = bx - ax
            if (dx == 0.0) {
                if (ax < -hw || ax > hw) return false
            } else {
                var ta = (-hw - ax) / dx
                var tb = (hw - ax) / dx
                if (ta > tb) { val s = ta; ta = tb; tb = s }
                if (ta > t0) t0 = ta
                if (tb < t1) t1 = tb
                if (t0 > t1) return false
            }
            val dy = by - ay
            if (dy == 0.0) {
                if (ay < -hh || ay > hh) return false
            } else {
                var ta = (-hh - ay) / dy
                var tb = (hh - ay) / dy
                if (ta > tb) { val s = ta; ta = tb; tb = s }
                if (ta > t0) t0 = ta
                if (tb < t1) t1 = tb
                if (t0 > t1) return false
            }
            return true
        }
    }
}
