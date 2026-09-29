package com.carom.core.game

import com.carom.core.level.LevelParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSessionTest {

    /** A 1000×600 box; ball on the left, goal on the right, a wall between them. */
    private fun level(bounces: Int, friction: Double = 0.0, extra: String = "", zone: Double = 130.0) = LevelParser.parse(
        "test",
        """
        {
          "size": [1000, 600], "bounces": $bounces, "friction": $friction, "drag": 0, "launchZone": $zone,
          "ball": [100, 300], "goal": [900, 300],
          "obstacles": [ {"type": "wall", "points": [500, 200, 500, 450]} $extra ]
        }
        """,
    )

    private fun GameSession.runToEnd(): GameSession {
        var guard = 0
        while (state == GameSession.State.MOVING && guard++ < 100_000) step()
        return this
    }

    /** Aim at a point by mirroring the goal in the top edge, so the shot bounces once to score. */
    private fun GameSession.bankShotOffTop() {
        val topInner = level.ballRadius // the edge is an invisible zero-thickness wall
        val mirroredGoalY = 2 * topInner - level.goal.y
        launch(level.goal.x - level.ball.x, mirroredGoalY - level.ball.y, 1.0)
    }

    @Test
    fun straightShotIntoAWallFailsWhenNoBouncesAreLeft() {
        val s = GameSession(level(bounces = 0))
        assertTrue(s.launch(1.0, 0.0, 1.0))
        s.runToEnd()
        assertEquals(GameSession.State.FAILED, s.state)
        assertEquals(GameSession.FailReason.OUT_OF_BOUNCES, s.failReason)
    }

    @Test
    fun bankShotScoresUsingOneBounce() {
        val s = GameSession(level(bounces = 1))
        s.bankShotOffTop()
        s.runToEnd()
        assertEquals(GameSession.State.WON, s.state)
        assertEquals(0, s.bouncesLeft)
        assertEquals(1, s.bouncesUsed)
        assertNull(s.failReason)
    }

    @Test
    fun theSameBankShotFailsWithoutABounceToSpend() {
        val s = GameSession(level(bounces = 0))
        s.bankShotOffTop()
        s.runToEnd()
        assertEquals(GameSession.State.FAILED, s.state)
        assertEquals(GameSession.FailReason.OUT_OF_BOUNCES, s.failReason)
    }

    @Test
    fun everyWallHitCostsOneBounce() {
        val bounces = mutableListOf<Int>()
        val s = GameSession(level(bounces = 5))
        s.listener = object : GameSession.Listener {
            override fun onBounce(impact: GameSession.Impact, bouncesLeft: Int) {
                bounces += bouncesLeft
            }
        }
        s.launch(0.0, -1.0, 1.0) // straight up and down between the floor and ceiling
        s.runToEnd()
        assertEquals(listOf(4, 3, 2, 1, 0), bounces)
        assertEquals(GameSession.FailReason.OUT_OF_BOUNCES, s.failReason)
    }

    @Test
    fun frictionStopsAWeakShot() {
        val s = GameSession(level(bounces = 3, friction = 600.0))
        s.launch(0.0, 1.0, 0.01)
        s.runToEnd()
        assertEquals(GameSession.State.FAILED, s.state)
        assertEquals(GameSession.FailReason.STOPPED, s.failReason)
    }

    @Test
    fun travelDistanceGrowsLinearlyWithPower() {
        val lvl = LevelParser.parse(
            "open",
            """{"size": [100000, 1000], "bounces": 0, "ball": [100, 500], "goal": [99000, 500], "friction": 600, "drag": 0}""",
        )
        fun distance(power: Double): Double {
            val s = GameSession(lvl)
            s.launch(1.0, 0.0, power)
            s.runToEnd()
            return s.ball.x - 100
        }
        val full = distance(1.0)
        assertEquals(lvl.maxReach, full, 1e-6)
        assertEquals(full / 2, distance(0.5), 1e-6)
        assertEquals(full / 4, distance(0.25), 1e-6)
    }

    @Test
    fun frameRateDoesNotChangeTheOutcome() {
        fun finalPosition(frame: Double): Pair<Double, Double> {
            val s = GameSession(level(bounces = 9, friction = 300.0))
            s.launch(0.8, -0.35, 0.9)
            while (s.state == GameSession.State.MOVING) s.advance(frame)
            return s.ball.x to s.ball.y
        }
        val at60 = finalPosition(1 / 60.0)
        val at90 = finalPosition(1 / 90.0)
        val at144 = finalPosition(1 / 144.0)
        assertEquals(at60.first, at90.first, 1e-9)
        assertEquals(at60.second, at144.second, 1e-9)
    }

    @Test
    fun restartRestoresEverythingAndKeepsTheLastPath() {
        val s = GameSession(level(bounces = 1))
        s.bankShotOffTop()
        s.runToEnd()
        val path = s.path.toList()
        assertEquals(3, path.size) // launch, bounce, goal

        s.restart()
        assertEquals(GameSession.State.AIMING, s.state)
        assertEquals(1, s.bouncesLeft)
        assertEquals(s.level.ball.x, s.ball.x, 0.0)
        assertEquals(s.level.ball.y, s.ball.y, 0.0)
        assertEquals(0.0, s.ball.speed, 0.0)
        assertTrue(s.path.isEmpty())
        assertEquals(path, s.lastShotPath)
    }

    @Test
    fun restartingMidFlightKeepsThePathSoFar() {
        val s = GameSession(level(bounces = 1))
        s.launch(1.0, 0.0, 1.0)
        repeat(10) { s.step() }
        val x = s.ball.x
        s.restart()
        assertEquals(2, s.lastShotPath.size)
        assertEquals(x, s.lastShotPath[1].x, 0.0)
    }

    @Test
    fun cannotLaunchTwice() {
        val s = GameSession(level(bounces = 1))
        assertTrue(s.launch(1.0, 0.0, 1.0))
        assertFalse(s.launch(-1.0, 0.0, 1.0))
    }

    @Test
    fun headOnHitsAreHarderThanGlancingOnesAndTheBreakingHitIsKept() {
        val strengths = mutableListOf<Double>()
        val s = GameSession(level(bounces = 1))
        s.listener = object : GameSession.Listener {
            override fun onBounce(impact: GameSession.Impact, bouncesLeft: Int) {
                strengths += impact.strength
            }
        }
        s.bankShotOffTop() // a glancing bounce off the top edge, then the goal
        s.runToEnd()
        assertEquals(1, strengths.size)
        assertTrue(strengths[0] in 0.05..0.6)

        val head = GameSession(level(bounces = 0))
        head.launch(1.0, 0.0, 1.0) // straight into the middle wall: no bounces left, so it breaks
        head.runToEnd()
        assertEquals(GameSession.FailReason.OUT_OF_BOUNCES, head.failReason)
        val hit = head.lastImpact!!
        assertEquals(1.0, hit.strength, 1e-9)
        assertEquals(-1.0, hit.nx, 1e-9) // the wall faces back towards the ball
        head.restart()
        assertNull(head.lastImpact)
    }

    @Test
    fun theBallCanBeMovedAroundItsLaunchZoneButNotThroughWalls() {
        val s = GameSession(level(bounces = 1))
        assertTrue(s.placeBall(100.0, 0.0)) // straight up, 300 away: stops at the zone's edge
        assertEquals(100.0, s.ball.x, 1e-9)
        assertEquals(300.0 - s.level.launchZone, s.ball.y, 1e-9)
        s.launch(1.0, 0.0, 1.0)
        assertEquals(300.0 - s.level.launchZone, s.path[0].y, 1e-9) // the shot starts where the ball was put
        assertFalse(s.placeBall(100.0, 300.0)) // not while it is flying
        s.restart()
        assertEquals(300.0, s.ball.y, 1e-9) // a restart brings it back to the start

        val wide = GameSession(level(bounces = 1, zone = 600.0))
        wide.placeBall(900.0, 300.0) // towards the middle wall: stops against it, never through it
        val face = 500.0 - wide.level.wallThickness / 2 - wide.level.ballRadius
        assertEquals(face, wide.ball.x, 1e-3)
        assertTrue(wide.ball.x <= face)
    }
}
