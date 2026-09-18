package com.fliperror.core

/**
 * LEVEL 17 - "THE ABYSS"  (WORLD 3)
 *
 * The skill check. Every word the water knows, in one level, with almost nothing
 * between them.
 *
 * Crystal, ring, swell, wall, jelly, arm, chain, split, orb: nine shapes, and the
 * level cycles them fast enough that the player is never answering the thing in
 * front of them, they are answering the thing after it. That is the difference
 * between this and LEVEL 15 - the density there was one decision a second, and
 * here it is one decision a second made from the memory of the last three.
 *
 * Its take-off windows sit at the floor of what this game is ever allowed to ask
 * for, 0.075s, and they get there in the finish rather than in the middle. What
 * makes the middle hard is the reading, and the reading has no ceiling.
 */
object Level17 {

    const val BPM = 200.0
    private val A = Abyss(BPM)

    private const val GROUND = 0.0
    private const val LOW = -3.2

    val finishX = A.beat(121.0)                  // 345.0 units -> 36.3 seconds

    fun build(): Level {
        val solids = listOf(
            Solid(-14.0, 50.0, GROUND),
            Solid(54.2, 140.0, GROUND),        // gap 4.20u - a long unbroken run
            Solid(146.8, 232.0, GROUND),       // gap 6.60u - the boost, then another
            Solid(236.2, 280.0, LOW),          // gap 4.20u, and down
            Solid(284.2, 318.0, LOW),          // gap 4.20u
            Solid(324.4, 380.0, LOW),          // gap 6.40u - the last boost
        )

        val hazards = ArrayList<Hazard>()
        // 0-12%: four shapes in forty units, all of them the two with the widest
        // windows in the kit. The swell belongs to this level too and it waits
        // until 63%, because a wide thing four units from a ledge is a crossing
        // the player has to start from inside its sweep.
        hazards += A.crystal(10.0, GROUND, lowAt = 10.0)
        hazards += A.pressureRing(20.0, GROUND, reach = 2.0)
        hazards += A.crystal(30.0, GROUND, lowAt = 30.0)
        hazards += A.pressureRing(40.0, GROUND, reach = 2.2)
        // 17-30%: wall, jelly, crystal, chain - the long run, and nothing on it
        // is more than ten units from the next thing.
        hazards += A.bubbleWall(60.0, GROUND, openBottom = 1.7, openTop = 4.0)
        hazards += A.jelly(70.0, GROUND, openAt = 70.0)
        hazards += A.crystal(80.0, GROUND, lowAt = 80.0)
        hazards += A.bubbleChain(90.0, GROUND, count = 3, spacing = 7.0, size = 1.15)
        // 34-39%: the split, and then the run-up to the boost.
        hazards += A.floaters(108.0, GROUND, count = 2, spacing = 3.0)
        hazards += A.splitBubble(116.0, GROUND, at = 126.0, pieces = 2)
        hazards += A.crystal(134.0, GROUND, lowAt = 134.0)
        // 45-63%: the second long run. Arm, orb, crystal, ring, jelly, wall,
        // swell: seven shapes in sixty units, which is the densest stretch in
        // the game that is not a finish.
        hazards += A.tentacle(156.0, GROUND, upAt = 156.0)
        hazards += A.abyssOrb(166.0, GROUND, radius = 2.0)
        hazards += A.crystal(178.0, GROUND, lowAt = 178.0)
        hazards += A.pressureRing(188.0, GROUND, reach = 2.4)
        hazards += A.jelly(198.0, GROUND, openAt = 198.0)
        hazards += A.bubbleWall(208.0, GROUND, openBottom = 1.7, openTop = 4.0)
        hazards += A.risingWave(218.0, GROUND, reach = 2.2)
        // 71-79%: the low shelf. Crystal, arm, a short chain.
        hazards += A.crystal(246.0, LOW, lowAt = 246.0)
        hazards += A.tentacle(256.0, LOW, upAt = 256.0)
        hazards += A.bubbleChain(266.0, LOW, count = 2, spacing = 7.0, size = 1.2)
        // 85-90%: jelly and crystal into the last boost. A ring belongs here too
        // and is not here: at 310 its sweep reached 313.2 and the take-off for a
        // 6.40u crossing is at 316.6, which is two units of strip to land, stand
        // and leave from. Density is not worth a crossing nobody can start.
        hazards += A.jelly(292.0, LOW, openAt = 292.0)
        hazards += A.crystal(302.0, LOW, lowAt = 302.0)
        // 96-98%: the slot.
        hazards += A.stillBubble(330.0, LOW)
        hazards += A.stillBubble(334.14, LOW)
        hazards += A.stillBubble(338.28, LOW)

        val stars = listOf(
            Star(48.0, 4.4),
            Star(226.0, 4.4),
            Star(322.0, LOW + 4.4),
        )

        return Level(
            id = 17, name = "THE ABYSS", subtitle = "READ THE ONE AFTER NEXT",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
