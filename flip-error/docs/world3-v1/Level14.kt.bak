package com.fliperror.core

/**
 * LEVEL 14 - "SPLIT"  (WORLD 3)
 *
 * The bubble stops being one thing. A large one comes at the runner, and at a
 * moment fixed by where it is, it stops being there and three small ones start
 * being there - on their own paths, at their own heights.
 *
 * That is a real split rather than a trick of the art: the parent's blink goes
 * off exactly where the children's come on, all four derived from the same x, so
 * what the player sees and what the collision does are one event. It also means
 * the answer changes shape mid-obstacle, which is the thing this level is for -
 * the jump that clears the parent is not the jump that clears what it leaves.
 *
 * The corridor arrives here too: a ceiling that closes and opens along a run, so
 * the lesson of LEVEL 13 gets its first real test under pressure.
 */
object Level14 {

    const val BPM = 194.0
    private val A = Abyss(BPM)

    private const val GROUND = 0.0
    private const val LOW = -3.2

    private fun spike(x: Double, y: Double) = Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0)

    val finishX = A.beat(118.0)                  // 346.9 units -> 36.5 seconds

    fun build(): Level {
        val solids = listOf(
            Solid(-14.0, 54.0, GROUND),        // the opening
            Solid(58.2, 104.0, GROUND),        // gap 4.20u
            Solid(108.2, 152.0, GROUND),       // gap 4.20u
            Solid(158.8, 202.0, GROUND),       // gap 6.60u - the boost
            Solid(206.2, 250.0, LOW),          // gap 4.20u, and down
            // A floor made of bubbles. These ones hold, and it is here so that
            // LEVEL 16's bridge, which does not, is a change to something the
            // player has already stood on rather than a new object.
            A.bubbleFloor(254.2, 298.0, LOW),  // gap 4.20u
            Solid(304.6, 356.0, LOW),          // gap 6.60u - the second boost
        )

        val hazards = ArrayList<Hazard>()
        // 0-12%: known shapes only.
        hazards += spike(12.0, GROUND)
        hazards += spike(24.0, GROUND)
        hazards += spike(36.0, GROUND)
        // 13-18%: one plain bubble, so the split has something to be different from.
        hazards += A.chasingBubble(46.0, GROUND)
        // 20-30%: the first split, alone on a long floor.
        hazards += A.splitBubble(66.0, GROUND, at = 78.0)
        // 32-42%: a corridor - the ceiling closes, and the floor is clear.
        hazards += A.lightCorridor(112.0, 126.0, 2.1, openAt = 116.0)
        hazards += spike(140.0, GROUND)
        // 45-56%: the second split, then the run-up to the boost gap.
        hazards += A.splitBubble(164.0, GROUND, at = 176.0)
        hazards += spike(192.0, GROUND)
        // 60-70%: the low shelf. Mines to thread under a descending wall.
        hazards += A.mine(214.0, LOW + 1.8)
        hazards += A.mine(222.0, LOW + 2.2, phase = 0.5)
        hazards += A.descendingWall(234.0, LOW + 2.1, downAt = 234.0)
        // 73-84%: bubble, then a corridor with a spike inside it.
        hazards += A.chasingBubble(262.0, LOW)
        hazards += A.lightCorridor(276.0, 288.0, LOW + 2.2, openAt = 280.0)
        // 90-100%: the finish, on still floor.
        hazards += spike(318.0, LOW)
        hazards += spike(328.0, LOW)
        hazards += spike(332.18, LOW)
        hazards += spike(336.36, LOW)

        val stars = listOf(
            Star(18.0, 2.5),
            Star(98.0, 4.4),
            Star(294.0, LOW + 4.4),
        )

        return Level(
            id = 14, name = "SPLIT", subtitle = "IT DOES NOT STAY ONE THING",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
