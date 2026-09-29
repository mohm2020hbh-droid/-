package com.carom.core.level

import com.carom.core.game.WorldBuilder
import com.carom.core.math.Vec2

/** Design-time checks that catch broken level files before a player does. */
object LevelValidator {

    fun problems(level: LevelData): List<String> {
        val problems = ArrayList<String>()
        val world = WorldBuilder.build(level)
        val r = level.ballRadius

        fun inside(p: Vec2, margin: Double) =
            p.x >= margin && p.y >= margin && p.x <= level.width - margin && p.y <= level.height - margin

        if (!inside(level.ball, r)) problems += "ball is outside the level"
        if (!inside(level.goal, 0.0)) problems += "goal is outside the level"
        if (world.overlaps(level.ball.x, level.ball.y, r)) problems += "ball overlaps a wall"
        if (world.overlaps(level.goal.x, level.goal.y, r)) problems += "goal centre is too close to a wall for the ball"
        if (level.ball.distanceTo(level.goal) <= level.goalRadius) problems += "ball starts inside the goal"
        if ("," in level.id) problems += "level id must not contain ','"
        for (o in level.obstacles) {
            if (o is Block) {
                if (containsPoint(o.points, level.ball)) problems += "ball is inside a block"
                if (containsPoint(o.points, level.goal)) problems += "goal is inside a block"
            }
            val points = when (o) {
                is Wall -> o.points
                is Block -> o.points
            }
            if (points.zipWithNext().any { (a, b) -> a == b }) problems += "obstacle has a zero-length edge"
        }
        return problems
    }

    /** Even-odd point-in-polygon test. */
    fun containsPoint(polygon: List<Vec2>, p: Vec2): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }
}
