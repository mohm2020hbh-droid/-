package com.fliperror.core

/**
 * LEVEL 16 - "CURRENT"  (WORLD 3)
 *
 * Where the abyss stops asking WHEN and starts asking WHERE.
 *
 * The bubble wall is this level's reason to exist. A formation of bubbles fills
 * the lane from the floor to the top of the screen with one opening in it, drawn
 * as plainly as the wall is, and the opening is at head height. Every obstacle
 * in three worlds up to this point has been answered by being in the air or not
 * being in the air; this one is answered by being at a PARTICULAR HEIGHT, which
 * means the jump has to start in the right place and not merely at the right
 * moment. It is also the first obstacle in the game where the second tap is
 * lethal: a double jump through that opening puts the runner into the bubbles
 * above it.
 *
 * The arms come up out of the floor alongside it, the swells roll down the lane,
 * and the ground itself turns to bubbles for a stretch. Nothing in this level is
 * a spike, a ledge or a beam.
 */
object Level16 {

    const val BPM = 198.0
    private val A = Abyss(BPM)

    private const val GROUND = 0.0
    private const val LOW = -3.2

    val finishX = A.beat(120.0)                  // 345.5 units -> 36.4 seconds

    fun build(): Level {
        val solids = listOf(
            Solid(-14.0, 52.0, GROUND),
            Solid(56.2, 98.0, GROUND),              // gap 4.20u
            A.bubbleFloor(102.2, 146.0, GROUND),    // gap 4.20u - and the floor is water
            Solid(152.6, 196.0, GROUND),            // gap 6.60u - the boost
            Solid(200.2, 244.0, LOW),               // gap 4.20u
            A.bubbleFloor(248.2, 292.0, LOW),       // gap 4.20u
            Solid(298.6, 350.0, LOW),               // gap 6.60u - the second boost
        )

        val hazards = ArrayList<Hazard>()
        // 0-12%: crystal, ring, crystal - 0.203s and 0.216s, the two widest
        // windows in the kit. The swell and the arm are this level's new words and
        // they wait until the first eighth is behind the player, because an
        // opening is for shapes that are already known.
        hazards += A.crystal(11.0, GROUND, lowAt = 11.0)
        hazards += A.pressureRing(22.0, GROUND, reach = 2.0)
        hazards += A.crystal(34.0, GROUND, lowAt = 34.0)
        hazards += A.risingWave(46.0, GROUND, reach = 1.8)
        // 18-25%: THE WALL. Alone on its stretch the first time, because it asks
        // for something the player has never been asked for.
        hazards += A.bubbleWall(64.0, GROUND, openBottom = 1.7, openTop = 4.2)
        hazards += A.risingWave(76.0, GROUND, reach = 2.2)
        hazards += A.tentacle(88.0, GROUND, upAt = 88.0)
        // 31-39%: the chain, over water that is also the floor.
        hazards += A.bubbleChain(108.0, GROUND, count = 3, spacing = 7.4, size = 1.2)
        hazards += A.abyssOrb(134.0, 2.8, radius = 2.2)
        // 47-54%: past the boost. Arm, wall, swell - and now the wall has company.
        hazards += A.tentacle(164.0, GROUND, upAt = 164.0)
        hazards += A.bubbleWall(174.0, GROUND, openBottom = 1.7, openTop = 4.2)
        hazards += A.risingWave(186.0, GROUND, reach = 2.2)
        // 61-69%: the low shelf. An orb on its circle, an arm, a short chain.
        hazards += A.abyssOrb(210.0, LOW + 2.8, radius = 2.0)
        hazards += A.tentacle(222.0, LOW, upAt = 222.0)
        hazards += A.bubbleChain(232.0, LOW, count = 2, spacing = 7.2, size = 1.2)
        // 75-81%: wall, swell, arm across the bubble floor.
        hazards += A.bubbleWall(258.0, LOW, openBottom = 1.7, openTop = 4.2)
        hazards += A.risingWave(268.0, LOW, reach = 2.2)
        hazards += A.tentacle(280.0, LOW, upAt = 280.0)
        // 90-98%: an orb over the run-in, and the slot.
        hazards += A.abyssOrb(312.0, LOW + 2.8, radius = 2.0)
        hazards += A.stillBubble(330.0, LOW)
        hazards += A.stillBubble(334.15, LOW)
        hazards += A.stillBubble(338.3, LOW)

        val stars = listOf(
            Star(52.0, 4.4),
            Star(204.0, LOW + 4.4),
            Star(304.0, LOW + 4.4),
        )

        return Level(
            id = 16, name = "CURRENT", subtitle = "FIND THE OPENING",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
