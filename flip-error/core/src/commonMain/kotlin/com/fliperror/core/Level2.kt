package com.fliperror.core

/**
 * LEVEL 2 - "GAP LOGIC"
 *
 * Level 1 teaches that the ground can kill you. Level 2 teaches that the ground
 * can stop existing. Its subject is gap width: every gap is a different size, so
 * no single rhythm carries you through, and one gap in each half is wider than a
 * jump can reach. Those are the level's thesis - the second jump is not a bonus
 * here, it is the only way across, and Level2Test proves it by running the
 * verifier as a single-jump player and requiring that run to fail.
 *
 * Widths are quoted against the two reaches the tuning gives: 4.94u for a jump
 * and about 7.7u for a jump and a boost.
 */
object Level2 {

    private fun beat(n: Double) = n * Tuning.BEAT_UNITS

    private const val GROUND = 0.0
    private const val STEP = -1.6
    private const val LOW = -3.4

    private fun spike(x: Double, y: Double) = Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0)
    private fun ceilingSpikes(x0: Double, x1: Double, tip: Double): List<Hazard> {
        val out = ArrayList<Hazard>()
        var x = x0
        while (x < x1 - 0.01) { out += Hazard(HazardKind.SPIKE_DOWN, x, x + 1.0, tip, tip + 1.0); x += 1.0 }
        return out
    }

    val finishX = beat(82.0)                  // 333.9 units -> 35.1 seconds

    fun build(): Level {
        val solids = listOf(
            // --- 0-10% HOOK: two spikes, then the floor runs out -----------
            Solid(-14.0, 26.0, GROUND),
            // --- 10-30% the four gaps, each a different answer -------------
            Solid(29.6, 50.0, GROUND),        // gap 3.60u - comfortable, teaches the shape
            Solid(52.4, 68.0, GROUND),        // gap 2.40u - short, and it lands you into work
            Solid(72.3, 94.0, GROUND),        // gap 4.30u - at the edge of one jump
            Solid(100.6, 122.0, GROUND),      // gap 6.60u - past it. the boost, or nothing
            // --- 30-55% combine: widths alternate so no rhythm survives ----
            Solid(126.4, 142.0, GROUND),      // gap 4.40u
            Solid(144.8, 158.0, GROUND),      // gap 2.80u
            Solid(164.9, 180.0, GROUND),      // gap 6.90u - boost
            Solid(183.4, 208.0, GROUND),      // gap 3.40u, into the low ceiling
            // --- 55-70% the breath, and the coin worth stopping for --------
            Solid(211.6, 244.0, GROUND),      // gap 3.60u into a long flat rest
            // --- 70-90% GAUNTLET: descending, tightening -------------------
            Solid(248.0, 258.0, STEP),        // gap 4.00u
            Solid(262.4, 272.0, LOW),         // gap 4.40u
            Solid(279.1, 290.0, LOW),         // gap 7.10u - boost, over the deepest pit
            Solid(293.6, 308.0, LOW),         // gap 3.60u
            // --- 90-100% the finish -----------------------------------------
            Solid(312.0, 345.0, LOW),         // gap 4.00u, then the slot
        )

        val hazards = ArrayList<Hazard>()
        // 0-10%: the hook. The level opens by asking for something rather than
        // by letting you watch.
        hazards += spike(13.0, GROUND)
        hazards += spike(18.0, GROUND)
        // 10-30%: one spike per platform, set far enough back that clearing it
        // still leaves a readable run-up to the ledge behind it.
        hazards += spike(58.0, GROUND)
        hazards += spike(84.0, GROUND)
        // 30-55%: spikes guarding the landings, so a gap is never the only threat.
        hazards += spike(133.0, GROUND)
        hazards += spike(150.0, GROUND)
        hazards += spike(171.0, GROUND)
        hazards += spike(200.0, GROUND)
        // A low ceiling on the run-up to the rest: the one place the second tap
        // is fatal, set immediately after the gaps that demand it.
        hazards += ceilingSpikes(186.0, 194.0, 2.3)
        // 70-90%: the gauntlet's spikes sit on the two longest landings.
        hazards += spike(283.0, LOW)
        hazards += spike(300.0, LOW)
        // 90-100%: clear one, then land in the slot between the last two.
        hazards += spike(318.0, LOW)
        hazards += spike(325.0, LOW)
        hazards += spike(328.7, LOW)

        // The rest is short on purpose: one spike closes it before the gauntlet,
        // so level 2 never hands back as much quiet as level 1 does.
        hazards += spike(220.0, GROUND)
        hazards += spike(234.0, GROUND)

        // Three STAR COINS, none of them on the easy line.
        val stars = listOf(
            Star(228.0, 2.6),        // the breath: one plain jump, if you take it
            Star(144.2, 4.3),        // over the 4.40u gap, which the line crosses with one
                                     // jump: reaching this costs a boost it did not need
            Star(275.0, 3.9),        // mid-gauntlet, over the 5.80u gap and the deepest pit
        )

        return Level(
            id = 2,
            name = "GAP LOGIC",
            subtitle = "THE FLOOR IS A SUGGESTION",
            bpm = Tuning.BPM,
            solids = solids,
            hazards = hazards,
            stars = stars,
            finishX = finishX,
        )
    }
}
