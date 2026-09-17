package com.fliperror.core

/**
 * LEVEL 9 - "SUN STRIKE"  (WORLD 2)
 *
 * Where the desert stops being scenery. Two timed hazards that contradict each
 * other: sand geysers that erupt out of the floor and have to be jumped, and
 * beams from the sun that hang into the lane and cannot be jumped into.
 *
 * One says GO UP. The other says STAY DOWN. Neither is ever a wall - a geyser
 * is 1.6u and a jump is 2.6u, a beam always stops short of the floor - so every
 * moment of this level has an answer. What it asks is that the player find the
 * answer while both things are pulsing at them, which is a different kind of
 * hard from anything world 1 does, and it is the kind world 2 is for.
 *
 * One layout rule earned itself here: BOTH BOOST GAPS LEAVE FROM STILL STONE.
 * A six-unit gap is already the longest thing the runner can cross, and taking
 * it off sand that is breathing under the take-off means the second tap has to
 * answer a different launch height every attempt - measured, that was a 0.050s
 * window. The sand makes the crossings; the stone makes the long ones.
 */
object Level9 {

    const val BPM = 176.0
    private val D = Desert(BPM)

    private const val GROUND = 0.0
    private const val MID = -1.8
    private const val LOW = -3.6

    private fun spike(x: Double, y: Double) = Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0)

    val finishX = D.beat(106.0)                  // 343.3 units -> 36.1 seconds

    fun build(): Level {
        // The breathing stretches are named, because everything standing on them
        // takes THEIR motion rather than a copy of its numbers - rule 1. A geyser
        // pinned at a fixed height over sand that moves cost this level a 0.038s
        // take-off before the sand was handed to it directly.
        val sandA = D.shiftingSand(100.2, 134.0, GROUND, 0.6, bars = 3.0)
        val sandB = D.shiftingSand(224.2, 258.0, MID, 0.7, phase = 0.5, bars = 4.0)

        val solids = listOf(
            Solid(-14.0, 50.0, GROUND),        // the opening
            Solid(54.2, 96.0, GROUND),         // gap 4.20u
            sandA,                             // gap 4.20u, and the floor starts breathing
            Solid(138.2, 176.0, GROUND),       // gap 4.20u, back to stone for the long one
            Solid(182.8, 220.0, MID),          // gap 6.80u - the first boost, and down
            sandB,                             // gap 4.20u, breathing again
            Solid(262.2, 300.0, LOW),          // gap 4.20u, and down
            Solid(306.4, 352.0, LOW),          // gap 6.40u - the second boost, into the finish
        )

        val hazards = ArrayList<Hazard>()
        // 0-12%: three spikes. Nothing new, on purpose - a level gets to be as
        // hard as it likes once the player has had a moment to look at it, and
        // not before. The geyser used to be the second thing that happened here,
        // which put a 0.163s take-off inside the first eighth of the level.
        hazards += spike(12.0, GROUND)
        hazards += spike(24.0, GROUND)
        hazards += spike(36.0, GROUND)
        // 13%: and now the floor erupts, alone, on still stone, with the whole
        // rest of the platform to land on.
        hazards += D.sandGeyser(46.0, GROUND, upAt = 46.8)
        // 17-27%: the beam, alone. It burns across the first half of a long flat
        // run and is out before anything has to be jumped, so the lesson is free.
        hazards += D.sunLaser(64.0, 2.2, onAt = 60.0)
        hazards += spike(78.0, GROUND)
        hazards += D.sunLaser(88.0, 2.2, onAt = 84.0)
        // 30-38%: both of them, on sand that is breathing underneath.
        hazards += D.sandGeyser(110.0, GROUND, bars = 3.0, upAt = 110.8, riding = sandA.motion)
        hazards += D.sunLaser(118.0, 2.4, bars = 3.0, onAt = 114.0)
        hazards += D.sandSpike(126.0, GROUND, sandA.motion!!)
        // 42-50%: stone, and the run-up to the first boost gap.
        hazards += D.sandWave(146.0, GROUND, 2.2)
        hazards += spike(160.0, GROUND)
        hazards += D.sandWave(166.0, GROUND, 1.8)
        // 55-64%: the mid shelf. Geyser, then beam, back to back.
        hazards += D.sandGeyser(192.0, MID, upAt = 192.8)
        hazards += D.sunLaser(202.0, MID + 2.3, onAt = 198.0)
        hazards += spike(212.0, MID)
        // 67-75%: breathing sand, with a crest riding it.
        hazards += D.waveOnSand(232.0, MID, 2.0, 0.7, sandPhase = 0.5)
        hazards += D.sandSpike(248.0, MID, sandB.motion!!)
        // 78-87%: the low shelf, and the run-up to the second boost.
        hazards += spike(270.0, LOW)
        hazards += D.sandWave(280.0, LOW, 2.2)
        hazards += D.sandGeyser(292.0, LOW, upAt = 292.8)
        // 92-100%: the finish. Still stone, nothing pulsing - three spikes and
        // the timing the whole world has been teaching.
        hazards += spike(316.0, LOW)
        hazards += spike(324.0, LOW)
        hazards += spike(328.18, LOW)
        hazards += spike(332.36, LOW)

        // Coins are hung where the verified line is RUNNING, never where it is
        // already flying. Hung over a boost gap they sat directly on the arc the
        // line takes anyway, which is a free coin dressed as a dare - all four
        // desert levels made that mistake and the gate caught all four.
        val stars = listOf(
            Star(16.0, 2.5),          // one honest jump off still stone
            Star(152.0, 4.4),         // above what a single jump reaches: the coin
                                      // costs a second tap the crossing never wanted
            Star(286.0, 0.8),         // the same price, out on the low shelf
        )

        return Level(
            id = 9, name = "SUN STRIKE", subtitle = "UP OR DOWN. CHOOSE.",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
