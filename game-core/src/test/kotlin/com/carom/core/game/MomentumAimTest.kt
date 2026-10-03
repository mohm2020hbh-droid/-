package com.carom.core.game

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hold / move / let go control: the ball goes where the finger takes it and, when the finger lets go, leaves with the
 * velocity it had. Numbers are world units and seconds; the tests drive a finger in straight lines at a steady rate of
 * 120 events a second, like a touch screen.
 */
class MomentumAimTest {

    private val dt = 1.0 / 120

    /** A ball and a finger. The ball is placed where the aim wants it, inside a box (x and y clamped), like the real zone. */
    private class Rig(val aim: MomentumAim, val minX: Double = -1e9, val maxX: Double = 1e9, val minY: Double = -1e9, val maxY: Double = 1e9) {
        var ballX = 450.0
        var ballY = 1500.0
        var fx = ballX
        var fy = ballY
        var t = 0.0

        fun down() {
            fx = ballX
            fy = ballY
            aim.begin(fx, fy, ballX, ballY, t)
        }

        fun moveTo(x: Double, y: Double) {
            fx = x
            fy = y
            aim.move(x, y, t)
            ballX = aim.wantX.coerceIn(minX, maxX)
            ballY = aim.wantY.coerceIn(minY, maxY)
        }

        /** The finger goes on at [vx], [vy] for [seconds]. */
        fun go(vx: Double, vy: Double, seconds: Double, step: Double = 1.0 / 120) {
            val n = Math.round(seconds / step).toInt()
            repeat(n) {
                t += step
                moveTo(fx + vx * step, fy + vy * step)
            }
        }

        fun rest(seconds: Double, step: Double = 1.0 / 120) {
            repeat(Math.round(seconds / step).toInt()) {
                t += step
                moveTo(fx, fy)
            }
        }
    }

    private fun rig(vararg limits: Double) =
        if (limits.size == 4) Rig(MomentumAim(), limits[0], limits[1], limits[2], limits[3]) else Rig(MomentumAim())

    @Test
    fun theBallGoesWhereTheFingerTakesItKeepingTheGripItHad() {
        val r = rig()
        r.fx = 500.0 // grabbed 50 to the right of its middle and 30 above
        r.fy = 1470.0
        r.aim.begin(r.fx, r.fy, r.ballX, r.ballY, 0.0)
        assertEquals("nothing jumps on the first touch", 450.0, r.aim.wantX, 1e-9)
        assertEquals(1500.0, r.aim.wantY, 1e-9)
        r.aim.move(600.0, 1400.0, 0.01)
        assertEquals(550.0, r.aim.wantX, 1e-9)
        assertEquals(1430.0, r.aim.wantY, 1e-9)
    }

    @Test
    fun theBallLeavesWithTheVelocityItHadWhateverItsSpeed() {
        for (speed in listOf(40.0, 150.0, 400.0, 900.0, 1800.0, 2400.0)) {
            val r = rig()
            r.down()
            r.go(0.6 * speed, -0.8 * speed, 0.25)
            val out = r.aim.release(r.t)
            assertNotNull("a movement of $speed throws", out)
            assertEquals("speed $speed", speed, out!!.speed, speed * 0.02)
            assertEquals("direction x", 0.6, out.vx / out.speed, 1e-6)
            assertEquals("direction y", -0.8, out.vy / out.speed, 1e-6)
        }
    }

    @Test
    fun aVerySlowMovementStillThrowsAndThereIsNoDeadZone() {
        for (speed in listOf(0.5, 2.0, 5.0, 12.0)) {
            val r = rig()
            r.down()
            r.go(speed, 0.0, 0.5)
            val out = r.aim.release(r.t)
            assertNotNull("even $speed units per second throws", out)
            assertEquals(speed, out!!.speed, speed * 0.02)
        }
    }

    @Test
    fun aBallThatRestedBeforeTheFingerLiftedThrowsNothing() {
        val r = rig()
        r.down()
        r.go(900.0, 0.0, 0.2)
        r.rest(0.15) // stopped for longer than the window
        assertNull(r.aim.release(r.t))
    }

    @Test
    fun aTouchThatNeverMovedThrowsNothing() {
        val r = rig()
        r.down()
        r.rest(0.3)
        assertNull(r.aim.release(r.t))
        val q = rig()
        q.down()
        assertNull("lifting at once", q.aim.release(0.0))
    }

    @Test
    fun aMovementThatDiesAwayBeforeTheLiftIsWeakerThanOneThatDoesNot() {
        val steady = rig()
        steady.down()
        steady.go(1000.0, 0.0, 0.3)
        val fast = steady.aim.release(steady.t)!!.speed

        val fading = rig()
        fading.down()
        fading.go(1000.0, 0.0, 0.3)
        fading.rest(0.04) // half the window at rest
        val slower = fading.aim.release(fading.t)!!.speed
        assertTrue("$slower vs $fast", slower < 0.65 * fast && slower > 0.35 * fast)
    }

    @Test
    fun onlyTheLastMomentsBeforeTheLiftCount() {
        val r = rig()
        r.down()
        r.go(2000.0, 0.0, 0.2) // a fast start...
        r.go(200.0, 0.0, 0.2) // ...then a gentle finish
        val out = r.aim.release(r.t)!!
        assertEquals(200.0, out.speed, 15.0)
    }

    @Test
    fun aCurvedMovementLeavesAlongTheDirectionItEnded() {
        val r = rig()
        r.down()
        r.go(500.0, 0.0, 0.2)
        r.go(0.0, -500.0, 0.2) // turned a corner
        val out = r.aim.release(r.t)!!
        assertTrue("goes up, not right (${out.vx}, ${out.vy})", out.vy < -450 && kotlin.math.abs(out.vx) < 30)
    }

    @Test
    fun aFlickThatRunsPastTheEdgeOfTheZoneKeepsItsSpeedBecauseNobodyCanSeeTheEdge() {
        val r = rig(0.0, 1e9, 1400.0, 1e9) // a ceiling at y = 1400: the ball cannot go above it
        r.down()
        r.go(0.0, -1200.0, 0.3) // the finger flicks up for 360 units; the ball is stopped after the first 100
        assertEquals(1400.0, r.ballY, 1e-9)
        val out = r.aim.release(r.t)
        assertNotNull(out)
        assertEquals(1200.0, out!!.speed, 60.0)
        assertTrue(out.vy < 0.0 && kotlin.math.abs(out.vx) < 1e-6)
    }

    @Test
    fun theThrowDoesNotDependOnHowOftenTheScreenReportsTheFinger() {
        val speeds = listOf(30.0, 60.0, 120.0, 240.0).map { rate ->
            val r = rig()
            r.down()
            r.go(700.0, 0.0, 0.3, step = 1.0 / rate)
            r.aim.release(r.t)!!.speed
        }
        speeds.forEach { assertEquals(700.0, it, 25.0) }
    }

    @Test
    fun aLiftJustAfterTheLastEventKeepsTheSpeed() {
        val r = rig()
        r.down()
        r.go(600.0, 0.0, 0.3)
        val out = r.aim.release(r.t + 0.004) // the up event a few milliseconds after the last move
        assertEquals(600.0, out!!.speed, 60.0)
    }

    @Test
    fun cancellingOrReleasingEndsTheHold() {
        val r = rig()
        r.down()
        r.go(300.0, 0.0, 0.2)
        assertTrue(r.aim.isActive)
        r.aim.cancel()
        assertFalse(r.aim.isActive)
        assertNull(r.aim.release(r.t))
        val q = rig()
        q.down()
        q.go(300.0, 0.0, 0.2)
        assertNotNull(q.aim.release(q.t))
        assertFalse(q.aim.isActive)
        assertNull("a second release throws nothing", q.aim.release(q.t))
    }

    @Test
    fun theGainScalesTheVelocity() {
        val r = Rig(MomentumAim(gain = 1.5))
        r.down()
        r.go(400.0, 0.0, 0.3)
        assertEquals(600.0, r.aim.release(r.t)!!.speed, 25.0)
        assertEquals(hypot(3.0, 4.0), 5.0, 1e-12)
    }
}
