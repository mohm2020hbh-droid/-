package com.carom.core.game

import com.carom.core.level.LevelParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSessionTest {

    /** A 1000×600 box; ball on the left, goal on the right, a wall between them. */
    private fun level(bounces: Int, friction: Double = 0.0, extra: String = "") = LevelParser.parse(
        "test",
        """
        {
          "size": [1000, 600], "bounces": $bounces, "friction": $friction,
          "ball": [100, 300], "goal": [900, 300],
          "obstacles": [ {"type": "wall", "points": [500, 150, 500, 450]} $extra ]
        }
        """,
    )

    private fun GameSession.runToEnd(): GameSession {
        var guard = 0
        while (state == GameSession.State.MOVING && guard++ < 100_000) step()
        return this
    }

    /** Aim at a point by mirroring the goal in the top wall, so the shot bounces once to score. */
    private fun GameSession.bankShotOffTop() {
        val topInner = level.wallThickness / 2 + level.ballRadius
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
            override fun onBounce(x: Double, y: Double, bouncesLeft: Int) {
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
            """{"size": [100000, 1000], "bounces": 0, "ball": [100, 500], "goal": [99000, 500], "friction": 600}""",
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
    fun aimFiresOppositeToThePullWithProportionalPower() {
        val aim = SlingshotAim(maxPull = 200.0)
        assertFalse(aim.tryBegin(500.0, 500.0, ballX = 100.0, ballY = 300.0, grabRadius = 60.0))
        assertTrue(aim.tryBegin(120.0, 310.0, ballX = 100.0, ballY = 300.0, grabRadius = 60.0))
        aim.drag(20.0, 310.0) // pulled 100 units to the left
        assertEquals(0.5, aim.power, 1e-9)
        assertEquals(1.0, aim.dirX, 1e-9)
        assertEquals(0.0, aim.dirY, 1e-9)
        aim.drag(-500.0, 310.0) // beyond max pull: power is capped
        assertEquals(1.0, aim.power, 1e-9)

        val s = GameSession(level(bounces = 1))
        assertTrue(aim.release(s))
        assertEquals(GameSession.State.MOVING, s.state)
        assertEquals(1.0, s.ball.dirX, 1e-9)
    }

    @Test
    fun tinyPullIsACancelNotAShot() {
        val aim = SlingshotAim(maxPull = 200.0)
        aim.tryBegin(100.0, 300.0, 100.0, 300.0, 50.0)
        aim.drag(95.0, 300.0)
        assertFalse(aim.isShotReady)
        val s = GameSession(level(bounces = 1))
        assertFalse(aim.release(s))
        assertEquals(GameSession.State.AIMING, s.state)
    }
}
