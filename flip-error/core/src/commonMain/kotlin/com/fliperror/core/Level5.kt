package com.fliperror.core

/**
 * LEVEL 5 - "OVERDRIVE"
 *
 * Everything at once, at 186. Gaps that need a boost next to gaps that punish
 * one, hazards on three different paths, and floors that are only there for
 * part of every bar. A blinking platform is the level's own idea: it turns a
 * landing into a timing problem before the jump is even taken, because the
 * question is not "can I reach it" but "will it be there when I do".
 *
 * The blink is on the bar like everything else here, and it fades before it
 * goes, so the platform tells the player it is leaving rather than dropping
 * them through it.
 */
object Level5 {

    const val BPM = 186.0
    private val BEAT = Tuning.RUN_SPEED * 60.0 / BPM        // 3.0645 u
    private val BAR_S = 4.0 * 60.0 / BPM                    // 1.2903 s
    private fun beat(n: Double) = n * BEAT

    private const val GROUND = 0.0
    private const val MID = -1.8
    private const val LOW = -3.6

    private fun spike(x: Double, y: Double) = Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0)

    private fun lid(x0: Double, x1: Double, tip: Double): List<Hazard> {
        val out = ArrayList<Hazard>()
        var x = x0
        while (x < x1 - 0.01) { out += Hazard(HazardKind.SPIKE_DOWN, x, x + 1.0, tip, tip + 1.0); x += 1.0 }
        return out
    }

    private fun slider(x: Double, y: Double, reach: Double, phase: Double = 0.0, bars: Double = 1.0) =
        Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0,
            Motion(dx = reach, period = BAR_S * bars, phase = phase))

    private fun piston(x: Double, y: Double, rise: Double, phase: Double = 0.0, bars: Double = 0.75) =
        Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0,
            Motion(dy = rise, period = BAR_S * bars, phase = phase))

    private fun blinker(x0: Double, x1: Double, top: Double, phase: Double, on: Double = 0.62) =
        Solid(x0, x1, top, -40.0, null, Blink(BAR_S * 2.0, on, phase))

    val finishX = beat(112.0)                    // 343.2 units -> 36.1 seconds

    fun build(): Level {
        val solids = listOf(
            Solid(-14.0, 62.0, GROUND),           // the opening floor, and the last long one
            Solid(66.4, 104.0, GROUND),           // gap 4.40u
            Solid(110.8, 140.0, GROUND),          // gap 6.80u - boost
            // Both blink phases are DERIVED from the x they are landed on - see
            // blinkPhaseFor. Hand-picked, the first of them was dark at exactly
            // the moment a plain jump would have arrived, so the only crossing
            // left was a second tap in the last frames of its window: solvable,
            // and indistinguishable from a broken level at 42%.
            blinker(144.6, 156.0, GROUND, blinkPhaseFor(144.9, BAR_S * 2.0)),
            Solid(160.4, 196.0, GROUND),          // gap 4.40u
            Solid(200.6, 226.0, MID),             // gap 4.20u, step down
            blinker(230.8, 242.0, MID, blinkPhaseFor(231.1, BAR_S * 2.0)),
            Solid(246.4, 286.0, LOW),             // gap 4.40u, and down again
            Solid(293.0, 320.0, LOW),             // gap 7.00u - the second boost
            Solid(324.4, 352.0, LOW),             // gap 4.40u into the finish
        )

        val hazards = ArrayList<Hazard>()
        // 0-10%: no teaching. Level 5 opens at the speed it ends at.
        hazards += spike(12.0, GROUND)
        hazards += slider(21.0, GROUND, 1.8)
        hazards += spike(31.0, GROUND)
        hazards += piston(40.0, GROUND - 0.9, 1.4)
        hazards += spike(51.0, GROUND)
        // 10-35%: the boost gap, guarded on both sides.
        hazards += spike(74.0, GROUND)
        hazards += slider(86.0, GROUND, 2.2, phase = 0.5)
        hazards += spike(97.0, GROUND)
        hazards += spike(120.0, GROUND)
        hazards += slider(131.0, GROUND, 1.8, phase = 0.25)
        // 35-55%: the blinkers, with something on the far side of each.
        hazards += spike(170.0, GROUND)
        hazards += piston(182.0, GROUND - 0.9, 1.5)
        hazards += spike(210.0, MID)
        hazards += slider(219.0, MID, 1.6, bars = 0.75)
        // 55-80%: a lid across the run-up to the second boost, so it has to be
        // taken from exactly the right place.
        hazards += spike(256.0, LOW)
        hazards += lid(262.0, 278.0, 3.7)
        hazards += spike(268.0, LOW)
        hazards += slider(276.0, LOW, 1.5, bars = 0.5)
        // 80-100%: the finish. Everything the level has, in twenty units.
        hazards += spike(300.0, LOW)
        hazards += piston(310.0, LOW - 0.9, 1.4, phase = 0.5)
        hazards += spike(330.0, LOW)
        hazards += spike(334.0, LOW)
        hazards += spike(338.3, LOW)

        val stars = listOf(
            Star(45.0, 2.5),          // the opening floor, one honest jump
            Star(198.3, 4.2),         // over a gap the line crosses with ONE jump:
                                      // reaching it costs a boost it did not need
            Star(289.5, 4.1),         // over the second, above the deepest pit
        )

        return Level(
            id = 5, name = "OVERDRIVE", subtitle = "ALL OF IT, AT ONCE",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
