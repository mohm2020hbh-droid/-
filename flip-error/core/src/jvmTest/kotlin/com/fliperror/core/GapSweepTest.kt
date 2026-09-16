package com.fliperror.core

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Ground truth for a gap, by brute force rather than by graph: every take-off
 * frame crossed with every legal boost frame, simulated to the landing. This is
 * what the level designer needs to know - not "is it possible" but "how many
 * ways are there to do it".
 */
class GapSweepTest {

    private fun gapLevel(width: Double) = Level(
        id = 0, name = "G", subtitle = "", bpm = Tuning.BPM,
        solids = listOf(Solid(-10.0, 40.0, 0.0), Solid(40.0 + width, 110.0, 0.0)),
        hazards = emptyList(), stars = emptyList(), finishX = 100.0,
    )

    /** Jump at [takeOffX], boost [boostFrame] air frames in (-1 for none). */
    private fun crosses(width: Double, takeOffX: Double, boostFrame: Int): Boolean {
        val g = Game(gapLevel(width))
        var tapped = false
        var air = 0
        var boosted = false
        var guard = 0
        while (g.state == GameState.RUNNING && guard++ < 6000) {
            if (!tapped && g.x >= takeOffX) { g.onTap(); tapped = true }
            if (tapped && !g.grounded) {
                air++
                if (!boosted && boostFrame >= 0 && air >= boostFrame && g.canDoubleJump) {
                    g.onTap(); boosted = true
                }
            }
            g.update(Tuning.FIXED_DT)
            if (tapped && g.grounded && g.x > 40.0 + width) return true
            if (g.x > 40.0 + width + 8.0) return g.state == GameState.RUNNING
        }
        return false
    }

    @Test fun `sweep every way across each gap`() {
        println("GAP SWEEP  (take-off positions that work, and the boost window at the best one)")
        var w = 5.0
        while (w <= 7.2001) {
            // take-off positions, a frame apart, over the last 6 units of floor
            var singleOk = 0
            var bestBoostRun = 0
            var bestAt = 0.0
            var x = 34.0
            while (x <= 41.0) {
                if (crosses(w, x, -1)) singleOk++
                var run = 0; var best = 0
                for (b in 20..86) {
                    if (crosses(w, x, b)) { run++; if (run > best) best = run } else run = 0
                }
                if (best > bestBoostRun) { bestBoostRun = best; bestAt = x }
                x += Tuning.RUN_SPEED * Tuning.FIXED_DT
            }
            println("  %.2fu  single-jump take-offs=%-3d  widest boost window=%.3fs (%d frames) at x=%.2f".format(
                w, singleOk, bestBoostRun * Tuning.FIXED_DT, bestBoostRun, bestAt))
            w += 0.2
        }
        assertTrue(true)
    }
}
