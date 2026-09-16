package com.fliperror.core

/**
 * LEVEL 1 - "FIRST STEPS HURT"
 *
 * Built from the beat map in docs/FLIP_ERROR_GDD.md section 13.
 * 140 BPM, one bar = 4 beats = 16.29 units, so `beat(n)` is the x of beat n.
 *
 * Deviations from the written beat map, and why, are listed in
 * docs/FLIP_ERROR_LEVEL1_NOTES.md. Nothing here is placed off the beat grid
 * except the one deliberate early spike in the bar 12-13 trap.
 */
object Level1 {

    private fun beat(n: Double) = n * Tuning.BEAT_UNITS

    private const val GROUND = 0.0
    private const val LOW = -3.6          // the floor the gauntlet descends to

    private fun spike(x: Double, y: Double) = Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0)
    private fun ceilingSpikes(x0: Double, x1: Double, tip: Double): List<Hazard> {
        val out = ArrayList<Hazard>()
        var x = x0
        while (x < x1 - 0.01) { out += Hazard(HazardKind.SPIKE_DOWN, x, x + 1.0, tip, tip + 1.0); x += 1.0 }
        return out
    }

    val finishX = beat(74.0)               // 301.3 units -> 31.7 seconds

    fun build(): Level {
        val solids = listOf(
            // --- bars 1-5: flat teaching ground ---------------------------
            Solid(-14.0, 78.0, GROUND),
            // --- bar 5-6: step up, then the first gap ---------------------
            Solid(78.0, 88.0, 2.0),
            Solid(91.85, 100.0, 2.0),                 // gap = 3.85u = 78% of a full jump
            // --- bars 7-10: back down, ceiling corridor, spike chain ------
            Solid(100.0, 141.0, GROUND),
            Solid(112.0, 122.0, 6.0, 3.3),            // the corridor slab itself
            Solid(144.85, 215.0, GROUND),             // gap = 3.85u
            // --- bars 14-15: the gauntlet, a staircase over a pit ---------
            Solid(219.0, 225.0, -1.2),
            Solid(229.0, 235.0, -2.4),
            Solid(239.0, 250.0, LOW),
            Solid(252.5, 272.0, LOW),
            // --- bars 16-17: the finish spike -----------------------------
            Solid(272.0, 279.0, -1.6),                // step up, must be jumped onto
            Solid(283.0, 320.0, LOW),
        )

        val hazards = ArrayList<Hazard>()
        // bar 2: the first tap.
        hazards += spike(beat(6.0), GROUND)
        // bars 3-4: the pulse. Half-bar spacing; see notes for why not one beat.
        hazards += spike(beat(9.0), GROUND)
        hazards += spike(beat(11.0), GROUND)
        hazards += spike(beat(13.0), GROUND)
        // bar 5: two touching spikes, one jump covers both.
        hazards += spike(beat(17.0), GROUND)
        hazards += spike(beat(17.0) + 1.0, GROUND)
        // bar 7: the spike that lures you into jumping right before the corridor.
        hazards += spike(108.0, GROUND)
        // bars 7-8: ceiling corridor. Any jump in here is fatal.
        hazards += ceilingSpikes(111.5, 122.0, 2.3)
        // bars 9-10: spike, spike, gap.
        hazards += spike(126.5, GROUND)
        hazards += spike(134.6, GROUND)
        hazards += spike(150.5, GROUND)
        hazards += spike(158.6, GROUND)
        // bars 12-13: three on the beat, the fourth half a beat early.
        hazards += spike(beat(45.0), GROUND)
        hazards += spike(beat(47.0), GROUND)
        hazards += spike(beat(49.0), GROUND)
        hazards += spike(beat(50.5), GROUND)
        // bar 15: the spike on the last stair.
        hazards += spike(243.5, LOW)
        // bars 16-17: land in the slot between these two.
        hazards += spike(289.0, LOW)
        hazards += spike(292.9, LOW)

        // Three STAR COINS. All optional, none of them on the line the verifier
        // flies, so the level's own difficulty is untouched by them.
        val stars = listOf(
            Star(60.0, 2.4),         // between two spikes: one ordinary jump, taken on purpose
            Star(171.0, 4.3),        // bar 11's breath, hung above a single jump's ceiling:
                                     // this is what the second jump is FOR
            Star(258.0, -1.0),       // deep in the gauntlet, on the one stretch the fast line
                                     // runs along the floor: taking it costs an extra jump
                                     // between two gaps, where a mistimed one is a pit
        )

        return Level(
            id = 1,
            name = "FIRST STEPS HURT",
            subtitle = "DON'T BLINK",
            bpm = Tuning.BPM,
            solids = solids,
            hazards = hazards,
            stars = stars,
            finishX = finishX,
        )
    }
}
