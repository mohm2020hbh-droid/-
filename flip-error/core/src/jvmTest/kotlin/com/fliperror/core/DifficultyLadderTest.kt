package com.fliperror.core

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The curve, measured rather than asserted by feel.
 *
 * "Harder" turned out to be two different things, and the ladder only made sense
 * once they were separated.
 *
 * REFLEX is the tightest take-off window on the line a perfect player flies -
 * difficulty the player has to HIT. It cannot climb forever. LEVEL 6 ends world
 * 1 at 0.075s, about four and a half frames at 60Hz, and that is the floor this
 * whole project is built on not crossing. Six more levels of "one frame tighter"
 * would have run the game off the end of what a hand can do by level 9.
 *
 * READING is everything else - movers, pulses, floors that leave, beams, wind -
 * difficulty the player has to SEE. It has no such ceiling, and it is what world
 * 2 escalates instead.
 *
 * So the rule is per world: inside a world reflex tightens to the floor and
 * stops, and a new world may open looser than the last one closed because it is
 * teaching a new vocabulary. What is never allowed to slip is the floor itself,
 * and the fact that each world asks the player to read more than the last.
 */
class DifficultyLadderTest {

    private companion object { const val FAIR_FLOOR = 0.075 }

    private val world1 = listOf(
        Level1.build(), Level2.build(), Level3.build(),
        Level4.build(), Level5.build(), Level6.build(),
    )
    private val world2 = listOf(
        Level7.build(), Level8.build(), Level9.build(),
        Level10.build(), Level11.build(), Level12.build(),
    )
    private val world3 = listOf(
        Level13.build(), Level14.build(), Level15.build(),
        Level16.build(), Level17.build(), Level18.build(),
    )
    private val worlds = listOf(world1, world2, world3)
    private val levels = world1 + world2 + world3
    private val reports by lazy { levels.associateWith { LevelVerifier(it).analyse() } }

    /** Everything on screen that is not standing still. */
    private fun Level.movingParts() =
        hazards.count { it.moves || it.pulses } + solids.count { it.moves || it.blinks } + winds.size

    @Test fun `every level can be beaten`() {
        levels.forEach { lv ->
            assertTrue(reports[lv]!!.solvable, "LEVEL ${lv.id} '${lv.name}' cannot be beaten")
        }
    }

    @Test fun `nothing in either world goes under the floor`() {
        levels.forEach { lv ->
            val w = reports[lv]!!.minWindow
            assertTrue(w >= FAIR_FLOOR,
                "LEVEL ${lv.id} '${lv.name}' asks for ${"%.3f".format(w)}s; the floor is $FAIR_FLOOR")
        }
    }

    @Test fun `inside a world, reflex only tightens`() {
        worlds.forEachIndexed { wi, world ->
            world.map { reports[it]!!.minWindow }.zipWithNext().forEachIndexed { i, (a, b) ->
                assertTrue(b <= a,
                    "WORLD ${wi + 1}: LEVEL ${world[i + 1].id} (${"%.3f".format(b)}s) is looser " +
                        "than LEVEL ${world[i].id} (${"%.3f".format(a)}s)")
            }
        }
    }

    @Test fun `every world ends on the floor`() {
        worlds.forEachIndexed { wi, world ->
            val last = reports[world.last()]!!.minWindow
            assertTrue(last <= FAIR_FLOOR + 0.001,
                "WORLD ${wi + 1} finishes at ${"%.3f".format(last)}s, which is not the floor")
        }
    }

    /**
     * Each world opens tighter than the last one opened.
     *
     * Reflex cannot climb forever - the floor is 0.075s and worlds 1, 2 and 3 all
     * end on it - so a rule about where a world FINISHES says nothing after the
     * first. Where it STARTS does: world 2 hands the player a new vocabulary at
     * 0.092s, world 3 hands them a harder one at 0.088s, and neither gets the
     * gentle opening world 1 had.
     */
    @Test fun `each world opens tighter than the last`() {
        val opens = worlds.map { reports[it.first()]!!.minWindow }
        opens.zipWithNext().forEachIndexed { i, (a, b) ->
            assertTrue(b < a,
                "WORLD ${i + 2} opens at ${"%.3f".format(b)}s, no tighter than " +
                    "WORLD ${i + 1}'s ${"%.3f".format(a)}s")
        }
    }

    @Test fun `each world asks the player to read more than the last`() {
        val counts = worlds.map { w -> w.sumOf { it.movingParts() } }
        counts.zipWithNext().forEachIndexed { i, (a, b) ->
            assertTrue(b > a,
                "WORLD ${i + 2} has $b moving parts against WORLD ${i + 1}'s $a; a new world " +
                    "that does not tighten has to be the one that reads harder")
        }
    }

    @Test fun `the second jump becomes compulsory and stays that way`() {
        assertTrue(reports[world1[0]]!!.boosts == 0, "level 1 should not force a boost")
        levels.drop(1).forEach { lv ->
            assertTrue(reports[lv]!!.boosts > 0, "LEVEL ${lv.id} never forces the second jump")
        }
    }

    @Test fun `no level opens on its hardest moment`() {
        levels.forEach { lv ->
            val opening = reports[lv]!!.jumps.filter { it.percent < 12 }
            if (opening.isNotEmpty()) {
                assertTrue(opening.all { it.window >= 0.18 },
                    "LEVEL ${lv.id} opens with ${"%.3f".format(opening.minOf { it.window })}s")
            }
        }
    }

    @Test fun `the tempo climbs inside each world`() {
        worlds.forEachIndexed { wi, world ->
            world.map { it.bpm }.zipWithNext().forEach { (a, b) ->
                assertTrue(b >= a, "WORLD ${wi + 1}'s soundtrack slows down: $a then $b")
            }
        }
        assertTrue(world1.last().bpm >= 165.0, "world 1 should finish running hot")
        // BPM is pure level geometry now - the audio has no tempo at all - but
        // each world still lays itself out on a faster grid than the last.
        assertTrue(world3.first().bpm > world2.last().bpm,
            "world 3 should be laid out faster than world 2 finished")
    }

    /**
     * THE INSTINCTIVE SECOND TAP HAS TO BE ONE OF THE ONES THAT WORKS.
     *
     * The verifier measures how WIDE a boost window is. It says nothing about
     * WHERE in the legal window it sits, and that turned out to be the thing that
     * made two levels feel broken while measuring perfectly fine.
     *
     * A boost fired late travels further than one fired early - the first jump's
     * height is kept longer before the second tap resets vy - so a crossing that
     * needs near-maximum distance is survivable only in the last frames of the
     * window. At LEVEL 4's 29% that was 22 workable frames after 41 identical
     * deaths, every one on the same hazard at the same x. A player taps at the top
     * of the jump, because that is what the top of a jump is for, and learns
     * nothing at all from dying.
     *
     * So this asks the question from the player's chair: tap at the apex, when the
     * runner is weightless, and do it from a spread of the places they could
     * reasonably have jumped from - because on a gap people leave at the ledge and
     * over a spike they leave early, and a rule that only tried one of those would
     * be measuring a habit instead of a level. Most of those attempts have to
     * live. A level where they mostly die is not hard, it is lying about which
     * input it wants.
     */
    @Test fun `the second tap works when a person would actually make it`() {
        val apexFrame = Math.round(Tuning.RISE_TIME / Tuning.FIXED_DT).toInt()

        /** Fly the line to [idx], take off [offset] units late, tap at the apex. */
        fun attempt(lv: Level, plan: List<LevelVerifier.Jump>, idx: Int, offset: Double): Boolean? {
            val jump = plan[idx]
            val g = Game(lv)
            var i = 0
            var owed = false
            var guard = 0
            while (g.state == GameState.RUNNING && i < idx && guard++ < 60_000) {
                if (g.x >= plan[i].x && g.grounded) { owed = plan[i].boosted; i++; g.onTap() }
                else if (owed && g.canDoubleJump && g.x >= plan[i - 1].boostX) { g.onTap(); owed = false }
                g.update(Tuning.FIXED_DT)
            }
            while (g.state == GameState.RUNNING && g.x < jump.x + offset && guard++ < 60_000)
                g.update(Tuning.FIXED_DT)
            // Off the end of the ledge, or dead on the way: not a take-off a person
            // could have made, so it is not evidence either way.
            if (g.state != GameState.RUNNING || !g.grounded) return null
            g.onTap()
            i = idx + 1
            owed = false
            var air = 0
            var boosted = false
            while (g.state == GameState.RUNNING && guard++ < 60_000) {
                if (!boosted && air >= apexFrame) {
                    if (!g.canDoubleJump) return false
                    g.onTap(); boosted = true
                }
                g.update(Tuning.FIXED_DT); air++
                if (boosted && g.grounded) break
            }
            if (!boosted) return false
            // Keep flying the line afterwards. Coasting instead - just running on
            // with no further input - kills the runner on whatever comes next and
            // blames it on this boost, which had the probe reporting a perfectly
            // good crossing as broken. The question being asked is "does this
            // second tap leave me able to carry on", so the probe has to carry on.
            var after = 0
            while (g.state == GameState.RUNNING && after++ < 260) {
                if (i < plan.size && g.x >= plan[i].x && g.grounded) {
                    owed = plan[i].boosted; i++; g.onTap()
                } else if (owed && g.canDoubleJump && g.x >= plan[i - 1].boostX) {
                    g.onTap(); owed = false
                }
                g.update(Tuning.FIXED_DT)
            }
            return g.state != GameState.DEAD
        }

        levels.forEach { lv ->
            val plan = reports[lv]!!.jumps
            plan.forEachIndexed { idx, jump ->
                if (!jump.boosted) return@forEachIndexed
                val span = jump.window * Tuning.RUN_SPEED
                val tries = listOf(0.0, 0.25, 0.5, 0.75).mapNotNull { attempt(lv, plan, idx, it * span) }
                if (tries.isEmpty()) return@forEachIndexed
                val lived = tries.count { it }
                assertTrue(lived * 2 >= tries.size,
                    "LEVEL ${lv.id} at ${"%.0f".format(jump.percent)}% (x=${"%.1f".format(jump.x)}): " +
                        "tapping at the apex - the tap a person makes - survives only " +
                        "$lived of $tries.size take-offs across the window. The boost " +
                        "window is ${"%.3f".format(jump.boostWindow)}s wide but it is in " +
                        "the wrong part of the flight.")
            }
        }
    }

    @Test fun `report the curve`() {
        println("DIFFICULTY LADDER")
        worlds.forEachIndexed { wi, world ->
            println("  WORLD ${wi + 1}  (${world.sumOf { it.movingParts() }} moving parts)")
            world.forEach { lv ->
                val r = reports[lv]!!
                println("    LEVEL ${lv.id.toString().padStart(2)} ${lv.name.padEnd(16)} " +
                    "bpm=${lv.bpm.toInt()} taps=${r.taps} boosts=${r.boosts} " +
                    "minWindow=${"%.3f".format(r.minWindow)}s " +
                    "rest=${"%.2f".format(r.longestRest(lv.durationSeconds))}s " +
                    "moving=${lv.movingParts()}")
            }
        }
    }
}
