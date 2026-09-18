package com.fliperror.core

/**
 * LEVEL 17 - "MEMORY"  (WORLD 3)
 *
 * The same phrase, twice, faster the second time.
 *
 * Its middle is built out of two runs of the identical sequence - bubble, no
 * tap, corridor, split, jump - laid down once with room to read it and once with
 * the room taken away. That is not a trick: a runner never stops, so the second
 * pass is genuinely the same geometry compressed, and a player who read the
 * first one already knows the answer to the second. It is the first level in the
 * game that rewards having been here before rather than reacting well.
 *
 * Everything the abyss knows is on the field by now except the electric lanes,
 * which arrive here as the thing that punishes staying airborne.
 */
object Level17 {

    const val BPM = 200.0
    private val A = Abyss(BPM)

    private const val GROUND = 0.0
    private const val LOW = -3.2

    private fun spike(x: Double, y: Double) = Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0)

    /** The phrase, laid down from [x] with its beats [step] apart. Twice: once
     *  wide, once tight, and the second one is the level's whole idea. */
    private fun phrase(x: Double, base: Double, step: Double): List<Hazard> {
        val out = ArrayList<Hazard>()
        out += A.chasingBubble(x, base, reach = 2.0)
        out += A.descendingWall(x + step, base + 2.1, downAt = x + step)
        out += A.lightCorridor(x + step * 2, x + step * 2 + 8.4, base + 2.2,
            openAt = x + step * 2 + 2.0)
        out += A.splitBubble(x + step * 3.2, base, at = x + step * 3.2 + 11.0, pieces = 2)
        return out
    }

    val finishX = A.beat(121.0)                  // 345.0 units -> 36.3 seconds

    fun build(): Level {
        val solids = listOf(
            Solid(-14.0, 50.0, GROUND),
            Solid(54.2, 140.0, GROUND),        // gap 4.20u - the first phrase runs here
            Solid(146.8, 232.0, GROUND),       // gap 6.60u - the boost, then the second
            Solid(236.2, 280.0, LOW),          // gap 4.20u, and down
            Solid(284.2, 318.0, LOW),          // gap 4.20u
            Solid(324.4, 380.0, LOW),          // gap 6.40u - the last boost
        )

        // Over FLOOR, never over a gap. A burst that changes the height of a
        // 6.40u crossing while it is being made measured a 0.029s take-off, and
        // the rule this level relearned is the one LEVEL 15 wrote down: water may
        // change what a jump is worth, never whether a crossing exists.
        val winds = listOf(A.currentBurst(292.0, 306.0, 16.0))

        val hazards = ArrayList<Hazard>()
        hazards += spike(11.0, GROUND)
        hazards += spike(23.0, GROUND)
        hazards += spike(35.0, GROUND)
        // 16-38%: the phrase, with room to read it.
        hazards += phrase(60.0, GROUND, 18.0)
        // 43-64%: the same phrase, tighter. A player who read the first one
        // already knows this; a player who did not is meeting it at speed.
        hazards += phrase(152.0, GROUND, 15.0)
        hazards += A.mine(212.0, 2.0, phase = 0.25)
        hazards += spike(224.0, GROUND)
        // 69-78%: the low shelf, and the lanes that punish being in the air.
        hazards += A.electricCurrent(244.0, LOW + 2.0, onAtX = 244.0)
        hazards += A.mine(256.0, LOW + 1.9)
        hazards += A.electricCurrent(268.0, LOW + 2.0, onAtX = 268.0)
        // 84-94%: past the updraft. An orb, then an arm.
        hazards += A.abyssOrb(296.0, LOW + 2.9)
        hazards += A.chasingBubble(308.0, LOW)
        // 96-99%: the finish. It used to sit at 348, which is PAST this level's
        // own finish line at 344.9 - three spikes nobody ever reached, and a
        // level whose hardest moment was therefore somewhere in its middle.
        hazards += spike(330.0, LOW)
        hazards += spike(334.14, LOW)
        hazards += spike(338.28, LOW)

        val stars = listOf(
            Star(17.0, 2.5),
            Star(228.0, 4.4),        // on floor, not on the arc the line already flies
            Star(312.0, LOW + 4.4),  // on floor, past the crossing rather than over it
        )

        return Level(
            id = 17, name = "MEMORY", subtitle = "YOU HAVE SEEN THIS BEFORE",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars,
            finishX = finishX, winds = winds,
        )
    }
}
