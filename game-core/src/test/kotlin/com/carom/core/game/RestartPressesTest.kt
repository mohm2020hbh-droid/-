package com.carom.core.game

import com.carom.core.audio.AudioCue
import com.carom.core.level.LevelParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestartPressesTest {

    @Test
    fun aVerySlowBallGivesTheThreeSoundsInOrderAndNothingElseDoes() {
        assertEquals(AudioCue.RESTART_TAP_1, RestartSound.cueFor(1, true))
        assertEquals(AudioCue.RESTART_TAP_2, RestartSound.cueFor(2, true))
        assertEquals(AudioCue.RESTART_TAP_3, RestartSound.cueFor(3, true))
        for (step in 0..5) assertEquals("a ball at any other speed: silent (step $step)", null, RestartSound.cueFor(step, false))
        assertEquals(null, RestartSound.cueFor(4, true))
        assertEquals(null, RestartSound.cueFor(0, true))
    }

    @Test
    fun withAVerySlowBallTheBallIsPutBackOnTheThirdPressNotBefore() {
        assertFalse(RestartSound.resetsNow(1, true))
        assertFalse(RestartSound.resetsNow(2, true))
        assertTrue(RestartSound.resetsNow(3, true))
        for (step in 1..3) assertTrue("at any other speed a press restarts at once (step $step)", RestartSound.resetsNow(step, false))
    }

    @Test
    fun theStepIsTheNumberOfThePressInItsSet() {
        val c = PressCounter()
        c.press(1.0); assertEquals(1, c.step)
        c.press(1.2); assertEquals(2, c.step)
        assertTrue(c.press(1.4)); assertEquals("also right after the third", 3, c.step)
        c.press(1.6); assertEquals("a fourth press begins a new set", 1, c.step)
        c.press(5.0); assertEquals("a long pause too", 1, c.step)
    }

    @Test
    fun theThirdQuickPressIsTheOneAndNoEarlierPressIs() {
        val c = PressCounter()
        assertFalse(c.press(10.0))
        assertFalse(c.press(10.3))
        assertTrue(c.press(10.6))
    }

    @Test
    fun aFourthPressStartsANewSet() {
        val c = PressCounter()
        c.press(1.0); c.press(1.2)
        assertTrue(c.press(1.4))
        assertFalse(c.press(1.6))
        assertFalse(c.press(1.8))
        assertTrue(c.press(2.0))
    }

    @Test
    fun slowPressesNeverAddUp() {
        val c = PressCounter()
        var t = 0.0
        repeat(12) { assertFalse(c.press(t)); t += 1.5 }
    }

    @Test
    fun aLongPauseInTheMiddleStartsTheCountAgain() {
        val c = PressCounter()
        assertFalse(c.press(0.0))
        assertFalse(c.press(0.3))
        assertFalse(c.press(2.0)) // the first of a new set
        assertFalse(c.press(2.2))
        assertTrue(c.press(2.4))
    }

    @Test
    fun cancelForgetsThePresses() {
        val c = PressCounter()
        c.press(0.0); c.press(0.2)
        c.cancel()
        assertFalse(c.press(0.4))
        assertFalse(c.press(0.6))
        assertTrue(c.press(0.8))
    }

    @Test
    fun aSetStartsWithTheFirstPressAndWithNoOtherUntilItEnds() {
        val c = PressCounter()
        assertTrue(c.startsSet(5.0))
        c.press(5.0)
        assertFalse(c.startsSet(5.3))
        c.press(5.3)
        assertFalse(c.startsSet(5.6))
        assertTrue(c.press(5.6))
        assertTrue("after the third, the next press begins a set", c.startsSet(5.8))
        c.press(5.8)
        assertTrue("a long pause ends a set", c.startsSet(9.0))
    }

    private fun level() = LevelParser.parse("t", """{"size": [900, 2000], "bounces": 3, "ball": [450, 1600], "goal": [450, 200], "controlZone": {"rect": [90, 1440, 720, 360]}}""")

    /** Ref units to world units for the level above (the top speed is 120 ref units). */
    private fun GameSession.ref(v: Double) = v * level.maxSpeed / 120.0

    @Test
    fun aBallIsVerySlowOnlyWhileItIsAliveInFlightAndCrawling() {
        val s = GameSession(level())
        assertFalse("a ball waiting to be thrown is not slow, it is at rest", s.isBallVerySlow())
        s.applyImpulse(0.0, -s.ref(5.0))
        assertTrue("5 ref units is a crawl", s.isBallVerySlow())
        s.restart()
        s.applyImpulse(0.0, -s.ref(60.0))
        assertFalse("half of the top speed is not", s.isBallVerySlow())
        s.restart()
        s.applyImpulse(0.0, -s.ref(8.0))
        assertFalse("8 ref units is already past 'very slow'", s.isBallVerySlow())
    }

    @Test
    fun aBallThatKeepsSlowingBecomesVerySlowOnItsOwn() {
        // a long room, so the ball runs out of speed before it runs out of floor
        val s = GameSession(LevelParser.parse("t", """{"size": [8000, 2000], "bounces": 3, "ball": [200, 1000], "goal": [7800, 200], "launchZone": 0}"""))
        s.applyImpulse(s.ref(30.0), 0.0)
        assertFalse(s.isBallVerySlow())
        var steps = 0
        while (!s.isBallVerySlow() && s.state == GameSession.State.MOVING && steps++ < 20000) s.step()
        assertTrue("the drag brings it down to a crawl before it stops (after $steps steps)", s.isBallVerySlow())
        assertEquals(GameSession.State.MOVING, s.state)
    }

    @Test
    fun aLostOrRestartedTryIsNotVerySlow() {
        val s = GameSession(level())
        s.applyImpulse(0.0, -s.ref(4.0))
        assertTrue(s.isBallVerySlow())
        s.restart()
        assertFalse("put back at its start it is at rest, not crawling", s.isBallVerySlow())
        assertEquals(GameSession.State.AIMING, s.state)
    }
}
