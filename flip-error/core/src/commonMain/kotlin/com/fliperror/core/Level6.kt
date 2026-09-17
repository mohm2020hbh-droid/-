package com.fliperror.core

/**
 * LEVEL 6 - "SYSTEM CRASH"  (WORLD 1, FINALE)
 *
 * The city's last level, and the tightest the game is ever allowed to be. Every
 * word world 1 taught is spoken here at 192: sliders, pistons, a lid over the
 * run-up to a boost, and two blinking platforms that are only ground for part of
 * every bar.
 *
 * It does not introduce anything. A finale that teaches is a finale that stalls
 * - this one just asks whether the player learned the first five levels, and it
 * asks in the last twenty units, where a three-spike stutter leaves the tightest
 * landing slot in the game.
 */
object Level6 {

    const val BPM = 192.0
    private val BEAT = Tuning.RUN_SPEED * 60.0 / BPM        // 2.9688 u
    private val BAR_S = 4.0 * 60.0 / BPM                    // 1.2500 s
    private fun beat(n: Double) = n * BEAT

    private const val GROUND = 0.0
    private const val MID = -1.8
    private const val LOW = -3.6

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

    private fun blinker(x0: Double, x1: Double, top: Double, phase: Double, on: Double = 0.60) =
        Solid(x0, x1, top, -40.0, null, Blink(BAR_S * 2.0, on, phase))

    /**
     * A blinker's phase is derived, never chosen.
     *
     * x advances at a fixed speed, so the moment the runner lands on a platform
     * is a property of where that platform is - which makes a hand-picked phase
     * a guess about arithmetic. The first guess here had both blinkers dark at
     * the exact instant the runner arrived, and the level was unbeatable.
     *
     * Given the x the floor is landed on, this returns the phase that switches
     * it on [spent] of an ON window before the runner gets there, so the floor is
     * already visibly on its way out when they land and they cannot dawdle.
     */
    private fun blinkPhaseAt(x: Double, spent: Double = 0.05): Double {
        val u = spent - (x / Tuning.RUN_SPEED) / (BAR_S * 2.0)
        return ((u % 1.0) + 1.0) % 1.0
    }

    val finishX = beat(116.0)                    // 344.4 units -> 36.3 seconds

    fun build(): Level {
        val solids = listOf(
            Solid(-14.0, 58.0, GROUND),           // the opening, and the last wide floor
            Solid(62.3, 96.0, GROUND),            // gap 4.30u
            Solid(102.8, 132.0, GROUND),          // gap 6.80u - the first boost
            blinker(136.3, 148.0, GROUND, blinkPhaseAt(136.6)),  // gap 4.30u onto a floor that leaves
            Solid(152.3, 186.0, GROUND),          // gap 4.30u
            Solid(190.5, 214.0, MID),             // gap 4.20u, and down
            blinker(218.3, 230.0, MID, blinkPhaseAt(218.6)),     // gap 4.30u, and it leaves sooner
            Solid(234.3, 274.0, LOW),             // gap 4.30u, down again
            Solid(280.8, 308.0, LOW),             // gap 6.80u - the second boost
            Solid(312.4, 352.0, LOW),             // gap 4.40u into the finish
        )

        val hazards = ArrayList<Hazard>()
        // 0-15%: no teaching. The finale opens at the speed it ends at.
        hazards += spike(11.0, GROUND)
        hazards += slider(19.5, GROUND, 1.8)
        hazards += spike(29.0, GROUND)
        hazards += piston(38.0, GROUND - 0.9, 1.4)
        hazards += spike(47.0, GROUND)
        // 15-40%: the first boost gap, guarded on both sides.
        hazards += spike(70.0, GROUND)
        hazards += slider(80.0, GROUND, 2.2, phase = 0.5)
        hazards += spike(90.0, GROUND)
        hazards += spike(112.0, GROUND)
        hazards += slider(122.0, GROUND, 1.8, phase = 0.25)
        // 40-60%: the blinkers, each with something waiting on the far side.
        hazards += spike(160.0, GROUND)
        hazards += piston(172.0, GROUND - 0.9, 1.5)
        hazards += spike(200.0, MID)
        hazards += slider(208.0, MID, 1.6, bars = 0.75)
        // 60-85%: a lid over the run-up to the second boost, so the take-off has
        // one place to happen and the player has to find it while ducking.
        hazards += spike(244.0, LOW)
        hazards += lid(250.0, 266.0, 3.7)
        hazards += spike(256.0, LOW)
        hazards += slider(264.0, LOW, 1.5, bars = 0.5)
        // 85-100%: the finish, and the tightest slot in the game.
        hazards += spike(288.0, LOW)
        hazards += piston(298.0, LOW - 0.9, 1.4, phase = 0.5)
        // 4.14u apart, which is the whole point of this level: a jump covers
        // 4.94u and the runner is 0.9u wide, so the slot between two of these is
        // 3.24u of floor to land 0.9u of player in. Measured, that is a 0.075s
        // window - eighteen frames at 240Hz, four and a half at 60 - and it is
        // the floor the whole game is built on. Nothing in FLIP ERROR, in this
        // world or the desert after it, is allowed to ask for less.
        hazards += spike(330.0, LOW)
        hazards += spike(334.14, LOW)
        hazards += spike(338.28, LOW)

        val stars = listOf(
            Star(52.0, 2.5),          // the opening floor: one honest jump
            Star(188.4, 4.2),         // over a 4.20u gap the line crosses with ONE jump,
                                      // so the coin costs a boost the crossing never needed
            Star(277.4, 4.1),         // out over the deepest pit in world 1
        )

        return Level(
            id = 6, name = "SYSTEM CRASH", subtitle = "EVERYTHING YOU LEARNED",
            bpm = BPM, solids = solids, hazards = hazards, stars = stars, finishX = finishX,
        )
    }
}
