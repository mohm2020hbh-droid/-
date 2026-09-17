package com.fliperror.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The tightest any single input in the game is allowed to be: 0.075s, which is
 *  between four and five frames at 60Hz. It applies to take-offs and to the
 *  second tap alike, because they are the same kind of ask. */
private const val FAIR_FLOOR = 0.075

class Level4Test {
    private val level = Level4.build()
    private val report by lazy { LevelVerifier(level).analyse() }


    @Test fun `it can be beaten`() {
        assertTrue(report.solvable,
            "no sequence of taps clears ${level.name}; the furthest any line reaches is " +
                "x=${"%.1f".format(report.furthestX)} of ${level.finishX}")
    }

    @Test fun `every jump has a humanly hittable window`() {
        val worst = report.jumps.minByOrNull { it.window }!!
        assertTrue(worst.window >= FAIR_FLOOR,
            "tightest take-off ${"%.3f".format(worst.window)}s at ${"%.0f".format(worst.percent)}%")
        val boosts = report.jumps.filter { it.boosted }
        if (boosts.isNotEmpty()) {
            assertTrue(boosts.minOf { it.boostWindow } >= FAIR_FLOOR,
                "the second tap is frame perfect somewhere: " +
                    "${"%.3f".format(boosts.minOf { it.boostWindow })}s")
        }
    }

    @Test fun `the hardest moment is the finish`() {
        val tightest = report.jumps.minByOrNull { it.window }!!
        assertTrue(tightest.percent >= 90.0,
            "the tightest jump (${"%.3f".format(tightest.window)}s) is at " +
                "${"%.0f".format(tightest.percent)}%, not in the finish")
    }

    @Test fun `it needs the second jump`() {
        assertTrue(report.boosts > 0, "nothing here forces a boost")
    }

    @Test fun `three star coins, and none of them free`() {
        assertEquals(3, level.stars.size)
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
            "the fastest line collects " + g.takenStarIndices().sorted().joinToString { k ->
                "#$k at (${level.stars[k].x}, ${level.stars[k].y})"
            } + " on its way past")
    }

    @Test fun `export the plan`() {
        val f = java.io.File("build/level${level.id}-plan.json")
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
                append(",\"boostX\":").append(j.boostX).append("}")
            }
            append("]}")
        })
        assertTrue(f.length() > 50)
    }

    @Test fun `report`() {
        println("LEVEL 4 '${level.name}' -> solvable=${report.solvable} taps=${report.taps} " +
            "boosts=${report.boosts} duration=${"%.2f".format(level.durationSeconds)}s " +
            "minWindow=${"%.3f".format(report.minWindow)}s " +
            "longestRest=${"%.2f".format(report.longestRest(level.durationSeconds))}s " +
            "furthest=${"%.1f".format(report.furthestX)}/${level.finishX}")
        report.jumps.forEachIndexed { i, j ->
            println("  jump ${(i + 1).toString().padStart(2)}: window ${"%.3f".format(j.window)}s " +
                "at x=${"%.1f".format(j.x)} (${"%.0f".format(j.percent)}%)" +
                if (j.boosted) "  +BOOST ${"%.3f".format(j.boostWindow)}s" else "")
        }
        assertTrue(true)
    }
}
