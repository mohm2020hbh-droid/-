package com.carom.core.game

import com.carom.core.level.Block
import com.carom.core.level.Guide
import com.carom.core.level.LevelData
import com.carom.core.level.Wall
import com.carom.core.math.Vec2
import com.carom.core.physics.Contact
import com.carom.core.physics.Segment
import com.carom.core.physics.Sweep

/**
 * The route a level's [Guide] throw takes: the ball's path (start, each bounce, and where it
 * enters the goal) and the obstacles it bounces off or passes close to, which are the ones worth
 * pointing out. Played through the real rules, so it is exactly what that throw does.
 */
class HintRoute(val points: List<Vec2>, val nearObstacles: Set<Int>, val scores: Boolean) {

    companion object {
        /** Extra distance (world units) within which a passing obstacle counts as close. */
        private const val NEAR = 30.0

        fun plan(level: LevelData, guide: Guide): HintRoute {
            val session = GameSession(level)
            guide.from?.let { session.placeBall(it.x, it.y) }
            val dir = Vec2.fromDegrees(guide.angle)
            session.launch(dir.x, dir.y, 1.0)
            var guard = 0
            while (session.state == GameSession.State.MOVING && guard++ < 200_000) session.step()
            val points = session.path.toList()

            val samples = ArrayList<Vec2>()
            for ((a, b) in points.zipWithNext()) {
                val n = (a.distanceTo(b) / 8.0).toInt().coerceAtLeast(1)
                for (i in 0..n) samples += Vec2(a.x + (b.x - a.x) * i / n, a.y + (b.y - a.y) * i / n)
            }
            val contact = Contact()
            val near = HashSet<Int>()
            level.obstacles.forEachIndexed { i, o ->
                val segments = when (o) {
                    is Wall -> chain(o.points, o.closed, o.thickness / 2)
                    is Block -> chain(o.core, closed = true, radius = o.radius)
                }
                val close = samples.any { p ->
                    segments.any { s -> Sweep.distanceToSegment(p.x, p.y, s, contact) < level.ballRadius + s.radius + NEAR }
                }
                if (close) near += i
            }
            return HintRoute(points, near, session.state == GameSession.State.WON)
        }

        private fun chain(points: List<Vec2>, closed: Boolean, radius: Double): List<Segment> {
            val count = if (closed) points.size else points.size - 1
            return (0 until count).map { i ->
                val a = points[i]
                val b = points[(i + 1) % points.size]
                Segment(a.x, a.y, b.x, b.y, radius)
            }
        }
    }
}
