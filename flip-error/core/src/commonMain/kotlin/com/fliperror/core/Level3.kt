package com.fliperror.core

/**
 * LEVEL 3 - "MOVING CHAOS"
 *
 * Level 2 made the floor stop existing. Level 3 makes the danger refuse to stay
 * still. Almost every hazard here is on a path - sliding along its platform,
 * rising out of the floor, or dropping from the ceiling - and the level's
 * question is no longer "when do I jump" but "where will that be when I get
 * there".
 *
 * Every path is a sine on the level's own bar, so a hazard is back where it
 * started every bar and the answer is the same on every attempt. Nothing here
 * is random; it is all readable on the approach and learnable in one death.
 */
object Level3 {

    const val BPM = 160.0
    private val BEAT = Tuning.RUN_SPEED * 60.0 / BPM        // 3.5625 u
    private val BAR_S = 4.0 * 60.0 / BPM                    // 1.5 s
    private fun beat(n: Double) = n * BEAT

    private const val GROUND = 0.0
    private const val HIGH = 2.4
    private const val LOW = -3.0

    private fun spike(x: Double, y: Double) = Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0)
    private fun drop(x: Double, tip: Double) = Hazard(HazardKind.SPIKE_DOWN, x, x + 1.0, tip, tip + 1.0)

    private fun slider(x: Double, y: Double, reach: Double, phase: Double = 0.0, bars: Double = 1.0) =
        Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0,
            Motion(dx = reach, period = BAR_S * bars, phase = phase))

    private fun piston(x: Double, y: Double, rise: Double, phase: Double = 0.0, bars: Double = 0.75) =
        Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0,
            Motion(dy = rise, period = BAR_S * bars, phase = phase))

    /** A ceiling blade that swings down into the jump arc and back up. */
    private fun blade(x: Double, tip: Double, fall: Double, phase: Double = 0.0, bars: Double = 1.0) =
        Hazard(HazardKind.SPIKE_DOWN, x, x + 1.0, tip, tip + 1.0,
            Motion(dy = -fall, period = BAR_S * bars, phase = phase))

    val finishX = beat(96.0)                     // 342 units -> 36.0 seconds

    fun build(): Level {
        val solids = listOf(
            Solid(-14.0, 92.0, GROUND),           // the long teaching floor
            Solid(96.4, 142.0, GROUND),           // gap 4.40u
            Solid(146.6, 188.0, GROUND),          // gap 4.60u
            Solid(194.8, 236.0, GROUND),          // gap 6.80u - boost
            Solid(240.2, 268.0, GROUND),          // gap 4.20u, the short breath
            // a lift: it carries, and it is the only way up to the high shelf
            Solid(272.0, 278.0, -0.2, -40.0, Motion(dy = 2.8, period = BAR_S * 1.5)),
            Solid(282.0, 300.0, HIGH),            // the shelf the lift reaches
            Solid(304.4, 355.0, GROUND),          // gap 4.40u down to the finish run
        )

        val hazards = ArrayList<Hazard>()
        // 0-10% hook: one still spike, then immediately one that is not.
        hazards += spike(14.0, GROUND)
        hazards += slider(26.0, GROUND, 2.2)
        // 10-35%: the vocabulary, one idea at a time, on a floor that forgives -
        // but never for long. Roughly a hazard every ten units from here on.
        hazards += spike(36.0, GROUND)
        hazards += slider(52.0, GROUND, 1.8, phase = 0.5)
        hazards += piston(65.0, GROUND - 0.9, 1.4)
        hazards += spike(76.0, GROUND)
        hazards += slider(85.0, GROUND, 2.0, bars = 0.75)
        // 35-55%: the same ideas, now over gaps and under a blade.
        hazards += spike(106.0, GROUND)
        hazards += slider(118.0, GROUND, 2.6, phase = 0.25)
        hazards += blade(130.0, 3.4, 1.3)
        hazards += spike(156.0, GROUND)
        hazards += slider(168.0, GROUND, 3.0, phase = 0.75)
        hazards += piston(180.0, GROUND - 0.9, 1.5)
        // 55-70%: past the boost gap, the pressure does not let up.
        hazards += spike(204.0, GROUND)
        hazards += slider(216.0, GROUND, 2.4, phase = 0.5)
        hazards += piston(228.0, GROUND - 0.9, 1.4, phase = 0.25)
        hazards += spike(250.0, GROUND)
        hazards += slider(260.0, GROUND, 1.8, bars = 0.75)
        // 70-90%: the lift and the shelf, with a blade over the landing.
        hazards += blade(290.0, 3.4, 1.2, phase = 0.5)
        // 90-100%: the finish. Three in a row, the last two a slot to land in,
        // and a slider across the approach so the slot is never in the same place
        // twice - this is the hardest thing in the level, by design.
        hazards += spike(314.0, GROUND)
        hazards += slider(322.0, GROUND, 1.6, bars = 0.5)
        hazards += spike(332.0, GROUND)
        hazards += spike(335.6, GROUND)

        val stars = listOf(
            Star(60.0, 2.5),          // one honest jump on the teaching floor
            Star(144.3, 4.3),         // over the 4.60u gap, which one jump clears:
                                      // the coin is what the boost is spent on
            Star(291.0, 5.0),         // on the high shelf, past the blade
        )

        return Level(
            id = 3, name = "MOVING CHAOS", subtitle = "IT WON'T WAIT FOR YOU",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
