package com.carom.core.tools

import com.carom.core.game.GameSession
import com.carom.core.level.LevelData
import com.carom.core.math.Vec2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Measures a level the way a player meets it: the ball can be moved to any spot of its control zone and let go with any
 * velocity, so every throw from a grid of spots, at every angle (and at the given speeds), is played through the real
 * rules. From the wins it reads how many bounces the level really needs, how wide the widest window of angles is, and
 * how much of the speed range works for the best throw.
 */
object LevelLab {

    /** A window narrower than this (degrees) is not something a finger can aim for. */
    const val REAL_WINDOW = 2.0

    class Win(val spot: Vec2, val angle: Double, val speed: Double, val bounces: Int)

    class Report(
        val level: LevelData,
        val spots: Int,
        val wins: List<Win>,
        /** The fewest bounces any throw from anywhere in the zone needed, or null if none scored. */
        val minBounces: Int?,
        /** The fewest bounces a throw from the level's own start needed. */
        val startMin: Int?,
        val angleStep: Double,
    ) {
        val solvable: Boolean get() = minBounces != null && minBounces <= level.bounces

        /**
         * The fewest bounces with a real window: a way through that needs the aim right to within [REAL_WINDOW] degrees is
         * no way a finger can take, so it does not count as the level's minimum.
         */
        val realMinBounces: Int? by lazy {
            val m = minBounces ?: return@lazy null
            (m..level.bounces + 3).firstOrNull { b -> window { it.bounces <= b }.first >= REAL_WINDOW - 1e-9 } ?: m
        }

        /** The widest run of winning angles (degrees) at one spot and speed, among throws within the level's bounces. */
        val widestWindow: Double by lazy { window { it.bounces <= level.bounces }.first }

        /** The same, among throws that use exactly the fewest bounces. */
        val widestWindowAtMin: Double by lazy { window { it.bounces == minBounces }.first }

        /** Where the widest window is: a spot, and the middle angle of the window. */
        val bestThrow: Win? by lazy { window { it.bounces <= level.bounces }.second }

        /** How many of the spots have a scoring throw within the level's bounces. */
        val winningSpots: Int by lazy { wins.filter { it.bounces <= level.bounces }.map { it.spot }.toSet().size }

        private fun window(pred: (Win) -> Boolean): Pair<Double, Win?> {
            var best = 0.0
            var bestWin: Win? = null
            val groups = wins.filter(pred).groupBy { Triple(it.spot.x, it.spot.y, it.speed) }
            for (list in groups.values) {
                val sorted = list.sortedBy { it.angle }
                var start = 0
                for (i in 1..sorted.size) {
                    if (i == sorted.size || sorted[i].angle - sorted[i - 1].angle > angleStep * 1.01) {
                        val width = sorted[i - 1].angle - sorted[start].angle + angleStep
                        if (width > best) {
                            best = width
                            val mid = sorted[(start + i - 1) / 2]
                            bestWin = mid
                        }
                        start = i
                    }
                }
            }
            return best to bestWin
        }
    }

    /** One throw with [vx], [vy] from [spot]; the session it left standing (played to the end). */
    private fun play(session: GameSession, vx: Double, vy: Double): GameSession {
        session.restart()
        if (session.launchAt(vx, vy)) {
            var guard = 0
            while (session.state == GameSession.State.MOVING && guard++ < 200_000) session.step()
        }
        return session
    }

    /**
     * Plays every throw from a grid of spots [spacing] apart (the ones the ball can really be moved to), at every
     * [angleStep] degrees and each of the [speeds] (fractions of the level's top speed). Throws may use up to [cap]
     * bounces so that the true minimum shows even when the level allows fewer (a level with several balls gives every ball exactly its budget).
     */
    fun analyze(
        level: LevelData,
        spacing: Double = 160.0,
        angleStep: Double = 0.5,
        speeds: List<Double> = listOf(1.0),
        cap: Int = if (level.exitRequired > 1) level.bounces else min(level.bounces + 3, 8),
    ): Report {
        val spots = ShotSearch.reachableSpots(level, spacing = spacing)
        val wins = ArrayList<Win>()
        val steps = (360.0 / angleStep).toInt()
        for (spot in spots) {
            val session = GameSession(level.copy(ball = spot, bounces = cap))
            for (speed in speeds) {
                val v = level.maxSpeed * speed
                for (i in 0 until steps) {
                    val a = i * angleStep
                    val rad = Math.toRadians(a)
                    play(session, cos(rad) * v, sin(rad) * v)
                    // With several balls every ball has the level's own budget and the throw is judged by winning.
                    if (session.state == GameSession.State.WON) wins += Win(spot, a, speed, if (level.exitRequired > 1) min(session.bouncesUsed, level.bounces) else session.bouncesUsed)
                }
            }
        }
        val start = level.ball
        val startMin = wins.filter { it.spot.x == start.x && it.spot.y == start.y }.minOfOrNull { it.bounces }
        return Report(level, spots.size, wins, wins.minOfOrNull { it.bounces }, startMin, angleStep)
    }

    /**
     * How much of the speed range works for one throw: the lowest and highest fractions of the top speed (in steps of
     * 0.05) that still score when the ball is let go at [from] along [angle], counting only the run of speeds that
     * contains [around].
     */
    fun speedRange(level: LevelData, from: Vec2, angle: Double, around: Double = 1.0): Pair<Double, Double> {
        val session = GameSession(level.copy(ball = from))
        val rad = Math.toRadians(angle)
        fun scores(f: Double): Boolean {
            play(session, cos(rad) * level.maxSpeed * f, sin(rad) * level.maxSpeed * f)
            return session.state == GameSession.State.WON
        }
        val grid = (1..20).map { it * 0.05 }
        val start = grid.minByOrNull { kotlin.math.abs(it - around) }!!
        if (!scores(start)) return start to start
        var lo = start
        var hi = start
        while (lo - 0.05 >= 0.05 - 1e-9 && scores(lo - 0.05)) lo -= 0.05
        while (hi + 0.05 <= 1.0 + 1e-9 && scores(hi + 0.05)) hi += 0.05
        return lo to hi
    }

    /** Distance of a point from the ball's start, for reports. */
    fun dist(a: Vec2, b: Vec2) = hypot(a.x - b.x, a.y - b.y)
}
