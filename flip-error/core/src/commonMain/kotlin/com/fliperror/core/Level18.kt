package com.fliperror.core

/**
 * LEVEL 18 - "THE SUN BELOW"  (WORLD 3, FINALE)
 *
 * The last level of the abyss, and the densest thing in the game. Every word all
 * three worlds know is spoken here and nothing is introduced - a finale that
 * teaches is a finale that stalls.
 *
 * Its last fifteen per cent is the FINAL GAUNTLET: bubble, split, arm, current,
 * corridor, and a three-spike slot, one after another with no quiet floor in
 * between. It sits on the 0.075s floor and is not allowed past it, because the
 * floor is the same in every world - what makes this the hardest level in the
 * game is that there is nowhere in it to stop reading.
 */
object Level18 {

    const val BPM = 202.0
    private val A = Abyss(BPM)

    private const val GROUND = 0.0
    private const val MID = -1.8
    private const val LOW = -3.6

    private fun spike(x: Double, y: Double) = Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0)

    val finishX = A.beat(123.0)                  // 347.0 units -> 36.5 seconds

    fun build(): Level {
        val solids = listOf(
            Solid(-14.0, 48.0, GROUND),
            Solid(52.2, 92.0, GROUND),          // gap 4.20u
            A.burstingBubble(96.2, 110.0, GROUND, arriveAt = 96.5),
            Solid(114.2, 154.0, GROUND),        // gap 4.20u
            // 7.00u, not 6.60u. Dropping to MID adds most of a unit of reach, so
            // at 6.60 the crossing sat right on the edge of a single jump and the
            // second tap had to be near perfect - 0.058s. Past 7.00 the boost is
            // the only answer and the timing relaxes right out.
            Solid(161.0, 202.0, MID),           // gap 7.00u - the boost, and down
            Solid(206.2, 244.0, MID),           // gap 4.20u
            A.burstingBubble(248.2, 260.0, LOW, arriveAt = 248.5),
            Solid(264.2, 300.0, LOW),           // gap 4.20u
            Solid(306.6, 360.0, LOW),           // gap 6.60u - the last boost
        )

        val winds = listOf(A.currentBurst(268.0, 280.0, 15.0))

        val hazards = ArrayList<Hazard>()
        // 0-12%: even a finale gives the player an eighth of a level to look at it.
        hazards += spike(10.0, GROUND)
        hazards += spike(21.0, GROUND)
        hazards += spike(32.0, GROUND)
        // 13-25%: bubble, arm - the two widest things, with room between them.
        // An arm sweeps seven units and a bubble six. Landing from the gap at 56
        // and finding an arm at 62 left under two units of strip and a 0.046s
        // take-off; the widest things in the world get the most room, always.
        hazards += A.chasingBubble(42.0, GROUND)
        hazards += A.tentacle(70.0, GROUND)
        hazards += A.descendingWall(86.0, 2.0, downAt = 86.0)
        // 33-44%: across the bursting bridge, then a split and a corridor.
        hazards += A.splitBubble(118.0, GROUND, at = 130.0, pieces = 2)
        hazards += A.lightCorridor(140.0, 150.0, 2.2, openAt = 144.0)
        // 48-58%: the mid shelf. Orb, mines, lane.
        hazards += A.abyssOrb(170.0, MID + 2.9)
        hazards += A.mine(188.0, MID + 1.9)
        hazards += A.electricCurrent(196.0, MID + 2.0, onAtX = 196.0)
        // 61-70%: arm and bubble before the second bridge.
        hazards += A.tentacle(224.0, MID)
        hazards += A.chasingBubble(236.0, MID)
        // 76-85%: the low shelf, in the updraft.
        hazards += A.mine(270.0, LOW + 2.0, phase = 0.25)
        hazards += A.descendingWall(286.0, LOW + 2.1, downAt = 286.0)
        // 88-100%: THE FINAL GAUNTLET. No quiet floor from here to the line.
        hazards += A.chasingBubble(312.0, LOW, reach = 2.0)
        hazards += A.splitBubble(322.0, LOW, at = 330.0, pieces = 2)
        hazards += spike(336.0, LOW)
        hazards += spike(340.14, LOW)
        hazards += spike(344.28, LOW)

        val stars = listOf(
            Star(16.0, 2.5),
            Star(150.0, 4.4),
            Star(296.0, LOW + 4.4),
        )

        return Level(
            id = 18, name = "THE SUN BELOW", subtitle = "NOWHERE LEFT TO LOOK AWAY",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars,
            finishX = finishX, winds = winds,
        )
    }
}
