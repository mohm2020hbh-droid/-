package com.carom.core.game

import com.carom.core.level.LevelData
import com.carom.core.level.LevelParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The clock takes speed from the ball and nothing else: half of it (much more when it was slow), once per entry, in the same
 * direction. It never speeds anything up, never touches time or the other elements, and is no wall.
 */
class ClockTest {

    private fun level(elements: String, w: Int = 8000, extra: String = "", obstacles: String = ""): LevelData = LevelParser.parse(
        "t",
        """{"size": [$w, 2000], "bounces": 3, "ball": [200, 1000], "goal": [7800, 200], "launchZone": 0 $extra,
           "obstacles": [$obstacles], "elements": [$elements]}""",
    )

    private class Log : GameSession.Listener {
        val entries = ArrayList<Pair<Double, Double>>()
        override fun onClock(ball: Int, element: Int, speedBefore: Double, speedAfter: Double) { entries.add(speedBefore to speedAfter) }
    }

    private fun session(lvl: LevelData, log: Log? = null) = GameSession(lvl).also { if (log != null) it.listener = log }

    private val clockAt1000 = """{"kind": "clock", "pos": [1000, 1000], "scale": [500, 500]}"""

    private fun GameSession.steps(n: Int) { repeat(n) { step() } }

    /** Speed in ref units (the level's top speed is 120 of them). */
    private fun GameSession.ref(): Double = ball.speed / (level.maxSpeed / 120.0)

    @Test
    fun aFastBallLeavesWithHalfItsSpeed() {
        val log = Log()
        val s = session(level(clockAt1000, extra = ""","drag":0"""), log)
        val v = 100.0 * s.level.maxSpeed / 120.0
        s.applyImpulse(v, 0.0)
        s.steps(300)
        assertEquals("one entry", 1, log.entries.size)
        assertEquals(v * 0.5, s.ball.speed, 1e-6)
        assertTrue("it went on", s.ball.x > 1300.0)
    }

    @Test
    fun theHalvingHappensOnceNotEveryStepInsideTheClock() {
        val log = Log()
        val s = session(level(clockAt1000, extra = ""","drag":0"""), log)
        val v = 100.0 * s.level.maxSpeed / 120.0
        s.applyImpulse(v, 0.0)
        val inside = ArrayList<Double>()
        repeat(300) {
            s.step()
            if (hypot(s.ball.x - 1000.0, s.ball.y - 1000.0) < 240.0) inside.add(s.ball.speed)
        }
        assertTrue("the ball spent many steps inside", inside.size > 20)
        for (sp in inside) assertEquals("the same speed all the way through", v * 0.5, sp, 1e-6)
        assertEquals(1, log.entries.size)
    }

    @Test
    fun theClockNeverChangesTheDirection() {
        val s = session(level(clockAt1000, extra = ""","drag":0"""))
        s.applyImpulse(1400.0, 150.0)
        val before = atan2(s.ball.dirY, s.ball.dirX)
        s.steps(240)
        assertEquals(before, atan2(s.ball.dirY, s.ball.dirX), 1e-9)
        assertTrue("it went on", s.ball.x > 1500.0)
    }

    @Test
    fun aWeakBallAllButStopsInTheClock() {
        val unit = 2400.0 / 120.0
        val near = """{"kind": "clock", "pos": [600, 1000], "scale": [400, 400]}"""
        for ((enter, maxAfter) in listOf(30.0 to 5.0, 20.0 to 2.0, 10.0 to 0.6)) {
            val s = session(level(near, extra = ""","drag":0"""))
            s.applyImpulse(enter * unit, 0.0)
            s.steps(480)
            val after = if (s.state != GameSession.State.MOVING) 0.0 else s.ref()
            assertTrue("$enter ref units in, ${"%.2f".format(after)} out (at most $maxAfter)", after <= maxAfter)
        }
    }

    @Test
    fun aStrongBallStillHasHalfItsSpeedAndTheMiddleOnesLoseMore() {
        val unit = 2400.0 / 120.0
        assertEquals(50.0, GameSession(level(clockAt1000)).dampedByClock(100.0 * unit) / unit, 1e-9)
        assertEquals(30.0, GameSession(level(clockAt1000)).dampedByClock(60.0 * unit) / unit, 1e-9)
        val mid = GameSession(level(clockAt1000)).dampedByClock(40.0 * unit) / unit
        assertTrue("40 in, less than 20 out: $mid", mid < 20.0 && mid > 5.0)
    }

    @Test
    fun theClockOnlyTakesSpeedNeverGivesAny() {
        val s = GameSession(level(clockAt1000))
        var v = 0.5
        while (v <= 2400.0) {
            assertTrue("$v", s.dampedByClock(v) <= v * 0.5 + 1e-9)
            assertTrue(s.dampedByClock(v) >= 0.0)
            v *= 1.07
        }
    }

    @Test
    fun comingBackIntoTheClockCostsSpeedAgain() {
        val log = Log()
        // a wall beyond the clock sends the ball back through it; no drag, so every speed is the clock's doing
        val s = session(level(clockAt1000, w = 3000, extra = ""","drag":0""", obstacles = """{"type": "wall", "points": [2000, 0, 2000, 2000]}"""), log)
        val v = 120.0 * s.level.maxSpeed / 120.0
        s.applyImpulse(v, 0.0)
        s.steps(300)
        assertEquals("in, back in again", 2, log.entries.size)
        assertEquals(v * 0.5, log.entries[0].second, 1e-6)
        assertEquals(v * 0.25, log.entries[1].second, 1e-6)
    }

    @Test
    fun aThinClockCrossedInOneStepStillCountsOnce() {
        val log = Log()
        val thin = """{"kind": "clock", "pos": [1000, 1000], "scale": [30, 600], "shape": "rect"}"""
        val s = session(level(thin, extra = ""","drag":0"""), log)
        s.applyImpulse(2400.0, 0.0) // 20 units a step
        s.steps(300)
        assertEquals(1, log.entries.size)
    }

    @Test
    fun theClockIsNotAWallAndLeavesTimeAndOtherElementsAlone() {
        val mover = """{"kind": "solid", "pos": [3000, 1000], "scale": [60, 400], "moving": {"to": [3000, 1600], "period": 3}}"""
        val with = session(level("$clockAt1000, $mover", extra = ""","drag":0"""))
        val without = session(level(mover, extra = ""","drag":0"""))
        with.applyImpulse(1200.0, 0.0)
        without.applyImpulse(1200.0, 0.0)
        repeat(240) {
            with.step()
            without.step()
            assertEquals("time runs the same", without.gameTime, with.gameTime, 0.0)
            assertEquals("the moving bar goes where it always goes", without.elements[0].y, with.elements[1].y, 1e-9)
        }
        assertTrue("the ball passed through the clock without a bounce", with.bouncesLeft == with.balls[0].total)
    }

    @Test
    fun aRetryOrAStartInsideAClockStartsFresh() {
        val log = Log()
        val s = session(level(clockAt1000, extra = ""","drag":0"""), log)
        s.applyImpulse(1500.0, 0.0)
        s.steps(600)
        assertEquals(1, log.entries.size)
        s.restart()
        s.applyImpulse(1500.0, 0.0)
        s.steps(600)
        assertEquals("the second attempt pays the clock again", 2, log.entries.size)
        assertTrue(abs(log.entries[0].second - log.entries[1].second) < 1e-9)
    }
}
