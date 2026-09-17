package com.fliperror.core

/**
 * LEVEL 11 - "COLLAPSE"  (WORLD 2)
 *
 * The ground itself becomes the hazard. Three spans of temple bridge that are
 * only floor for part of every bar, a mirage that is gone more often than it is
 * there, and two columns of desert air that change what a jump is worth.
 *
 * Every bridge phase is SOLVED, not chosen: x advances at a fixed speed, so the
 * instant the runner lands on a span is arithmetic, and each one is set to
 * switch on a fraction of a window before they arrive - so it is always already
 * fading under their feet. They are never asked whether the floor is there.
 * They are asked how long they are willing to stand on it.
 *
 * The wind is vertical, as all wind in this game is. Sideways air would make x
 * a function of the player's history and every proof this game makes about its
 * own fairness would stop being true.
 */
object Level11 {

    const val BPM = 184.0
    private val D = Desert(BPM)

    private const val GROUND = 0.0
    private const val MID = -1.8
    private const val LOW = -3.6

    private fun spike(x: Double, y: Double) = Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0)

    val finishX = D.beat(111.0)                  // 343.9 units -> 36.2 seconds

    fun build(): Level {
        val solids = listOf(
            Solid(-14.0, 46.0, GROUND),                                  // the opening
            D.collapsingBridge(50.2, 66.0, GROUND, arriveAt = 50.6),     // gap 4.20u
            Solid(70.2, 102.0, GROUND),                                  // gap 4.20u
            D.collapsingBridge(106.2, 122.0, GROUND, arriveAt = 106.6),  // gap 4.20u
            Solid(126.2, 160.0, GROUND),                                 // gap 4.20u
            Solid(166.8, 204.0, MID),                                    // gap 6.80u - boost, in the updraft
            D.miragePlatform(208.2, 222.0, MID, arriveAt = 208.6),       // gap 4.20u
            Solid(226.2, 258.0, MID),                                    // gap 4.20u
            D.collapsingBridge(262.2, 278.0, LOW, arriveAt = 262.6),     // gap 4.20u, and down
            Solid(282.2, 308.0, LOW),                                    // gap 4.20u
            Solid(314.8, 352.0, LOW),                                    // gap 6.80u - the second boost
        )

        // A column of rising air over the first long gap, and a sinking one over
        // a short gap late on. Both change how much a jump is worth without
        // touching how far it reaches.
        val winds = listOf(
            D.windBlast(160.0, 166.8, 18.0),
            D.windBlast(258.0, 262.2, -14.0),
        )

        val hazards = ArrayList<Hazard>()
        // 0-12%: still stone, spikes, and the last quiet floor in the world. A
        // geyser stood at 20 and opened the level on a 0.163s take-off, which is
        // an ambush; its own word can wait until the player has had a look.
        hazards += spike(10.0, GROUND)
        hazards += spike(20.0, GROUND)
        hazards += spike(31.0, GROUND)
        hazards += D.sandWave(38.0, GROUND, 2.0)
        // 16-27%: the first bridge, and the run after it.
        hazards += spike(58.0, GROUND)
        hazards += D.sandWave(76.0, GROUND, 2.2)
        hazards += D.sandGeyser(84.0, GROUND, upAt = 84.8)
        hazards += spike(90.0, GROUND)
        // 32-43%: the second bridge, masonry, and the run-up to the updraft.
        hazards += spike(112.0, GROUND)
        hazards += D.fallingRuin(132.0, GROUND, downAt = 132.8)
        hazards += spike(146.0, GROUND)
        // 51-58%: the mid shelf, with a beam over it.
        hazards += spike(176.0, MID)
        hazards += D.sunLaser(188.0, MID + 2.3, onAt = 184.0)
        hazards += spike(198.0, MID)
        // 67-73%: past the mirage. A relic, then a crest.
        hazards += spike(230.0, MID)
        hazards += D.rotatingRelic(240.0, MID + 1.7, 1.3)
        hazards += D.sandWave(250.0, MID, 2.0)
        // 83-88%: the last bridge is behind them; the low shelf is not.
        hazards += spike(286.0, LOW)
        hazards += D.sandGeyser(296.0, LOW, upAt = 296.8)
        // 92-100%: the finish, on still stone, at the floor of what is fair.
        // Three spikes, not four: a fourth at 318 turned the stutter into
        // something the cheapest line simply boosted over in one arc, and a
        // finish you can fly past is not a finish.
        hazards += spike(322.0, LOW)
        hazards += spike(326.14, LOW)
        hazards += spike(330.28, LOW)

        val stars = listOf(
            Star(14.0, 2.5),          // one honest jump off still stone
            Star(138.0, 4.4),         // only a second tap reaches this high
            Star(290.0, 0.8),         // and again, on the last shelf in the desert
        )

        return Level(
            id = 11, name = "COLLAPSE", subtitle = "THE FLOOR IS LEAVING",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars,
            finishX = finishX, winds = winds,
        )
    }
}
