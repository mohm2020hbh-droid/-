package com.fliperror.core

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The curve, measured rather than asserted by feel.
 *
 * "Harder" is a slippery word, so this pins it to the one number that is the
 * same kind of thing at every level: the tightest take-off window on the line a
 * perfect player flies. Each level's worst moment must be worse than the last
 * level's worst moment. Everything else - movers, lids, blinkers, boosts - is
 * difficulty the player has to read; this is difficulty they have to hit.
 */
class DifficultyLadderTest {

    private val levels = listOf(
        Level1.build(), Level2.build(), Level3.build(), Level4.build(), Level5.build(),
    )
    private val reports by lazy { levels.map { LevelVerifier(it).analyse() } }

    @Test fun `every level can be beaten`() {
        levels.zip(reports).forEach { (lv, r) ->
            assertTrue(r.solvable, "LEVEL ${lv.id} '${lv.name}' cannot be beaten")
        }
    }

    @Test fun `each level's tightest moment is tighter than the last`() {
        val windows = reports.map { it.minWindow }
        windows.zipWithNext().forEachIndexed { i, (a, b) ->
            assertTrue(b < a,
                "LEVEL ${i + 2} (${"%.3f".format(b)}s) is not tighter than " +
                    "LEVEL ${i + 1} (${"%.3f".format(a)}s)")
        }
        assertTrue(windows.last() >= 0.075,
            "the hardest level asks for ${"%.3f".format(windows.last())}s; that is past fair")
    }

    @Test fun `the second jump becomes compulsory and stays that way`() {
        assertTrue(reports[0].boosts == 0, "level 1 should not force a boost")
        reports.drop(1).forEachIndexed { i, r ->
            assertTrue(r.boosts > 0, "LEVEL ${i + 2} never forces the second jump")
        }
    }

    @Test fun `no level opens on its hardest moment`() {
        levels.zip(reports).forEach { (lv, r) ->
            val opening = r.jumps.filter { it.percent < 12 }
            if (opening.isNotEmpty()) {
                assertTrue(opening.all { it.window >= 0.18 },
                    "LEVEL ${lv.id} opens with ${opening.minOf { it.window }}s")
            }
        }
    }

    @Test fun `the tempo climbs with the difficulty`() {
        levels.map { it.bpm }.zipWithNext().forEach { (a, b) ->
            assertTrue(b >= a, "the soundtrack slows down between levels: $a then $b")
        }
        assertTrue(levels.last().bpm >= 165.0, "the last level should be running hot")
    }

    @Test fun `report the curve`() {
        println("DIFFICULTY LADDER")
        levels.zip(reports).forEach { (lv, r) ->
            val movers = lv.hazards.count { it.moves } + lv.solids.count { it.moves || it.blinks }
            println("  LEVEL ${lv.id} ${lv.name.padEnd(16)} " +
                "bpm=${lv.bpm.toInt()} taps=${r.taps} boosts=${r.boosts} " +
                "minWindow=${"%.3f".format(r.minWindow)}s " +
                "rest=${"%.2f".format(r.longestRest(lv.durationSeconds))}s moving=$movers")
        }
    }
}
