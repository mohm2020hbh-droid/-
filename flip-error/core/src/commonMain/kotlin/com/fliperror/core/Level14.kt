package com.fliperror.core

/**
 * LEVEL 14 - "SPLIT"  (WORLD 3)
 *
 * The bubble stops being one thing.
 *
 * A large one comes at the runner and, at a moment fixed by where it is, it
 * stops being there and two or three small ones start being there - on their own
 * paths, at their own speeds. That is a real split rather than a trick of the
 * art: the parent's blink goes off exactly where the children's come on, all of
 * them derived from the same x, so what the player sees and what the collision
 * does are one event. It fades for the last quarter of its life first, which is
 * the telegraph.
 *
 * The chain arrives with it, and the orb. Between them this is the level where
 * the second tap stops being a way of crossing gaps and becomes a decision: a
 * chain of three is a rhythm you settle into, and an orb on its column is a
 * thing you have to be past before it comes back down.
 */
object Level14 {

    const val BPM = 194.0
    private val A = Abyss(BPM)

    private const val GROUND = 0.0
    private const val LOW = -3.2

    val finishX = A.beat(118.0)                  // 346.7 units -> 36.5 seconds

    fun build(): Level {
        val solids = listOf(
            Solid(-14.0, 54.0, GROUND),
            Solid(58.2, 104.0, GROUND),        // gap 4.20u
            Solid(108.2, 152.0, GROUND),       // gap 4.20u
            Solid(158.8, 202.0, GROUND),       // gap 6.60u - the boost
            Solid(206.2, 250.0, LOW),          // gap 4.20u, and down
            Solid(254.2, 298.0, LOW),          // gap 4.20u
            Solid(304.6, 356.0, LOW),          // gap 6.60u - the second boost
        )

        val hazards = ArrayList<Hazard>()
        // 0-12%: known shapes only. A level that opens on its new word teaches
        // the player that the new word is unfair.
        hazards += A.crystal(11.0, GROUND, lowAt = 11.0)
        hazards += A.pressureRing(22.0, GROUND, reach = 2.0)
        hazards += A.crystal(33.0, GROUND, lowAt = 33.0)
        // The first WIDE thing waits until past the first eighth. A crystal is
        // 0.203s and a ring 0.216s; a bubble this size is 0.185s, and the rule
        // for an opening is 0.18.
        hazards += A.chasingBubble(46.0, GROUND, size = 1.3, reach = 1.8)
        // 19-23%: the first split, alone on a long floor, so its two halves can
        // be watched once without anything else asking for the same hand.
        hazards += A.splitBubble(66.0, GROUND, at = 78.0, pieces = 3)
        // 26-29%: the orb, on its column: down into the lane exactly as they
        // arrive, and clear over their head a bar later.
        hazards += A.orbColumn(90.0, GROUND, lowAt = 90.0)
        // 96 and not 102. At 102 the crystal stood two units short of the ledge at
        // 104, so one take-off had to clear the crystal AND a 4.20u gap: two
        // consecutive 0.033s windows, which is not a hard level, it is a typo.
        hazards += A.crystal(96.0, GROUND, lowAt = 96.0)
        // 34-42%: the chain. Three bubbles, each a little bigger and a little
        // slower than the last, close enough to be one phrase.
        hazards += A.bubbleChain(118.0, GROUND, count = 3, spacing = 7.6, size = 1.15)
        hazards += A.crystal(144.0, GROUND, lowAt = 144.0)
        // 49-56%: the second split, tighter, past the boost gap.
        hazards += A.splitBubble(170.0, GROUND, at = 182.0, pieces = 2)
        hazards += A.crystal(194.0, GROUND, lowAt = 194.0)
        // 62-69%: the low shelf. An orb on a real circle, then a short chain.
        hazards += A.abyssOrb(216.0, LOW, radius = 2.0)
        hazards += A.bubbleChain(228.0, LOW, count = 2, spacing = 7.0, size = 1.2)
        // 76-83%: crystal, ring, crystal into the second boost.
        hazards += A.crystal(264.0, LOW, lowAt = 264.0)
        hazards += A.pressureRing(274.0, LOW, reach = 2.4)
        hazards += A.crystal(286.0, LOW, lowAt = 286.0)
        // 92-97%: the slot.
        hazards += A.stillBubble(318.0, LOW)
        hazards += A.stillBubble(328.0, LOW)
        hazards += A.stillBubble(332.18, LOW)
        hazards += A.stillBubble(336.36, LOW)

        val stars = listOf(
            Star(139.0, 4.4),
            Star(210.0, LOW + 4.4),
            Star(310.0, LOW + 4.4),
        )

        return Level(
            id = 14, name = "SPLIT", subtitle = "IT DOES NOT STAY ONE THING",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
