package com.fliperror.core

/**
 * LEVEL 16 - "THE ARMS"  (WORLD 3)
 *
 * Where the abyss stops introducing things and starts combining them. Tentacles
 * sweep in from the edge of the world on a long cycle, and they arrive on top of
 * everything the world already has: a bubble to clear, a ceiling to stay under,
 * a floor made of bubbles that burst.
 *
 * The rule the combinations obey is that no two things ever ask for the same
 * input at the same instant. A tentacle you must jump and a wall you must not
 * jump under are a contradiction, not a difficulty, and the solver would call it
 * unsolvable - correctly. What this level does instead is put them in SEQUENCE,
 * close enough that the reading has to be done early.
 */
object Level16 {

    const val BPM = 198.0
    private val A = Abyss(BPM)

    private const val GROUND = 0.0
    private const val LOW = -3.2

    private fun spike(x: Double, y: Double) = Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0)

    val finishX = A.beat(120.0)                  // 345.5 units -> 36.4 seconds

    fun build(): Level {
        val solids = listOf(
            Solid(-14.0, 52.0, GROUND),
            Solid(56.2, 98.0, GROUND),         // gap 4.20u
            // a bridge of bubbles, each already fading when it is landed on
            A.burstingBubble(102.2, 116.0, GROUND, arriveAt = 102.5),
            Solid(120.2, 162.0, GROUND),       // gap 4.20u
            Solid(168.8, 212.0, GROUND),       // gap 6.60u - the boost
            Solid(216.2, 258.0, LOW),          // gap 4.20u
            A.burstingBubble(262.2, 276.0, LOW, arriveAt = 262.5),
            Solid(280.2, 322.0, LOW),          // gap 4.20u
            Solid(326.2, 372.0, LOW),          // gap 4.20u into the finish
        )

        val hazards = ArrayList<Hazard>()
        hazards += spike(11.0, GROUND)
        hazards += spike(23.0, GROUND)
        hazards += spike(35.0, GROUND)
        // 13-20%: the first arm, alone, so its cycle can be watched once.
        hazards += A.tentacle(45.0, GROUND)
        // An arm sweeps seven units and a bubble six, so two of them need real
        // distance between them: at 66 and 80 the landing strip between the two
        // sweeps was under two units wide and the take-off measured 0.058s. They
        // are the widest things in the world; they get room.
        hazards += A.chasingBubble(62.0, GROUND)
        hazards += A.tentacle(84.0, GROUND)
        // 35-46%: across the bursting bridge, a ceiling, then a split.
        hazards += A.descendingWall(128.0, 2.0, downAt = 128.0)
        hazards += A.splitBubble(138.0, GROUND, at = 150.0, pieces = 2)
        // 50-60%: the run-up to the boost, with an orb over it.
        hazards += A.abyssOrb(176.0, 2.8)
        hazards += spike(196.0, GROUND)
        // 63-74%: the low shelf. Arm, mines, corridor.
        hazards += A.tentacle(224.0, LOW)
        hazards += A.mine(240.0, LOW + 1.9)
        hazards += A.lightCorridor(246.0, 256.0, LOW + 2.2, openAt = 250.0)
        // 82-92%: the second bridge is behind them; a bubble and an arm are not.
        hazards += A.chasingBubble(286.0, LOW)
        hazards += A.tentacle(302.0, LOW)
        // 92-100%: the finish.
        hazards += spike(330.0, LOW)
        hazards += spike(334.15, LOW)
        hazards += spike(338.3, LOW)

        val stars = listOf(
            Star(17.0, 2.5),
            Star(156.0, 4.4),
            Star(316.0, LOW + 4.4),
        )

        return Level(
            id = 16, name = "THE ARMS", subtitle = "IT REACHES IN",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
