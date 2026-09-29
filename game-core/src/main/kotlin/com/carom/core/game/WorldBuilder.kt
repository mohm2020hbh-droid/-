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
            val corners = listOf(
                Vec2(0.0, 0.0), Vec2(level.width, 0.0),
                Vec2(level.width, level.height), Vec2(0.0, level.height),
            )
            addChain(segments, corners, closed = true, radius = level.wallThickness / 2)
        }
        for (obstacle in level.obstacles) {
            when (obstacle) {
                is Wall -> addChain(segments, obstacle.points, obstacle.closed, obstacle.thickness / 2)
                // A block's edges are infinitely thin; the ball's own radius rounds its corners.
                is Block -> addChain(segments, obstacle.points, closed = true, radius = 0.0)
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
