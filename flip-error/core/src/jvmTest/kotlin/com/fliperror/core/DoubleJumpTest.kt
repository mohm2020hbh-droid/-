package com.fliperror.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun flat(len: Double = 400.0) = Level(
    id = 0, name = "T", subtitle = "", bpm = 140.0,
    solids = listOf(Solid(-10.0, len, 0.0)),
    hazards = emptyList(), stars = emptyList(), finishX = len,
)

private fun ledge(edge: Double) = Level(
    id = 0, name = "L", subtitle = "", bpm = 140.0,
    solids = listOf(Solid(-10.0, edge, 0.0)),
    hazards = emptyList(), stars = emptyList(), finishX = 400.0,
)

private val DT = Tuning.FIXED_DT
private fun Game.frames(n: Int) = repeat(n) { update(DT) }

/** Jump, optionally tap again once [second] first says yes, and report the flight. */
private class Flight(val peak: Double, val airTime: Double, val distance: Double, val doubles: Int)

private fun fly(g: Game, second: (Game, Double) -> Boolean = { _, _ -> false }): Flight {
    val x0 = g.x
    g.onTap()
    g.frames(1)                       // the ground jump fires on the first step
    var peak = g.y
    var t = DT
    var tapped = false
    while (!g.grounded && t < 3.0 && g.state == GameState.RUNNING) {
        if (!tapped && second(g, t)) { g.onTap(); tapped = true }
        g.update(DT); t += DT
        peak = maxOf(peak, g.y)
    }
    return Flight(peak, t, g.x - x0, g.doubleJumps)
}

class DoubleJumpTest {

    @Test fun `a second tap at the top lifts the runner clearly higher`() {
        val single = fly(Game(flat()))
        val double = fly(Game(flat())) { g, _ -> g.vy <= 0.0 }
        assertEquals(0, single.doubles)
        assertEquals(1, double.doubles)
        assertTrue(double.peak > single.peak + 1.5,
            "double peak ${double.peak} should clear single peak ${single.peak} by a wide margin")
        assertTrue(double.peak in 4.3..4.9, "double peak ${double.peak} should be about apex + ${Tuning.DOUBLE_JUMP_APEX}")
    }

    @Test fun `the boost is a real lift, not a hover`() {
        val double = fly(Game(flat())) { g, _ -> g.vy <= 0.0 }
        // Airtime grows, but it stays inside a single musical beat so the level's
        // rhythm still reads. A double jump that floats would erase the timing.
        assertTrue(double.airTime in 0.70..0.90, "double airtime ${double.airTime}s")
        assertTrue(double.airTime < Tuning.BEAT * 2, "a double jump must not outlast two beats")
    }

    @Test fun `mashing the second tap immediately buys nothing`() {
        val mashed = fly(Game(flat())) { _, t -> t <= DT * 2 }
        val single = fly(Game(flat()))
        assertEquals(0, mashed.doubles, "a tap inside the lockout must not fire the boost")
        assertTrue(mashed.peak < single.peak + 0.01, "mashed peak ${mashed.peak} vs single ${single.peak}")
    }

    @Test fun `the window opens only after the lockout`() {
        val g = Game(flat())
        g.onTap(); g.frames(1)
        assertFalse(g.canDoubleJump, "the window must be shut at take-off")
        while (g.elapsed < Tuning.DOUBLE_LOCKOUT - DT) g.update(DT)
        assertFalse(g.canDoubleJump, "still shut one step before the lockout ends")
        g.frames(2)
        assertTrue(g.canDoubleJump, "the window must be open once the lockout has passed")
    }

    @Test fun `the window shuts once the runner is committed to falling`() {
        val g = Game(flat())
        g.onTap(); g.frames(1)
        while (g.vy > Tuning.DOUBLE_MIN_VY && !g.grounded) g.update(DT)
        assertFalse(g.canDoubleJump, "a runner already dropping has spent the chance")
        val before = g.doubleJumps
        g.onTap()
        assertEquals(before, g.doubleJumps, "a late tap must not become a boost")
    }

    @Test fun `a late tap still buffers into the next jump`() {
        // The window closing is what keeps the landing buffer working; without it
        // the forgiveness in GDD 1.4 would be spent on an accidental boost.
        val g = Game(flat())
        g.onTap(); g.frames(1)
        while (!g.grounded) {
            g.update(DT)
            // tap once inside the buffer's reach of the ground
            if (g.vy < 0 && g.y < 0.35 && g.taps == 1) g.onTap()
        }
        assertEquals(0, g.doubleJumps, "that tap belonged to the landing, not to a boost")
        g.frames(2)
        assertFalse(g.grounded, "the buffered tap must jump again off the landing")
    }

    @Test fun `there is no third jump`() {
        val g = Game(flat())
        g.onTap(); g.frames(1)
        while (g.vy > 0) g.update(DT)
        g.onTap()                                   // the boost
        assertEquals(1, g.doubleJumps)
        g.frames(4)
        val peakAfter = g.y
        repeat(20) { g.onTap(); g.frames(1) }       // mash the rest of the flight
        assertEquals(1, g.doubleJumps, "only one boost per jump, however many taps arrive")
        assertTrue(g.y > peakAfter - 20.0)          // still just falling, not climbing
    }

    @Test fun `stepping off a ledge does not arm a boost`() {
        val g = Game(ledge(20.0))
        while (g.grounded) g.update(DT)             // run off the edge
        while (g.elapsed < 0.25) g.update(DT)       // past coyote time and the lockout
        assertFalse(g.canDoubleJump, "a fall the player never jumped into owes them nothing")
        val before = g.doubleJumps
        g.onTap()
        assertEquals(before, g.doubleJumps)
    }

    @Test fun `landing re-arms nothing until the next jump`() {
        val g = Game(flat())
        g.onTap(); g.frames(1)
        while (g.vy > 0) g.update(DT)
        g.onTap()                                   // spend the boost
        while (!g.grounded) g.update(DT)
        assertFalse(g.canDoubleJump, "on the ground there is nothing to boost")
        g.onTap(); g.frames(2)                      // a fresh jump
        while (g.vy > 0) g.update(DT)
        assertTrue(g.canDoubleJump, "the next jump arms the next boost")
    }

    @Test fun `the boost buys height, and only a little ground`() {
        val single = fly(Game(flat()))
        val best = fly(Game(flat())) { g, _ -> g.vy <= 0.0 }
        // The reach matters more than the peak for difficulty: a double jump that
        // cleared two spaced hazards at once would delete the level's patterns.
        assertTrue(best.distance < Tuning.BEAT_UNITS * 2,
            "a double jump reaches ${best.distance}u; two beats apart is ${Tuning.BEAT_UNITS * 2}u " +
                "and must stay out of one jump's range")
        assertTrue(best.distance in 6.5..8.2, "double jump reach ${best.distance}u")
        assertTrue(single.distance in 4.7..5.2, "single jump reach ${single.distance}u is unchanged")
    }

    @Test fun `a later boost trades height away`() {
        val early = fly(Game(flat())) { _, t -> t >= Tuning.DOUBLE_LOCKOUT }
        val atTop = fly(Game(flat())) { g, _ -> g.vy <= 0.0 }
        assertTrue(atTop.peak > early.peak,
            "boosting at the top (${atTop.peak}) must beat boosting on the way up (${early.peak})")
    }

    @Test fun `the face calls out the second jump`() {
        val g = Game(flat())
        g.onTap(); g.frames(1)
        while (g.vy > 0) g.update(DT)
        assertEquals(Face.JUMP, g.face)
        g.onTap()
        assertEquals(Face.DOUBLE, g.face, "the boost needs its own read")
        while (!g.grounded) g.update(DT)
        assertEquals(Face.RUN, g.face)
    }
}
