package com.carom.core.game

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hold / pull / release control. Numbers are world units: the ball follows at up to 1750 per second, a finger
 * 75 away has pulled away, a pull of 75 is the least that throws, 275 is full power. The launch zone is the disc of
 * radius 130 round (450, 1500), which the test stands in for when it "places" the ball.
 */
class PullAimTest {
    private val cx = 450.0
    private val cy = 1500.0
    private val zone = 130.0

    private fun aim(flick: Boolean = false) = if (flick) {
        PullAim(1750.0, 75.0, 40.0, 75.0, 275.0, flickStart = 2250.0, flickFull = 6500.0, flickBoost = 0.25)
    } else {
        PullAim(1750.0, 75.0, 40.0, 75.0, 275.0)
    }

    /** A little world: the ball, and the finger moving in a straight line at a given speed, one frame at a time. */
    private class Rig(val aim: PullAim, val zoneX: Double, val zoneY: Double, val zoneR: Double) {
        var ballX = zoneX
        var ballY = zoneY
        var fx = zoneX
        var fy = zoneY
        var t = 0.0

        fun down(x: Double = ballX, y: Double = ballY) {
            fx = x
            fy = y
            aim.begin(x, y, ballX, ballY, t)
        }

        private fun place() {
            if (aim.phase != PullAim.Phase.HOLDING) return
            var x = aim.wantX
            var y = aim.wantY
            val d = hypot(x - zoneX, y - zoneY)
            if (d > zoneR) {
                x = zoneX + (x - zoneX) / d * zoneR
                y = zoneY + (y - zoneY) / d * zoneR
            }
            ballX = x
            ballY = y
        }

        /** Moves the finger to (x, y) at [speed] world units per second, 60 frames a second, the ball following. */
        fun drag(x: Double, y: Double, speed: Double) {
            val dist = hypot(x - fx, y - fy)
            val frames = maxOf(1, Math.ceil(dist / speed * 60).toInt())
            val x0 = fx
            val y0 = fy
            for (i in 1..frames) {
                t += 1.0 / 60
                fx = x0 + (x - x0) * i / frames
                fy = y0 + (y - y0) * i / frames
                aim.move(fx, fy, t, ballX, ballY)
                place()
            }
        }

        fun rest(seconds: Double) {
            var left = seconds
            while (left > 1e-9) {
                val dt = minOf(left, 1.0 / 60)
                t += dt
                aim.advanceBy(dt, ballX, ballY)
                place()
                left -= dt
            }
        }

        fun up() = aim.release(fx, fy, t, ballX, ballY)
    }

    private fun rig(a: PullAim = aim()) = Rig(a, cx, cy, zone)

    @Test
    fun movingTheBallAboutCalmlyCarriesItAndNeverThrows() {
        val r = rig()
        r.down()
        r.drag(cx + 100.0, cy, 500.0)
        assertEquals(PullAim.Phase.HOLDING, r.aim.phase)
        assertEquals(cx + 100.0, r.ballX, 1e-6) // the ball is where the finger took it
        r.drag(cx - 100.0, cy - 60.0, 800.0)
        r.rest(0.3)
        assertEquals(PullAim.Phase.HOLDING, r.aim.phase)
        assertEquals(cx - 100.0, r.ballX, 1e-6)
        assertEquals(cy - 60.0, r.ballY, 1e-6)
        assertFalse(r.aim.isThrowReady)
        assertNull(r.up()) // letting go of a held ball: it stays where it was put
        assertEquals(PullAim.Phase.IDLE, r.aim.phase)
    }

    @Test
    fun aTapOrATinyNudgeThrowsNothing() {
        val r = rig()
        r.down()
        assertNull(r.up())
        val n = rig()
        n.down()
        n.drag(cx + 20.0, cy, 300.0)
        assertNull(n.up())
    }

    @Test
    fun aHardPullLeavesTheBallBehindAndLetGoThrowsWayItWasPulled() {
        val r = rig()
        r.down()
        r.drag(cx, cy - 200.0, 6000.0) // a quick pull straight up, much faster than the ball can follow
        assertEquals(PullAim.Phase.PULLING, r.aim.phase)
        assertTrue("the ball stayed near where it was", hypot(r.ballX - cx, r.ballY - cy) < 60.0)
        val ballY = r.ballY
        r.rest(0.2)
        assertEquals("a pulling ball does not follow the finger", ballY, r.ballY, 1e-9)
        val t = r.up()!!
        assertEquals(0.0, t.dirX, 1e-9)
        assertEquals(-1.0, t.dirY, 1e-9)
        val length = hypot(cx - cx, (cy - 200.0) - ballY)
        assertEquals(length / 275.0, t.power, 1e-9) // strength from how far the finger is from the ball
    }

    @Test
    fun aSlowDragOutPastTheEdgeOfTheZoneIsAPullToo() {
        val r = rig()
        r.down()
        r.drag(cx + 230.0, cy, 500.0) // the ball goes to the edge (130) and cannot follow any further
        assertEquals(PullAim.Phase.PULLING, r.aim.phase)
        assertEquals(cx + zone, r.ballX, 1e-6)
        r.drag(cx + 260.0, cy, 500.0)
        val t = r.up()!!
        assertEquals(1.0, t.dirX, 1e-9)
        assertEquals(0.0, t.dirY, 1e-9)
        assertEquals((260.0 - zone) / 275.0, t.power, 1e-6)
    }

    @Test
    fun thePowerGrowsWithThePullAndStopsAtFull() {
        fun powerFor(pull: Double): Double {
            val r = rig()
            r.down()
            r.drag(cx, cy + pull, 8000.0)
            return r.up()!!.power
        }
        assertTrue(powerFor(150.0) < powerFor(250.0))
        assertTrue(powerFor(250.0) < powerFor(400.0))
        assertEquals(1.0, powerFor(1200.0), 1e-9)
    }

    @Test
    fun aPullTooShortToCountThrowsNothing() {
        val r = rig()
        r.down()
        r.drag(cx, cy + 90.0, 8000.0) // detaches (over 75) ...
        assertEquals(PullAim.Phase.PULLING, r.aim.phase)
        r.drag(cx, cy + 60.0, 800.0) // ... then eases back to a pull under the least that throws
        assertFalse(r.aim.isThrowReady)
        assertNull(r.up())
    }

    @Test
    fun bringingTheFingerBackToTheBallCancelsThePull() {
        val r = rig()
        r.down()
        r.drag(cx, cy - 250.0, 6000.0)
        assertEquals(PullAim.Phase.PULLING, r.aim.phase)
        r.drag(r.ballX, r.ballY + 10.0, 1200.0) // back onto the ball
        assertEquals(PullAim.Phase.HOLDING, r.aim.phase)
        assertNull(r.up())
        // and it is held again: it follows the finger from where it is
        val again = rig()
        again.down()
        again.drag(cx, cy - 250.0, 6000.0)
        again.drag(again.ballX, again.ballY, 1200.0)
        again.drag(again.ballX + 60.0, again.ballY, 400.0)
        assertEquals(PullAim.Phase.HOLDING, again.aim.phase)
        assertEquals(cx + 60.0, again.ballX, 1e-6)
    }

    @Test
    fun theBallDoesNotJumpToTheFingerWhereverItWasGrabbed() {
        val r = rig()
        r.down(cx + 100.0, cy - 40.0) // grabbed away from the ball's centre
        r.rest(0.1)
        assertEquals(cx, r.ballX, 1e-9)
        assertEquals(cy, r.ballY, 1e-9)
        r.drag(cx + 130.0, cy - 40.0, 500.0) // the finger moves 30: so does the ball
        assertEquals(cx + 30.0, r.ballX, 1e-6)
    }

    @Test
    fun aFastFlickAddsPower() {
        val plain = rig(aim())
        plain.down()
        plain.drag(cx, cy - 200.0, 3000.0)
        plain.rest(0.3) // the finger rests before it lets go: no boost
        val soft = plain.up()!!.power

        val quick = rig(aim(flick = true))
        quick.down()
        quick.drag(cx, cy - 200.0, 9000.0)
        val boosted = quick.up()!!.power // lets go while still moving fast
        assertTrue("$boosted > $soft", boosted > soft)
        assertTrue(boosted <= 1.0)
    }

    @Test
    fun afterTheThrowTheAimIsOverAndTheFingerIsForgotten() {
        val r = rig()
        r.down()
        r.drag(cx, cy - 250.0, 6000.0)
        assertNotNull(r.up())
        assertFalse(r.aim.isActive)
        r.drag(cx + 100.0, cy + 100.0, 500.0) // the finger keeps moving: nothing is listening
        assertNull(r.aim.release(0.0, 0.0, r.t, r.ballX, r.ballY))
    }

    @Test
    fun theSameGestureIsTheSameAtAnyFrameRate() {
        fun throwAt(fps: Int): PullAim.Throw {
            val a = aim()
            val ball = doubleArrayOf(cx, cy)
            a.begin(cx, cy, cx, cy, 0.0)
            val frames = fps / 4 // a quarter of a second
            for (i in 1..frames) {
                val t = i.toDouble() / fps
                a.move(cx + 240.0 * i / frames, cy - 60.0 * i / frames, t, ball[0], ball[1])
                if (a.phase == PullAim.Phase.HOLDING) {
                    val d = hypot(a.wantX - cx, a.wantY - cy)
                    val k = if (d > zone) zone / d else 1.0
                    ball[0] = cx + (a.wantX - cx) * k
                    ball[1] = cy + (a.wantY - cy) * k
                }
            }
            return a.release(cx + 240.0, cy - 60.0, 0.25, ball[0], ball[1])!!
        }
        val a = throwAt(30)
        val b = throwAt(144)
        assertEquals(a.dirX, b.dirX, 0.06)
        assertEquals(a.dirY, b.dirY, 0.06)
        assertEquals(a.power, b.power, 0.08)
    }
}
