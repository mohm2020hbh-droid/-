package com.fliperror.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Level2Test {

    private val level = Level2.build()
    private val report by lazy { LevelVerifier(level).analyse() }
    private val singleJumpOnly by lazy { LevelVerifier(level, allowBoost = false).analyse() }
    private val level1 by lazy { LevelVerifier(Level1.build()).analyse() }

    @Test fun `level 2 can be beaten`() {
        assertTrue(report.solvable,
            "no sequence of taps clears LEVEL 2; the furthest any line reaches is " +
                "x=${"%.1f".format(report.furthestX)} of ${level.finishX}")
    }

    /** The level's whole thesis: this is where the second jump stops being optional. */
    @Test fun `level 2 cannot be beaten with single jumps alone`() {
        assertFalse(singleJumpOnly.solvable,
            "a single-jump player clears LEVEL 2; then GAP LOGIC teaches nothing new")
        assertTrue(singleJumpOnly.furthestX < level.finishX * 0.45,
            "a single-jump player gets ${"%.0f".format(100 * singleJumpOnly.furthestX / level.finishX)}% " +
                "through; the wall should arrive in the first half")
        assertTrue(report.boosts >= 3, "only ${report.boosts} boosts are forced")
    }

    @Test fun `every jump has a humanly hittable window`() {
        val worst = report.jumps.minByOrNull { it.window }!!
        assertTrue(worst.window >= 0.10,
            "tightest take-off ${"%.3f".format(worst.window)}s at ${"%.0f".format(worst.percent)}%")
        val worstBoost = report.jumps.filter { it.boosted }.minOf { it.boostWindow }
        assertTrue(worstBoost >= 0.10,
            "tightest boost window is ${"%.3f".format(worstBoost)}s; the second tap must not be frame perfect")
    }

    @Test fun `level 2 is clearly harder than level 1`() {
        assertTrue(report.taps > level1.taps,
            "level 2 asks for ${report.taps} taps, level 1 asks for ${level1.taps}")
        assertTrue(level1.boosts == 0 && report.boosts > 0,
            "level 1 forces ${level1.boosts} boosts and level 2 forces ${report.boosts}")
        // Precision moments, not just taps: every take-off plus every boost.
        val moments2 = report.jumps.size + report.boosts
        val moments1 = level1.jumps.size + level1.boosts
        assertTrue(moments2 >= moments1 + 5,
            "level 2 has $moments2 timed inputs against level 1's $moments1")
        assertTrue(report.minWindow < level1.minWindow,
            "level 2's tightest window (${"%.3f".format(report.minWindow)}s) is no tighter than " +
                "level 1's (${"%.3f".format(level1.minWindow)}s)")
    }

    @Test fun `the hardest moment is the finish`() {
        val tightest = report.jumps.minByOrNull { it.window }!!
        assertTrue(tightest.percent >= 90.0,
            "the tightest jump (${"%.3f".format(tightest.window)}s) is at " +
                "${"%.0f".format(tightest.percent)}%, not in the finish")
        val earlier = report.jumps.filter { it.percent < 90 }.minOf { it.window }
        assertTrue(earlier > tightest.window,
            "nothing before the finish may be as tight as the finish itself")
    }

    @Test fun `the opening is readable before it is punishing`() {
        val opening = report.jumps.filter { it.percent < 15 }
        assertTrue(opening.isNotEmpty(), "nothing happens in the first 15%")
        assertTrue(opening.all { it.window >= 0.20 },
            "the hook must stay readable: ${opening.map { "%.3f".format(it.window) }}")
    }

    @Test fun `the level breathes, but less than level 1 did`() {
        val rest = report.longestRest(level.durationSeconds)
        assertTrue(rest >= 1.2, "longest input-free stretch is only ${"%.2f".format(rest)}s")
        assertTrue(rest <= 3.6, "level 2 hands back ${"%.2f".format(rest)}s of quiet; that is a level 1 rest")
    }

    @Test fun `three star coins, spread across the risk`() {
        assertEquals(3, level.stars.size)
        val safest = level.stars.minByOrNull { it.y }!!
        val highest = level.stars.maxByOrNull { it.y }!!
        assertTrue(safest.y <= Tuning.JUMP_APEX, "the first coin should be inside one jump")
        assertTrue(highest.y > Tuning.JUMP_APEX + 0.9,
            "no coin needs the second jump; then two of the three are the same coin")
        assertTrue(level.stars.map { it.x }.distinct().size == 3)
    }

    @Test fun `the ceiling corridor still forbids the second tap somewhere`() {
        val ceiling = level.hazards.filter { it.kind == HazardKind.SPIKE_DOWN }
        assertTrue(ceiling.isNotEmpty(), "level 2 never asks the player NOT to boost")
        assertTrue(ceiling.minOf { it.y0 } < Tuning.JUMP_APEX)
    }

    @Test fun `export the level 2 plan`() {
        val f = java.io.File("build/level2-plan.json")
        f.parentFile.mkdirs()
        f.writeText(buildString {
            append("{\"finishX\":").append(level.finishX)
            append(",\"durationSeconds\":").append(level.durationSeconds)
            append(",\"taps\":").append(report.taps)
            append(",\"boosts\":").append(report.boosts)
            append(",\"jumps\":[")
            report.jumps.forEachIndexed { i, j ->
                if (i > 0) append(",")
                append("{\"x\":").append(j.x).append(",\"window\":").append(j.window)
                append(",\"boosted\":").append(j.boosted)
                append(",\"boostWindow\":").append(j.boostWindow)
                append(",\"boostX\":").append(j.boostX).append("}")
            }
            append("]}")
        })
        assertTrue(f.length() > 50)
    }

    /**
     * A collectible that the optimal line picks up on its way past is not a
     * reward, it is decoration: it pays the player for doing nothing different.
     */
    @Test fun `no star coin is free on the verified line`() {
        val g = Game(level)
        var i = 0
        var owed = false
        var guard = 0
        while (g.state == GameState.RUNNING && guard++ < 40_000) {
            if (i < report.jumps.size && g.x >= report.jumps[i].x) {
                owed = report.jumps[i].boosted; i++; g.onTap()
            } else if (owed && g.canDoubleJump && g.x >= report.jumps[i - 1].boostX) {
                g.onTap(); owed = false
            }
            g.update(Tuning.FIXED_DT)
        }
        assertEquals(GameState.COMPLETE, g.state, "the exported line does not clear the level")
        assertEquals(0, g.starsCollected,
            "the fastest line collects " + g.takenStarIndices().sorted().joinToString { i ->
                "#$i at (${level.stars[i].x}, ${level.stars[i].y})"
            } + " on its way past")
    }

    @Test fun `report`() {
        println("LEVEL 2 '${level.name}' -> solvable=${report.solvable} taps=${report.taps} " +
            "boosts=${report.boosts} duration=${"%.2f".format(level.durationSeconds)}s " +
            "minWindow=${"%.3f".format(report.minWindow)}s " +
            "longestRest=${"%.2f".format(report.longestRest(level.durationSeconds))}s " +
            "nodes=${report.nodesExplored}")
        report.jumps.forEachIndexed { i, j ->
            println("  jump ${(i + 1).toString().padStart(2)}: window ${"%.3f".format(j.window)}s " +
                "at x=${"%.1f".format(j.x)} (${"%.0f".format(j.percent)}%)" +
                if (j.boosted) "  +BOOST window ${"%.3f".format(j.boostWindow)}s" else "")
        }
        println("SINGLE-JUMP-ONLY solvable=${singleJumpOnly.solvable} " +
            "furthest x=${"%.1f".format(singleJumpOnly.furthestX)}")
    }
}
