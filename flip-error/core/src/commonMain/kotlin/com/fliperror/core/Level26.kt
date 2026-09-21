package com.fliperror.core

/**
 * LEVEL 26 - "BLOOM"  (WORLD 5, BLOOM GARDENS)
 *
 * The gardens, where the forest starts using more than one clock.
 *
 * A flower pair is two mouths out of step - the second derived to shut half a
 * cycle after the first, which at this speed is a real distance rather than a
 * number - so the safe moment does not sit anywhere, it walks between them. The
 * bloom arrives with it: petals growing UP out of the ground rather than
 * switching on, which means there is no instant at which it appears, only an
 * instant at which it is tall enough to matter.
 *
 * And the pod, which is the opposite: nothing at all, and then the whole thorn
 * structure, with a quarter of a second of swelling in between.
 */
object Level26 {

    const val BPM = 218.0
    private val O = Overgrowth(BPM)

    private const val GROUND = 0.0
    private const val LOW = -3.2

    val finishX = O.beat(132.0)                  // 345.1 units -> 36.3 seconds

    fun build(): Level {
        val solids = listOf(
            O.moss(-14.0, 50.0, GROUND),
            O.moss(54.2, 96.0, GROUND),        // gap 4.20u
            O.moss(100.2, 144.0, GROUND),      // gap 4.20u
            O.moss(150.6, 192.0, GROUND),      // gap 6.60u - the boost
            O.moss(196.2, 240.0, LOW),         // gap 4.20u
            O.moss(244.2, 288.0, LOW),         // gap 4.20u
            O.moss(294.6, 348.0, LOW),         // gap 6.60u - the second boost
        )

        val hazards = ArrayList<Hazard>()
        // 0-12%: known words, widest windows.
        hazards += O.rootRise(11.0, GROUND, height = 1.25, upAt = 11.0)
        hazards += O.snapFlower(22.0, GROUND, size = 0.9, snapAt = 22.0)
        hazards += O.rootRise(33.0, GROUND, height = 1.25, upAt = 33.0)
        // 13-16%: the pod. Quiet, then the whole thorn structure - and it swells
        // where the player can see it first.
        // A ninth of a pod smaller than the ones later in the level. At full
        // size this take-off measured 0.179s at 11.85% of the level, which is
        // under the 0.18 the ladder holds every opening to - by four
        // thousandths of a second and a tenth of a percent, which is exactly
        // the kind of miss that only a test catches.
        hazards += O.thornPod(44.0, GROUND, size = 0.9, burstAt = 44.0)
        // 18-26%: the flower pair, out of step. This is the level's idea.
        hazards += O.flowerPair(62.0, GROUND, apart = 9.0)
        hazards += O.rootRise(74.0, GROUND, upAt = 74.0)
        hazards += O.vineSweep(84.0, GROUND, nearAt = 84.0)
        // 30-40%: the bloom, alone the first time, then a pod after it.
        hazards += O.carnivorousBloom(106.0, GROUND, openAt = 106.0)
        hazards += O.thornPod(118.0, GROUND, burstAt = 118.0)
        hazards += O.vineCurtain(124.0, 130.0, GROUND, tip = 1.4)
        hazards += O.snapFlower(136.0, GROUND, snapAt = 136.0)
        // 46-53%: past the boost. Bloom, vine, flower.
        hazards += O.carnivorousBloom(160.0, GROUND, openAt = 160.0)
        hazards += O.vineSweep(172.0, GROUND, nearAt = 172.0)
        hazards += O.snapFlower(182.0, GROUND, snapAt = 182.0)
        // 60-67%: the lower garden. A pair again, tighter.
        hazards += O.flowerPair(206.0, LOW, apart = 8.0)
        hazards += O.rootRise(220.0, LOW, upAt = 220.0)
        hazards += O.thornPod(230.0, LOW, burstAt = 230.0)
        // 73-81%: blooms in a chain, each opening after the last.
        hazards += O.bloomChain(254.0, LOW, count = 2, spacing = 9.0)
        hazards += O.vineSweep(276.0, LOW, nearAt = 276.0)
        // 89-97%: a last bloom, and the slot.
        hazards += O.carnivorousBloom(306.0, LOW, openAt = 306.0)
        hazards += O.fallenSeed(316.0, LOW)
        hazards += O.fallenSeed(326.0, LOW)
        hazards += O.fallenSeed(330.16, LOW)
        hazards += O.fallenSeed(334.32, LOW)

        val stars = listOf(
            Star(52.0, 4.4),
            Star(200.0, LOW + 4.4),
            Star(298.0, LOW + 4.4),
        )

        return Level(
            id = 26, name = "BLOOM", subtitle = "MORE THAN ONE CLOCK",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
