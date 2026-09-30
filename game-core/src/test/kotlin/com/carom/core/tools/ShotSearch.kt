package com.carom.core.tools

import com.carom.core.game.GameSession
import com.carom.core.game.WorldBuilder
import com.carom.core.level.ControlZone
import com.carom.core.level.LevelData
import com.carom.core.math.Vec2
import com.carom.core.physics.Ball
import com.carom.core.physics.CircleTrigger
import com.carom.core.physics.PhysicsWorld
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Brute-force solver used to check level design: fires the ball at every angle (in [stepDegrees]
 * increments) through the real game rules and records which shots score.
 */
object ShotSearch {

    class Result(
        val level: LevelData,
        val stepDegrees: Double,
        /** Angles (degrees, clockwise from +x) that score, in increasing order. */
        val winningAngles: List<Double>,
        /** Fewest bounces any winning shot needed. */
        val minBounces: Int?,
    ) {
        val solvable get() = winningAngles.isNotEmpty()

        /** Total winning arc, in degrees. The bigger, the more forgiving the level. */
        val winningArc get() = winningAngles.size * stepDegrees

        /** Contiguous winning windows as (first, last) angle pairs. */
        val windows: List<Pair<Double, Double>> by lazy {
            val result = ArrayList<Pair<Double, Double>>()
            var start: Double? = null
            var prev = 0.0
            for (a in winningAngles) {
                if (start == null) {
                    start = a
                } else if (a - prev > stepDegrees * 1.5) {
                    result += start to prev
                    start = a
                }
                prev = a
            }
            if (start != null) result += start to prev
            result
        }

        /** Width of the widest window: how precise the best solution must be. */
        val widestWindow get() = windows.maxOfOrNull { it.second - it.first + stepDegrees } ?: 0.0

        /** The centre of the widest window: a representative solution. */
        val sampleAngle get() = windows.maxByOrNull { it.second - it.first }?.let { (it.first + it.second) / 2 }
    }

    fun search(level: LevelData, power: Double = 1.0, stepDegrees: Double = 0.05): Result {
        val wins = ArrayList<Double>()
        var minBounces: Int? = null
        val steps = (360.0 / stepDegrees).toInt()
        for (i in 0 until steps) {
            val angle = i * stepDegrees
            val session = fire(level, angle, power)
            if (session.state == GameSession.State.WON) {
                wins += angle
                minBounces = minOf(minBounces ?: Int.MAX_VALUE, session.bouncesUsed)
            }
        }
        return Result(level, stepDegrees, wins, minBounces)
    }

    /** Plays one shot to the end, from [from] (moved there within the control zone) if given. */
    fun fire(level: LevelData, angleDegrees: Double, power: Double = 1.0, from: Vec2? = null): GameSession {
        val session = GameSession(level)
        if (from != null) session.placeBall(from.x, from.y)
        val dir = Vec2.fromDegrees(angleDegrees)
        session.launch(dir.x, dir.y, power)
        while (session.state == GameSession.State.MOVING) session.step()
        return session
    }

    /**
     * The spots (ball centres) inside [zone] that the player can actually move the ball to from the level's start: a
     * grid [spacing] apart, walked outwards from the start where the ball can go without touching a wall, with the
     * places just past the zone's line pulled onto the line so its edge is sampled too.
     */
    fun reachableSpots(level: LevelData, zone: ControlZone = level.zone, spacing: Double = 40.0): List<Vec2> {
        val world = WorldBuilder.build(level)
        val probe = Ball(level.ballRadius)
        val stop = object : PhysicsWorld.Listener {
            override fun onWallContact(x: Double, y: Double, nx: Double, ny: Double) = false
            override fun onTrigger(trigger: CircleTrigger, x: Double, y: Double) = true
        }
        val r = level.ballRadius
        val centre = DoubleArray(2)
        fun reach(fromX: Double, fromY: Double, x: Double, y: Double): Boolean {
            val dist = hypot(x - fromX, y - fromY)
            if (dist < 1e-9) return true
            probe.x = fromX
            probe.y = fromY
            probe.dirX = (x - fromX) / dist
            probe.dirY = (y - fromY) / dist
            world.move(probe, dist, stop)
            return hypot(probe.x - x, probe.y - y) < 1.0
        }
        val seen = HashSet<Long>()
        fun key(x: Double, y: Double) = Math.round(x * 100).shl(32) xor Math.round(y * 100)
        val out = ArrayList<Vec2>()
        val queue = ArrayDeque<Vec2>()
        val start = level.ball
        queue.add(start)
        seen.add(key(start.x, start.y))
        out.add(start)
        while (queue.isNotEmpty()) {
            val from = queue.removeFirst()
            for (dx in -1..1) for (dy in -1..1) {
                if (dx == 0 && dy == 0) continue
                val wantX = from.x + dx * spacing
                val wantY = from.y + dy * spacing
                zone.nearestCentre(wantX, wantY, r, centre)
                val x = centre[0].coerceIn(r, level.width - r)
                val y = centre[1].coerceIn(r, level.height - r)
                if (!seen.add(key(x, y))) continue
                if (!reach(from.x, from.y, x, y)) continue
                val spot = Vec2(x, y)
                out.add(spot)
                // Only spots on the grid are walked on from; those pulled onto the zone's line are ends.
                if (abs(x - wantX) < 1e-6 && abs(y - wantY) < 1e-6) queue.add(spot)
            }
        }
        return out
    }

    /** The fewest bounces a full-power shot needs from [spot], or null if none scores. Stops early below [enough]. */
    fun fewestBouncesFrom(level: LevelData, spot: Vec2, stepDegrees: Double = 0.5, enough: Int = -1): Int? {
        val session = GameSession(level.copy(ball = spot))
        var best: Int? = null
        var a = 0.0
        while (a < 360.0) {
            session.restart()
            val d = Vec2.fromDegrees(a)
            session.launch(d.x, d.y, 1.0)
            var guard = 0
            while (session.state == GameSession.State.MOVING && guard++ < 200_000) session.step()
            if (session.state == GameSession.State.WON) {
                best = minOf(best ?: Int.MAX_VALUE, session.bouncesUsed)
                if (best <= enough) return best
            }
            a += stepDegrees
        }
        return best
    }

    /**
     * The fewest bounces any full-power shot needs from anywhere the ball can be moved to inside [zone], or null if
     * nothing scores. With [below] it stops as soon as a spot needs fewer bounces than that (a short cut).
     */
    fun minBouncesFromZone(
        level: LevelData,
        zone: ControlZone = level.zone,
        spacing: Double = 40.0,
        stepDegrees: Double = 0.5,
        below: Int? = null,
    ): Int? {
        var best: Int? = null
        for (spot in reachableSpots(level, zone, spacing)) {
            val n = fewestBouncesFrom(level, spot, stepDegrees, enough = (below ?: 0) - 1) ?: continue
            best = minOf(best ?: Int.MAX_VALUE, n)
            if (below != null && n < below) return best
        }
        return best
    }
}
