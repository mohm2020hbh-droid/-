package com.fliperror.core

/**
 * LEVEL 8 - "FALLING TEMPLE"  (WORLD 2)
 *
 * The ruins the sand has been burying for a thousand years, coming down while
 * the player runs through them. Two new words: blocks of masonry that DROP
 * through the lane on the bar, and temple stone that is itself in motion - a
 * slab sliding along its track, a lift riding its shaft.
 *
 * The difference from world 1's movers is what the runner is asked to do with
 * them. A slider in the city was something to jump over. A temple block is
 * something to jump ONTO, which means reading where it will be in half a second
 * rather than where it is now. Its phase is derived so it is always at the near
 * end of its travel as the runner arrives - the desert never moves the landing
 * away from you, it only makes you see that it moved.
 */
object Level8 {

    const val BPM = 172.0
    private val D = Desert(BPM)

    private const val GROUND = 0.0
    private const val MID = -1.8
    private const val LOW = -3.6

    private fun spike(x: Double, y: Double) = Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0)

    val finishX = D.beat(104.0)                  // 344.7 units -> 36.3 seconds

    fun build(): Level {
        val solids = listOf(
            Solid(-14.0, 52.0, GROUND),                  // the opening, on solid stone
            // a slab sliding on its track, waiting at the near end of it as you land
            D.templeBlock(56.2, 68.0, GROUND, 2.0, phase = D.phaseAt(56.2, 0.75, 2.0)),
            Solid(72.2, 104.0, GROUND),                  // gap 4.20u
            D.templeLift(108.2, 122.0, GROUND, 1.6, phase = D.phaseAt(108.2, 0.75, 2.0)),
            Solid(126.2, 158.0, GROUND),                 // gap 4.20u
            Solid(164.2, 196.0, GROUND),                 // gap 6.20u - the boost
            D.templeBlock(200.2, 214.0, MID, 2.2, phase = D.phaseAt(200.2, 0.75, 2.0)),
            Solid(218.2, 252.0, MID),                    // gap 4.20u
            D.templeLift(256.2, 270.0, LOW, 1.8, bars = 2.5, phase = D.phaseAt(256.2, 0.75, 2.5)),
            Solid(274.2, 352.0, LOW),                    // gap 4.20u into the finish
        )

        val hazards = ArrayList<Hazard>()
        // 0-15%: the masonry starts coming down, on stone that is not moving, so
        // the only new thing to read is the drop itself.
        hazards += spike(13.0, GROUND)
        hazards += spike(26.0, GROUND)
        hazards += spike(38.0, GROUND)
        // The first ruin lands at 46, which is 13% in. It used to come down at 26
        // and it cost the level its opening: a 0.146s take-off inside the first
        // eighth, which is an ambush, not an introduction. A level gets to be as
        // hard as it likes once the player has had a moment to look at it.
        hazards += D.fallingRuin(46.0, GROUND, downAt = 46.8)
        // 20-30%: a crest on the stone between the two movers.
        hazards += D.sandWave(80.0, GROUND, 2.2)
        hazards += spike(94.0, GROUND)
        // 36-46%: ruins over the run-up to the boost gap.
        hazards += D.fallingRuin(132.0, GROUND, downAt = 132.8)
        hazards += spike(146.0, GROUND)
        hazards += D.sandWave(172.0, GROUND, 2.4)
        hazards += spike(186.0, GROUND)
        // 63-73%: down on the mid shelf, a ruin and a crest together.
        hazards += D.fallingRuin(226.0, MID, downAt = 226.8)
        hazards += D.sandWave(238.0, MID, 2.2)
        // 80-88%: off the lift and onto the last floor.
        hazards += spike(282.0, LOW)
        hazards += D.sandWave(292.0, LOW, 2.0)
        // 90-100%: the finish - a three-spike stutter on still stone.
        hazards += spike(306.0, LOW)
        hazards += D.fallingRuin(316.0, LOW, downAt = 316.8)
        hazards += spike(328.0, LOW)
        hazards += spike(332.2, LOW)
        hazards += spike(336.4, LOW)

        val stars = listOf(
            Star(18.0, 2.5),          // the opening stone: one honest jump, taken
                                      // where the line is running and not jumping
            Star(106.0, 4.4),         // over a 4.20u gap the line crosses with ONE
                                      // tap - the coin's price is a boost the
                                      // crossing never needed. Hung over the boost
                                      // gap instead, it sat directly on the arc the
                                      // line already flies, which is a free coin.
            Star(266.0, -0.6),        // out on the lift, at the top of its travel
        )

        return Level(
            id = 8, name = "FALLING TEMPLE", subtitle = "IT IS COMING DOWN",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
