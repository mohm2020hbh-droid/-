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

    /**
     * A spike that slides along its platform. One bar per round trip, so it is
     * moving visibly for the whole approach and is back where it started every
     * time the music comes round - the player learns it once and owns it.
     */
    private fun slider(x: Double, y: Double, reach: Double, phase: Double = 0.0) =
        Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0,
            Motion(dx = reach, period = Tuning.BAR, phase = phase))

    /** A spike that rises out of the floor and sinks back into it. */
    private fun piston(x: Double, y: Double, rise: Double, period: Double, phase: Double = 0.0) =
        Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0,
            Motion(dy = rise, period = period, phase = phase))
    private fun ceilingSpikes(x0: Double, x1: Double, tip: Double): List<Hazard> {
        val out = ArrayList<Hazard>()
        var x = x0
        while (x < x1 - 0.01) { out += Hazard(HazardKind.SPIKE_DOWN, x, x + 1.0, tip, tip + 1.0); x += 1.0 }
        return out
    }

    val finishX = beat(83.0)                  // 338.0 units -> 35.6 seconds

    fun build(): Level {
        val solids = listOf(
            // --- 0-10% HOOK: two spikes, then the floor runs out -----------
            Solid(-14.0, 26.0, GROUND),
            // --- 10-30% the four gaps, each a different answer -------------
            Solid(29.6, 50.0, GROUND),        // gap 3.60u - comfortable, teaches the shape
            Solid(52.4, 68.0, GROUND),        // gap 2.40u - short, and it lands you into work
            Solid(72.3, 94.0, GROUND),        // gap 4.30u - at the edge of one jump
            // 6.30u, not 6.60u. At 6.60 the crossing needed a near-maximum boost,
            // which is a LATE one - the first jump's height is kept longer before
            // the second tap resets vy - so the tap at the top of the arc, the one
            // a person actually makes, fell in the pit every time. The gap is
            // still past what a single jump reaches; what changed is that the
            // instinctive second tap now clears it.
            Solid(100.3, 122.0, GROUND),      // gap 6.30u - past one jump. the boost, or nothing
            // --- 30-55% combine: widths alternate so no rhythm survives ----
            Solid(126.4, 142.0, GROUND),      // gap 4.40u
            Solid(144.8, 158.0, GROUND),      // gap 2.80u
            // A BOOST GAP NEEDS A RUNWAY. The second tap can be taken anywhere in
            // its window and the distance it buys varies by almost a full unit -
            // measured, 7.68u at the apex against 8.63u at the last legal frame -
            // so a boosted crossing does not land on a spot, it lands in a BAND.
            // Both of this level's boost gaps used to put their next obstacle two
            // units past the far end of that band, which meant the tap at the top
            // of the arc landed correctly and then ran straight into something
            // with no room to set up. The landing platform is longer now and what
            // is on it has moved back.
            Solid(164.0, 186.0, GROUND),      // gap 6.00u - boost, then room to land
            Solid(189.4, 210.0, GROUND),      // gap 3.40u, into the low ceiling
            // --- 55-70% the breath, and the coin worth stopping for --------
            Solid(213.6, 246.0, GROUND),      // gap 3.60u into a long flat rest
            // --- 70-90% GAUNTLET: descending, tightening -------------------
            Solid(250.0, 260.0, STEP),        // gap 4.00u
            Solid(264.4, 274.0, LOW),         // gap 4.40u
            Solid(280.4, 294.0, LOW),         // gap 6.40u - boost over the deepest pit, and a runway after it
            Solid(297.6, 312.0, LOW),         // gap 3.60u
            // --- 90-100% the finish -----------------------------------------
            Solid(316.0, 349.0, LOW),         // gap 4.00u, then the slot
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
        hazards += slider(133.0, GROUND, 2.0)                 // slides: read where it IS
        hazards += spike(150.0, GROUND)
        hazards += slider(175.0, GROUND, 1.8, phase = 0.5)    // the same idea, out of step
        hazards += spike(202.0, GROUND)
        // A low ceiling on the run-up to the rest: the one place the second tap
        // is fatal, set immediately after the gaps that demand it.
        hazards += ceilingSpikes(192.0, 200.0, 2.3)
        // 70-90%: the gauntlet's spikes sit on the two longest landings.
        hazards += spike(288.0, LOW)
        hazards += piston(304.0, LOW - 0.9, 1.3, Tuning.BAR * 0.75)  // rises out of the floor
        // 90-100%: clear one, then land in the slot between the last two.
        hazards += spike(322.0, LOW)
        hazards += spike(329.0, LOW)
        hazards += spike(332.7, LOW)

        // The rest is short on purpose: one spike closes it before the gauntlet,
        // so level 2 never hands back as much quiet as level 1 does.
        hazards += spike(222.0, GROUND)
        hazards += spike(236.0, GROUND)

        // Three STAR COINS, none of them on the easy line.
        val stars = listOf(
            Star(230.0, 2.6),        // the breath: one plain jump, if you take it
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
