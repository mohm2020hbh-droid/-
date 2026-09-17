package com.fliperror.core

/**
 * LEVEL 10 - "DESERT CHAOS"  (WORLD 2)
 *
 * Every word the desert knows, in one run. Breathing sand, rolling crests,
 * erupting geysers, hanging beams, falling masonry, a sliding temple block, and
 * the relics - the one obstacle in the game with no straight line in it, orbiting
 * on a circle because equal reach on both axes a quarter turn apart IS a circle.
 *
 * The circle is not decoration. Everything else here moves back and forth, which
 * means it is always either coming or going; a relic is always doing both, and
 * reading one is the skill this level is named after. It is also the most
 * legible mover in the game once you have it, because a circle tells you where
 * it goes next from any point on it.
 */
object Level10 {

    const val BPM = 180.0
    private val D = Desert(BPM)

    private const val GROUND = 0.0
    private const val MID = -1.8
    private const val LOW = -3.6

    private fun spike(x: Double, y: Double) = Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0)

    val finishX = D.beat(108.0)                  // 342.0 units -> 36.0 seconds

    fun build(): Level {
        val sandA = D.shiftingSand(96.2, 130.0, GROUND, 0.7, bars = 3.0)
        val sandB = D.shiftingSand(238.2, 272.0, MID, 0.8, phase = 0.5, bars = 4.0)

        val solids = listOf(
            Solid(-14.0, 48.0, GROUND),        // the opening
            Solid(52.2, 92.0, GROUND),         // gap 4.20u
            sandA,                             // gap 4.20u, breathing
            Solid(134.2, 172.0, GROUND),       // gap 4.20u, stone for the long one
            // 6.80u, not 6.60u. At 6.60 a plain jump plus the 1.8u drop just barely
            // reached the far lip, so the cheapest line took it with ONE tap and a
            // 0.008s window - a razor pretending to be a decision. Past 6.80 the
            // second tap is the only answer and the take-off relaxes right out.
            Solid(178.8, 216.0, MID),          // gap 6.80u - the first boost, and down
            D.templeBlock(220.2, 234.0, MID, 2.0, phase = D.phaseAt(220.2, 0.75, 2.0)),
            sandB,                             // gap 4.20u, breathing
            Solid(276.2, 302.0, LOW),          // gap 4.20u, and down
            Solid(308.8, 352.0, LOW),          // gap 6.60u - the second boost, into the finish
        )

        val hazards = ArrayList<Hazard>()
        // 0-12%: the relic, introduced on still stone with room around it, and
        // spikes for everything else. A geyser used to stand at 41, which is
        // inside the first eighth, and it opened the level on a 0.163s take-off -
        // an ambush rather than an introduction.
        hazards += spike(11.0, GROUND)
        hazards += D.rotatingRelic(21.0, 1.7, 1.3)
        hazards += spike(32.0, GROUND)
        hazards += spike(41.0, GROUND)
        // 16-27%: crest, geyser, beam, spike - world 2's vocabulary, at speed.
        hazards += D.sandWave(56.0, GROUND, 2.2)
        hazards += D.sandGeyser(63.0, GROUND, upAt = 63.8)
        hazards += spike(70.0, GROUND)
        hazards += D.sunLaser(80.0, 2.2, onAt = 76.0)
        hazards += spike(88.0, GROUND)
        // 30-38%: on the breathing sand, and everything on it breathes with it.
        hazards += D.sandSpike(104.0, GROUND, sandA.motion!!)
        hazards += D.sandGeyser(116.0, GROUND, bars = 3.0, upAt = 116.8, riding = sandA.motion)
        // 41-50%: stone, masonry coming down, and the run-up to the first boost.
        hazards += D.sandWave(140.0, GROUND, 2.4)
        hazards += spike(154.0, GROUND)
        hazards += D.fallingRuin(162.0, GROUND, downAt = 162.8)
        // 55-63%: the mid shelf. A relic over the lane, then a beam after it.
        hazards += spike(188.0, MID)
        hazards += D.rotatingRelic(198.0, MID + 1.7, 1.3)
        hazards += D.sunLaser(208.0, MID + 2.3, onAt = 204.0)
        // 72-80%: onto breathing sand with a crest riding it.
        hazards += D.waveOnSand(246.0, MID, 2.0, 0.8, sandPhase = 0.5)
        hazards += D.sandSpike(262.0, MID, sandB.motion!!)
        // 82-88%: the low shelf, and the run-up to the second boost.
        hazards += spike(282.0, LOW)
        hazards += D.sandWave(290.0, LOW, 2.2)
        // 92-100%: the finish. Still stone, three spikes, no excuses.
        hazards += spike(314.0, LOW)
        hazards += spike(322.0, LOW)
        hazards += spike(326.16, LOW)
        hazards += spike(330.32, LOW)

        val stars = listOf(
            Star(16.0, 2.5),          // one honest jump off still stone
            Star(147.0, 4.4),         // only a second tap reaches this high
            Star(298.0, 0.8),         // and again, on the last shelf
        )

        return Level(
            id = 10, name = "DESERT CHAOS", subtitle = "ALL OF IT, MOVING",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
