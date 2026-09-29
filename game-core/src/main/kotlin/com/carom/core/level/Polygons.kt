package com.carom.core.level

import com.carom.core.math.Vec2
import kotlin.math.abs
import kotlin.math.hypot

/** Polygon helpers for level geometry. */
object Polygons {

    /** Twice the signed area; positive when the points run counter-clockwise in y-up axes. */
    fun signedArea2(points: List<Vec2>): Double {
        var sum = 0.0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            sum += a.x * b.y - b.x * a.y
        }
        return sum
    }

    /**
     * The convex polygon [points] with every edge moved inwards by [distance], or null if the
     * polygon is not convex or is too small to shrink that far. Growing the result by [distance]
     * in every direction gives back the original outline with its corners rounded.
     */
    fun insetConvex(points: List<Vec2>, distance: Double): List<Vec2>? {
        val n = points.size
        if (n < 3) return null
        val area = signedArea2(points)
        if (abs(area) < 1e-9) return null
        val sign = if (area > 0) 1.0 else -1.0
        for (i in 0 until n) {
            val a = points[i]
            val b = points[(i + 1) % n]
            val c = points[(i + 2) % n]
            if (((b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)) * sign < -1e-9) return null
        }

        // Each edge as a line pushed inwards: a point on it and its direction.
        val px = DoubleArray(n)
        val py = DoubleArray(n)
        val dx = DoubleArray(n)
        val dy = DoubleArray(n)
        for (i in 0 until n) {
            val a = points[i]
            val b = points[(i + 1) % n]
            val len = hypot(b.x - a.x, b.y - a.y)
            if (len < 1e-9) return null
            dx[i] = (b.x - a.x) / len
            dy[i] = (b.y - a.y) / len
            px[i] = a.x - dy[i] * sign * distance
            py[i] = a.y + dx[i] * sign * distance
        }

        // Corner i of the result is where the pushed edges before and after point i meet.
        val out = ArrayList<Vec2>(n)
        for (i in 0 until n) {
            val e0 = (i + n - 1) % n
            val e1 = i
            val cross = dx[e0] * dy[e1] - dy[e0] * dx[e1]
            if (abs(cross) < 1e-9) {
                // Straight-through point: keep it on the pushed edge.
                out.add(Vec2(px[e1], py[e1]))
                continue
            }
            val t = ((px[e1] - px[e0]) * dy[e1] - (py[e1] - py[e0]) * dx[e1]) / cross
            out.add(Vec2(px[e0] + dx[e0] * t, py[e0] + dy[e0] * t))
        }

        // The shrink went too far if any edge vanished or turned around.
        for (i in 0 until n) {
            val a = out[i]
            val b = out[(i + 1) % n]
            if ((b.x - a.x) * dx[i] + (b.y - a.y) * dy[i] <= 1e-6) return null
        }
        return out
    }
}
