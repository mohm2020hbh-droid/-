package com.fliperror.core

/**
 * LEVEL 28 - "HUNT"  (WORLD 5, HUNTING FOREST)
 *
 * Where the forest stops growing across the path and starts coming down it.
 *
 * The root wall is this level's reason to exist and it is the world's precision
 * obstacle: roots braiding into a barrier with one gap at head height, growing
 * and retreating on a cycle. Every other living thing here is answered by being
 * in the air or not being in the air; a wall with a gap at head height is
 * answered by being at a PARTICULAR height, which means the jump has to start in
 * the right place and not merely at the right moment - and a second tap through
 * that gap puts the runner into the roots above it.
 *
 * Around it: seeds rolling down the slope, vines hanging in curtains that take
 * the air away from whole stretches, and drifts crossing between them.
 */
object Level28 {

    const val BPM = 222.0
    private val O = Overgrowth(BPM)

    private const val GROUND = 0.0
    private const val LOW = -3.4

    val finishX = O.beat(134.0)                  // 344.1 units -> 36.2 seconds

    fun build(): Level {
        val solids = listOf(
            O.moss(-14.0, 48.0, GROUND),
            O.moss(52.2, 92.0, GROUND),        // gap 4.20u
            O.moss(96.2, 140.0, GROUND),       // gap 4.20u
            O.moss(147.0, 188.0, GROUND),      // gap 7.00u - the boost
            O.moss(192.2, 236.0, LOW),         // gap 4.20u, and down
            O.moss(240.2, 284.0, LOW),         // gap 4.20u
            O.moss(291.0, 346.0, LOW),         // gap 7.00u - the second boost
        )

        val hazards = ArrayList<Hazard>()
        // 0-12%: known words, widest windows.
        hazards += O.rootRise(11.0, GROUND, height = 1.25, upAt = 11.0)
        hazards += O.snapFlower(21.0, GROUND, size = 0.9, snapAt = 21.0)
        hazards += O.rootRise(31.0, GROUND, height = 1.25, upAt = 31.0)
        // 12-17%: the seed, rolling at the runner rather than sweeping across.
        hazards += O.seedRoll(42.0, GROUND, reach = 2.0)
        // 18-25%: THE WALL, alone on its stretch the first time, because it asks
        // for something nothing in this world has asked for yet.
        hazards += O.rootWall(60.0, GROUND, openBottom = 1.7, openTop = 4.2, grownAt = 60.0)
        hazards += O.carnivorousBloom(72.0, GROUND, openAt = 72.0)
        hazards += O.vineSweep(84.0, GROUND, nearAt = 84.0)
        // 29-39%: a curtain of vines over the whole stretch, and living things
        // under it. The curtain cannot touch a runner who stays down.
        // The curtain ends at 110 and the flower starts at 116, and the six units
        // between them are the whole reason this stretch works. At 112 the
        // flower's take-off fell inside the last vine's sway and the level became
        // unsolvable at x=111 - a wall of hanging vines followed immediately by
        // something that has to be jumped is not a hard sequence, it is a
        // contradiction.
        hazards += O.snapFlower(98.0, GROUND, snapAt = 98.0)
        hazards += O.vineCurtain(104.0, 110.0, GROUND, tip = 1.4)
        hazards += O.snapFlower(116.0, GROUND, snapAt = 116.0)
        hazards += O.sporeStream(126.0, GROUND, reach = 2.4)
        // 45-53%: past the boost. Wall, bloom, flower.
        hazards += O.rootWall(156.0, GROUND, openBottom = 1.7, openTop = 4.2, grownAt = 156.0)
        hazards += O.carnivorousBloom(168.0, GROUND, openAt = 168.0)
        hazards += O.snapFlower(180.0, GROUND, snapAt = 180.0)
        // 58-67%: the lower forest. Drift, seed, vine.
        hazards += O.sporeStream(202.0, LOW, reach = 2.8)
        hazards += O.seedRoll(216.0, LOW, reach = 2.2)
        hazards += O.vineSweep(228.0, LOW, nearAt = 228.0)
        // 72-81%: wall, curtain, bloom - the longest chain in the level.
        hazards += O.rootWall(250.0, LOW, openBottom = 1.7, openTop = 4.2, grownAt = 250.0)
        hazards += O.vineCurtain(260.0, 266.0, LOW, tip = 1.4)
        hazards += O.carnivorousBloom(272.0, LOW, openAt = 272.0)
        hazards += O.rootRise(280.0, LOW, upAt = 280.0)
        // 88-97%: a seed over the run-in, and the slot.
        hazards += O.snapFlower(298.0, LOW, snapAt = 298.0)
        hazards += O.seedRoll(306.0, LOW, reach = 2.0)
        hazards += O.fallenSeed(316.0, LOW)
        hazards += O.fallenSeed(326.0, LOW)
        hazards += O.fallenSeed(330.14, LOW)
        hazards += O.fallenSeed(334.28, LOW)

        val stars = listOf(
            Star(50.0, 4.4),
            Star(196.0, LOW + 4.4),
            Star(295.0, LOW + 4.4),
        )

        return Level(
            id = 28, name = "HUNT", subtitle = "IT COMES DOWN THE PATH",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
