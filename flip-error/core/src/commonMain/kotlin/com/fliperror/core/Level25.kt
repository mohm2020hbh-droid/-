package com.fliperror.core

/**
 * LEVEL 25 - "ROOTS"  (WORLD 5, OUTER GROVE)
 *
 * The edge of the forest, and its first three words: the root that comes up, the
 * flower that shuts, and the vine that swings.
 *
 * It teaches in the order the spec asks for - root, flower, root and flower,
 * vine, vine and root - and it teaches at speed. This is the outer grove and it
 * is already harder than anything in the machine: the forest opens at 0.079s
 * where CLOCKWORK opened at 0.083, and it does not leave the player alone for
 * two and a half seconds anywhere in its length.
 */
object Level25 {

    const val BPM = 216.0
    private val O = Overgrowth(BPM)

    private const val GROUND = 0.0
    private const val LOW = -3.2

    val finishX = O.beat(131.0)                  // 345.7 units -> 36.4 seconds

    fun build(): Level {
        val solids = listOf(
            O.moss(-14.0, 52.0, GROUND),
            O.moss(56.2, 98.0, GROUND),        // gap 4.20u
            O.moss(102.2, 146.0, GROUND),      // gap 4.20u
            O.moss(152.6, 194.0, GROUND),      // gap 6.60u - the boost
            O.moss(198.2, 242.0, LOW),         // gap 4.20u, and down under the canopy
            O.moss(246.2, 290.0, LOW),         // gap 4.20u
            O.moss(296.6, 350.0, LOW),         // gap 6.60u - the second boost
        )

        val hazards = ArrayList<Hazard>()
        // SECTION 1, 0-12%: roots, three of them, each on a slightly longer cycle
        // than the last so they come up as a wave. A root is 0.177s and a flower
        // 0.191s - the two widest windows this world has, which is what an
        // opening is for.
        // Small roots and a small flower for the first eighth. A root 1.0 x 1.5
        // is 0.177s on its own and 0.171s once the verifier couples it to the
        // landing before it, and an opening is not allowed under 0.18 - so the
        // ones out here are shorter, and the forest gets its full height at 13%.
        hazards += O.rootRun(11.0, GROUND, count = 3, spacing = 11.0, height = 1.25)
        // SECTION 2, 13-15%: the first flower, alone on a long stretch of moss.
        // At 50 it stood a unit short of the ledge at 52, so one take-off had to
        // clear the flower AND a 4.20u gap - two consecutive 0.029s windows.
        hazards += O.snapFlower(44.0, GROUND, snapAt = 44.0)
        // SECTION 3, 20-28%: root and flower together, ten units apart, which is
        // a decision a second.
        hazards += O.rootRise(66.0, GROUND, upAt = 66.0)
        hazards += O.snapFlower(78.0, GROUND, snapAt = 78.0)
        hazards += O.rootRise(90.0, GROUND, upAt = 90.0)
        // SECTION 4, 31-39%: the vine. Wide and slow, and its phase is solved so
        // it is swinging TOWARD the runner at the crossing.
        hazards += O.vineSweep(108.0, GROUND, nearAt = 108.0)
        hazards += O.snapFlower(122.0, GROUND, snapAt = 122.0)
        hazards += O.rootRise(134.0, GROUND, upAt = 134.0)
        // SECTION 5, 47-54%: past the boost. Vine, root, flower - and the vine at
        // 162 is hung high, so it cannot touch a runner who stays down and takes
        // the whole stretch from one who does not.
        hazards += O.vineSweep(162.0, GROUND, nearAt = 162.0)
        hazards += O.rootRise(174.0, GROUND, upAt = 174.0)
        hazards += O.snapFlower(184.0, GROUND, snapAt = 184.0)
        // 61-68%: the lower floor, under the canopy.
        hazards += O.rootRise(208.0, LOW, upAt = 208.0)
        hazards += O.vineSweep(220.0, LOW, nearAt = 220.0)
        hazards += O.snapFlower(232.0, LOW, snapAt = 232.0)
        // 74-81%: and again, into the second boost.
        hazards += O.snapFlower(256.0, LOW, snapAt = 256.0)
        hazards += O.rootRise(268.0, LOW, upAt = 268.0)
        hazards += O.vineSweep(278.0, LOW, nearAt = 278.0)
        // SECTION 6, 89-97%: a last root, and the slot - three fallen seeds a
        // jump apart, the one place in the forest where nothing is on a cycle.
        hazards += O.rootRise(308.0, LOW, upAt = 308.0)
        hazards += O.fallenSeed(318.0, LOW)
        hazards += O.fallenSeed(328.0, LOW)
        hazards += O.fallenSeed(332.16, LOW)
        hazards += O.fallenSeed(336.32, LOW)

        // Coins sit at 4.4, over a plain jump's reach, on moss the fastest line
        // is running across - so a coin is a second tap the player did not
        // otherwise owe, never change handed back on a crossing.
        val stars = listOf(
            Star(48.0, 4.4),
            Star(202.0, LOW + 4.4),
            Star(300.0, LOW + 4.4),
        )

        return Level(
            id = 25, name = "ROOTS", subtitle = "THE FOREST IS ALIVE",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
