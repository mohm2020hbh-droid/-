package com.fliperror.core

/**
 * LEVEL 4 - "TIGHT ROOM"
 *
 * The ceiling comes down. Most of this level is run under a lid low enough that
 * the second jump kills you and high enough that the first one does not, which
 * turns every spike inside a corridor into the same sentence: jump, and do not
 * follow it. After three levels spent learning that the boost is the answer,
 * this one spends its whole length asking the player to hold it back.
 *
 * Two lid heights do the work, and the numbers are not arbitrary. A jump peaks
 * at 2.6 and a boosted jump at 4.6; a ceiling spike kills from tip - 0.875. So a
 * lid at 3.7 is a corridor you may jump in (safe to 2.825) and may never boost
 * in, and a lid at 2.3 is a corridor you may not leave the floor in at all.
 */
object Level4 {

    const val BPM = 174.0
    private val BEAT = Tuning.RUN_SPEED * 60.0 / BPM        // 3.2759 u
    private val BAR_S = 4.0 * 60.0 / BPM                    // 1.3793 s
    private fun beat(n: Double) = n * BEAT

    private const val GROUND = 0.0
    private const val LOW = -2.8

    /** Jump permitted, boost fatal. */
    private const val LID_JUMP = 3.7
    /** Nothing may leave the floor. */
    private const val LID_NONE = 2.3

    private fun spike(x: Double, y: Double) = Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0)

    private fun lid(x0: Double, x1: Double, tip: Double): List<Hazard> {
        val out = ArrayList<Hazard>()
        var x = x0
        while (x < x1 - 0.01) { out += Hazard(HazardKind.SPIKE_DOWN, x, x + 1.0, tip, tip + 1.0); x += 1.0 }
        return out
    }

    private fun slider(x: Double, y: Double, reach: Double, phase: Double = 0.0, bars: Double = 1.0) =
        Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0,
            Motion(dx = reach, period = BAR_S * bars, phase = phase))

    private fun piston(x: Double, y: Double, rise: Double, phase: Double = 0.0, bars: Double = 0.75) =
        Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0,
            Motion(dy = rise, period = BAR_S * bars, phase = phase))

    val finishX = beat(104.0)                    // 340.7 units -> 35.9 seconds

    fun build(): Level {
        val solids = listOf(
            Solid(-14.0, 120.0, GROUND),          // the first room, all one floor
            Solid(124.6, 190.0, GROUND),          // gap 4.60u
            Solid(196.8, 250.0, GROUND),          // gap 6.80u - the level's one boost,
                                                  // taken in the open, between two lids
            // 7.20u, and the floor drops. This is the level's second forced boost:
            // narrowing the slider at 104 gave that crossing back to a single tap,
            // and a level called TIGHT ROOM should still be asking for the second
            // one twice - just somewhere the ask is "reach further", which is what
            // the boost is for, rather than "reach less", which is what it was.
            Solid(257.2, 300.0, LOW),             // gap 7.20u - the second boost
            Solid(304.6, 350.0, LOW),             // gap 4.20u into the finish
        )

        val hazards = ArrayList<Hazard>()
        // 0-10%: the room is introduced empty, then with something in it.
        hazards += spike(16.0, GROUND)
        hazards += lid(22.0, 34.0, LID_JUMP)
        hazards += spike(28.0, GROUND)            // inside the lid: jump, do not boost
        // 10-35%: longer corridors, more inside them.
        hazards += lid(44.0, 64.0, LID_JUMP)
        hazards += spike(50.0, GROUND)
        hazards += spike(59.0, GROUND)
        hazards += lid(72.0, 82.0, LID_NONE)      // a lid you cannot jump under at all
        hazards += spike(92.0, GROUND)
        // Reach 1.3, not 2.0. At 2.0 this slider swept 102 to 107, and the only
        // survivable answer was a second tap in the LAST QUARTER of its legal
        // window - because a late boost travels further than an early one, the
        // first jump's height being kept longer before the boost resets vy.
        // Every earlier tap died on this same slider at the same x, so the level
        // punished the instinctive apex tap and taught nothing by it: 41 frames
        // of identical death and 22 frames of success, with no way to tell them
        // apart. Narrowing the sweep leaves the crossing hard and the second tap
        // a choice rather than a guess.
        hazards += slider(104.0, GROUND, 1.0)
        // 35-55%: a corridor with a mover in it - the hazard moves, the lid does not.
        hazards += lid(132.0, 156.0, LID_JUMP)
        hazards += spike(138.0, GROUND)
        hazards += slider(148.0, GROUND, 1.8, phase = 0.5)
        hazards += spike(168.0, GROUND)
        hazards += piston(180.0, GROUND - 0.9, 1.4)
        // 55-75%: the boost gap sits here, deliberately in open air.
        hazards += spike(208.0, GROUND)
        hazards += lid(218.0, 238.0, LID_JUMP)
        hazards += spike(224.0, GROUND)
        hazards += spike(233.0, GROUND)
        // 75-90%: down a level, and the room gets tighter again.
        hazards += spike(266.0, LOW)
        hazards += lid(274.0, 292.0, LID_JUMP)
        hazards += spike(280.0, LOW)
        hazards += slider(288.0, LOW, 1.6, bars = 0.75)
        // 90-100%: the last room. A lid, two spikes and a slot between them.
        hazards += lid(312.0, 336.0, LID_JUMP)
        hazards += spike(318.0, LOW)
        hazards += spike(327.0, LOW)
        hazards += spike(330.6, LOW)

        val stars = listOf(
            Star(98.0, 2.4),          // one honest jump, in the open between two lids
            Star(122.3, 4.2),         // over the 4.60u gap, which one jump clears:
                                      // the coin is what the boost is spent on
            Star(286.0, 2.2),         // inside a lid, threading it at the top of a jump
        )

        return Level(
            id = 4, name = "TIGHT ROOM", subtitle = "HOLD IT BACK",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
