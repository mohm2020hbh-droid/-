package com.carom.core.tools

import com.carom.core.game.GameSession
import com.carom.core.level.LevelData
import com.carom.core.math.Vec2

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

    /** Plays one shot to the end, from [from] (moved there within the launch zone) if given. */
    fun fire(level: LevelData, angleDegrees: Double, power: Double = 1.0, from: Vec2? = null): GameSession {
        val session = GameSession(level)
        if (from != null) session.placeBall(from.x, from.y)
        val dir = Vec2.fromDegrees(angleDegrees)
        session.launch(dir.x, dir.y, power)
        while (session.state == GameSession.State.MOVING) session.step()
        return session
    }

    /**
     * The fewest bounces any full-power shot needs from anywhere in the level's launch zone
     * (sampled on a [spacing] grid, every [stepDegrees]), or null if nothing scores.
     */
    fun minBouncesFromZone(level: LevelData, spacing: Double = 40.0, stepDegrees: Double = 0.5): Int? {
        var best: Int? = null
        val z = level.launchZone
        var gx = -z
        while (gx <= z + 1e-9) {
            var gy = -z
            while (gy <= z + 1e-9) {
                if (gx * gx + gy * gy <= z * z + 1e-6) {
                    val spot = Vec2(level.ball.x + gx, level.ball.y + gy)
                    val probe = GameSession(level)
                    probe.placeBall(spot.x, spot.y)
                    // Only spots the ball can actually be moved to (not behind a wall).
                    if (spot.distanceTo(Vec2(probe.ball.x, probe.ball.y)) < 1.0) {
                        var a = 0.0
                        while (a < 360.0) {
                            val s = fire(level, a, from = spot)
                            if (s.state == GameSession.State.WON) best = minOf(best ?: Int.MAX_VALUE, s.bouncesUsed)
                            a += stepDegrees
                        }
                    }
                }
                gy += spacing
            }
            gx += spacing
        }
        return best
    }
}
