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
    private val worlds = listOf(world1, world2)
    private val levels = world1 + world2
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
        assertTrue(world2.all { it.bpm in 165.0..190.0 },
            "the desert's drum & bass lives between 165 and 190 BPM")
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
