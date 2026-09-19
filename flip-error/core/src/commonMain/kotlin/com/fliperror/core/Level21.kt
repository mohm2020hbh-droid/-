package com.fliperror.core

/**
 * LEVEL 21 - "PRESSURE"  (WORLD 4, PRESSURE WORKS)
 *
 * The part of the machine that does work, and the level where the recovery
 * stops.
 *
 * Vents fire out of the deck, rams come down in banks, and the press - two
 * plates, one from the floor and one from the ceiling, half a cycle apart -
 * takes away the ground and the air in turn. There is never an instant when it
 * has taken both: that is what the half cycle is for, and the verifier checks it
 * rather than trusting the arithmetic in this comment.
 *
 * The belt runs through the middle of it. It is an honest belt: the deck plates
 * travel and the runner does not travel with them, because the runner's x is
 * exactly RUN_SPEED * time and every proof this game makes about itself rests on
 * that. What a belt changes here is where the ground is when you come down.
 */
object Level21 {

    const val BPM = 208.0
    private val C = Clockwork(BPM)

    private const val GROUND = 0.0
    private const val LOW = -3.2

    val finishX = C.beat(126.0)                  // 345.3 units -> 36.3 seconds

    fun build(): Level {
        val solids = listOf(
            C.deck(-14.0, 52.0, GROUND),
            C.deck(56.2, 96.0, GROUND),                    // gap 4.20u
            // The belt, with its phase solved so it is at its NEAREST as the
            // runner lands on it - a 4.20u crossing stays a 4.20u crossing - and
            // a still deck starting a full reach inside its travel, so the join
            // between them is never a hole.
            C.conveyorFloor(100.2, 132.0, GROUND, reach = 1.8, bars = 3.0, landAt = 100.2),
            C.deck(130.2, 142.0, GROUND),
            C.deck(148.6, 190.0, GROUND),                  // gap 6.60u - the boost
            C.deck(194.2, 238.0, LOW),                     // gap 4.20u
            C.conveyorFloor(242.2, 274.0, LOW, reach = 1.6, bars = 2.5, landAt = 242.2),
            C.deck(272.4, 284.0, LOW),
            C.deck(290.6, 348.0, LOW),                     // gap 6.60u - the second boost
        )

        val hazards = ArrayList<Hazard>()
        // 0-12%: rail, gear, ram. Known parts, widest windows.
        hazards += C.energyRail(12.0, GROUND, onAt = 12.0)
        hazards += C.gear(24.0, GROUND, lowAt = 24.0)
        hazards += C.piston(36.0, GROUND, downAt = 36.0)
        // 14-19%: the vent. It charges where it can be seen for a quarter of a
        // second before it is lethal, which is the only reason a thing that
        // appears out of the floor is allowed to exist in this game.
        hazards += C.steamBurst(46.0, GROUND, upAt = 46.0)
        // 19-27%: a bank of three rams, each on a slightly longer cycle than the
        // last so they are visibly out of step - and each of them at the bottom
        // of its stroke as the runner reaches it, which is what makes a bank
        // three jumps instead of one.
        hazards += C.pistonBank(64.0, GROUND, count = 3, spacing = 8.0)
        // 30-40%: onto the belt. Vent, press, vent - the press is the new word
        // and it gets a vent either side of it rather than a companion.
        hazards += C.steamBurst(106.0, GROUND, upAt = 106.0)
        hazards += C.crushingWall(118.0, GROUND, floorUpAt = 118.0)
        hazards += C.steamBurst(132.0, GROUND, upAt = 132.0)
        // 46-53%: past the boost. Rail, ram, press.
        hazards += C.energyRail(158.0, GROUND, onAt = 158.0)
        hazards += C.piston(168.0, GROUND, downAt = 168.0)
        hazards += C.crushingWall(180.0, GROUND, floorUpAt = 180.0)
        // 59-67%: the lower deck. Vent, gear, ram.
        hazards += C.steamBurst(204.0, LOW, upAt = 204.0)
        hazards += C.gear(216.0, LOW, lowAt = 216.0)
        hazards += C.piston(228.0, LOW, downAt = 228.0)
        // 73-80%: the second belt. Press, rail, vent.
        hazards += C.crushingWall(252.0, LOW, floorUpAt = 252.0)
        hazards += C.energyRail(264.0, LOW, onAt = 264.0)
        hazards += C.steamBurst(276.0, LOW, upAt = 276.0)
        // 88-97%: a ram over the run-in, and the slot.
        hazards += C.piston(302.0, LOW, downAt = 302.0)
        hazards += C.boltedPart(314.0, LOW)
        hazards += C.boltedPart(326.0, LOW)
        hazards += C.boltedPart(330.16, LOW)
        hazards += C.boltedPart(334.32, LOW)

        val stars = listOf(
            Star(54.0, 4.4),
            Star(198.0, LOW + 4.4),
            Star(294.0, LOW + 4.4),
        )

        return Level(
            id = 21, name = "PRESSURE", subtitle = "THE WORKS DO NOT WAIT",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
