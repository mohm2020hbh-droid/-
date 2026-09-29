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
          "size": [1000, 600], "bounces": $bounces, "friction": $friction, "launchZone": $zone,
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

    /** Feeds a straight finger movement from (x0, y0) to (x1, y1) over [seconds], 60 samples a second. */
    private fun FlickAim.stroke(x0: Double, y0: Double, x1: Double, y1: Double, t0: Double, seconds: Double) {
        val n = (seconds * 60).toInt().coerceAtLeast(1)
        for (i in 1..n) move(x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n, t0 + seconds * i / n)
    }

    @Test
    fun aStrokeThrowsTheBallItsWayWithPowerFromItsLength() {
        val right = FlickAim(maxStroke = 200.0, restDistance = 4.0)
        right.begin(100.0, 300.0, 0.0)
        right.stroke(100.0, 300.0, 200.0, 300.0, 0.0, 0.1) // 100 units to the right
        assertEquals(0.5, right.power, 1e-9)
        val t = right.release(200.0, 300.0, 0.11)!!
        assertEquals(1.0, t.dirX, 1e-9)
        assertEquals(0.0, t.dirY, 1e-9)
        assertEquals(0.5, t.power, 1e-9)

        val up = FlickAim(maxStroke = 200.0, restDistance = 4.0)
        up.begin(100.0, 300.0, 0.0)
        up.stroke(100.0, 300.0, 100.0, -300.0, 0.0, 0.2) // far upwards: full power, capped
        val u = up.release(100.0, -300.0, 0.21)!!
        assertEquals(-1.0, u.dirY, 1e-9)
        assertEquals(1.0, u.power, 1e-9)
    }

    @Test
    fun movingTheBallIntoPlaceThenSwipingThrowsAlongTheSwipeOnly() {
        val aim = FlickAim(maxStroke = 200.0, restDistance = 4.0)
        aim.begin(100.0, 300.0, 0.0)
        aim.stroke(100.0, 300.0, 180.0, 300.0, 0.0, 0.2) // carry the ball to the right...
        aim.move(180.0, 300.0, 0.5) // ...and hold it there
        assertEquals(80.0, aim.offsetX, 1e-9) // the ball follows the finger
        aim.stroke(180.0, 300.0, 180.0, 200.0, 0.5, 0.1) // then swipe up
        val t = aim.release(180.0, 200.0, 0.61)!!
        assertEquals(0.0, t.dirX, 1e-9)
        assertEquals(-1.0, t.dirY, 1e-9)
        assertEquals(0.5, t.power, 1e-9)
    }

    @Test
    fun turningBackStartsANewStroke() {
        val aim = FlickAim(maxStroke = 200.0, restDistance = 4.0)
        aim.begin(100.0, 300.0, 0.0)
        aim.stroke(100.0, 300.0, 250.0, 300.0, 0.0, 0.15)
        aim.stroke(250.0, 300.0, 150.0, 300.0, 0.15, 0.1)
        val t = aim.release(150.0, 300.0, 0.26)!!
        assertEquals(-1.0, t.dirX, 1e-9)
        assertEquals(0.5, t.power, 1e-6)
    }

    @Test
    fun lettingGoOfAHeldBallOrATinyNudgeThrowsNothing() {
        val held = FlickAim(maxStroke = 200.0, restDistance = 4.0)
        held.begin(100.0, 300.0, 0.0)
        held.stroke(100.0, 300.0, 200.0, 300.0, 0.0, 0.1)
        assertNull(held.release(200.0, 300.0, 0.6)) // rested, then lifted: the ball just stays put

        val nudge = FlickAim(maxStroke = 200.0, restDistance = 4.0)
        nudge.begin(100.0, 300.0, 0.0)
        nudge.stroke(100.0, 300.0, 110.0, 300.0, 0.0, 0.05)
        assertFalse(nudge.isThrowReady)
        assertNull(nudge.release(110.0, 300.0, 0.06))
    }

    @Test
    fun aQuickFlickAddsPower() {
        val aim = FlickAim(maxStroke = 200.0, restDistance = 4.0, flickStart = 500.0, flickFull = 1500.0, flickBoost = 0.25)
        aim.begin(100.0, 300.0, 0.0)
        aim.move(150.0, 300.0, 0.025)
        aim.move(200.0, 300.0, 0.05) // 100 units in 50 ms: 2000 units/s
        assertEquals(0.5, aim.power, 1e-9)
        assertEquals(0.75, aim.release(200.0, 300.0, 0.05)!!.power, 1e-9)
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
