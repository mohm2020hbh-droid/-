package com.fliperror.core

/**
 * LEVEL 27 - "SPORE"  (WORLD 5, SPORE CAVERNS)
 *
 * Under the roots, where things move.
 *
 * Everything in the two levels before this either switched on or grew; the
 * caverns are the first stretch of the forest where the hazard TRAVELS. A spore
 * drift crosses the lane slowly, its phase solved so it is always coming at the
 * runner rather than fleeing them - the half of the cycle a person can read -
 * and a pulse plant beats beside it on a cycle of its own.
 *
 * The floor breathes here too: boughs that rise and fall and carry whoever is
 * standing on them, which the engine allows because vertical movement does not
 * touch the runner's x, and the whole fairness proof rests on x staying exactly
 * RUN_SPEED * time.
 */
object Level27 {

    const val BPM = 220.0
    private val O = Overgrowth(BPM)

    private const val GROUND = 0.0
    private const val LOW = -3.2

    val finishX = O.beat(133.0)                  // 344.5 units -> 36.3 seconds

    fun build(): Level {
        val solids = listOf(
            O.moss(-14.0, 50.0, GROUND),
            O.moss(54.2, 94.0, GROUND),        // gap 4.20u
            O.breathingBough(98.2, 132.0, GROUND, rise = 0.8, bars = 3.0, lowAt = 99.0),
            O.moss(136.2, 178.0, GROUND),      // gap 4.20u
            // 7.60u, not 6.60u. Dropping 3.2 to the lower cavern buys the runner
            // most of two units of reach, so a 6.60 crossing here is barely past
            // what a single jump covers - the worst place a gap can be, because
            // the second tap then has to be near perfect. The verifier measured
            // it at 0.067s. Past 7.60 the boost is the only answer and the
            // timing relaxes back out.
            O.moss(185.6, 226.0, LOW),         // gap 7.60u - the boost, and down
            O.moss(230.2, 272.0, LOW),         // gap 4.20u
            O.breathingBough(276.2, 306.0, LOW, rise = 0.7, bars = 2.5, lowAt = 277.0),
            O.moss(313.0, 366.0, LOW),         // gap 7.00u - the second boost
        )

        val hazards = ArrayList<Hazard>()
        // 0-12%: known words.
        hazards += O.rootRise(11.0, GROUND, height = 1.25, upAt = 11.0)
        hazards += O.snapFlower(22.0, GROUND, size = 0.9, snapAt = 22.0)
        hazards += O.rootRise(33.0, GROUND, height = 1.25, upAt = 33.0)
        // 13-17%: the first drift, alone, so its speed can be watched once.
        hazards += O.sporeStream(44.0, GROUND, reach = 2.6)
        // 19-26%: the pulse, and a seed out of the canopy between two of them.
        hazards += O.pulsePlant(62.0, GROUND, pulseAt = 62.0)
        hazards += O.fallingSeed(72.0, GROUND, downAt = 72.0)
        hazards += O.pulsePlant(82.0, GROUND, pulseAt = 82.0)
        // 30-38%: onto the breathing bough. Drift, root, drift.
        hazards += O.sporeStream(104.0, GROUND, reach = 2.8)
        hazards += O.rootRise(118.0, GROUND, upAt = 118.0)
        hazards += O.sporeStream(126.0, GROUND, reach = 2.4)
        // 42-50%: still floor again, and the run-up to the boost.
        hazards += O.fallingSeed(146.0, GROUND, downAt = 146.0)
        hazards += O.pulsePlant(156.0, GROUND, pulseAt = 156.0)
        hazards += O.rootRise(166.0, GROUND, upAt = 166.0)
        // 56-64%: the lower cavern.
        hazards += O.sporeStream(194.0, LOW, reach = 2.8)
        hazards += O.pulsePlant(208.0, LOW, pulseAt = 208.0)
        hazards += O.fallingSeed(218.0, LOW, downAt = 218.0)
        // 69-78%: roots in a wave, then a drift over them.
        hazards += O.rootRun(240.0, LOW, count = 3, spacing = 9.0)
        // 81-88%: the second bough.
        hazards += O.pulsePlant(284.0, LOW, pulseAt = 284.0)
        hazards += O.sporeStream(294.0, LOW, reach = 2.2)
        // 94-98%: the slot.
        hazards += O.fallenSeed(324.0, LOW)
        hazards += O.fallenSeed(328.16, LOW)
        hazards += O.fallenSeed(332.32, LOW)

        val stars = listOf(
            Star(52.0, 4.4),
            Star(188.0, LOW + 4.4),
            Star(316.0, LOW + 4.4),
        )

        return Level(
            id = 27, name = "SPORE", subtitle = "IT TRAVELS",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
