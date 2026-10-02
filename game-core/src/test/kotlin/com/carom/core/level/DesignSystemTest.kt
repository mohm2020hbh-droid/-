package com.carom.core.level

import com.carom.core.game.WorldBuilder
import com.carom.core.math.Vec2
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The design language of the campaign, as rules the shipped levels must keep so that all sixty look and play like one
 * game: one wall thickness, one goal size, one control zone kept clear of the puzzle and away from the screen's edges,
 * a goal that sits in the calm middle of the picture (never in the corners or under the top bar), walls that keep to
 * a few angles, and a shape vocabulary that is used and not just listed.
 */
class DesignSystemTest {

    private class DirectorySource(private val dir: File) : LevelSource {
        override fun list() = dir.listFiles { f -> f.name.endsWith(".json") }!!.map { it.name.removeSuffix(".json") }
        override fun read(id: String) = File(dir, "$id.json").readText()
    }

    private val dir = File(System.getProperty("levels.dir") ?: "../game/src/main/assets/levels")
    private val levels by lazy { LevelRepository(DirectorySource(dir)).let { r -> (0 until r.size).map(r::load) } }

    @Test
    fun oneWallThicknessEverywhere() {
        val odd = levels.flatMap { level ->
            level.obstacles.filterIsInstance<Wall>().filter { it.thickness != LevelDefaults.WALL_THICKNESS }.map { "${level.id}: ${it.thickness}" } +
                (if (level.wallThickness != LevelDefaults.WALL_THICKNESS) listOf("${level.id}: level thickness ${level.wallThickness}") else emptyList())
        }
        assertEquals("every wall is ${LevelDefaults.WALL_THICKNESS} thick", emptyList<String>(), odd)
    }

    @Test
    fun oneGoalSizeAndItIsGenerous() {
        val odd = levels.filter { it.goalRadius != LevelDefaults.GOAL_RADIUS }.map { "${it.id}: ${it.goalRadius}" }
        assertEquals("the goal is ${LevelDefaults.GOAL_RADIUS} everywhere; difficulty comes from the geometry, never from a smaller goal", emptyList<String>(), odd)
    }

    @Test
    fun theControlZoneIsTheSameLargeQuietRectangleEverywhere() {
        val first = levels.first().zone as ControlZone.Box
        assertTrue("wide enough for a thumb: ${first.width}", first.width >= 700.0)
        assertTrue("tall enough to move the ball about: ${first.height}", first.height >= 340.0)
        assertTrue("clear of the screen's left edge", first.x >= 60.0)
        assertTrue("clear of the screen's right edge", first.right <= levels.first().width - 60.0)
        for (level in levels) assertEquals("level ${level.id}'s zone differs from level 1's", first, level.zone)
    }

    @Test
    fun nothingOfThePuzzleReachesIntoTheControlZone() {
        val problems = levels.mapNotNull { level ->
            val zone = level.zone as ControlZone.Box
            val world = WorldBuilder.build(level)
            var hit: Vec2? = null
            var y = zone.y
            while (y <= zone.bottom && hit == null) {
                var x = zone.x
                while (x <= zone.right && hit == null) {
                    if (world.overlaps(x, y, 0.0)) hit = Vec2(x, y)
                    x += 30.0
                }
                y += 30.0
            }
            hit?.let { "${level.id}: a wall reaches (${it.x.toInt()}, ${it.y.toInt()}) inside the control zone" }
        }
        assertEquals(emptyList<String>(), problems)
    }

    /** Calm: nothing crowds the control zone's line (90 units clear, more than the ball's own radius) and nothing touches a goal's ring. */
    @Test
    fun nothingCrowdsTheZoneLineOrTheGoal() {
        val crowded = levels.mapNotNull { level ->
            val zone = level.zone as ControlZone.Box
            val world = WorldBuilder.build(level)
            var x = zone.x
            var near = false
            while (x <= zone.right) {
                if (world.overlaps(x, zone.y, 90.0)) near = true
                x += 30.0
            }
            when {
                near -> "${level.id}: a wall comes within 90 of the zone's top line"
                world.overlaps(level.goal.x, level.goal.y, level.goalRadius) -> "${level.id}: a wall touches the goal's ring"
                else -> null
            }
        }
        assertEquals(emptyList<String>(), crowded)
    }

    @Test
    fun theGoalSitsInTheCalmMiddleOfThePicture() {
        val bad = levels.filter { level ->
            val zone = level.zone as ControlZone.Box
            level.goal.x !in 150.0..(level.width - 150.0) || level.goal.y < 330.0 || level.goal.y > zone.y - 160.0
        }.map { "${it.id}: (${it.goal.x.toInt()}, ${it.goal.y.toInt()})" }
        assertEquals("goal outside x 150..750, y 330..(zone top - 160)", emptyList<String>(), bad)
    }

    /** Bars keep to the straight and the diagonal: nine in ten run along an axis or at 45 degrees. */
    @Test
    fun wallsKeepToAFewAngles() {
        var total = 0
        var plain = 0
        for (level in levels) for (o in level.obstacles) {
            if (o !is Wall) continue
            for ((a, b) in o.points.zipWithNext()) {
                total++
                val deg = Math.toDegrees(atan2(b.y - a.y, b.x - a.x)).let { ((it % 90) + 90) % 90 }
                if (deg < 1.0 || abs(deg - 45.0) < 1.0 || deg > 89.0) plain++
            }
        }
        assertTrue("$plain of $total wall segments are straight or at 45 degrees", total == 0 || plain >= 0.9 * total)
    }

    /** The geometry world uses each of its shapes: rectangles, triangles, discs, half discs, hexagons. */
    @Test
    fun theGeometryWorldUsesEveryShape() {
        val world = levels.subList(Worlds.firstLevel(1), Worlds.firstLevel(2))
        fun count(match: (Obstacle) -> Boolean) = world.sumOf { l -> l.obstacles.count(match) }
        val circles = count { it is Block && it.points.size == 8 && it.radius >= 40.0 }
        val halves = count { it is Block && it.points.size in 10..40 && flatSide(it) }
        val hexes = count { it is Block && it.points.size == 6 }
        val triangles = count { it is Block && it.points.size == 3 }
        val boxes = count { it is Block && it.points.size == 4 }
        assertTrue("discs: $circles", circles >= 1)
        assertTrue("half discs: $halves", halves >= 1)
        assertTrue("hexagons: $hexes", hexes >= 1)
        assertTrue("triangles: $triangles", triangles >= 1)
        assertTrue("boxes: $boxes", boxes >= 1)
    }

    /** A half disc has one straight side: its first and last point are the ends of a diameter. */
    private fun flatSide(b: Block): Boolean {
        val p = b.points
        val d = hypot(p.first().x - p.last().x, p.first().y - p.last().y)
        val r = p.maxOf { hypot(it.x - (p.first().x + p.last().x) / 2, it.y - (p.first().y + p.last().y) / 2) }
        return abs(d - 2 * r) < 8.0   // the coordinates are tidied to multiples of 10
    }
}
