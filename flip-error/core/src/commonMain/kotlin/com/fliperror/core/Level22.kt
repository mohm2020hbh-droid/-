package com.fliperror.core

/**
 * LEVEL 22 - "ROTATION"  (WORLD 4, ROTATION CORE)
 *
 * Everything in this room goes round.
 *
 * The drum is the gear's long cousin - fewer arms, more reach, and a real gap
 * between them rather than a slot. The deck itself turns: plates on arms, and
 * the rule the engine has always kept is what makes them safe to stand on, which
 * is that a plate carries the runner UP and DOWN but never sideways. Sideways
 * would make the runner's x depend on their own history, and the solver would
 * stop being able to say whether any of this is possible.
 *
 * Two gear locks sit in here as well, because a room about rotation should ask
 * the player to read two rotations against each other at least twice.
 */
object Level22 {

    const val BPM = 210.0
    private val C = Clockwork(BPM)

    private const val GROUND = 0.0
    private const val MID = -1.6
    private const val LOW = -3.4

    val finishX = C.beat(127.0)                  // 344.7 units -> 36.3 seconds

    fun build(): Level {
        val solids = listOf(
            C.deck(-14.0, 50.0, GROUND),
            C.deck(54.2, 94.0, GROUND),                              // gap 4.20u
            // A plate on a shaft, rising and falling under the runner's feet. It
            // is at the bottom of its travel exactly when they land on it.
            C.liftPlate(98.2, 116.0, GROUND, rise = 1.1, bars = 2.0, lowAt = 99.0),
            C.deck(120.2, 158.0, GROUND),                            // gap 4.20u
            C.deck(164.6, 206.0, MID),                               // gap 6.60u - the boost
            C.deck(210.2, 252.0, MID),                               // gap 4.20u
            C.liftPlate(256.2, 274.0, LOW, rise = 1.2, bars = 2.0, lowAt = 257.0),
            C.deck(278.2, 316.0, LOW),                               // gap 4.20u
            C.deck(322.6, 376.0, LOW),                               // gap 6.60u - the last boost
        )

        val hazards = ArrayList<Hazard>()
        // 0-12%: known parts first, as always.
        hazards += C.energyRail(12.0, GROUND, onAt = 12.0)
        hazards += C.gear(24.0, GROUND, lowAt = 24.0)
        hazards += C.piston(36.0, GROUND, downAt = 36.0)
        // 14-18%: THE DRUM. Three arms on a circle of three units, so the gap
        // between them is a gap and not a slot, and it turns over four bars -
        // slower than anything else in the machine, because it is the biggest.
        hazards += C.rotatingCylinder(46.0, GROUND, radius = 3.0, arms = 3,
            bars = 4.0, lowAt = 46.0)
        // 19-26%: chain and gate across the deck.
        hazards += C.chainSweep(66.0, GROUND, nearAt = 66.0)
        hazards += C.shutterGate(78.0, GROUND, shutAt = 78.0)
        hazards += C.piston(88.0, GROUND, downAt = 88.0)
        // 30-40%: over the rising plate, then the lock.
        hazards += C.energyRail(108.0, GROUND, onAt = 108.0)
        hazards += C.gearLock(128.0, GROUND, apart = 9.0, bars = 3.0)
        hazards += C.chainSweep(150.0, GROUND, bars = 4.0, nearAt = 150.0)
        // 51-58%: past the boost, on the middle deck. Drum, ram, gate.
        hazards += C.rotatingCylinder(176.0, MID, radius = 3.0, arms = 3,
            bars = 4.0, lowAt = 176.0)
        hazards += C.piston(192.0, MID, downAt = 192.0)
        hazards += C.shutterGate(200.0, MID, shutAt = 200.0)
        // 62-71%: the second lock, and a chain after it.
        hazards += C.gearLock(220.0, MID, apart = 9.0, bars = 3.0)
        hazards += C.chainSweep(244.0, MID, nearAt = 244.0)
        // 78-88%: the lower plate and the deck past it.
        hazards += C.energyRail(288.0, LOW, onAt = 288.0)
        hazards += C.piston(300.0, LOW, downAt = 300.0)
        hazards += C.gear(310.0, LOW, lowAt = 310.0)
        // 94-98%: the slot.
        hazards += C.boltedPart(330.0, LOW)
        hazards += C.boltedPart(334.14, LOW)
        hazards += C.boltedPart(338.28, LOW)

        val stars = listOf(
            Star(58.0, 4.4),
            Star(168.0, MID + 4.4),
            Star(326.0, LOW + 4.4),
        )

        return Level(
            id = 22, name = "ROTATION", subtitle = "READ THE TURN",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
