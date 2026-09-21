package com.fliperror.core

/**
 * LEVEL 29 - "OVERGROWTH"  (WORLD 5)
 *
 * The skill test. Every living thing the forest knows, in one level, with almost
 * nothing between them.
 *
 * Flower, vine, root, pod, drift, seed, bloom, wall, pulse: nine behaviours, and
 * the level cycles them fast enough that the player is never answering the thing
 * in front of them - they are answering the thing after it. That is the
 * difference between this and LEVEL 27, where the density was one decision a
 * second; here it is one decision a second made from the memory of the last
 * three.
 *
 * Its take-off windows sit on 0.075s, the floor this game has never crossed in
 * five worlds, and they get there in the finish. What makes the middle hard is
 * the reading, and the reading has no ceiling.
 */
object Level29 {

    const val BPM = 224.0
    private val O = Overgrowth(BPM)

    private const val GROUND = 0.0
    private const val MID = -1.7
    private const val LOW = -3.4

    val finishX = O.beat(136.0)                  // 345.9 units -> 36.4 seconds

    fun build(): Level {
        val solids = listOf(
            O.moss(-14.0, 48.0, GROUND),
            O.moss(52.2, 92.0, GROUND),        // gap 4.20u
            O.breathingBough(96.2, 128.0, GROUND, rise = 0.8, bars = 3.0, lowAt = 97.0),
            O.moss(132.2, 172.0, GROUND),      // gap 4.20u
            O.moss(179.0, 220.0, MID),         // gap 7.00u - the boost, and down
            O.moss(224.2, 266.0, MID),         // gap 4.20u
            O.moss(270.2, 300.0, LOW),         // gap 4.20u, and down again
            O.moss(307.0, 360.0, LOW),         // gap 7.00u - the last boost
        )

        val hazards = ArrayList<Hazard>()
        // 0-12%: known words only.
        hazards += O.rootRise(11.0, GROUND, height = 1.25, upAt = 11.0)
        hazards += O.snapFlower(21.0, GROUND, size = 0.9, snapAt = 21.0)
        hazards += O.rootRise(31.0, GROUND, height = 1.25, upAt = 31.0)
        // 12-25%: pod, wall, vine, bloom - four behaviours in forty units, and
        // the wall in the middle of them asking for a height rather than a moment.
        hazards += O.thornPod(42.0, GROUND, burstAt = 42.0)
        hazards += O.rootWall(60.0, GROUND, openBottom = 1.7, openTop = 4.0, grownAt = 60.0)
        hazards += O.vineSweep(72.0, GROUND, nearAt = 72.0)
        hazards += O.carnivorousBloom(84.0, GROUND, openAt = 84.0)
        // 29-37%: onto the breathing bough. Drift, pulse, flower.
        hazards += O.sporeStream(102.0, GROUND, reach = 2.6)
        hazards += O.pulsePlant(114.0, GROUND, pulseAt = 114.0)
        hazards += O.snapFlower(124.0, GROUND, snapAt = 124.0)
        // 40-48%: still moss, and the run-up to the boost.
        hazards += O.fallingSeed(140.0, GROUND, downAt = 140.0)
        hazards += O.seedRoll(150.0, GROUND, reach = 2.0)
        hazards += O.rootRise(162.0, GROUND, upAt = 162.0)
        // 54-62%: the middle floor. Wall, pod, vine.
        hazards += O.rootWall(188.0, MID, openBottom = 1.7, openTop = 4.0, grownAt = 188.0)
        hazards += O.thornPod(200.0, MID, burstAt = 200.0)
        hazards += O.vineSweep(211.0, MID, nearAt = 211.0)
        // 67-75%: pair, drift, pulse.
        hazards += O.flowerPair(234.0, MID, apart = 8.0)
        hazards += O.rootRise(246.0, MID, upAt = 246.0)
        hazards += O.sporeStream(256.0, MID, reach = 2.4)
        // 80-85%: down again. Bloom and a curtain over the last of it.
        hazards += O.carnivorousBloom(278.0, LOW, openAt = 278.0)
        hazards += O.vineCurtain(284.0, 294.0, LOW, tip = 1.4)
        hazards += O.snapFlower(310.0, LOW, snapAt = 310.0)
        // 90-97%: a pulse over the run-in, and the slot.
        hazards += O.pulsePlant(318.0, LOW, pulseAt = 318.0)
        hazards += O.fallenSeed(328.0, LOW)
        hazards += O.fallenSeed(332.14, LOW)
        hazards += O.fallenSeed(336.28, LOW)

        val stars = listOf(
            Star(50.0, 4.4),
            Star(183.0, MID + 4.4),
            Star(311.0, LOW + 4.4),
        )

        return Level(
            id = 29, name = "OVERGROWTH", subtitle = "READ THE ONE AFTER NEXT",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
