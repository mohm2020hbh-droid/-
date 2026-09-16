package com.fliperror.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The near miss drives a feedback cue, so it has exactly two jobs: fire when a
 * spike was genuinely close, and stay silent otherwise. A cue that fires on
 * every comfortable jump is noise, and noise is worse than no cue at all.
 */
class NearMissTest {

    private fun withSpike(spikeX: Double) = Level(
        id = 0, name = "N", subtitle = "", bpm = Tuning.BPM,
        solids = listOf(Solid(-10.0, 200.0, 0.0)),
        hazards = listOf(Hazard(HazardKind.SPIKE_UP, spikeX, spikeX + 1.0, 0.0, 1.0)),
        stars = emptyList(), finishX = 400.0,
    )

    /** Jump from [takeOff] and report how it went past the spike. */
    private class Pass(val survived: Boolean, val nearMisses: Int, val clearance: Double)

    private fun jumpAt(takeOff: Double, spikeX: Double): Pass {
        val level = withSpike(spikeX)
        val g = Game(level)
        // Measure through the same boxes the game kills with, or the number here
        // describes a different pass than the one the cue is judging.
        val box = level.hazards[0].hitBoxAt(0.0)
        var clearance = Double.MAX_VALUE
        var tapped = false
        var guard = 0
        while (g.state == GameState.RUNNING && g.x < spikeX + 6.0 && guard++ < 20_000) {
            if (!tapped && g.x >= takeOff) { g.onTap(); tapped = true }
            g.update(Tuning.FIXED_DT)
            val hb = g.hitBox
            if (hb.x1 > box.x0 && hb.x0 < box.x1) clearance = minOf(clearance, hb.y0 - box.y1)
        }
        return Pass(g.state == GameState.RUNNING, g.nearMisses, clearance)
    }

    @Test fun `a spike cleared with room to spare says nothing`() {
        val spikeX = 40.0
        // Sweep every take-off that survives and keep the roomiest one.
        var roomiest: Pass? = null
        var x = spikeX - 6.0
        while (x < spikeX) {
            val p = jumpAt(x, spikeX)
            if (p.survived && (roomiest == null || p.clearance > roomiest!!.clearance)) roomiest = p
            x += 0.05
        }
        val best = roomiest!!
        assertTrue(best.clearance > Tuning.NEAR_MISS_GAP,
            "the roomiest survivable jump only clears by ${best.clearance}u")
        assertEquals(0, best.nearMisses, "a comfortable jump must not fire the cue")
    }

    @Test fun `a spike cleared by a hair says so, exactly once`() {
        val spikeX = 40.0
        var tightest: Pass? = null
        var tightestX = 0.0
        var x = spikeX - 6.0
        while (x < spikeX) {
            val p = jumpAt(x, spikeX)
            if (p.survived && p.clearance >= 0.0 &&
                (tightest == null || p.clearance < tightest!!.clearance)) { tightest = p; tightestX = x }
            x += 0.05
        }
        val hair = tightest!!
        assertTrue(hair.clearance <= Tuning.NEAR_MISS_GAP,
            "the tightest survivable jump still clears by ${hair.clearance}u, " +
                "so the cue can never fire; NEAR_MISS_GAP is too small")
        assertEquals(1, hair.nearMisses,
            "grazing the spike at take-off x=$tightestX (clearance ${hair.clearance}u) fired ${hair.nearMisses} cues")
        println("NEAR MISS: tightest survivable pass clears by ${"%.3f".format(hair.clearance)}u " +
            "from take-off x=${"%.2f".format(tightestX)} (spike at $spikeX)")
    }

    @Test fun `running along the ground past nothing is silent`() {
        val g = Game(withSpike(400.0))
        repeat(2000) { g.update(Tuning.FIXED_DT) }
        assertEquals(0, g.nearMisses)
    }

    /** Guards the threshold: it only means anything if real passes fall either side. */
    @Test fun `the threshold sits between the tight passes and the roomy ones`() {
        val level = Level1.build()
        val report = LevelVerifier(level).analyse()
        val g = Game(level)
        val mins = HashMap<Double, Double>()
        var i = 0
        var guard = 0
        while (g.state == GameState.RUNNING && guard++ < 40_000) {
            if (i < report.jumps.size && g.x >= report.jumps[i].x) { g.onTap(); i++ }
            g.update(Tuning.FIXED_DT)
            val hb = g.hitBox
            level.forEachHazardNear(hb.x0, hb.x1) { h ->
                val b = h.hitBoxAt(g.elapsed)
                if (hb.x1 > b.x0 && hb.x0 < b.x1) {
                    val gap = if (h.kind == HazardKind.SPIKE_UP) hb.y0 - b.y1 else b.y0 - hb.y1
                    val cur = mins[h.x0]
                    if (cur == null || gap < cur) mins[h.x0] = gap
                }
            }
        }
        val sorted = mins.values.sorted()
        println("CLEARANCES on the verified line (${sorted.size} hazards passed): " +
            sorted.joinToString(" ") { "%.3f".format(it) })
        val tight = sorted.count { it <= Tuning.NEAR_MISS_GAP }
        val roomy = sorted.count { it > Tuning.NEAR_MISS_GAP }
        assertTrue(tight > 0, "nothing on the verified line is tight enough to ever fire the cue")
        assertTrue(roomy > tight, "most of the line should be comfortable; $tight tight vs $roomy roomy")
    }

    @Test fun `the cue fires on level 1, but rarely enough to still mean something`() {
        // A feedback cue has two failure modes and this pins both: silent, so the
        // work was wasted, or constant, so it is just noise on every spike.
        val level = Level1.build()
        val report = LevelVerifier(level).analyse()
        val g = Game(level)
        var i = 0
        var guard = 0
        while (g.state == GameState.RUNNING && guard++ < 40_000) {
            if (i < report.jumps.size && g.x >= report.jumps[i].x) { g.onTap(); i++ }
            g.update(Tuning.FIXED_DT)
        }
        assertEquals(GameState.COMPLETE, g.state)
        assertTrue(g.nearMisses in 1..10,
            "a 31s run fired ${g.nearMisses} close calls; it should mark a handful, not every spike")
    }
}
