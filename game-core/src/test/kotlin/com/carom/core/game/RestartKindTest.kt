package com.carom.core.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestartKindTest {

    @Test
    fun onlyTheTripleRestartChargesTheGenerator() {
        assertEquals(listOf(RestartKind.TRIPLE), RestartKind.entries.filter { it.chargesGenerator })
        assertFalse(RestartKind.AUTOMATIC.chargesGenerator)
        assertFalse(RestartKind.MANUAL.chargesGenerator)
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
