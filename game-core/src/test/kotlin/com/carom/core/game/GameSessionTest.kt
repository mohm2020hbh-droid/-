package com.carom.core.game

import com.carom.core.level.LevelParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSessionTest {

    /** A 1000×600 box; ball on the left, goal on the right, a wall between them. */
    private fun level(bounces: Int, friction: Double = 0.0, extra: String = "", zone: Double = 130.0, controlZone: String? = null) = LevelParser.parse(
        "test",
        """
        {
          "size": [1000, 600], "bounces": $bounces, "friction": $friction, "drag": 0,
          ${controlZone?.let { "\"controlZone\": $it," } ?: "\"launchZone\": $zone,"}
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
    fun onlyTheHitThatBreaksTheBallIsFatalAndItIsReportedWithTheLoss() {
        val bounced = mutableListOf<Boolean>()
        val lost = mutableListOf<GameSession.Impact?>()
        val s = GameSession(level(bounces = 2))
        s.listener = object : GameSession.Listener {
            override fun onBounce(impact: GameSession.Impact, bouncesLeft: Int) {
                bounced += impact.fatal
            }

            override fun onBallLost(ball: Int, reason: GameSession.FailReason, x: Double, y: Double) {
                lost += s.lastImpact
            }
        }
        s.launch(0.0, -1.0, 1.0) // straight up and down: two counted bounces, then the third hit breaks it
        s.runToEnd()
        assertEquals(listOf(false, false), bounced) // a fatal hit never comes through onBounce...
        assertEquals(1, lost.size)
        assertTrue(lost[0]!!.fatal) // ...it is the lastImpact of the loss it causes
        assertEquals(0, lost[0]!!.ball)
    }

    @Test
    fun theBallLeavesWithTheVelocityItHadAndAWeakOneStillMoves() {
        for (speed in listOf(30.0, 300.0, 1200.0)) {
            val s = GameSession(level(bounces = 3))
            assertTrue(s.launchAt(0.0, -speed))
            assertEquals(GameSession.State.MOVING, s.state)
            assertEquals(speed, s.ball.speed, 1e-9)
            assertEquals(-1.0, s.ball.dirY, 1e-9)
        }
        val fast = GameSession(level(bounces = 3))
        assertTrue(fast.launchAt(6000.0, 8000.0)) // faster than the top speed: limited to it
        assertEquals(fast.level.maxSpeed, fast.ball.speed, 1e-9)
        assertEquals(0.6, fast.ball.dirX, 1e-9)
        val still = GameSession(level(bounces = 3))
        assertFalse(still.launchAt(0.0, 0.0))
        assertFalse(still.launchAt(1e-6, 0.0)) // slower than a ball that would be dead at once
        assertEquals(GameSession.State.AIMING, still.state)
        assertFalse("only before the first throw", fast.launchAt(100.0, 0.0))
    }

    @Test
    fun aWeakThrowTravelsLessFarThanAStrongOne() {
        fun reach(speed: Double): Double {
            val s = GameSession(LevelParser.parse("open", """{"size": [100000, 1000], "bounces": 0, "ball": [100, 500], "goal": [99000, 500], "drag": 0.25}"""))
            s.launchAt(speed, 0.0)
            var guard = 0
            while (s.state == GameSession.State.MOVING && guard++ < 400_000) s.step()
            return s.ball.x - 100
        }
        val weak = reach(120.0)
        val medium = reach(600.0)
        val strong = reach(2400.0)
        assertTrue("$weak < $medium < $strong", weak > 100 && weak < medium && medium < strong)
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
    fun restartRestoresEverythingAndLeavesNoOldLine() {
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
        assertTrue("no old trajectory is left", s.trail.isEmpty())
    }

    @Test
    fun restartingMidFlightLeavesACleanBallAndNoLine() {
        val s = GameSession(level(bounces = 1))
        s.launch(1.0, 0.0, 1.0)
        repeat(10) { s.step() }
        s.restart()
        assertTrue(s.path.isEmpty())
        assertTrue(s.trail.isEmpty())
        assertEquals(0.0, s.ball.speed, 0.0)
        assertEquals(s.level.ball.x, s.ball.x, 0.0)
        assertEquals(s.level.ball.y, s.ball.y, 0.0)
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
    fun theBallCanBeMovedAroundItsControlZoneButNotThroughWalls() {
        val s = GameSession(level(bounces = 1))
        assertTrue(s.placeBall(100.0, 0.0)) // straight up, 300 away: stops at the zone's edge
        assertEquals(100.0, s.ball.x, 1e-9)
        assertEquals(300.0 - 130.0, s.ball.y, 1e-9)
        s.launch(1.0, 0.0, 1.0)
        assertEquals(300.0 - 130.0, s.path[0].y, 1e-9) // the shot starts where the ball was put
        assertFalse(s.placeBall(100.0, 300.0)) // not while it is flying
        s.restart()
        assertEquals(300.0, s.ball.y, 1e-9) // a restart brings it back to the start

        val wide = GameSession(level(bounces = 1, zone = 600.0))
        wide.placeBall(900.0, 300.0) // towards the middle wall: stops against it, never through it
        val face = 500.0 - wide.level.wallThickness / 2 - wide.level.ballRadius
        assertEquals(face, wide.ball.x, 1e-3)
        assertTrue(wide.ball.x <= face + 1e-6)
    }
}
