package com.fliperror.core

/**
 * LEVEL 18 - "ABYSS CORE"  (WORLD 3, FINALE)
 *
 * The bottom of the water, and the densest thing in FLIP ERROR.
 *
 * Nothing is introduced here. A finale that teaches is a finale that stalls, so
 * every shape in it is one the player has already met and the only new thing is
 * how little room there is between them. The floor drops twice, turns to bubbles
 * twice, and from 88% there is no stretch of it longer than five units with
 * nothing in front of the runner: that is the FINAL GAUNTLET, and it is measured
 * rather than claimed - the world 3 harness counts the frames past 88% with an
 * empty twelve units ahead, and the number has to be zero.
 *
 * Its take-off windows sit on 0.075s, about four and a half frames at 60Hz, and
 * they are not allowed past it. The floor is the same in every world. What makes
 * this the hardest level in the game is not that any single input is tighter
 * than LEVEL 6's was - it is that there is nowhere in it to stop reading.
 */
object Level18 {

    const val BPM = 202.0
    private val A = Abyss(BPM)

    private const val GROUND = 0.0
    private const val MID = -1.8
    private const val LOW = -3.6

    val finishX = A.beat(123.0)                  // 347.0 units -> 36.5 seconds

    fun build(): Level {
        val solids = listOf(
            Solid(-14.0, 48.0, GROUND),
            Solid(52.2, 92.0, GROUND),              // gap 4.20u
            A.bubbleFloor(96.2, 110.0, GROUND),     // gap 4.20u - water for a floor
            Solid(114.2, 154.0, GROUND),            // gap 4.20u
            // 7.00u, not 6.60u. Dropping to MID adds most of a unit of reach, so
            // at 6.60 the crossing sat right on the edge of a single jump and the
            // second tap had to be near perfect - 0.058s. Past 7.00 the boost is
            // the only answer and the timing relaxes right out.
            Solid(161.0, 202.0, MID),               // gap 7.00u - the boost, and down
            Solid(206.2, 244.0, MID),               // gap 4.20u
            A.bubbleFloor(248.2, 260.0, LOW),       // gap 4.20u
            Solid(264.2, 300.0, LOW),               // gap 4.20u
            Solid(306.6, 360.0, LOW),               // gap 6.60u - the last boost
        )

        val hazards = ArrayList<Hazard>()
        // 0-12%: even a finale gives the player an eighth of a level to look at it,
        // and it spends that eighth on the two widest windows the kit has.
        hazards += A.crystal(10.0, GROUND, lowAt = 10.0)
        hazards += A.pressureRing(20.0, GROUND, reach = 2.0)
        hazards += A.crystal(30.0, GROUND, lowAt = 30.0)
        hazards += A.pressureRing(40.0, GROUND, reach = 2.2)
        // 18-24%: wall, jelly, crystal - straight into it.
        hazards += A.bubbleWall(62.0, GROUND, openBottom = 1.7, openTop = 3.9)
        hazards += A.jelly(72.0, GROUND, openAt = 72.0)
        hazards += A.crystal(82.0, GROUND, lowAt = 82.0)
        // 29-39%: an arm on the bubble floor, then the chain, tighter than LEVEL
        // 17's - 6.8 units a link, which is the closest this game puts two
        // bubbles and still leaves ground to land on between them.
        hazards += A.tentacle(100.0, GROUND, upAt = 100.0)
        hazards += A.bubbleChain(122.0, GROUND, count = 3, spacing = 6.8, size = 1.15)
        hazards += A.crystal(146.0, GROUND, lowAt = 146.0)
        // 50-56%: the mid shelf. Swell, arm, crystal.
        hazards += A.risingWave(168.0, MID, reach = 2.0)
        hazards += A.tentacle(180.0, MID, upAt = 180.0)
        hazards += A.crystal(192.0, MID, lowAt = 192.0)
        // 62-68%: wall, orb, ring. The orb hangs in the air rather than sitting in
        // the lane, so it and the ring ten units later are not two landings, they
        // are one stretch with a roof on part of it.
        hazards += A.bubbleWall(214.0, MID, openBottom = 1.7, openTop = 3.9)
        hazards += A.abyssOrb(224.0, MID + 2.8, radius = 2.0)
        hazards += A.pressureRing(234.0, MID, reach = 2.4)
        // 73-84%: down to the bottom. Crystal on the bubbles, an arm, a split.
        hazards += A.crystal(252.0, LOW, lowAt = 252.0)
        hazards += A.tentacle(270.0, LOW, upAt = 270.0)
        hazards += A.splitBubble(280.0, LOW, at = 289.0, pieces = 2)
        // 88-100%: THE FINAL GAUNTLET. Jelly, crystal, and the tightest slot in
        // the game, with no quiet floor anywhere in it.
        hazards += A.jelly(312.0, LOW, openAt = 312.0)
        hazards += A.crystal(322.0, LOW, lowAt = 322.0)
        hazards += A.stillBubble(336.0, LOW)
        hazards += A.stillBubble(340.14, LOW)
        hazards += A.stillBubble(344.28, LOW)

        val stars = listOf(
            Star(46.0, 4.4),
            Star(141.0, 4.4),
            Star(304.0, LOW + 4.4),
        )

        return Level(
            id = 18, name = "ABYSS CORE", subtitle = "NOWHERE LEFT TO LOOK AWAY",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
