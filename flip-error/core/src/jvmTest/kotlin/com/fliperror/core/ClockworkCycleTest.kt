package com.fliperror.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * WORLD 4's one promise: SAME INPUT, SAME LEVEL, SAME RESULT.
 *
 * The machine is built out of cycles, and a cycle the player cannot rely on is
 * worse than no cycle at all - it turns every death into something they cannot
 * learn from. So this is the test that says the promise is kept, and it is a
 * JVM test rather than a browser one on purpose: the browser samples the
 * simulation at whatever moments the compositor hands it, so two runs there
 * disagree about where the runner was on frame 300 without anything in the game
 * being non-deterministic. Stepping the fixed timestep directly removes that
 * question and leaves only the one worth asking.
 */
class ClockworkCycleTest {

    private val levels = listOf(
        Level19.build(), Level20.build(), Level21.build(),
        Level22.build(), Level23.build(), Level24.build(),
    )

    /** Fly [lv] on a scripted line and return a trace of everything observable. */
    private fun trace(lv: Level, taps: List<Double>): List<String> {
        val g = Game(lv)
        val out = ArrayList<String>()
        var i = 0
        var guard = 0
        while (g.state == GameState.RUNNING && guard++ < 40_000) {
            if (i < taps.size && g.x >= taps[i]) { g.onTap(); i++ }
            g.update(Tuning.FIXED_DT)
            if (guard % 8 == 0) out += buildString {
                append(g.x.toRawBits()).append('|').append(g.y.toRawBits()).append('|')
                append(lv.hazards.count { it.activeAt(g.elapsed) }).append('|')
                append(lv.solids.count { it.presentAt(g.elapsed) }).append('|')
                append(g.starsCollected)
            }
        }
        out += "END ${g.state} ${g.x.toRawBits()} ${g.deathCause} ${g.starsCollected}"
        return out
    }

    @Test fun `the same line twice gives the same run, to the bit`() {
        levels.forEach { lv ->
            val plan = LevelVerifier(lv).analyse().jumps.map { it.x }
            val a = trace(lv, plan)
            val b = trace(lv, plan)
            assertEquals(a.size, b.size, "${lv.name}: the two runs are not even the same length")
            val diff = a.indices.firstOrNull { a[it] != b[it] }
            assertTrue(diff == null,
                "${lv.name}: two runs of the same line diverge at sample $diff - " +
                    "'${a.getOrNull(diff ?: 0)}' against '${b.getOrNull(diff ?: 0)}'")
        }
    }

    /**
     * And the same line taken WRONG dies in the same place twice.
     *
     * A player learns a machine by failing at it repeatedly, so the failure has
     * to be the same failure. This drops one planned jump and checks that both
     * runs end at the same x with the same cause - which is the property the
     * player actually experiences, as opposed to the one the engine guarantees.
     */
    @Test fun `the same mistake twice kills you in the same place`() {
        levels.forEach { lv ->
            val plan = LevelVerifier(lv).analyse().jumps.map { it.x }
            val dropped = plan.filterIndexed { i, _ -> i != plan.size / 2 }
            val a = trace(lv, dropped).last()
            val b = trace(lv, dropped).last()
            assertEquals(a, b, "${lv.name}: the same missed jump does not end the same way")
        }
    }

    /**
     * Every part of the machine is a function of level time and nothing else.
     *
     * Asked the same question a cycle apart, a mechanism has to give the same
     * answer - that is what makes a cycle a cycle. This walks every hazard in
     * world 4 through several periods of its own blink and checks that its state
     * repeats exactly, which is the formal version of "the player can learn it".
     */
    @Test fun `every cycle repeats exactly`() {
        levels.forEach { lv ->
            lv.hazards.forEach { hz ->
                val blink = hz.blink
                if (blink != null) {
                    (0..400).forEach { k ->
                        val t = k * blink.period / 400.0
                        assertEquals(blink.solidAt(t), blink.solidAt(t + blink.period * 3),
                            "${lv.name}: a ${hz.look} at x=${hz.x0} is in a different state " +
                                "three cycles later")
                    }
                }
                val m = hz.motion
                if (m != null) {
                    (0..400).forEach { k ->
                        val t = k * m.period / 400.0
                        val now = hz.drawBoxAt(t)
                        val later = hz.drawBoxAt(t + m.period * 3)
                        assertTrue(kotlin.math.abs(now.x0 - later.x0) < 1e-9 &&
                            kotlin.math.abs(now.y0 - later.y0) < 1e-9,
                            "${lv.name}: a ${hz.look} at x=${hz.x0} is in a different place " +
                                "three cycles later")
                    }
                }
            }
        }
    }
}
