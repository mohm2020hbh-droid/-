package com.fliperror.core

/**
 * LEVEL 15 - "PRESSURE"  (WORLD 3)
 *
 * The level where the water stops leaving gaps.
 *
 * Something is in front of the runner every ten units from the first second to
 * the last, and the jelly arrives to make sure of it: a geometric organism that
 * opens on the bar and shuts again, swelling where the player can see it for a
 * quarter of a second before it is lethal. Open, it is a jump. Shut, it is a
 * stretch of floor that is yours for exactly as long as it takes the next thing
 * to arrive.
 *
 * That is the pressure in the name. Nothing here is individually harder than
 * LEVEL 14 - the take-off windows are barely tighter - but there is nowhere in
 * it to stop deciding, and the decision is always the same one: one jump, or
 * two.
 */
object Level15 {

    const val BPM = 196.0
    private val A = Abyss(BPM)

    private const val GROUND = 0.0
    private const val LOW = -3.2

    val finishX = A.beat(119.0)                  // 346.1 units -> 36.4 seconds

    fun build(): Level {
        val solids = listOf(
            Solid(-14.0, 52.0, GROUND),
            Solid(56.2, 100.0, GROUND),        // gap 4.20u
            Solid(104.2, 150.0, GROUND),       // gap 4.20u
            Solid(157.2, 200.0, GROUND),       // gap 7.20u - the boost
            Solid(204.2, 248.0, LOW),          // gap 4.20u
            Solid(252.2, 296.0, LOW),          // gap 4.20u
            Solid(302.6, 356.0, LOW),          // gap 6.60u - the second boost
        )

        val hazards = ArrayList<Hazard>()
        // 0-12%: the opening still gets its eighth of a level at wide windows.
        hazards += A.crystal(11.0, GROUND, lowAt = 11.0)
        hazards += A.pressureRing(22.0, GROUND, reach = 2.0)
        hazards += A.crystal(33.0, GROUND, lowAt = 33.0)
        hazards += A.chasingBubble(46.0, GROUND, size = 1.3, reach = 1.8)
        // 18-27%: the jelly, twice, with a crystal and a ring between them. Both
        // of these are OPEN when the runner reaches them - the shut ones come
        // later, once the player knows what an open one looks like.
        hazards += A.jelly(64.0, GROUND, openAt = 64.0)
        hazards += A.crystal(74.0, GROUND, lowAt = 74.0)
        hazards += A.pressureRing(84.0, GROUND, reach = 2.4)
        hazards += A.jelly(94.0, GROUND, openAt = 94.0)
        // 32-41%: a split, then the run-up to the widest gap in the world.
        hazards += A.splitBubble(112.0, GROUND, at = 123.0, pieces = 2)
        hazards += A.crystal(134.0, GROUND, lowAt = 134.0)
        hazards += A.jelly(143.0, GROUND, openAt = 143.0)
        // 48-56%: past the 7.20u boost. Crystal, ring, jelly, crystal - ten units
        // apart, which is a decision every second and a bit.
        hazards += A.crystal(166.0, GROUND, lowAt = 166.0)
        hazards += A.pressureRing(176.0, GROUND, reach = 2.4)
        hazards += A.jelly(186.0, GROUND, openAt = 186.0)
        // Drift over the last of that stretch, hanging at 4.8 where a single jump
        // cannot reach it and a second tap can. The gap at 200 is 4.20u and takes
        // one tap, so the drift costs the crossing nothing - it costs the habit.
        hazards += A.floaters(192.0, GROUND, count = 3, spacing = 3.0)
        // 62-68%: the low shelf.
        hazards += A.chasingBubble(214.0, LOW, size = 1.4, reach = 2.0)
        hazards += A.crystal(226.0, LOW, lowAt = 226.0)
        hazards += A.jelly(236.0, LOW, openAt = 236.0)
        // 75-83%: ring, crystal, and a split that lands them on the ledge.
        hazards += A.pressureRing(260.0, LOW, reach = 2.6)
        hazards += A.crystalSlider(270.0, LOW, reach = 1.8, nearAt = 270.0)
        hazards += A.splitBubble(276.0, LOW, at = 285.0, pieces = 2)
        // 91-97%: the slot.
        hazards += A.stillBubble(316.0, LOW)
        hazards += A.stillBubble(326.0, LOW)
        hazards += A.stillBubble(330.16, LOW)
        hazards += A.stillBubble(334.32, LOW)

        val stars = listOf(
            Star(54.0, 4.4),
            Star(208.0, LOW + 4.4),
            Star(310.0, LOW + 4.4),
        )

        return Level(
            id = 15, name = "PRESSURE", subtitle = "NOWHERE TO STOP DECIDING",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
