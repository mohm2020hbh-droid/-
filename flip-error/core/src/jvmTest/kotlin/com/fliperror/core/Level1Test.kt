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

    /** Sweep every take-off frame on flat ground and report the best star haul. */
    private fun bestStars(starY: Double, boost: Boolean): Int {
        val probe = Level(
            id = 0, name = "P", subtitle = "", bpm = Tuning.BPM,
            solids = listOf(Solid(-10.0, 120.0, 0.0)),
            hazards = emptyList(), stars = listOf(Star(40.0, starY)), finishX = 300.0,
        )
        var best = 0
        // Take off anywhere in the 10 units before the star; that covers every
        // approach a player could take to it.
        val first = ((40.0 - 10.0) / Tuning.RUN_SPEED / Tuning.FIXED_DT).toInt()
        val last = ((40.0 + 1.0) / Tuning.RUN_SPEED / Tuning.FIXED_DT).toInt()
        for (takeOff in first..last) {
            val g = Game(probe)
            repeat(takeOff) { g.update(Tuning.FIXED_DT) }
            g.onTap()
            var boosted = !boost
            var guard = 0
            while (g.state == GameState.RUNNING && guard++ < 600) {
                if (!boosted && !g.grounded && g.vy <= 0.0) { g.onTap(); boosted = true }
                g.update(Tuning.FIXED_DT)
                if (g.grounded && guard > 20) break
            }
            best = maxOf(best, g.starsCollected)
        }
        return best
    }

    @Test fun `the star is what the second jump is for`() {
        assertEquals(1, level.stars.size)
        val y = level.stars[0].y
        assertEquals(0, bestStars(y, boost = false),
            "a single jump reaches the star at y=$y; then the boost has no purpose here")
        assertEquals(1, bestStars(y, boost = true),
            "a double jump cannot reach the star at y=$y; then it is just decoration")
    }

    /** Replay the verified line, but greedily boost every jump. */
    private fun replayWithBoost(boostEvery: Boolean): Game {
        val g = Game(level)
        var i = 0
        var boosted = true
        var guard = 0
        while (g.state == GameState.RUNNING && guard++ < 40_000) {
            if (i < report.jumps.size && g.x >= report.jumps[i].x) { g.onTap(); i++; boosted = !boostEvery }
            if (!boosted && !g.grounded && g.vy <= 0.0) { g.onTap(); boosted = true }
            g.update(Tuning.FIXED_DT)
        }
        return g
    }

    @Test fun `the verified line still clears the level untouched`() {
        val g = replayWithBoost(boostEvery = false)
        assertEquals(GameState.COMPLETE, g.state,
            "the single-jump line must survive the double jump landing in the game")
    }

    @Test fun `boosting every jump does not clear level 1`() {
        val g = replayWithBoost(boostEvery = true)
        assertEquals(GameState.DEAD, g.state,
            "a player who always taps twice cleared the level; the boost is a free pass, not a tool")
    }

    @Test fun `the ceiling corridor punishes the second tap hardest`() {
        val corridor = level.hazards.filter { it.kind == HazardKind.SPIKE_DOWN }
        assertTrue(corridor.isNotEmpty(), "level 1 has no place that forbids the boost")
        val tip = corridor.minOf { it.y0 }
        assertTrue(tip < Tuning.JUMP_APEX,
            "the corridor must already punish a single jump, or it teaches nothing about the second")
        assertTrue(tip < Tuning.JUMP_APEX + Tuning.DOUBLE_JUMP_APEX,
            "the boost must not be able to clear the corridor ceiling")
    }

    /** Exported so the browser playtest can drive a real perfect run. */
    @Test fun `export the perfect run plan`() {
        val f = java.io.File("build/level1-plan.json")
        f.parentFile.mkdirs()
        f.writeText(buildString {
            append("{\"finishX\":").append(level.finishX)
            append(",\"durationSeconds\":").append(level.durationSeconds)
            append(",\"taps\":").append(report.taps)
            append(",\"minWindow\":").append(report.minWindow)
            append(",\"jumps\":[")
            report.jumps.forEachIndexed { i, j ->
                if (i > 0) append(",")
                append("{\"x\":").append(j.x).append(",\"window\":").append(j.window).append("}")
            }
            append("]}")
        })
        assertTrue(f.length() > 50)
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
