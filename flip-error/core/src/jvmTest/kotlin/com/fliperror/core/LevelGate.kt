package com.fliperror.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The contract every level ships under, written once.
 *
 * Levels 1 to 7 each grew their own copy of this while the rules were still
 * being discovered; world 2 has six levels and copying it six more times would
 * have meant six places for a rule to quietly stop being checked.
 *
 * 0.075s is the floor, and it is a real number rather than a taste: it is the
 * window LEVEL 6's three-spike finish measures, about four and a half frames at
 * 60Hz, and nothing in either world is allowed to ask for less. Take-offs and
 * second taps are held to it alike, because they are the same kind of ask.
 */
abstract class LevelGate(protected val level: Level) {

    protected val report by lazy { LevelVerifier(level).analyse() }

    protected companion object { const val FAIR_FLOOR = 0.075 }

    /** How many second taps this level must force out of a perfect player. */
    protected open val minBoosts = 1

    @Test fun `it can be beaten`() {
        assertTrue(report.solvable,
            "no sequence of taps clears ${level.name}; the furthest any line reaches is " +
                "x=${"%.1f".format(report.furthestX)} of ${"%.1f".format(level.finishX)}")
    }

    @Test fun `every jump has a humanly hittable window`() {
        val worst = report.jumps.minByOrNull { it.window }!!
        assertTrue(worst.window >= FAIR_FLOOR,
            "${level.name}: tightest take-off ${"%.3f".format(worst.window)}s at " +
                "${"%.0f".format(worst.percent)}% (x=${"%.1f".format(worst.x)})")
    }

    @Test fun `no second tap is frame perfect`() {
        val boosts = report.jumps.filter { it.boosted }
        assertTrue(boosts.isNotEmpty(), "${level.name} never forces a second tap")
        val worst = boosts.minByOrNull { it.boostWindow }!!
        assertTrue(worst.boostWindow >= FAIR_FLOOR,
            "${level.name}: the second tap at ${"%.0f".format(worst.percent)}% asks for " +
                "${"%.3f".format(worst.boostWindow)}s")
    }

    @Test fun `the hardest moment is the finish`() {
        val tightest = report.jumps.minByOrNull { it.window }!!
        assertTrue(tightest.percent >= 90.0,
            "${level.name}: the tightest jump (${"%.3f".format(tightest.window)}s) is at " +
                "${"%.0f".format(tightest.percent)}%, not in the finish")
    }

    /**
     * A level gets to be as hard as it likes once the player has had a moment to
     * look at it, and not before. Nothing in the first eighth may ask for less
     * than 0.18s - more than twice the floor - which in practice means a level
     * opens on shapes the player already knows and introduces its own words just
     * after. This lived only in the ladder test, where it cost thirty-five
     * minutes to find out; it belongs next to the level it is about.
     */
    @Test fun `it does not open on its hardest moment`() {
        val opening = report.jumps.filter { it.percent < 12 }
        assertTrue(opening.isNotEmpty(), "${level.name} has no jumps in its first eighth")
        val worst = opening.minByOrNull { it.window }!!
        assertTrue(worst.window >= 0.18,
            "${level.name} opens with ${"%.3f".format(worst.window)}s at " +
                "${"%.0f".format(worst.percent)}% (x=${"%.1f".format(worst.x)})")
    }

    @Test fun `it forces the second jump as often as it claims to`() {
        assertTrue(report.boosts >= minBoosts,
            "${level.name} forces ${report.boosts} boost(s), not $minBoosts")
    }

    /**
     * World 2's own law, and the one that cost the most to learn.
     *
     * Anything RESTING on a stretch of floor that moves vertically must move
     * with it, or the jump over it is a different jump on every pass. That
     * single bug drove LEVEL 7's take-off windows to one frame and LEVEL 9's to
     * 0.038s, twice, before the sand started being handed to its own furniture
     * directly.
     *
     * It is a law about things standing on the ground, so it is scoped to them.
     * A relic orbits in the air and a sun beam hangs from the sky; neither is
     * anchored to the sand and neither should pretend to be. What a beam owes
     * instead is the promise in its own docs - that it can always be run under -
     * and the next test collects on that.
     */
    @Test fun `nothing resting on moving ground disagrees with it`() {
        val resting = setOf(Look.SPIKE, Look.SAND_WAVE, Look.RUIN, Look.GEYSER)
        val movers = level.solids.filter { (it.motion?.dy ?: 0.0) != 0.0 }
        level.hazards.filter { it.look in resting }.forEach { h ->
            val under = movers.firstOrNull { h.x0 >= it.x0 && h.x1 <= it.x1 } ?: return@forEach
            val ground = under.motion!!
            val drift = (0..48).maxOf { i ->
                val t = i * ground.period / 48.0
                kotlin.math.abs((h.motion?.offsetY(t) ?: 0.0) - ground.offsetY(t))
            }
            assertTrue(drift < 1e-9,
                "${level.name}: the ${h.look} at x=${h.x0} stands on ground that moves " +
                    "${ground.dy}u every ${"%.2f".format(ground.period)}s, and drifts " +
                    "${"%.3f".format(drift)}u away from it")
        }
    }

    /**
     * A beam is never a wall. It has to clear the runner's head at every height
     * the floor beneath it can reach, or it stops being "do not jump here" and
     * becomes a locked door in a game where the runner cannot stop.
     */
    @Test fun `every beam can be run under`() {
        level.hazards.filter { it.look == Look.LASER }.forEach { beam ->
            val floors = level.solids.filter { beam.x0 < it.x1 && beam.x1 > it.x0 }
            val highest = floors.maxOfOrNull { it.top + (it.motion?.reachY ?: 0.0) } ?: return@forEach
            val head = highest + Tuning.PLAYER_SIZE
            assertTrue(beam.y0 >= head + 0.4,
                "${level.name}: the beam at x=${beam.x0} reaches down to ${"%.2f".format(beam.y0)}, " +
                    "and the floor under it can put the runner's head at ${"%.2f".format(head)}")
        }
    }

    @Test fun `three star coins, and none of them free`() {
        assertEquals(3, level.stars.size, "${level.name} does not have three coins")
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
        assertEquals(GameState.COMPLETE, g.state,
            "${level.name}: the exported line does not clear the level")
        assertEquals(0, g.starsCollected,
            "${level.name}: the fastest line collects " +
                g.takenStarIndices().sorted().joinToString { k ->
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
        println("LEVEL ${level.id} '${level.name}' -> solvable=${report.solvable} " +
            "taps=${report.taps} boosts=${report.boosts} " +
            "duration=${"%.2f".format(level.durationSeconds)}s " +
            "minWindow=${"%.3f".format(report.minWindow)}s " +
            "longestRest=${"%.2f".format(report.longestRest(level.durationSeconds))}s " +
            "furthest=${"%.1f".format(report.furthestX)}/${"%.1f".format(level.finishX)}")
        report.jumps.forEachIndexed { i, j ->
            println("  jump ${(i + 1).toString().padStart(2)}: window ${"%.3f".format(j.window)}s " +
                "at x=${"%.1f".format(j.x)} (${"%.0f".format(j.percent)}%)" +
                if (j.boosted) "  +BOOST ${"%.3f".format(j.boostWindow)}s" else "")
        }
        assertTrue(true)
    }
}
