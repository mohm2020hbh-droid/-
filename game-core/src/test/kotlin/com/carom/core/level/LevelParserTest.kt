package com.carom.core.level

import com.carom.core.game.WorldBuilder
import com.carom.core.math.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelParserTest {

    @Test
    fun parsesAFullLevelWithComments() {
        val level = LevelParser.parse(
            "007",
            """
            // A comment
            {
              "name": "Mirror",
              "bounces": 2,
              "ball": [200, 450],
              "goal": [1400, 450],
              "size": [1600, 900],
              "goalRadius": 50,
              "speed": 2000, "friction": 500,
              "border": false,
              "hint": {"en": "Bounce!", "ar": "ارتد!"},
              "obstacles": [
                {"type": "wall", "points": [800, 0, 800, 600, 900, 700], "thickness": 20, "closed": true},
                {"type": "poly", "points": [1000, 900, 1200, 600, 1400, 900]},
                {"type": "rect", "x": 0, "y": 0, "w": 100, "h": 50}
              ]
            }
            """,
        )
        assertEquals("007", level.id)
        assertEquals("Mirror", level.name)
        assertEquals(2, level.bounces)
        assertEquals(Vec2(200.0, 450.0), level.ball)
        assertEquals(50.0, level.goalRadius, 0.0)
        assertEquals(2000.0, level.maxSpeed, 0.0)
        assertEquals(500.0, level.friction, 0.0)
        assertFalse(level.border)
        assertEquals("ارتد!", level.hintFor("ar"))
        assertEquals("Bounce!", level.hintFor("fr"))
        val wall = level.obstacles[0] as Wall
        assertEquals(3, wall.points.size)
        assertTrue(wall.closed)
        assertEquals(20.0, wall.thickness, 0.0)
        assertEquals(3, (level.obstacles[1] as Block).points.size)
        assertEquals(
            listOf(Vec2(0.0, 0.0), Vec2(100.0, 0.0), Vec2(100.0, 50.0), Vec2(0.0, 50.0)),
            (level.obstacles[2] as Block).points,
        )
    }

    @Test
    fun appliesDefaults() {
        val level = LevelParser.parse("1", """{"bounces": 0, "ball": [100, 100], "goal": [500, 500]}""")
        assertEquals("1", level.name)
        assertEquals(LevelDefaults.WIDTH, level.width, 0.0)
        assertEquals(LevelDefaults.MAX_SPEED, level.maxSpeed, 0.0)
        assertTrue(level.border)
        assertTrue(level.obstacles.isEmpty())
        assertEquals(null, level.hintFor("en"))
    }

    @Test
    fun blocksHaveRoundedCornersButKeepTheirFaces() {
        val level = LevelParser.parse(
            "b",
            """{"bounces": 0, "ball": [100, 100], "goal": [500, 500], "obstacles": [
                 {"type": "rect", "x": 300, "y": 300, "w": 200, "h": 100},
                 {"type": "rect", "x": 700, "y": 700, "w": 20, "h": 20},
                 {"type": "poly", "points": [0, 0, 100, 0, 50, 20, 100, 100, 0, 100], "round": 10},
                 {"type": "rect", "x": 800, "y": 100, "w": 50, "h": 50, "round": 0}
               ]}""",
        )
        val rect = level.obstacles[0] as Block
        assertEquals(LevelDefaults.BLOCK_ROUNDING, rect.radius, 0.0)
        val r = rect.radius
        assertEquals(
            listOf(Vec2(300 + r, 300 + r), Vec2(500 - r, 300 + r), Vec2(500 - r, 400 - r), Vec2(300 + r, 400 - r)),
            rect.core,
        )
        // A ball coming straight at the long face stops where it would against a sharp block.
        val world = WorldBuilder.build(level)
        assertFalse(world.overlaps(400.0, 300.0 - 60.0 - 0.01, 60.0))
        assertTrue(world.overlaps(400.0, 300.0 - 60.0 + 0.5, 60.0))
        // ...but its corner is round: diagonally off the corner there is room the sharp block had filled.
        assertFalse(world.overlaps(300.0 - 60.0 * 0.7071 + 3, 300.0 - 60.0 * 0.7071 + 3, 60.0))

        assertEquals(LevelDefaults.BLOCK_ROUNDING / 2, (level.obstacles[1] as Block).radius, 0.0) // too small: halved
        val dented = level.obstacles[2] as Block // not convex: keeps its sharp outline
        assertEquals(0.0, dented.radius, 0.0)
        assertEquals(dented.points, dented.core)
        assertEquals(0.0, (level.obstacles[3] as Block).radius, 0.0)
    }

    @Test
    fun parsesAPathHint() {
        val level = LevelParser.parse(
            "g",
            """{"bounces": 1, "ball": [100, 100], "goal": [500, 500], "guide": {"angle": 45.5, "from": [120, 110]}}""",
        )
        assertEquals(Guide(afterFails = 10, angle = 45.5, from = Vec2(120.0, 110.0)), level.guide)
        assertEquals(null, LevelParser.parse("n", """{"bounces": 0, "ball": [100, 100], "goal": [500, 500]}""").guide)
        assertThrows(LevelFormatException::class.java) {
            LevelParser.parse("bad", """{"bounces": 0, "ball": [100, 100], "goal": [500, 500], "guide": {"afterFails": 3}}""")
        }
    }

    @Test
    fun rotatedRectKeepsItsCentreAndSize() {
        val level = LevelParser.parse(
            "r",
            """{"bounces": 0, "ball": [100, 100], "goal": [500, 500],
               "obstacles": [{"type": "rect", "x": 100, "y": 200, "w": 200, "h": 20, "angle": 90}]}""",
        )
        val pts = (level.obstacles[0] as Block).points
        assertEquals(200.0, pts.map { it.x }.average(), 1e-9)
        assertEquals(210.0, pts.map { it.y }.average(), 1e-9)
        assertEquals(20.0, pts[0].distanceTo(pts[1]).let { if (it < 100) it else pts[1].distanceTo(pts[2]) }, 1e-9)
    }

    @Test
    fun reportsHelpfulErrors() {
        fun error(json: String) = assertThrows(LevelFormatException::class.java) { LevelParser.parse("bad", json) }.message!!
        assertTrue(error("""{"ball": [1, 1], "goal": [2, 2]}""").contains("'bounces' is required"))
        assertTrue(error("""{"bounces": 1.5, "ball": [1, 1], "goal": [2, 2]}""").contains("whole number"))
        assertTrue(error("""{"bounces": 1, "ball": [1], "goal": [2, 2]}""").contains("'ball' must be [x, y]"))
        assertTrue(
            error("""{"bounces": 1, "ball": [1, 1], "goal": [2, 2], "obstacles": [{"type": "spring"}]}""")
                .contains("unknown type 'spring'"),
        )
        assertTrue(error("""{"bounces": 1, "ball": [1, 1] "goal": [2, 2]}""").contains("line 1"))
        assertTrue(error("[1, 2]").contains("root must be an object"))
    }

    @Test
    fun validatorFlagsImpossibleSetups() {
        val bad = LevelParser.parse(
            "v",
            """{"bounces": 1, "ball": [10, 450], "goal": [800, 450],
               "obstacles": [{"type": "rect", "x": 700, "y": 350, "w": 200, "h": 200}]}""",
        )
        val problems = LevelValidator.problems(bad)
        assertTrue(problems.toString(), problems.any { "ball overlaps a wall" in it })
        assertTrue(problems.toString(), problems.any { "goal is inside a block" in it })
    }

    @Test
    fun levelOrderIsNumeric() {
        assertEquals(listOf("1", "2", "10", "011", "bonus"), LevelRepository.sortIds(listOf("bonus", "10", "2", "011", "1")))
    }

    @Test
    fun aCircleObstacleIsARoundSolidThatBouncesLikeOne() {
        val level = LevelParser.parse(
            "c",
            """{"bounces": 1, "drag": 0, "ball": [450, 1700], "goal": [100, 100], "difficulty": 3, "concept": "a disc",
                "obstacles": [{"type": "circle", "x": 450, "y": 1000, "r": 100}]}""",
        )
        assertEquals(3, level.difficulty)
        assertEquals("a disc", level.concept)
        val disc = level.obstacles.single() as Block
        // Round: the solid is the same distance (radius 100) from the centre all the way round.
        val world = WorldBuilder.build(level)
        for (deg in 0 until 360 step 15) {
            val a = Math.toRadians(deg.toDouble())
            val reach = 100.0 + level.ballRadius
            assertTrue("touching at $deg", world.overlaps(450 + Math.cos(a) * (reach - 2.0), 1000 + Math.sin(a) * (reach - 2.0), level.ballRadius))
            assertFalse("clear at $deg", world.overlaps(450 + Math.cos(a) * (reach + 2.0), 1000 + Math.sin(a) * (reach + 2.0), level.ballRadius))
        }
        assertTrue(disc.core.size == 8)
    }

    @Test
    fun aBallOffCentreLeavesACircleAtTheMirrorAngle() {
        // A ball fired straight up 60 to the right of a disc's centre meets its rim where the normal makes
        // asin(60 / (100 + 60)) with the vertical, so it is turned by 180° − twice that angle from the way it came.
        val level = LevelParser.parse(
            "c",
            """{"bounces": 2, "drag": 0, "ball": [510, 1700], "goal": [100, 100],
                "obstacles": [{"type": "circle", "x": 450, "y": 1000, "r": 100}]}""",
        )
        val s = com.carom.core.game.GameSession(level)
        s.launch(0.0, -1.0, 1.0)
        var guard = 0
        while (s.bouncesUsed == 0 && guard++ < 100_000) s.step()
        repeat(3) { s.step() }
        val expected = 180.0 - 2 * Math.toDegrees(Math.asin(60.0 / 160.0))
        val turned = Math.toDegrees(Math.atan2(s.ball.dirX, -s.ball.dirY)) // from straight up, clockwise
        assertEquals(expected, turned, 0.6)
    }

    @Test
    fun badCirclesAndRatingsAreRefused() {
        for (bad in listOf(
            """{"type": "circle", "x": 1, "y": 1, "r": 3}""", """{"type": "circle", "y": 1, "r": 30}""", """{"type": "circle", "x": 1, "y": 1}""",
        )) {
            assertThrows(LevelFormatException::class.java) { LevelParser.parse("c", """{"bounces": 1, "ball": [10, 10], "goal": [50, 50], "obstacles": [$bad]}""") }
        }
        assertThrows(LevelFormatException::class.java) { LevelParser.parse("c", """{"bounces": 1, "ball": [10, 10], "goal": [50, 50], "difficulty": 11}""") }
    }
}
