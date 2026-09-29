package com.carom.core.game

import com.carom.core.level.Block
import com.carom.core.level.LevelData
import com.carom.core.level.Wall
import com.carom.core.math.Vec2
import com.carom.core.physics.CircleTrigger
import com.carom.core.physics.PhysicsWorld
import com.carom.core.physics.Segment

/** Converts level data into collision geometry. This is where each obstacle kind gets its physics. */
object WorldBuilder {

    fun build(level: LevelData): PhysicsWorld {
        val segments = ArrayList<Segment>()
        if (level.border) {
            // The level's edges are the screen's edges: invisible, zero-thickness walls, so the
            // ball bounces the moment its edge meets the edge of the screen.
            val corners = listOf(
                Vec2(0.0, 0.0), Vec2(level.width, 0.0),
                Vec2(level.width, level.height), Vec2(0.0, level.height),
            )
            addChain(segments, corners, closed = true, radius = 0.0)
        }
        for (obstacle in level.obstacles) {
            when (obstacle) {
                is Wall -> addChain(segments, obstacle.points, obstacle.closed, obstacle.thickness / 2)
                // A block is its core polygon with rounded edges, so its corners are never sharp.
                is Block -> addChain(segments, obstacle.core, closed = true, radius = obstacle.radius)
            }
        }
        val goal = CircleTrigger(level.goal.x, level.goal.y, level.goalRadius)
        return PhysicsWorld(segments, listOf(goal))
    }

    private fun addChain(out: MutableList<Segment>, points: List<Vec2>, closed: Boolean, radius: Double) {
        val count = if (closed) points.size else points.size - 1
        for (i in 0 until count) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            out.add(Segment(a.x, a.y, b.x, b.y, radius))
        }
    }
}
