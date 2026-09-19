package com.fliperror.core

/**
 * LEVEL 19 - "MACHINE START"  (WORLD 4, CLOCKWORK)
 *
 * The outer mechanism, and the machine's first four words: the gear, the ram,
 * the rail and the gate.
 *
 * It teaches, but it does not let up while it teaches. Every part here is
 * introduced alone and then immediately put next to another one, because the
 * thing this world is actually about is not any single mechanism - it is reading
 * two cycles at once. The last third asks for a gate and a ram in the same
 * breath, which is a sentence rather than a word, and the player should finish
 * this level understanding that the machine is going to keep talking.
 */
object Level19 {

    const val BPM = 204.0
    private val C = Clockwork(BPM)

    private const val GROUND = 0.0
    private const val LOW = -3.2

    val finishX = C.beat(124.0)                  // 346.5 units -> 36.5 seconds

    fun build(): Level {
        val solids = listOf(
            C.deck(-14.0, 54.0, GROUND),
            C.deck(58.2, 100.0, GROUND),       // gap 4.20u
            C.deck(104.2, 148.0, GROUND),      // gap 4.20u
            C.deck(154.6, 196.0, GROUND),      // gap 6.60u - the boost
            C.deck(200.2, 244.0, LOW),         // gap 4.20u, and down into the works
            C.deck(248.2, 292.0, LOW),         // gap 4.20u
            C.deck(298.6, 352.0, LOW),         // gap 6.60u - the second boost
        )

        val hazards = ArrayList<Hazard>()
        // SECTION A, 0-12%: the gear on its own, then a rail. A tooth at the
        // bottom of its circle is 0.225s and a rail is 0.219s - the two widest
        // windows in the kit, which is what an opening is for.
        hazards += C.gear(14.0, GROUND, radius = 2.8, teeth = 3, bars = 3.0, lowAt = 14.0)
        hazards += C.energyRail(28.0, GROUND, onAt = 28.0)
        // SECTION B, 12-27%: the ram, three times, so its stroke can be learned
        // before anything is asked of it. It is the first part that is dangerous
        // BOTH ways - in the lane at the bottom of the stroke, over the runner's
        // head at the top, where a second tap would find it.
        hazards += C.piston(42.0, GROUND, downAt = 42.0)
        hazards += C.piston(68.0, GROUND, downAt = 68.0)
        // SECTION C, 23-27%: gear and ram together for the first time.
        hazards += C.gear(80.0, GROUND, radius = 2.8, teeth = 3, bars = 3.0, lowAt = 80.0)
        hazards += C.piston(92.0, GROUND, downAt = 92.0)
        // SECTION D, 33-39%: the gate. It comes down to 1.25u and stops, so it
        // can always be run under and never jumped under - the machine's way of
        // saying DO NOT PRESS, which no other world in this game says with a
        // moving part.
        hazards += C.energyRail(108.0, GROUND, onAt = 108.0)
        hazards += C.shutterGate(118.0, GROUND, shutAt = 118.0)
        hazards += C.gear(128.0, GROUND, radius = 2.8, teeth = 3, bars = 3.0, lowAt = 128.0)
        hazards += C.energyRail(136.0, GROUND, onAt = 136.0)
        // SECTION E, 48-54%: past the boost. Ram, gate, gear - jump, stay down,
        // jump - and that is the first real sentence in the language.
        hazards += C.piston(166.0, GROUND, downAt = 166.0)
        hazards += C.shutterGate(178.0, GROUND, shutAt = 178.0)
        hazards += C.gear(186.0, GROUND, radius = 2.8, teeth = 3, bars = 3.0, lowAt = 186.0)
        // 61-67%: the lower deck.
        hazards += C.energyRail(212.0, LOW, onAt = 212.0)
        hazards += C.piston(224.0, LOW, downAt = 224.0)
        hazards += C.gear(234.0, LOW, radius = 2.8, teeth = 3, bars = 3.0, lowAt = 234.0)
        // 74-81%: gate, ram, rail into the second boost.
        hazards += C.shutterGate(258.0, LOW, shutAt = 258.0)
        hazards += C.piston(270.0, LOW, downAt = 270.0)
        hazards += C.energyRail(280.0, LOW, onAt = 280.0)
        // SECTION F, 89-97%: a last gear, and the slot - three parts bolted down
        // a jump apart, where nothing moves and the answer is pure reflex.
        hazards += C.gear(310.0, LOW, radius = 2.8, teeth = 3, bars = 3.0, lowAt = 310.0)
        hazards += C.boltedPart(318.0, LOW)
        hazards += C.boltedPart(328.0, LOW)
        hazards += C.boltedPart(332.18, LOW)
        hazards += C.boltedPart(336.36, LOW)

        // Coins sit at 4.4 - over a plain jump's reach, so each one costs a
        // second tap - and they keep clear of the rams and gears, whose tops
        // reach 4.4 and 4.8 on their own.
        val stars = listOf(
            Star(52.0, 4.4),
            Star(204.0, LOW + 4.4),
            Star(302.0, LOW + 4.4),
        )

        return Level(
            id = 19, name = "MACHINE START", subtitle = "THE MACHINE NEVER STOPS",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
