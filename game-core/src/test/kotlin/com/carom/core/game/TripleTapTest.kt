package com.carom.core.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripleTapTest {

    /** A tap: lands at [t], lifts [hold] seconds later, a finger's wobble in between. Returns whether it completed a triple. */
    private fun TripleTap.tap(t: Double, hold: Double = 0.08, x: Double = 100.0, y: Double = 200.0, wobble: Double = 3.0): Boolean {
        down(x, y, t)
        move(x + wobble, y)
        return up(t + hold)
    }

    @Test
    fun threeQuickTapsAreATripleTap() {
        val tt = TripleTap()
        assertFalse(tt.tap(0.0))
        assertFalse(tt.tap(0.25))
        assertTrue(tt.tap(0.50))
    }

    @Test
    fun theTapsCanLandAnywhere() {
        val tt = TripleTap()
        assertFalse(tt.tap(0.0, x = 30.0, y = 40.0))
        assertFalse(tt.tap(0.2, x = 300.0, y = 600.0))
        assertTrue(tt.tap(0.4, x = 180.0, y = 90.0))
    }

    @Test
    fun aSlowGapStartsTheCountAgain() {
        val tt = TripleTap()
        assertFalse(tt.tap(0.0))
        assertFalse(tt.tap(0.2))
        assertFalse("too long after the second", tt.tap(1.2))
        assertFalse(tt.tap(1.4))
        assertTrue(tt.tap(1.6))
    }

    @Test
    fun aHeldTouchIsNotATap() {
        val tt = TripleTap()
        assertFalse(tt.tap(0.0))
        assertFalse(tt.tap(0.2))
        assertFalse("held for a second", tt.tap(0.4, hold = 1.0))
        assertFalse(tt.tap(1.6))
    }

    @Test
    fun aDragIsNotATapAndForgetsTheTapsBeforeIt() {
        val tt = TripleTap()
        assertFalse(tt.tap(0.0))
        assertFalse(tt.tap(0.2))
        tt.down(100.0, 100.0, 0.4)
        tt.move(100.0, 60.0) // a throw
        assertFalse(tt.up(0.5))
        assertFalse("only the first tap of a new set", tt.tap(0.6))
        assertFalse(tt.tap(0.8))
        assertTrue(tt.tap(1.0))
    }

    @Test
    fun aFourthTapBeginsANewSet() {
        val tt = TripleTap()
        tt.tap(0.0); tt.tap(0.2)
        assertTrue(tt.tap(0.4))
        assertFalse(tt.tap(0.6))
        assertFalse(tt.tap(0.8))
        assertTrue(tt.tap(1.0))
    }

    @Test
    fun cancelForgetsEverything() {
        val tt = TripleTap()
        tt.tap(0.0); tt.tap(0.2)
        tt.cancel()
        assertFalse(tt.tap(0.4))
    }
}
