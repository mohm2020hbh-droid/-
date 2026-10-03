package com.carom.core.math

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Immutable 2D vector used for level data and public APIs (the physics hot path uses raw doubles). */
data class Vec2(val x: Double, val y: Double) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Double) = Vec2(x * s, y * s)

    infix fun dot(o: Vec2) = x * o.x + y * o.y

    val length: Double get() = hypot(x, y)

    fun distanceTo(o: Vec2) = hypot(x - o.x, y - o.y)

    companion object {
        val ZERO = Vec2(0.0, 0.0)

        /** Unit vector pointing at [degrees], measured clockwise from +x (screen coordinates, y down). */
        fun fromDegrees(degrees: Double): Vec2 {
            val r = Math.toRadians(degrees)
            return Vec2(cos(r), sin(r))
        }
    }
}
