package com.carom.core.game

import com.carom.core.level.LevelFormatException
import com.carom.core.level.LevelParser
import com.carom.core.level.Route
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Moving obstacles: one deterministic motion model (line, circle, chain of points; ping-pong or loop; delay, rests, phase,
 * changes of speed), run either from the first throw or from the start of the level, with a ball that bounces off a
 * moving bar the way it should.
 */
class MovingObstaclesTest {

    private fun level(moving: String, clock: String = "launch", extra: String = "") = LevelParser.parse(
        "moving",
        """
        {
          "bounces": 3, "drag": 0.25,
          "controlZone": {"rect": [100, 1400, 700, 500]},
          "ball": [450, 1650], "goal": [450, 300],
          "elements": [ {"kind": "solid", "pos": [200, 900], "scale": [200, 60], "moving": {$moving, "clock": "$clock"}} $extra ]
        }
        """,
    )

    private fun GameSession.at(t: Double): Pair<Double, Double> {
        warpClock(t)
        return elements[0].x to elements[0].y
    }

    @Test
    fun aPlainLineStillSlidesOutAndBackAsBefore() {
        val s = GameSession(level("\"to\": [600, 900], \"period\": 4"))
        s.step() // nothing is moving yet; the barrier stands where the level puts it
        assertEquals(200.0, s.elements[0].x, 1.0)
        val m = s.level.elements[0].moving!!
        val out = DoubleArray(2)
        m.at(0.0, 200.0, 900.0, out); assertEquals(200.0, out[0], 1e-9)
        m.at(2.0, 200.0, 900.0, out); assertEquals(600.0, out[0], 1e-9)
        m.at(4.0, 200.0, 900.0, out); assertEquals(200.0, out[0], 1e-9)
        m.at(1.0, 200.0, 900.0, out); assertEquals(400.0, out[0], 1e-9) // eased: halfway at a quarter of the cycle
    }

    @Test
    fun delayHoldsTheStartThenTheMotionBegins() {
        val m = level("\"to\": [600, 900], \"period\": 4, \"delay\": 1.5, \"wave\": \"linear\"").elements[0].moving!!
        val out = DoubleArray(2)
        for (t in listOf(0.0, 0.7, 1.5)) { m.at(t, 200.0, 900.0, out); assertEquals(200.0, out[0], 1e-9) }
        m.at(2.5, 200.0, 900.0, out)
        assertEquals(400.0, out[0], 1e-9) // one second into a leg of two seconds that covers 400
    }

    @Test
    fun restsAtTheEndsOfAPingPong() {
        val m = level("\"to\": [600, 900], \"period\": 6, \"pause\": 1, \"wave\": \"linear\"").elements[0].moving!!
        val out = DoubleArray(2)
        m.at(0.5, 200.0, 900.0, out); assertEquals(200.0, out[0], 1e-9)
        m.at(2.0, 200.0, 900.0, out); assertEquals(400.0, out[0], 1e-9) // 1 s into the 2 s out-leg
        m.at(3.5, 200.0, 900.0, out); assertEquals(600.0, out[0], 1e-9)
        m.at(5.0, 200.0, 900.0, out); assertEquals(400.0, out[0], 1e-9)
    }

    @Test
    fun aCircleOrbitsItsCentre() {
        val m = level("\"center\": [450, 900], \"radius\": 250, \"angle\": 0, \"period\": 8").elements[0].moving!!
        assertEquals(Route.CIRCLE, m.route)
        val out = DoubleArray(2)
        m.at(0.0, 0.0, 0.0, out); assertEquals(700.0, out[0], 1e-6); assertEquals(900.0, out[1], 1e-6)
        m.at(2.0, 0.0, 0.0, out); assertEquals(450.0, out[0], 1e-6); assertEquals(1150.0, out[1], 1e-6) // a quarter turn, clockwise on the screen
        m.at(8.0, 0.0, 0.0, out); assertEquals(700.0, out[0], 1e-6)
        for (i in 0..40) { m.at(i * 0.2, 0.0, 0.0, out); assertEquals(250.0, hypot(out[0] - 450.0, out[1] - 900.0), 1e-6) }
    }

    @Test
    fun aClosedChainIsGoneRoundAndComesBackToItsStart() {
        val m = level("\"points\": [600, 900, 600, 1200, 200, 1200], \"mode\": \"loop\", \"period\": 10").elements[0].moving!!
        val out = DoubleArray(2)
        m.at(0.0, 200.0, 900.0, out); assertEquals(200.0, out[0], 1e-9); assertEquals(900.0, out[1], 1e-9)
        m.at(10.0, 200.0, 900.0, out); assertEquals(200.0, out[0], 1e-6); assertEquals(900.0, out[1], 1e-6)
        m.at(10.0 * 400 / 1400, 200.0, 900.0, out); assertEquals(600.0, out[0], 1e-6); assertEquals(900.0, out[1], 1e-6) // 400 of the 1400 round it
        assertTrue(out[0].isFinite())
    }

    @Test
    fun aMotionCanBeGivenBySpeedInsteadOfPeriod() {
        val m = level("\"to\": [600, 900], \"speed\": 200, \"wave\": \"linear\"").elements[0].moving!!
        assertEquals(4.0, m.period, 1e-9) // 400 units there and 400 back at 200 units per second: 4 s
    }

    @Test
    fun changingTheSpeedNeverMakesItGoBackwardsOrJump() {
        val m = level("\"to\": [600, 900], \"period\": 4, \"speedVar\": 0.8, \"wave\": \"linear\"").elements[0].moving!!
        val out = DoubleArray(2)
        var last = 200.0
        var maxStep = 0.0
        var t = 0.0
        while (t <= 2.0) { m.at(t, 200.0, 900.0, out); assertTrue("never backwards on the way out", out[0] >= last - 1e-9); maxStep = maxOf(maxStep, out[0] - last); last = out[0]; t += 1.0 / 120 }
        m.at(0.0, 200.0, 900.0, out); val a = out[0]
        m.at(2.0, 200.0, 900.0, out); assertEquals(200.0, a, 1e-9); assertEquals(600.0, out[0], 1e-9)
        assertTrue("speeds change (the fastest step is more than a constant-speed step of ${400.0 / 240})", maxStep > 400.0 / 240 * 1.4)
    }

    @Test
    fun everyMotionMovesSmoothlyNeverJumping() {
        val configs = listOf(
            "\"to\": [600, 900], \"period\": 3",
            "\"to\": [600, 900], \"period\": 3, \"pause\": 0.5, \"delay\": 1",
            "\"center\": [450, 900], \"radius\": 250, \"period\": 5",
            "\"center\": [450, 900], \"radius\": 250, \"period\": 5, \"mode\": \"pingpong\", \"sweep\": 120",
            "\"points\": [600, 900, 600, 1200], \"mode\": \"loop\", \"period\": 6",
            "\"points\": [600, 900, 600, 1200], \"period\": 6, \"speedVar\": 0.5",
        )
        for (c in configs) {
            val level = level(c)
            val m = level.elements[0].moving!!
            val sx = level.elements[0].x
            val sy = level.elements[0].y
            val out = DoubleArray(2)
            m.at(0.0, sx, sy, out)
            var px = out[0]
            var py = out[1]
            var t = 0.0
            while (t <= 14.0) {
                t += 1.0 / 120
                m.at(t, sx, sy, out)
                assertTrue("$c jumps at t=$t", hypot(out[0] - px, out[1] - py) < 12.0) // under 1440 units per second
                px = out[0]; py = out[1]
            }
        }
    }

    @Test
    fun theSameTimeAlwaysGivesTheSamePlace() {
        val a = GameSession(level("\"to\": [600, 900], \"period\": 3.7, \"speedVar\": 0.3", "level"))
        val b = GameSession(level("\"to\": [600, 900], \"period\": 3.7, \"speedVar\": 0.3", "level"))
        a.warpClock(1.234)
        // b gets there by living through it, frame by frame
        repeat(148) { b.advance(1.0 / 120) } // 148 steps = 1.2333 s
        b.warpClock(1.234)
        assertEquals(a.elements[0].x, b.elements[0].x, 1e-12)
        assertEquals(a.elements[0].y, b.elements[0].y, 1e-12)
    }

    @Test
    fun aLevelClockObstacleMovesWhileThePlayerAims() {
        val s = GameSession(level("\"to\": [600, 900], \"period\": 4, \"wave\": \"linear\"", "level"))
        assertTrue(s.animatesWhileAiming)
        val x0 = s.elements[0].x
        repeat(120) { s.advance(1.0 / 120) } // one second of aiming
        assertEquals(1.0, s.levelTime, 1e-6)
        assertNotEquals(x0, s.elements[0].x, 1.0)
        assertEquals(400.0, s.elements[0].x, 1.0)
    }

    @Test
    fun aLaunchClockObstacleWaitsForTheThrow() {
        val s = GameSession(level("\"to\": [600, 900], \"period\": 4"))
        assertFalse(s.animatesWhileAiming)
        repeat(240) { s.advance(1.0 / 120) }
        assertEquals(200.0, s.elements[0].x, 1e-9)
    }

    @Test
    fun theThrowDoesNotRestartALevelClockMotionAndARetryDoes() {
        val s = GameSession(level("\"to\": [600, 900], \"period\": 4, \"wave\": \"linear\"", "level"))
        repeat(60) { s.advance(1.0 / 120) } // half a second
        val before = s.elements[0].x
        s.launchAt(0.0, -300.0)
        assertEquals(before, s.elements[0].x, 1e-9) // the throw leaves it where it is
        repeat(60) { s.step() }
        assertEquals(600.0 * 0 + 200.0 + 400.0 * (1.0 / 2.0), s.elements[0].x, 1.0) // a second after the start of the level
        s.restart()
        assertEquals(200.0, s.elements[0].x, 1e-9) // the retry starts the pattern again
        assertEquals(0.0, s.levelTime, 1e-12)
    }

    @Test
    fun theWorldWaitsWhileTheBallIsHeldAfterAThrow() {
        val s = GameSession(level("\"to\": [600, 900], \"period\": 4", "level"))
        s.launchAt(0.0, -200.0)
        repeat(30) { s.step() }
        s.regrab()
        val t = s.levelTime
        repeat(120) { s.advance(1.0 / 120) }
        assertEquals(t, s.levelTime, 1e-12)
    }

    @Test
    fun theBallBouncesOffABarThatMovesIntoItAndNeverEndsInsideIt() {
        // A wide bar comes down the room at 133 units/s into a ball thrown slowly up at it.
        val s = GameSession(
            LevelParser.parse(
                "sweep",
                """
                {
                  "bounces": 5, "drag": 0.25,
                  "controlZone": {"rect": [100, 1400, 700, 500]},
                  "ball": [450, 1650], "goal": [450, 100],
                  "elements": [ {"kind": "solid", "pos": [450, 900], "scale": [700, 60], "moving": {"to": [450, 1300], "period": 6, "wave": "linear"}} ]
                }
                """,
            ),
        )
        s.launchAt(0.0, -240.0)
        var inside = 0
        var maxSpeed = 0.0
        var bounced = false
        repeat(1500) {
            s.step()
            val b = s.balls[0]
            if (b.alive) {
                val bar = s.elements[0]
                if (kotlin.math.abs(b.body.x - bar.x) < bar.sx / 2 && kotlin.math.abs(b.body.y - bar.y) < bar.sy / 2) inside++
                maxSpeed = maxOf(maxSpeed, b.body.speed)
                if (b.left < 5) bounced = true
            }
        }
        assertTrue("the ball met the bar", bounced)
        assertEquals("the ball is never inside the bar", 0, inside)
        assertTrue("the push of a moving bar stays modest ($maxSpeed)", maxSpeed < 0.6 * s.level.maxSpeed)
    }

    @Test
    fun badMotionsAreRefused() {
        for (m in listOf("\"period\": 4", "\"path\": \"circle\", \"period\": 4", "\"to\": [1, 1], \"speedVar\": 1.5", "\"to\": [1, 1], \"period\": 2, \"pause\": 1.2")) {
            try {
                level(m)
                org.junit.Assert.fail("accepted: $m")
            } catch (e: LevelFormatException) {
                // expected
            }
        }
    }
}
