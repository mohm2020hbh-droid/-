package com.carom.core.level

import com.carom.core.game.ElementRuntime
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
        // The ball starts inside its control zone, and moving it around the zone must never put it into the goal.
        if (!level.zone.holds(level.ball.x, level.ball.y, r)) problems += "ball starts outside its control zone"
        if (level.zone.distanceFromCentres(level.goal.x, level.goal.y, r) <= level.goalRadius) problems += "control zone reaches the goal"
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
        problems += elementProblems(level)
        return problems
    }

    /** Checks of the level's [Element]s: where they are, what they refer to, and whether the exit can be satisfied. */
    private fun elementProblems(level: LevelData): List<String> {
        val problems = ArrayList<String>()
        val ids = HashSet<String>()
        val portalIds = level.elements.filter { it.kind == ElementKind.PORTAL }.map { it.id }.toSet()
        var extraBalls = 0
        for ((i, e) in level.elements.withIndex()) {
            val name = "element #$i (${e.kind.name.lowercase()}${if (e.id.isNotEmpty()) " '${e.id}'" else ""})"
            // A barrier may be anchored to the frame like a wall (centre up to 60 outside it); a zone or portal must be inside.
            val bleed = if (e.physical) LevelDefaults.WALL_THICKNESS else 0.0
            if (e.x < -bleed || e.y < -bleed || e.x > level.width + bleed || e.y > level.height + bleed) problems += "$name is outside the level"
            if (e.id.isNotEmpty() && !ids.add(e.id)) problems += "$name: the id is used twice"
            if (e.channel !in 0..15) problems += "$name: channel must be 0..15"
            if (e.kind == ElementKind.PORTAL) {
                if (e.link.isEmpty()) problems += "$name has no 'link'"
                else if (e.link !in portalIds) problems += "$name links to '${e.link}', which is not a portal"
                else if (e.link == e.id) problems += "$name links to itself"
            }
            if (e.kind == ElementKind.BALL_CONTAINER) extraBalls += e.value.toInt().coerceAtLeast(1)
            if (e.kind == ElementKind.DESTRUCTIBLE && e.value < 1) problems += "$name needs a 'value' of at least 1 hit"
            if (!e.physical && (e.deathTrigger || e.kind == ElementKind.PORTAL || e.kind == ElementKind.SWITCH) &&
                ElementRuntime(e, i).contains(level.ball.x, level.ball.y)
            ) {
                problems += "the ball starts inside $name"
            }
        }
        if (level.exitRequired > 1 + extraBalls) problems += "the exit needs ${level.exitRequired} balls but at most ${1 + extraBalls} can exist"
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
