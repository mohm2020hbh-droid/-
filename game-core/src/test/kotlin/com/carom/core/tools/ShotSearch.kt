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

    /** Plays one shot to the end and returns the finished session. */
    fun fire(level: LevelData, angleDegrees: Double, power: Double = 1.0): GameSession {
        val session = GameSession(level)
        val dir = Vec2.fromDegrees(angleDegrees)
        session.launch(dir.x, dir.y, power)
        while (session.state == GameSession.State.MOVING) session.step()
        return session
    }
}
