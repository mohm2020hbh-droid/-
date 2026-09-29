package com.carom.core.physics

import kotlin.math.sqrt

/** The ball's physical state. Direction is kept as a unit vector, separate from speed. */
class Ball(val radius: Double) {
    var x = 0.0
    var y = 0.0
    var dirX = 1.0
    var dirY = 0.0
    var speed = 0.0

    fun place(x: Double, y: Double) {
        this.x = x
        this.y = y
        speed = 0.0
    }
}

/**
 * Static collision geometry plus triggers. It moves a ball along a straight line, reflecting it
 * off walls with the ideal reflection law (angle in = angle out, no spin, no randomness), so the
 * same shot always produces the same path.
 *
 * The world knows nothing about game rules: it reports every wall contact and trigger entry to a
 * [Listener], which decides whether the motion continues.
 */
class PhysicsWorld(
    val segments: List<Segment>,
    val triggers: List<CircleTrigger>,
) {
    interface Listener {
        /** The ball touched a wall at (x, y) with unit normal (nx, ny). Return false to stop there. */
        fun onWallContact(x: Double, y: Double, nx: Double, ny: Double): Boolean

        /** The ball's centre entered [trigger] at (x, y). Return false to stop there. */
        fun onTrigger(trigger: CircleTrigger, x: Double, y: Double): Boolean

        /**
         * The velocity of the surface at (x, y) if the wall just touched is itself moving (a sliding or
         * turning obstacle), written into [out] as (vx, vy). The ball then bounces off the surface as
         * seen from the surface, so a wall that moves into the ball throws it faster. False = still.
         * [world] is the world, whose [contactSegment] says which wall it was.
         */
        fun surfaceVelocity(world: PhysicsWorld, x: Double, y: Double, out: DoubleArray): Boolean = false
    }

    // Per-segment results of the current sweep, preallocated so moving the ball allocates nothing.
    private val hitT = DoubleArray(segments.size)
    private val hitNx = DoubleArray(segments.size)
    private val hitNy = DoubleArray(segments.size)
    private val contact = Contact()
    private val surface = DoubleArray(2)

    /** Index (in [segments]) of the wall touched by the contact being reported to the listener. */
    var contactSegment = -1
        private set

    /**
     * Moves [ball] a [distance] along its direction, bouncing off walls on the way.
     * Returns false if the listener stopped the motion.
     */
    fun move(ball: Ball, distance: Double, listener: Listener): Boolean {
        resolvePenetration(ball)
        var remaining = distance
        var contacts = 0
        while (remaining > MIN_DISTANCE) {
            val dx = ball.dirX * remaining
            val dy = ball.dirY * remaining

            // Earliest wall contact. Contacts at (almost) the same instant — a ball entering a
            // corner, or touching the shared vertex of two edges — are merged into one bounce
            // whose normal is the average of theirs.
            var tWall = Double.POSITIVE_INFINITY
            for (i in segments.indices) {
                if (segments[i].enabled && Sweep.circleVsSegment(ball.x, ball.y, dx, dy, ball.radius, segments[i], contact)) {
                    hitT[i] = contact.t
                    hitNx[i] = contact.nx
                    hitNy[i] = contact.ny
                    if (contact.t < tWall) tWall = contact.t
                } else {
                    hitT[i] = Double.POSITIVE_INFINITY
                }
            }

            // A trigger entered before the wall is reached is reported first.
            val tLimit = if (tWall <= 1.0) tWall else 1.0
            for (trigger in triggers) {
                val t = Sweep.triggerEntry(ball.x, ball.y, dx, dy, trigger)
                if (t <= tLimit) {
                    val ex = ball.x + dx * t
                    val ey = ball.y + dy * t
                    if (!listener.onTrigger(trigger, ex, ey)) {
                        ball.x = ex
                        ball.y = ey
                        return false
                    }
                }
            }

            if (tWall > 1.0) {
                ball.x += dx
                ball.y += dy
                return true
            }

            var nx = 0.0
            var ny = 0.0
            var first = -1
            for (i in segments.indices) {
                if (hitT[i] <= tWall + SIMULTANEOUS) {
                    nx += hitNx[i]
                    ny += hitNy[i]
                    if (first < 0 || hitT[i] < hitT[first]) first = i
                }
            }
            contactSegment = first
            val nLen = sqrt(nx * nx + ny * ny)
            if (nLen > 0.0) {
                nx /= nLen
                ny /= nLen
            } else {
                // Opposing normals cancel out (ball wedged between parallel walls): turn back.
                nx = -ball.dirX
                ny = -ball.dirY
            }

            ball.x += dx * tWall
            ball.y += dy * tWall
            remaining -= remaining * tWall
            if (!listener.onWallContact(ball.x, ball.y, nx, ny)) return false

            if (first >= 0 && listener.surfaceVelocity(this, ball.x, ball.y, surface)) {
                reflectOffMovingSurface(ball, nx, ny, surface[0], surface[1])
            } else {
                // Reflect: d' = d - 2(d·n)n, then renormalise to keep drift out of the direction.
                val dn = ball.dirX * nx + ball.dirY * ny
                if (dn < 0.0) {
                    var rx = ball.dirX - 2.0 * dn * nx
                    var ry = ball.dirY - 2.0 * dn * ny
                    val rLen = sqrt(rx * rx + ry * ry)
                    rx /= rLen
                    ry /= rLen
                    ball.dirX = rx
                    ball.dirY = ry
                }
            }
            // Step off the surface so the next sweep starts cleanly outside it.
            ball.x += nx * SKIN
            ball.y += ny * SKIN

            if (++contacts >= MAX_CONTACTS_PER_MOVE) return true
        }
        return true
    }

    /** Bounce as seen from the wall: reflect the ball's velocity relative to it, then add the wall's velocity back. */
    private fun reflectOffMovingSurface(ball: Ball, nx: Double, ny: Double, sx: Double, sy: Double) {
        val vx = ball.dirX * ball.speed - sx
        val vy = ball.dirY * ball.speed - sy
        val vn = vx * nx + vy * ny
        if (vn >= 0.0) return // already moving apart from the surface
        val rx = vx - 2.0 * vn * nx + sx
        val ry = vy - 2.0 * vn * ny + sy
        val speed = sqrt(rx * rx + ry * ry)
        if (speed > 0.0) {
            ball.dirX = rx / speed
            ball.dirY = ry / speed
            ball.speed = speed
        }
    }

    /**
     * Safety net: if the ball ever overlaps a wall (it shouldn't, but floating point is finite),
     * push it straight out so it can never get stuck inside geometry.
     */
    private fun resolvePenetration(ball: Ball) {
        repeat(MAX_DEPENETRATION_PASSES) {
            var moved = false
            for (seg in segments) {
                if (!seg.enabled) continue
                val limit = ball.radius + seg.radius
                val d = Sweep.distanceToSegment(ball.x, ball.y, seg, contact)
                if (d < limit - Sweep.TOUCH_TOLERANCE) {
                    val push = limit - d + SKIN
                    ball.x += contact.nx * push
                    ball.y += contact.ny * push
                    moved = true
                }
            }
            if (!moved) return
        }
    }

    /** True if a ball of [radius] centred at (x, y) would overlap any wall. */
    fun overlaps(x: Double, y: Double, radius: Double): Boolean =
        segments.any { it.enabled && Sweep.distanceToSegment(x, y, it, contact) < radius + it.radius }

    private companion object {
        const val MIN_DISTANCE = 1e-9
        const val SIMULTANEOUS = 1e-9
        const val SKIN = 1e-4
        const val MAX_CONTACTS_PER_MOVE = 16
        const val MAX_DEPENETRATION_PASSES = 4
    }
}
