package com.fliperror.core

/**
 * LEVEL 30 - "HEART OF THE FOREST"  (WORLD 5, FINALE)
 *
 * The middle of a living world, and the last level in FLIP ERROR.
 *
 * Nothing is introduced here. A finale that teaches is a finale that stalls, so
 * every behaviour in it is one the player has already met and what is new is how
 * little room there is between them. It runs in six phases, each one denser than
 * the last, and from 80% there is no quiet moss at all.
 *
 * And then, at 94%, it stops.
 *
 * The forest goes still for the last twenty units - no flower, no vine, no root,
 * nothing on a cycle - and the player runs the final second and a half with the
 * Heart ahead of them and nothing in the way. That is deliberate, and it is the
 * only level in the game built this way: the hardest sequence in five worlds,
 * and then the quiet. A game that ends on its tightest input ends on a reflex;
 * this one ends on arriving somewhere.
 */
object Level30 {

    const val BPM = 226.0
    private val O = Overgrowth(BPM)

    private const val GROUND = 0.0
    private const val MID = -1.8
    private const val LOW = -3.6

    val finishX = O.beat(137.0)                  // 345.6 units -> 36.4 seconds

    fun build(): Level {
        val solids = listOf(
            O.moss(-14.0, 46.0, GROUND),
            O.moss(50.2, 88.0, GROUND),        // gap 4.20u
            O.breathingBough(92.2, 122.0, GROUND, rise = 0.8, bars = 3.0, lowAt = 93.0),
            O.moss(126.2, 164.0, GROUND),      // gap 4.20u
            O.moss(171.0, 212.0, MID),         // gap 7.00u - the boost, and down
            O.moss(216.2, 254.0, MID),         // gap 4.20u
            O.moss(261.0, 292.0, LOW),         // gap 7.00u - the second, and down
            O.moss(296.2, 360.0, LOW),         // gap 4.20u into the last of it
        )

        val hazards = ArrayList<Hazard>()
        // PHASE 1, 0-12%: simple biological patterns. Even a finale gives the
        // player an eighth of a level to look at it.
        hazards += O.rootRise(11.0, GROUND, height = 1.25, upAt = 11.0)
        hazards += O.snapFlower(21.0, GROUND, size = 0.9, snapAt = 21.0)
        hazards += O.rootRise(31.0, GROUND, height = 1.25, upAt = 31.0)
        // PHASE 2, 15-24%: multiple flowers, out of step with each other.
        hazards += O.flowerPair(54.0, GROUND, apart = 8.0)
        hazards += O.rootRise(68.0, GROUND, upAt = 68.0)
        hazards += O.thornPod(78.0, GROUND, burstAt = 78.0)
        // PHASE 3, 28-36%: vine and curtain, over the breathing bough. The vine
        // is at 100 and not 98 because the bough's top travels 0.8u: landing on
        // it and taking off again inside three units, from a floor that is
        // itself moving, measured 0.075s at 27% of the level - the tightest
        // input in the game, in the wrong place entirely.
        hazards += O.vineSweep(100.0, GROUND, nearAt = 100.0)
        hazards += O.vineCurtain(104.0, 113.0, GROUND, tip = 1.4)
        // 38-45%: still moss. Root, then the wall.
        hazards += O.snapFlower(116.0, GROUND, snapAt = 116.0)
        hazards += O.rootRise(132.0, GROUND, upAt = 132.0)
        hazards += O.rootWall(144.0, GROUND, openBottom = 1.7, openTop = 3.9, grownAt = 144.0)
        // PHASE 4, 45-51%: the drift, into the boost.
        hazards += O.sporeStream(154.0, GROUND, reach = 2.6)
        // PHASE 5, 51-60%: high-density chains on the middle floor.
        hazards += O.fallingSeed(176.0, MID, downAt = 176.0)
        hazards += O.carnivorousBloom(186.0, MID, openAt = 186.0)
        hazards += O.pulsePlant(196.0, MID, pulseAt = 196.0)
        hazards += O.snapFlower(202.0, MID, snapAt = 202.0)
        // 65-72%: seed, wall, pod.
        hazards += O.seedRoll(226.0, MID, reach = 2.0)
        hazards += O.rootWall(238.0, MID, openBottom = 1.7, openTop = 3.9, grownAt = 238.0)
        hazards += O.thornPod(246.0, MID, burstAt = 246.0)
        // PHASE 6, 77-96%: FULL OVERGROWTH. No quiet moss from here until the
        // forest lets go.
        hazards += O.vineSweep(268.0, LOW, nearAt = 268.0)
        hazards += O.pulsePlant(276.0, LOW, pulseAt = 276.0)
        hazards += O.sporeStream(282.0, LOW, reach = 2.2)
        // A short curtain over the run-up to the last drop. The browser harness
        // caught this stretch: from 284 to 290 there was nothing in the next
        // fourteen units, which in a phase whose whole claim is that it never
        // lets go is a hole. It hangs rather than blocks - the plan takes no
        // jump between 279 and the gap at 292, so the curtain asks the player
        // to KEEP RUNNING through the one place they might have relaxed, and
        // stops short of 290 so the take-off for the drop is clean air. The
        // sway is halved because a full-sway sweeper spends part of its swing
        // BEHIND the runner, and the harness found the half-unit of nothing
        // that opened up while it was back there; the root moved a unit closer
        // for the same reason, so the two overlap with room to spare.
        hazards += O.vineCurtain(286.0, 289.0, LOW, tip = 1.35, spacing = 1.6, sway = 0.5)
        hazards += O.rootRise(301.0, LOW, upAt = 301.0)
        hazards += O.carnivorousBloom(312.0, LOW, openAt = 312.0)
        hazards += O.fallenSeed(322.0, LOW)
        hazards += O.fallenSeed(326.14, LOW)
        hazards += O.fallenSeed(330.28, LOW)
        // 96-100%: THE HEART. Nothing. The forest is still, and the player runs
        // the last fifteen units of the game with a clear path in front of them.

        val stars = listOf(
            Star(48.0, 4.4),
            Star(175.0, MID + 4.4),
            Star(265.0, LOW + 4.4),
        )

        return Level(
            id = 30, name = "HEART OF THE FOREST", subtitle = "AND THEN IT IS QUIET",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
