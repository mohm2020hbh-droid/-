package com.fliperror.core

/**
 * LEVEL 13 - "DEEP SIGNAL"  (WORLD 3, THE ABYSS)
 *
 * The first level of the world that asks a new question. Worlds 1 and 2 both
 * answered every obstacle with a tap at the right moment; this one opens by
 * teaching the opposite - a slab that drops through exactly the air a jump would
 * occupy, where the answer is to keep running and not press anything.
 *
 * It introduces three words and nothing else: the chasing bubble, the descending
 * wall, and the two of them together. Everything the abyss knows arrives later;
 * a level that teaches four things teaches none.
 */
object Level13 {

    const val BPM = 192.0
    private val A = Abyss(BPM)

    private const val GROUND = 0.0
    private const val LOW = -3.2

    private fun spike(x: Double, y: Double) = Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0)

    val finishX = A.beat(116.0)                  // 344.4 units -> 36.3 seconds

    fun build(): Level {
        val solids = listOf(
            Solid(-14.0, 56.0, GROUND),        // the opening, still and readable
            Solid(60.2, 100.0, GROUND),        // gap 4.20u
            Solid(104.2, 148.0, GROUND),       // gap 4.20u
            Solid(154.8, 196.0, GROUND),       // gap 6.80u - the boost
            Solid(200.2, 244.0, LOW),          // gap 4.20u, and down into the dark
            Solid(248.2, 292.0, LOW),          // gap 4.20u
            Solid(298.8, 352.0, LOW),          // gap 6.60u - the second boost
        )

        val hazards = ArrayList<Hazard>()
        // 0-12%: three spikes. The abyss does not open on something new.
        hazards += spike(12.0, GROUND)
        hazards += spike(24.0, GROUND)
        hazards += spike(36.0, GROUND)
        // 13-16%: the first bubble, alone, with the whole floor to land on.
        hazards += A.chasingBubble(46.0, GROUND)
        // 19-28%: and the wall that comes DOWN. It hangs at 2.0, which a running
        // runner passes under and a jumping one does not. Nothing else is on this
        // stretch, because the lesson is the whole point: do not tap here.
        hazards += A.descendingWall(68.0, 2.0, downAt = 68.0)
        hazards += A.descendingWall(84.0, 2.0, downAt = 84.0)
        // 30-42%: bubble, then wall, so the two answers sit side by side.
        hazards += A.chasingBubble(110.0, GROUND)
        hazards += A.descendingWall(126.0, 2.1, downAt = 126.0)
        hazards += spike(140.0, GROUND)
        // 45-55%: the run-up to the boost gap, with a bubble on it.
        hazards += A.chasingBubble(164.0, GROUND, reach = 2.0)
        hazards += spike(182.0, GROUND)
        // 58-70%: the low shelf. A mine to thread, then a wall.
        hazards += A.mine(210.0, LOW + 1.9)
        hazards += A.descendingWall(224.0, LOW + 2.1, downAt = 224.0)
        hazards += spike(236.0, LOW)
        // 73-84%: bubble and mine on the same stretch.
        hazards += A.chasingBubble(258.0, LOW)
        hazards += A.mine(276.0, LOW + 2.0, phase = 0.5)
        // 90-100%: the finish, on still floor, and a three-spike slot.
        hazards += spike(312.0, LOW)
        hazards += spike(322.0, LOW)
        hazards += spike(326.2, LOW)
        hazards += spike(330.4, LOW)

        val stars = listOf(
            Star(18.0, 2.5),          // one honest jump, off still floor
            Star(94.0, 4.4),          // higher than a jump reaches: the coin costs
                                      // a second tap the crossing never needed
            Star(288.0, LOW + 4.4),   // and again, out over the dark
        )

        return Level(
            id = 13, name = "DEEP SIGNAL", subtitle = "SOMETIMES, DO NOT JUMP",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
