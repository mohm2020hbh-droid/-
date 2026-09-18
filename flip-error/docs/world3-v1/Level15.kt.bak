package com.fliperror.core

/**
 * LEVEL 15 - "PRESSURE"  (WORLD 3)
 *
 * Water that shoves. Columns of current run through this level and they are
 * VERTICAL, because sideways is the one direction that cannot exist here - the
 * runner's x is exactly RUN_SPEED * time and every proof this game makes about
 * itself rests on it. What a burst changes is how high a jump goes, which is
 * enough: a crossing learned on still water is the wrong crossing inside one.
 *
 * The abyss orb arrives with it - the largest single thing in the game, riding a
 * circle, because a circle tells you where it goes next from any point on it.
 */
object Level15 {

    const val BPM = 196.0
    private val A = Abyss(BPM)

    private const val GROUND = 0.0
    private const val LOW = -3.2

    private fun spike(x: Double, y: Double) = Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0)

    val finishX = A.beat(119.0)                  // 346.1 units -> 36.4 seconds

    fun build(): Level {
        val solids = listOf(
            Solid(-14.0, 52.0, GROUND),
            Solid(56.2, 100.0, GROUND),        // gap 4.20u
            Solid(104.2, 150.0, GROUND),       // gap 4.20u
            Solid(157.2, 200.0, GROUND),       // gap 7.20u - a boost, in the updraft
            Solid(204.2, 248.0, LOW),          // gap 4.20u
            Solid(252.2, 296.0, LOW),          // gap 4.20u
            Solid(302.6, 356.0, LOW),          // gap 6.60u - the second boost
        )

        // Bursts, not a constant flow: the pattern is normal, burst, gap, burst,
        // and the gap is where the level is actually crossed.
        val winds = listOf(
            A.currentBurst(150.0, 157.2, 17.0),      // lifts the long crossing
            A.currentBurst(224.0, 232.0, -13.0),     // and one that pushes down
            // The third burst sits over open FLOOR, not over the last gap. Over
            // the gap it was changing the height of a 6.60u crossing while it was
            // being made, and a 0.029s take-off is not a current, it is a coin
            // flip: water may change what a jump is worth, never whether a
            // crossing exists.
            A.currentBurst(266.0, 276.0, 15.0),
        )

        val hazards = ArrayList<Hazard>()
        hazards += spike(11.0, GROUND)
        hazards += spike(23.0, GROUND)
        hazards += spike(35.0, GROUND)
        // 13-18%: a bubble on still water, then the first orb.
        hazards += A.chasingBubble(44.0, GROUND)
        hazards += A.abyssOrb(64.0, 2.6)
        // 22-30%: corridor, and a wall coming down after it.
        hazards += A.lightCorridor(78.0, 90.0, 2.1, openAt = 82.0)
        hazards += A.descendingWall(96.0, 2.0, downAt = 96.0)
        // 32-42%: the split, then mines.
        hazards += A.splitBubble(110.0, GROUND, at = 122.0, pieces = 2)
        hazards += A.mine(140.0, 1.9, phase = 0.25)
        // 45-57%: the boost gap sits in the updraft. Nothing else is on the
        // run-up: a crossing whose height is being changed by the water is
        // enough to be reading at once.
        hazards += spike(166.0, GROUND)
        hazards += A.abyssOrb(182.0, 2.8, phase = 0.5)
        // 60-72%: the low shelf, with the downdraft over the middle of it.
        hazards += A.chasingBubble(212.0, LOW)
        // LEVEL 13's wall came down and had to be run under. This one comes UP out
        // of the floor and has to be jumped, on its own cycle - the same object
        // asking the opposite question, which is the only reason it can arrive
        // this late without being a new word.
        hazards += A.risingWall(238.0, LOW, upAt = 238.0)
        // 75-86%: mines and a corridor.
        hazards += A.mine(258.0, LOW + 1.8)
        hazards += A.mine(266.0, LOW + 2.3, phase = 0.5)
        hazards += A.lightCorridor(278.0, 290.0, LOW + 2.2, openAt = 282.0)
        // 90-100%: the finish.
        hazards += spike(316.0, LOW)
        hazards += spike(326.0, LOW)
        hazards += spike(330.16, LOW)
        hazards += spike(334.32, LOW)

        val stars = listOf(
            Star(17.0, 2.5),
            Star(94.0, 4.4),
            Star(292.0, LOW + 4.4),
        )

        return Level(
            id = 15, name = "PRESSURE", subtitle = "THE WATER PUSHES BACK",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars,
            finishX = finishX, winds = winds,
        )
    }
}
