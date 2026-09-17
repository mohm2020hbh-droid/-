package com.fliperror.core

/**
 * LEVEL 7 - "SAND RUN"  (WORLD 2, NEON DESERT)
 *
 * The first level of a world that does not stand still. Where world 1 asked
 * when to jump, this one asks where the ground will BE when you land: half the
 * floor here breathes up and down, and the sand rolls sideways across the rest.
 *
 * Its whole job is to teach two words - shifting sand and the sand wave - one at
 * a time, on terms gentle enough to learn them, and only then to hand the player
 * both at once on the way to the low shelf. Everything it uses comes out of
 * [Desert], including the rule that anything standing on breathing sand breathes
 * with it, which is what makes any of this fair.
 */
object Level7 {

    const val BPM = 168.0
    private val D = Desert(BPM)

    private const val GROUND = 0.0
    private const val LOW = -3.0

    private fun spike(x: Double, y: Double) = Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0)

    val finishX = D.beat(101.0)                  // 342.7 units -> 36.1 seconds

    fun build(): Level {
        val solids = listOf(
            Solid(-14.0, 54.0, GROUND),                             // the opening, still and honest
            D.shiftingSand(58.2, 84.0, GROUND, 0.7),                // gap 4.20u, the floor breathes
            Solid(88.2, 120.0, GROUND),                             // gap 4.20u, back to stone
            D.shiftingSand(124.2, 152.0, GROUND, 0.7, phase = 0.5), // gap 4.20u, out of step
            Solid(158.4, 190.0, GROUND),                            // gap 6.40u - the boost
            D.shiftingSand(194.2, 220.0, GROUND, 0.8, bars = 2.5),  // gap 4.20u, a longer breath
            Solid(224.2, 262.0, GROUND),                            // gap 4.20u, the one rest
            D.shiftingSand(266.2, 292.0, LOW, 1.0, phase = 0.25, bars = 4.0),  // gap 4.20u, and down
            Solid(296.2, 350.0, LOW),                               // gap 4.20u into the finish
        )

        val hazards = ArrayList<Hazard>()
        // 0-16%: still ground. A spike, then the first crest, with nothing else
        // happening - this is where the sand wave is learned.
        hazards += spike(14.0, GROUND)
        hazards += D.sandWave(28.0, GROUND, 2.4)
        hazards += spike(42.0, GROUND)
        // 17-24%: the floor breathes for the first time, and carries one spike.
        hazards += D.sandSpike(70.0, GROUND, 0.7)
        // 26-35%: back on stone, a spike and a crest, then a clear run-up.
        hazards += spike(96.0, GROUND)
        hazards += D.sandWave(104.0, GROUND, 2.4)
        // 36-44%: breathing sand again, half a cycle out of phase with the first.
        hazards += D.sandSpike(136.0, GROUND, 0.7, phase = 0.5)
        // 46-55%: stone, and the run-up to the 6.40u boost gap.
        hazards += spike(168.0, GROUND)
        hazards += D.sandWave(176.0, GROUND, 2.2)
        // 57-64%: the long breath, with something riding it.
        hazards += D.sandSpike(206.0, GROUND, 0.8, bars = 2.5)
        // 65-76%: the rest. One crest, one spike, far apart, nothing moving under them.
        hazards += D.sandWave(236.0, GROUND, 2.6)
        hazards += spike(250.0, GROUND)
        // 78-85%: down onto the low shelf, and both words together for the first
        // time - a crest rolling on sand that is breathing under it.
        hazards += D.waveOnSand(276.0, LOW, 2.2, 1.0, sandPhase = 0.25)
        // 90-100%: the finish. Still ground, so nothing here is luck: a spike, a
        // crest across the run-up, and a two-spike slot to land between.
        hazards += spike(310.0, LOW)
        hazards += D.sandWave(318.0, LOW, 2.0)
        hazards += spike(330.0, LOW)
        hazards += spike(333.5, LOW)

        val stars = listOf(
            Star(46.0, 2.5),          // the still opening: one honest jump
            Star(112.0, 4.3),         // too high for a jump alone - the boost is
                                      // the price of the coin, not of any crossing
            Star(284.0, -0.7),        // out over the breathing shelf, timed to its rise
        )

        return Level(
            id = 7, name = "SAND RUN", subtitle = "THE GROUND IS BREATHING",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
