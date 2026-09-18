package com.fliperror.core

/**
 * LEVEL 13 - "DEEP SIGNAL"  (WORLD 3, THE ABYSS)
 *
 * The water's first three words: the crystal that swings down into the lane, the
 * ring of pressure travelling it, and the bubble that comes at you.
 *
 * It is the gentlest level of the abyss and it is still harder than anything in
 * the desert, because of the one thing every level in this world holds to: there
 * is no empty floor in it. World 2's levels left the player alone for five and
 * six seconds at a stretch; this one never leaves them alone for two. The
 * obstacles are cheap - a crystal costs the lane 1.2 units and nothing else - so
 * the density is paid for out of geometry rather than out of the take-off
 * windows, which stay wide here on purpose. A player should finish LEVEL 13
 * tired rather than beaten.
 */
object Level13 {

    const val BPM = 192.0
    private val A = Abyss(BPM)

    private const val GROUND = 0.0
    private const val LOW = -3.2

    val finishX = A.beat(116.0)                  // 344.4 units -> 36.3 seconds

    fun build(): Level {
        val solids = listOf(
            Solid(-14.0, 56.0, GROUND),
            Solid(60.2, 100.0, GROUND),        // gap 4.20u
            Solid(104.2, 148.0, GROUND),       // gap 4.20u
            Solid(154.8, 196.0, GROUND),       // gap 6.80u - the boost
            Solid(200.2, 244.0, LOW),          // gap 4.20u, and down into the dark
            Solid(248.2, 292.0, LOW),          // gap 4.20u
            Solid(298.8, 352.0, LOW),          // gap 6.60u - the second boost
        )

        val hazards = ArrayList<Hazard>()
        // 0-12%: two crystals and a slow ring, a dozen units apart. The windows
        // here are the widest in the world and they are meant to be: this is the
        // eighth of a level in which the player learns that a crystal comes DOWN.
        hazards += A.crystal(12.0, GROUND, lowAt = 12.0)
        hazards += A.crystal(24.0, GROUND, lowAt = 24.0)
        hazards += A.pressureRing(36.0, GROUND, reach = 2.0)
        // 14-16%: the first bubble, small and slow, with the whole floor to land on.
        hazards += A.chasingBubble(48.0, GROUND, size = 1.3, reach = 1.8)
        // 20-28%: the three words in rotation, across the first two gaps.
        hazards += A.crystal(68.0, GROUND, lowAt = 68.0)
        hazards += A.pressureRing(80.0, GROUND, reach = 2.2)
        hazards += A.crystal(92.0, GROUND, lowAt = 92.0)
        // 32-40%: a bigger bubble, then the run-up to the boost gap.
        hazards += A.chasingBubble(112.0, GROUND, size = 1.4, reach = 2.0)
        hazards += A.crystal(124.0, GROUND, lowAt = 124.0)
        hazards += A.pressureRing(134.0, GROUND, reach = 2.4)
        // 48-55%: past the boost, the same three in a tighter rotation.
        hazards += A.crystal(164.0, GROUND, lowAt = 164.0)
        hazards += A.chasingBubble(176.0, GROUND, size = 1.5, reach = 2.2)
        hazards += A.crystal(188.0, GROUND, lowAt = 188.0)
        // 61-67%: the low shelf.
        hazards += A.pressureRing(210.0, LOW, reach = 2.4)
        hazards += A.crystal(222.0, LOW, lowAt = 222.0)
        hazards += A.chasingBubble(232.0, LOW, size = 1.3, reach = 1.8)
        // 75-81%: and again, into the second boost.
        hazards += A.crystal(258.0, LOW, lowAt = 258.0)
        hazards += A.pressureRing(268.0, LOW, reach = 2.6)
        hazards += A.crystal(280.0, LOW, lowAt = 280.0)
        // 90-96%: THE SLOT. Four bubbles holding station, a jump apart - the one
        // place in the abyss where the answer is pure reflex, and the moment the
        // whole ladder is measured at.
        hazards += A.stillBubble(312.0, LOW)
        hazards += A.stillBubble(322.0, LOW)
        hazards += A.stillBubble(326.2, LOW)
        hazards += A.stillBubble(330.4, LOW)

        // Every coin sits at 4.4 - higher than a single jump reaches - over floor
        // the fastest line is RUNNING across. So a coin is never something the
        // player gets for free on a crossing they had to make anyway: it costs a
        // second tap they did not otherwise owe.
        val stars = listOf(
            Star(74.0, 4.4),
            Star(204.0, LOW + 4.4),
            Star(306.0, LOW + 4.4),
        )

        return Level(
            id = 13, name = "DEEP SIGNAL", subtitle = "THE WATER IS AWAKE",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
