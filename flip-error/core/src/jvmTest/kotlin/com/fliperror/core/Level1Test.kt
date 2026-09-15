package com.fliperror.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Level1Test {

    private val level = Level1.build()
    private val report by lazy { LevelVerifier(level).analyse() }

    @Test fun `level 1 can be beaten`() {
        assertTrue(report.solvable, "no sequence of taps clears LEVEL 1")
    }

    @Test fun `level 1 runs for about half a minute`() {
        assertTrue(level.durationSeconds in 28.0..36.0, "duration ${level.durationSeconds}s")
    }

    @Test fun `level 1 asks for 20 to 30 taps`() {
        assertTrue(report.taps in 20..30, "perfect run needs ${report.taps} taps")
    }

    /**
     * No jump may be frame perfect. 0.12s is what the GDD's own jump arc can
     * actually offer for its tightest intended pattern - see the window note in
     * docs/FLIP_ERROR_LEVEL1_NOTES.md, the GDD's 0.38s figure is not reachable
     * with a 0.52s jump at 9.5 u/s.
     */
    @Test fun `every jump has a humanly hittable window`() {
        val worst = report.jumps.minByOrNull { it.window }!!
        assertTrue(worst.window >= 0.12,
            "tightest window ${"%.3f".format(worst.window)}s at ${"%.0f".format(worst.percent)}%")
    }

    @Test fun `most of the level is not at the limit`() {
        val tight = report.jumps.count { it.window < 0.18 }
        assertTrue(tight <= 3, "$tight jumps are at the limit; a level 1 should have 2-3 killers")
        assertTrue(report.jumps.count { it.window >= 0.20 } >= 15, "not enough readable jumps")
    }

    @Test fun `the level breathes`() {
        val rest = report.longestRest(level.durationSeconds)
        assertTrue(rest >= 1.2, "longest input-free stretch is only ${"%.2f".format(rest)}s")
    }

    @Test fun `the finish spike is the hardest moment of the run`() {
        val finale = report.jumps.filter { it.percent >= 85 }
        assertTrue(finale.isNotEmpty(), "nothing happens in the last 15% of the level")
        val tightestFinale = finale.minOf { it.window }
        val median = report.jumps.map { it.window }.sorted()[report.jumps.size / 2]
        assertTrue(tightestFinale < median,
            "the ending (${"%.3f".format(tightestFinale)}s) must be tighter than the median (${"%.3f".format(median)}s)")
        assertTrue(tightestFinale <= 0.20, "the finish spike is too forgiving: ${"%.3f".format(tightestFinale)}s")
    }

    @Test fun `the opening is readable before it is punishing`() {
        val opening = report.jumps.filter { it.percent < 20 }
        assertTrue(opening.all { it.window >= 0.20 },
            "the first fifth must stay readable: ${opening.map { "%.3f".format(it.window) }}")
    }

    @Test fun `hazards never intersect a landing surface`() {
        for (h in level.hazards) for (s in level.solids) {
            if (h.hitBox.x0 < s.x1 && h.hitBox.x1 > s.x0) {
                val sitsOn = kotlin.math.abs(h.hitBox.y0 - s.top) < 0.3
                val above = h.hitBox.y0 >= s.top - 0.3
                val below = h.hitBox.y1 <= s.bottom + 0.3
                assertTrue(sitsOn || above || below, "hazard $h intersects solid $s")
            }
        }
    }

    @Test fun `the single star is inside jump reach`() {
        assertEquals(1, level.stars.size)
        assertTrue(level.stars[0].y <= Tuning.JUMP_APEX + 0.9, "star is out of reach")
    }

    @Test fun `report`() {
        println("LEVEL 1 '${level.name}' -> solvable=${report.solvable} taps=${report.taps} " +
            "duration=${"%.2f".format(level.durationSeconds)}s " +
            "minWindow=${"%.3f".format(report.minWindow)}s " +
            "longestRest=${"%.2f".format(report.longestRest(level.durationSeconds))}s " +
            "nodes=${report.nodesExplored}")
        report.jumps.forEachIndexed { i, j ->
            println("  jump ${(i + 1).toString().padStart(2)}: window ${"%.3f".format(j.window)}s " +
                "at x=${"%.1f".format(j.x)} (${"%.0f".format(j.percent)}%)")
        }
    }
}
