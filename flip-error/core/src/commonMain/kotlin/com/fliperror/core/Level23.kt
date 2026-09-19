package com.fliperror.core

/**
 * LEVEL 23 - "THE FACTORY"  (WORLD 4, MAIN FACTORY)
 *
 * The floor of the machine where everything it knows how to do is being done at
 * once, and the level is an endurance test rather than a puzzle.
 *
 * It runs in six phases and each one is a pair: gear and ram, gate and vent,
 * gear and chain, belt and rail, press and press, and then all of it. Nothing
 * here is new - that is the point of a factory floor. What is new is that the
 * player never gets a stretch to think on, because the next pair has started
 * before the last one finished.
 */
object Level23 {

    const val BPM = 212.0
    private val C = Clockwork(BPM)

    private const val GROUND = 0.0
    private const val LOW = -3.2

    val finishX = C.beat(128.0)                  // 344.2 units -> 36.2 seconds

    fun build(): Level {
        val solids = listOf(
            C.deck(-14.0, 50.0, GROUND),
            C.deck(54.2, 96.0, GROUND),        // gap 4.20u
            C.deck(100.2, 142.0, GROUND),      // gap 4.20u
            C.deck(148.6, 190.0, GROUND),      // gap 6.60u - the boost
            // PHASE 4's belt, and the still deck that overlaps its travel so the
            // join is never a hole.
            C.conveyorFloor(194.2, 226.0, LOW, reach = 1.6, bars = 2.5, landAt = 194.2),
            C.deck(224.4, 236.0, LOW),         // gap 4.20u down onto the belt
            C.deck(240.2, 282.0, LOW),         // gap 4.20u
            C.deck(288.6, 346.0, LOW),         // gap 6.60u - the second boost
        )

        val hazards = ArrayList<Hazard>()
        // PHASE 1, 0-13%: gear and ram. Known parts, widest windows, as every
        // level in this game opens.
        hazards += C.gear(12.0, GROUND, lowAt = 12.0)
        hazards += C.piston(24.0, GROUND, downAt = 24.0)
        hazards += C.steamBurst(36.0, GROUND, upAt = 36.0)
        // PHASE 2, 18-25%: gate and vent - the two that argue. One says stay
        // down and the other says get off the deck, and they are eight units
        // apart, which is less than a second.
        hazards += C.shutterGate(62.0, GROUND, shutAt = 62.0)
        hazards += C.steamBurst(74.0, GROUND, upAt = 74.0)
        hazards += C.shutterGate(86.0, GROUND, shutAt = 86.0)
        // PHASE 3, 32-38%: gear and chain.
        hazards += C.gear(110.0, GROUND, lowAt = 110.0)
        hazards += C.chainSweep(122.0, GROUND, nearAt = 122.0)
        hazards += C.gear(132.0, GROUND, lowAt = 132.0)
        // PHASE 4, 46-52%: belt and rail.
        hazards += C.energyRail(158.0, GROUND, onAt = 158.0)
        hazards += C.piston(170.0, GROUND, downAt = 170.0)
        // 184 and not 178: the edge at 190 is a STEP down onto the belt rather
        // than a gap, so nothing there asks for a jump, and at 178 this rail left
        // twenty-six units of deck with nothing on it in the middle of the
        // factory floor.
        hazards += C.energyRail(184.0, GROUND, onAt = 184.0)
        // PHASE 5, 59-66%: the press, twice, over the belt, with a vent between
        // them. The press takes the floor and the ceiling in turn and never both
        // at once - the two plates are half a cycle apart and the verifier
        // checks the gap on every build.
        hazards += C.crushingWall(204.0, LOW, floorUpAt = 204.0)
        hazards += C.steamBurst(216.0, LOW, upAt = 216.0)
        hazards += C.crushingWall(228.0, LOW, floorUpAt = 228.0)
        // PHASE 6, 73-82%: all of it. Gear, gate, chain.
        hazards += C.gear(250.0, LOW, lowAt = 250.0)
        hazards += C.shutterGate(262.0, LOW, shutAt = 262.0)
        hazards += C.chainSweep(272.0, LOW, nearAt = 272.0)
        // 87-97%: ram, gear, and the slot.
        hazards += C.piston(300.0, LOW, downAt = 300.0)
        hazards += C.gear(312.0, LOW, lowAt = 312.0)
        hazards += C.boltedPart(326.0, LOW)
        hazards += C.boltedPart(330.14, LOW)
        hazards += C.boltedPart(334.28, LOW)

        val stars = listOf(
            Star(44.0, 4.4),
            Star(244.0, LOW + 4.4),
            Star(292.0, LOW + 4.4),
        )

        return Level(
            id = 23, name = "THE FACTORY", subtitle = "EVERY MACHINE AT ONCE",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
