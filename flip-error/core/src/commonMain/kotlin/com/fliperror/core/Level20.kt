package com.fliperror.core

/**
 * LEVEL 20 - "IRON TEETH"  (WORLD 4, GEAR CHAMBER)
 *
 * Deeper in, and the gears come in pairs.
 *
 * A gear lock is two of them turning against each other with their teeth half a
 * step apart, so the safe moment is not in one place - it travels between them.
 * The player stops reading a cycle and starts reading a RELATIONSHIP, which is
 * the whole difference between this level and the one before it.
 *
 * The chain arrives here too, and it is the first part of the machine that is
 * wide: a weight on a swing crossing the lane and coming back. Wide things move
 * slowly in this game and this one is no exception - a heavy thing moving fast
 * leaves no manoeuvre, only luck.
 */
object Level20 {

    const val BPM = 206.0
    private val C = Clockwork(BPM)

    private const val GROUND = 0.0
    private const val LOW = -3.2

    val finishX = C.beat(125.0)                  // 345.9 units -> 36.4 seconds

    fun build(): Level {
        val solids = listOf(
            C.deck(-14.0, 52.0, GROUND),
            C.deck(56.2, 98.0, GROUND),        // gap 4.20u
            C.deck(102.2, 146.0, GROUND),      // gap 4.20u
            C.deck(152.6, 194.0, GROUND),      // gap 6.60u - the boost
            C.deck(198.2, 242.0, LOW),         // gap 4.20u
            C.deck(246.2, 290.0, LOW),         // gap 4.20u
            C.deck(296.6, 350.0, LOW),         // gap 6.60u - the second boost
        )

        val hazards = ArrayList<Hazard>()
        // 0-12%: known parts, widest windows.
        hazards += C.gear(13.0, GROUND, radius = 2.8, teeth = 3, bars = 3.0, lowAt = 13.0)
        hazards += C.energyRail(26.0, GROUND, onAt = 26.0)
        hazards += C.piston(38.0, GROUND, downAt = 38.0)
        // 14-19%: the chain, alone on a long deck the first time. It sweeps 2.4u
        // each way over three bars and it is the widest thing in the machine.
        hazards += C.chainSweep(48.0, GROUND, nearAt = 48.0)
        // 20-30%: THE GEAR LOCK. Two gears seven units apart, one at the bottom
        // of its circle as the runner meets it and the other at the top of its,
        // so they are never both in the lane - and the window walks from one to
        // the other while the player is inside it.
        hazards += C.gearLock(66.0, GROUND, apart = 9.0, radius = 2.8, teeth = 3, bars = 3.0)
        hazards += C.piston(88.0, GROUND, downAt = 88.0)
        // 32-42%: gate and chain, on different cycles. Two bars against three is
        // six bars before the pattern comes round again, which is longer than
        // this deck - so it never reads as a loop.
        hazards += C.shutterGate(112.0, GROUND, shutAt = 112.0)
        hazards += C.chainSweep(124.0, GROUND, bars = 4.0, nearAt = 124.0)
        hazards += C.gear(136.0, GROUND, radius = 2.8, teeth = 3, bars = 3.0, lowAt = 136.0)
        // 47-54%: past the boost. Ram, gate, ram - the middle one forbidding the
        // air that the other two demand.
        hazards += C.piston(162.0, GROUND, downAt = 162.0)
        hazards += C.shutterGate(174.0, GROUND, shutAt = 174.0)
        hazards += C.piston(184.0, GROUND, downAt = 184.0)
        // 61-68%: the lower deck, and the second lock.
        hazards += C.gearLock(208.0, LOW, apart = 9.0, radius = 2.8, teeth = 3, bars = 3.0)
        hazards += C.chainSweep(232.0, LOW, nearAt = 232.0)
        // 74-82%: rail, gate, gear into the second boost.
        hazards += C.energyRail(256.0, LOW, onAt = 256.0)
        hazards += C.shutterGate(266.0, LOW, shutAt = 266.0)
        hazards += C.gear(278.0, LOW, radius = 2.8, teeth = 3, bars = 3.0, lowAt = 278.0)
        // 90-97%: a chain over the run-in, then the slot.
        hazards += C.chainSweep(308.0, LOW, bars = 4.0, nearAt = 308.0)
        hazards += C.boltedPart(326.0, LOW)
        hazards += C.boltedPart(330.16, LOW)
        hazards += C.boltedPart(334.32, LOW)

        val stars = listOf(
            Star(58.0, 4.4),
            Star(202.0, LOW + 4.4),
            Star(300.0, LOW + 4.4),
        )

        return Level(
            id = 20, name = "IRON TEETH", subtitle = "TWO CYCLES, ONE WINDOW",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
