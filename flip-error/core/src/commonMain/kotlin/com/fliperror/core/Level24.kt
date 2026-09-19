package com.fliperror.core

/**
 * LEVEL 24 - "MACHINE CORE"  (WORLD 4, FINALE)
 *
 * The middle of the machine, and the hardest thing in FLIP ERROR.
 *
 * There is no boss down here because the machine IS the boss, and a finale that
 * introduces a new part is a finale that stalls - so nothing in this level is
 * new. What is new is the density, and the middle of it: THREE wide crossings
 * in ninety units, each one past what a single jump reaches, with the machine
 * still running between them. That is the double jump gauntlet.
 *
 * A live rail under a swinging chain was the first version of that section - a
 * rail to jump and a chain overhead that only a second tap clears. It measured
 * 0.025s, because the corridor between a hazard at 1.1 and one at 1.6 is
 * narrower than the runner, so the only line was over the top and the boost had
 * to be perfect. A gap does the same job honestly: it is wide, it is visible
 * from a long way off, and it has been asking for the second tap since LEVEL 2.
 *
 * From 84% there is no quiet deck left: ram, rail, gate, vent, and then the slot.
 */
object Level24 {

    const val BPM = 214.0
    private val C = Clockwork(BPM)

    private const val GROUND = 0.0
    private const val MID = -1.8
    private const val LOW = -3.6

    val finishX = C.beat(130.0)                  // 346.3 units -> 36.5 seconds

    fun build(): Level {
        val solids = listOf(
            C.deck(-14.0, 48.0, GROUND),
            C.deck(52.2, 92.0, GROUND),        // gap 4.20u
            C.conveyorFloor(96.2, 126.0, GROUND, reach = 1.6, bars = 2.5, landAt = 96.2),
            C.deck(124.4, 136.0, GROUND),      // gap 4.20u onto the belt
            // 7.00u, not 6.60u, and the same again for the two after it. A plain
            // jump covers 4.94u on the flat and 5.79u when it drops 1.8 on the
            // way, so a 6.60u crossing lands in the worst place there is: just
            // past what one tap reaches, which means the second tap has to be
            // near perfect. The verifier measured exactly that - 0.008s - on the
            // first draft of this section. Past 7.00 the boost is the only
            // answer and the timing relaxes right back out.
            C.deck(143.0, 184.0, MID),         // gap 7.00u - the first boost, and down
            C.deck(191.0, 216.0, MID),         // gap 7.00u - the second
            C.deck(223.0, 248.0, MID),         // gap 7.00u - the third
            C.deck(255.4, 278.0, LOW),         // gap 7.40u - the fourth, and down
            C.deck(282.2, 352.0, LOW),         // gap 4.20u into the gauntlet
        )

        val hazards = ArrayList<Hazard>()
        // PHASE 1, 0-13%: gear and ram. Even a finale gives the player an eighth
        // of a level to look at it.
        hazards += C.gear(12.0, GROUND, lowAt = 12.0)
        hazards += C.piston(23.0, GROUND, downAt = 23.0)
        hazards += C.gear(34.0, GROUND, lowAt = 34.0)
        // PHASE 2, 17-26%: gate and vent.
        hazards += C.shutterGate(58.0, GROUND, shutAt = 58.0)
        hazards += C.steamBurst(68.0, GROUND, upAt = 68.0)
        hazards += C.shutterGate(80.0, GROUND, shutAt = 80.0)
        // PHASE 3, 30-37%: gear and chain, over the belt.
        hazards += C.gear(104.0, GROUND, lowAt = 104.0)
        hazards += C.chainSweep(116.0, GROUND, nearAt = 116.0)
        hazards += C.piston(128.0, GROUND, downAt = 128.0)
        // PHASE 4, 44-50%: rail and ram on the middle deck.
        hazards += C.energyRail(152.0, MID, onAt = 152.0)
        hazards += C.piston(162.0, MID, downAt = 162.0)
        hazards += C.energyRail(174.0, MID, onAt = 174.0)
        // PHASE 5, 57-71%: THE DOUBLE JUMP GAUNTLET. Two 6.60u crossings and a
        // 6.60u drop, thirty units apart, with the machine still running on the
        // decks between them. A chain hangs over the first deck at head height:
        // it cannot touch a runner who stays down and it takes the whole deck
        // away from one who does not.
        hazards += C.piston(200.0, MID, downAt = 200.0)
        hazards += C.chainSweepHigh(206.0, MID, nearAt = 206.0)
        hazards += C.energyRail(231.0, MID, onAt = 231.0)
        hazards += C.gear(239.0, MID, lowAt = 239.0)
        // PHASE 6, 76-80%: into the core. Press and vent.
        hazards += C.crushingWall(262.0, LOW, floorUpAt = 262.0)
        hazards += C.steamBurst(270.0, LOW, upAt = 270.0)
        // 84-100%: THE FINAL GAUNTLET. No quiet deck from here to the line.
        hazards += C.piston(294.0, LOW, downAt = 294.0)
        hazards += C.energyRail(304.0, LOW, onAt = 304.0)
        hazards += C.shutterGate(312.0, LOW, shutAt = 312.0)
        hazards += C.steamBurst(322.0, LOW, upAt = 322.0)
        hazards += C.boltedPart(332.0, LOW)
        hazards += C.boltedPart(336.14, LOW)
        hazards += C.boltedPart(340.28, LOW)

        val stars = listOf(
            Star(42.0, 4.4),
            Star(195.0, MID + 4.4),
            Star(288.0, LOW + 4.4),
        )

        return Level(
            id = 24, name = "MACHINE CORE", subtitle = "THE MACHINE IS THE BOSS",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
