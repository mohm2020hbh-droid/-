package com.carom.core.game

import com.carom.core.audio.AudioCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestartPressesTest {

    @Test
    fun theThreePressesHaveTheirThreeSoundsInOrderAndNothingElseHasOne() {
        assertEquals(AudioCue.RESTART_TAP_1, RestartSound.cueFor(1))
        assertEquals(AudioCue.RESTART_TAP_2, RestartSound.cueFor(2))
        assertEquals(AudioCue.RESTART_TAP_3, RestartSound.cueFor(3))
        assertEquals(null, RestartSound.cueFor(0))
        assertEquals(null, RestartSound.cueFor(4))
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
}
