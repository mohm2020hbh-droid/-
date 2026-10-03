package com.carom.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class PhysicsWorldTest {

    /** Records contacts and always lets the ball continue. */
    private class Recorder : PhysicsWorld.Listener {
        val contacts = ArrayList<DoubleArray>()
        override fun onWallContact(x: Double, y: Double, nx: Double, ny: Double): Boolean {
            contacts.add(doubleArrayOf(x, y, nx, ny))
            return true
        }

        override fun onTrigger(trigger: CircleTrigger, x: Double, y: Double) = true
    }

    private fun ball(x: Double, y: Double, dirDegrees: Double, radius: Double = 10.0) = Ball(radius).apply {
        place(x, y)
        dirX = cos(Math.toRadians(dirDegrees))
        dirY = sin(Math.toRadians(dirDegrees))
    }

    private fun box(size: Double, thickness: Double = 0.0) = listOf(
        Segment(0.0, 0.0, size, 0.0, thickness / 2),
        Segment(size, 0.0, size, size, thickness / 2),
        Segment(size, size, 0.0, size, thickness / 2),
        Segment(0.0, size, 0.0, 0.0, thickness / 2),
    )

    @Test
    fun headOnHitReflectsBackAndStopsAtTheSurface() {
        val world = PhysicsWorld(listOf(Segment(100.0, -50.0, 100.0, 50.0, 5.0)), emptyList())
        val b = ball(0.0, 0.0, 0.0)
        val rec = Recorder()
        world.move(b, 100.0, rec)

        assertEquals(1, rec.contacts.size)
        // Contact where the ball's edge meets the wall's surface: 100 - 5 - 10.
        assertEquals(85.0, rec.contacts[0][0], 1e-6)
        assertEquals(-1.0, rec.contacts[0][2], 1e-9)
        assertEquals(-1.0, b.dirX, 1e-9)
        // It travelled 85 to the wall and the remaining 15 back.
        assertEquals(70.0, b.x, 1e-3)
    }

    @Test
    fun angleOfReflectionEqualsAngleOfIncidence() {
        val world = PhysicsWorld(listOf(Segment(-1000.0, 100.0, 1000.0, 100.0, 0.0)), emptyList())
        for (angle in listOf(10.0, 30.0, 45.0, 60.0, 85.0)) {
            val b = ball(0.0, 0.0, angle)
            val rec = Recorder()
            world.move(b, 1000.0, rec)
            assertEquals(1, rec.contacts.size)
            val out = Math.toDegrees(atan2(b.dirY, b.dirX))
            assertEquals("reflection of $angle°", -angle, out, 1e-9)
        }
    }

    @Test
    fun fastBallNeverTunnelsThroughAThinWall() {
        val world = PhysicsWorld(listOf(Segment(50.0, -10.0, 50.0, 10.0, 0.0)), emptyList())
        val b = ball(0.0, 0.0, 0.0, radius = 1.0)
        val rec = Recorder()
        // Travel a million units in a single move.
        world.move(b, 1_000_000.0, rec)
        assertEquals(1, rec.contacts.size)
        assertTrue("ball must end on the near side, was at ${b.x}", b.x < 50.0)
    }

    @Test
    fun rightAngleCornerIsOneBounceThatReversesDirection() {
        // Two walls meeting at (100, 100); the ball flies straight into the corner at 45°.
        val world = PhysicsWorld(
            listOf(Segment(0.0, 100.0, 100.0, 100.0, 0.0), Segment(100.0, 0.0, 100.0, 100.0, 0.0)),
            emptyList(),
        )
        val b = ball(0.0, 0.0, 45.0)
        val rec = Recorder()
        world.move(b, 200.0, rec)
        assertEquals(1, rec.contacts.size)
        assertEquals(-sqrt(0.5), b.dirX, 1e-9)
        assertEquals(-sqrt(0.5), b.dirY, 1e-9)
    }

    @Test
    fun wallEndIsRoundSoGlancingHitsDeflectPredictably() {
        // Ball passes the end of a wall with its centre 5 units inside the wall's reach.
        val world = PhysicsWorld(listOf(Segment(100.0, -100.0, 100.0, 0.0, 0.0)), emptyList())
        val b = ball(0.0, 5.0, 0.0)
        val rec = Recorder()
        world.move(b, 50.0 + 100.0, rec)
        assertEquals(1, rec.contacts.size)
        val n = rec.contacts[0]
        // Normal points from the wall's end (100, 0) to the ball's centre at contact.
        val cx = n[0] - 100.0
        val cy = n[1] - 0.0
        assertEquals(10.0, sqrt(cx * cx + cy * cy), 1e-6)
        assertEquals(cx / 10.0, n[2], 1e-9)
        assertEquals(cy / 10.0, n[3], 1e-9)
    }

    @Test
    fun movingAlongAWallDoesNotCountAsContact() {
        val world = PhysicsWorld(listOf(Segment(0.0, 20.0, 1000.0, 20.0, 0.0)), emptyList())
        val b = ball(10.0, 10.0, 0.0) // touching the wall, moving parallel to it
        val rec = Recorder()
        world.move(b, 500.0, rec)
        assertEquals(0, rec.contacts.size)
        assertEquals(510.0, b.x, 1e-9)
    }

    @Test
    fun overlappingBallIsPushedOutInsteadOfSticking() {
        val world = PhysicsWorld(listOf(Segment(0.0, 0.0, 100.0, 0.0, 5.0)), emptyList())
        val b = ball(50.0, 3.0, 90.0) // centre 3 units from a wall it should be 15 away from
        world.move(b, 10.0, Recorder())
        assertTrue("ball must be clear of the wall, y=${b.y}", b.y >= 15.0 - 1e-6)
    }

    @Test
    fun triggerStopsTheBallAtTheEntryPoint() {
        val world = PhysicsWorld(emptyList(), listOf(CircleTrigger(100.0, 0.0, 20.0)))
        val b = ball(0.0, 0.0, 0.0)
        val stopped = !world.move(b, 500.0, object : PhysicsWorld.Listener {
            override fun onWallContact(x: Double, y: Double, nx: Double, ny: Double) = true
            override fun onTrigger(trigger: CircleTrigger, x: Double, y: Double) = false
        })
        assertTrue(stopped)
        assertEquals(80.0, b.x, 1e-9)
    }

    @Test
    fun triggerBehindAWallIsNotReached() {
        val world = PhysicsWorld(
            listOf(Segment(50.0, -100.0, 50.0, 100.0, 0.0)),
            listOf(CircleTrigger(100.0, 0.0, 20.0)),
        )
        var triggered = false
        world.move(ball(0.0, 0.0, 0.0), 200.0, object : PhysicsWorld.Listener {
            override fun onWallContact(x: Double, y: Double, nx: Double, ny: Double) = true
            override fun onTrigger(trigger: CircleTrigger, x: Double, y: Double): Boolean {
                triggered = true
                return false
            }
        })
        assertFalse(triggered)
    }

    @Test
    fun ballNeverLeavesOrPenetratesAClosedBox() {
        // Fuzz: random shots with random step sizes inside a box full of random obstacles.
        val random = Random(1234)
        repeat(200) {
            val segments = ArrayList(box(1000.0, thickness = 10.0))
            repeat(6) {
                val x = 150 + random.nextDouble() * 700
                val y = 150 + random.nextDouble() * 700
                val a = random.nextDouble() * Math.PI
                val len = 50 + random.nextDouble() * 200
                segments.add(Segment(x, y, x + cos(a) * len, y + sin(a) * len, random.nextDouble() * 8))
            }
            val world = PhysicsWorld(segments, emptyList())
            var start: DoubleArray
            do {
                start = doubleArrayOf(40 + random.nextDouble() * 920, 40 + random.nextDouble() * 920)
            } while (world.overlaps(start[0], start[1], 12.0 + 1.0))
            val b = ball(start[0], start[1], random.nextDouble() * 360, radius = 12.0)
            repeat(300) {
                world.move(b, random.nextDouble() * 400, Recorder())
                for (s in segments) {
                    val d = Sweep.distanceToSegment(b.x, b.y, s, Contact())
                    assertTrue("ball penetrated a wall (distance $d)", d >= b.radius + s.radius - 1e-3)
                }
                assertTrue(b.x > 0 && b.x < 1000 && b.y > 0 && b.y < 1000)
            }
        }
    }

    @Test
    fun sameShotAlwaysGivesTheSamePath() {
        fun run(): List<DoubleArray> {
            val world = PhysicsWorld(box(1000.0) + Segment(300.0, 200.0, 700.0, 650.0, 4.0), emptyList())
            val b = ball(100.0, 500.0, 17.3)
            val rec = Recorder()
            repeat(1000) { world.move(b, 7.0, rec) }
            return rec.contacts
        }
        val first = run()
        val second = run()
        assertEquals(first.size, second.size)
        for (i in first.indices) assertTrue(first[i].contentEquals(second[i]))
        assertTrue(first.size > 3)
    }

    @Test
    fun stepSizeDoesNotChangeThePath() {
        fun contacts(step: Double): List<DoubleArray> {
            val world = PhysicsWorld(box(1000.0) + Segment(300.0, 200.0, 700.0, 650.0, 4.0), emptyList())
            val b = ball(100.0, 500.0, 17.3)
            val rec = Recorder()
            var travelled = 0.0
            while (travelled < 6000.0) {
                world.move(b, step, rec)
                travelled += step
            }
            return rec.contacts
        }
        val coarse = contacts(200.0)
        val fine = contacts(3.0)
        assertEquals(coarse.size, fine.size)
        for (i in coarse.indices) {
            assertTrue(abs(coarse[i][0] - fine[i][0]) < 1e-3 && abs(coarse[i][1] - fine[i][1]) < 1e-3)
        }
    }
}
