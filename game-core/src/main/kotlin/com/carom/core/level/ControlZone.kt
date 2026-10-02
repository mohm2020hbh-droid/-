package com.carom.core.level

import kotlin.math.hypot
import kotlin.math.max

/**
 * The control zone: where the player may pick the ball up, move it and prepare a throw before letting go, and where
 * they may take hold of it again after a throw for as long as any part of it is still inside. The ball can be moved
 * anywhere inside it and nowhere outside (its whole body stays within the zone's line), a touch outside it does not
 * pick the ball up, and a drag that goes past its edge is what prepares the throw.
 *
 * The line is drawn (dashed, quietly) but it is not a wall: it is only where the player's control stops. The ball
 * crosses it freely, with no bounce and no change of path; once the ball is entirely across it the player no longer has it.
 *
 * A zone is a region of the level in world units: a [Box] (a band across the level, a rectangle) or a [Circle].
 * The zone is the region the ball's *body* occupies, so its dashed line is where the ball's edge stops; the
 * ball's centre is kept one ball-radius inside it. Nothing here knows about walls: walls stop the ball on top
 * of the zone.
 */
sealed interface ControlZone {

    /** Whether a ball of [radius] centred at (x, y) lies entirely inside the zone. */
    fun holds(x: Double, y: Double, radius: Double): Boolean

    /** Writes into [out] the centre closest to (x, y) that keeps a ball of [radius] entirely inside the zone. */
    fun nearestCentre(x: Double, y: Double, radius: Double, out: DoubleArray)

    /** How far (x, y) is from the nearest centre a ball of [radius] can have in the zone; 0 if it is one. */
    fun distanceFromCentres(x: Double, y: Double, radius: Double): Double

    /** Whether a touch at (x, y) is in the zone, or within [margin] of its line. */
    fun reaches(x: Double, y: Double, margin: Double): Boolean

    /**
     * Whether a ball of [radius] centred at (x, y) has any part inside the zone (touching its line counts). The ball is
     * outside the zone only when this is false: fully across the line, nothing of it left inside.
     */
    fun overlaps(x: Double, y: Double, radius: Double): Boolean

    /** A rectangle: its top-left corner and its size. A band across the whole level is a box as wide as the level. */
    data class Box(val x: Double, val y: Double, val width: Double, val height: Double) : ControlZone {
        val right: Double get() = x + width
        val bottom: Double get() = y + height

        override fun holds(x: Double, y: Double, radius: Double) =
            x - radius >= this.x - EPS && x + radius <= right + EPS && y - radius >= this.y - EPS && y + radius <= bottom + EPS

        override fun nearestCentre(x: Double, y: Double, radius: Double, out: DoubleArray) {
            out[0] = axis(x, this.x, width, radius)
            out[1] = axis(y, this.y, height, radius)
        }

        override fun distanceFromCentres(x: Double, y: Double, radius: Double): Double {
            val cx = axis(x, this.x, width, radius)
            val cy = axis(y, this.y, height, radius)
            return hypot(x - cx, y - cy)
        }

        override fun reaches(x: Double, y: Double, margin: Double) =
            x >= this.x - margin && x <= right + margin && y >= this.y - margin && y <= bottom + margin

        override fun overlaps(x: Double, y: Double, radius: Double): Boolean {
            val dx = max(0.0, max(this.x - x, x - right))
            val dy = max(0.0, max(this.y - y, y - bottom))
            return hypot(dx, dy) <= radius + EPS
        }

        private fun axis(v: Double, start: Double, size: Double, radius: Double): Double =
            if (size <= 2 * radius) start + size / 2 else v.coerceIn(start + radius, start + size - radius)
    }

    /** A disc: its centre and radius. */
    data class Circle(val x: Double, val y: Double, val radius: Double) : ControlZone {
        override fun holds(x: Double, y: Double, radius: Double) = hypot(x - this.x, y - this.y) + radius <= this.radius + EPS

        override fun nearestCentre(x: Double, y: Double, radius: Double, out: DoubleArray) {
            val limit = max(0.0, this.radius - radius)
            val d = hypot(x - this.x, y - this.y)
            if (d <= limit) {
                out[0] = x
                out[1] = y
            } else {
                out[0] = this.x + (x - this.x) / d * limit
                out[1] = this.y + (y - this.y) / d * limit
            }
        }

        override fun distanceFromCentres(x: Double, y: Double, radius: Double) =
            max(0.0, hypot(x - this.x, y - this.y) - max(0.0, this.radius - radius))

        override fun reaches(x: Double, y: Double, margin: Double) = hypot(x - this.x, y - this.y) <= radius + margin

        override fun overlaps(x: Double, y: Double, radius: Double) = hypot(x - this.x, y - this.y) <= this.radius + radius + EPS
    }

    companion object {
        private const val EPS = 1e-6

        /**
         * The zone of a level that does not set one: a band across the whole level, from [above] units over the ball's
         * start down to the level's bottom edge, like the dashed line across the reference.
         */
        fun band(levelWidth: Double, levelHeight: Double, ballY: Double, above: Double = LevelDefaults.ZONE_ABOVE_BALL): Box {
            val top = (ballY - above).coerceAtLeast(0.0)
            return Box(0.0, top, levelWidth, levelHeight - top)
        }
    }
}
