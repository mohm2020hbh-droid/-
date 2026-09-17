package com.fliperror.core

/**
 * LEVEL 12 - "THE SUN CORE"  (WORLD 2, FINALE)
 *
 * The end of the desert, and the densest thing in the game. Every word both
 * worlds know is spoken here, and nothing is introduced - a finale that teaches
 * is a finale that stalls.
 *
 * It sits exactly where LEVEL 6 does, on the 0.075s floor, and it is not allowed
 * to go past it. That is the deliberate shape of this game's curve: reflex stops
 * getting harder after world 1, because there is a limit to what a human hand can
 * be asked for and the whole project is built on not crossing it. What climbs
 * instead is how much the player has to READ - more movers, more things pulsing
 * at once, less still floor - and this level has the most of all of it.
 */
object Level12 {

    const val BPM = 188.0
    private val D = Desert(BPM)

    private const val GROUND = 0.0
    private const val MID = -1.8
    private const val LOW = -3.6

    private fun spike(x: Double, y: Double) = Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0)

    val finishX = D.beat(113.0)                  // 342.6 units -> 36.1 seconds

    fun build(): Level {
        val sandA = D.shiftingSand(48.2, 80.0, GROUND, 0.7, bars = 4.0)
        val sandB = D.shiftingSand(202.2, 236.0, MID, 0.8, phase = 0.5, bars = 4.0)

        val solids = listOf(
            Solid(-14.0, 44.0, GROUND),                                // the last still floor
            sandA,                                                     // gap 4.20u
            D.collapsingBridge(84.2, 100.0, GROUND, arriveAt = 84.6, on = 0.72),  // gap 4.20u
            Solid(104.2, 138.0, GROUND),                               // gap 4.20u
            Solid(144.8, 180.0, MID),                                  // gap 6.80u - the first boost
            D.templeBlock(184.2, 198.0, MID, 2.0, phase = D.phaseAt(184.2, 0.75, 2.0)),
            sandB,                                                     // gap 4.20u
            D.miragePlatform(240.2, 254.0, LOW, arriveAt = 240.6, on = 0.58),  // gap 4.20u, and down
            Solid(258.2, 292.0, LOW),                                  // gap 4.20u
            // 7.40u, because the updraft below is worth about a unit of reach: at
            // 6.80 the core's own wind made the gap crossable on one tap with a
            // 0.008s window, which is the razor this game does not ship.
            Solid(299.4, 352.0, LOW),                                  // gap 7.40u - the last boost
        )

        // The core's own updraft, over the last gap in the desert.
        val winds = listOf(D.windBlast(292.0, 299.4, 16.0))

        val hazards = ArrayList<Hazard>()
        // 0-12%: three spikes. Even a finale gives the player an eighth of a level
        // to look at it - a geyser stood at 20 and opened the game's last level on
        // a 0.163s take-off, which is not difficulty, it is a surprise.
        hazards += spike(10.0, GROUND)
        hazards += spike(20.0, GROUND)
        hazards += spike(32.0, GROUND)
        // 16-23%: breathing sand, with a geyser and a crest both riding it.
        hazards += D.sandSpike(56.0, GROUND, sandA.motion!!)
        hazards += D.sandGeyser(61.0, GROUND, bars = 4.0, upAt = 61.8, riding = sandA.motion)
        hazards += D.waveOnSand(70.0, GROUND, 2.0, 0.7, sandPhase = 0.0)
        // 26-40%: the bridge, then stone: relic, crest, masonry, back to back.
        hazards += spike(90.0, GROUND)
        hazards += D.rotatingRelic(106.0, 1.7, 1.3)
        hazards += D.sandWave(114.0, GROUND, 2.2)
        hazards += D.fallingRuin(124.0, GROUND, downAt = 124.8)
        // 44-51%: the mid shelf. Beam and geyser in the same eight units, which
        // is the argument world 2 has been building to: one forbids the air, the
        // other demands it, and the moment that satisfies both is always there.
        hazards += spike(152.0, MID)
        hazards += D.sunLaser(162.0, MID + 2.3, onAt = 158.0)
        hazards += D.sandGeyser(172.0, MID, upAt = 172.8)
        // 62-70%: the sliding block, then breathing sand with a crest on it.
        hazards += D.sandSpike(212.0, MID, sandB.motion!!)
        hazards += D.waveOnSand(224.0, MID, 2.0, 0.8, sandPhase = 0.5)
        // 72-84%: past the mirage, onto the low shelf: relic, beam, crest.
        hazards += spike(246.0, LOW)
        hazards += D.rotatingRelic(266.0, LOW + 1.7, 1.3)
        hazards += D.sunLaser(276.0, LOW + 2.3, onAt = 272.0)
        hazards += D.sandWave(284.0, LOW, 2.0)
        // 91-100%: the finish. Still stone, three spikes, 4.14u apart - the same
        // slot world 1 ended on, because there is nowhere tighter to go.
        hazards += spike(312.0, LOW)
        hazards += spike(320.0, LOW)
        hazards += spike(324.14, LOW)
        hazards += spike(328.28, LOW)

        val stars = listOf(
            Star(14.0, 2.5),          // the last easy coin in the game
            Star(130.0, 4.4),         // only a second tap reaches this high
            Star(260.0, 0.8),         // and the last coin in the desert, on the shelf
        )

        return Level(
            id = 12, name = "THE SUN CORE", subtitle = "THE END OF THE DESERT",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars,
            finishX = finishX, winds = winds,
        )
    }
}
