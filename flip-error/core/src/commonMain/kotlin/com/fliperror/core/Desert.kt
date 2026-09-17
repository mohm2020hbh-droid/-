package com.fliperror.core

/**
 * WORLD 2's vocabulary - the ten things the NEON DESERT is built out of.
 *
 * World 1 was a city of still ledges and things that slid along them. The
 * desert's premise is the opposite: almost nothing here holds still, so the
 * question stops being WHEN to jump and becomes WHERE the ground will be when
 * you land. That is a different verb, and it needs a different kit.
 *
 * Three rules hold every piece of it together, and all three were paid for:
 *
 *  1. ANYTHING STANDING ON MOVING SAND MOVES WITH IT. A spike pinned at a fixed
 *     height over a floor that breathes 0.9u is a different jump every pass. It
 *     drove LEVEL 7's take-off windows down to a single frame before the rule
 *     existed; now [sandSpike] and [waveOnSand] take their motion FROM the sand.
 *
 *  2. NOTHING WIDE MOVES FAST. A jump covers 4.94u and the runner is 0.9u wide.
 *     A three-unit crest sweeping at half the run speed leaves no manoeuvre at
 *     all - so crests are 1.6u, they sweep over four bars, and their phase is
 *     DERIVED so they are always rolling toward the runner at the moment of the
 *     crossing, which is the half of the cycle that can be read and jumped.
 *
 *  3. NOTHING ARRIVES WITHOUT WARNING. Everything that switches on - geysers,
 *     beams, falling ruins - spends the moments before it becomes lethal
 *     visibly charging ([Blink.warmAt]), and every floor that leaves fades
 *     first ([Blink.strengthAt]). A beam that simply appears is a coin flip.
 *
 * Every helper takes the level's own BPM, because the desert's geometry is laid
 * out on its own beat grid the way world 1's was on its.
 */
class Desert(val bpm: Double) {

    val beatUnits = Tuning.RUN_SPEED * 60.0 / bpm
    val barSeconds = 4.0 * 60.0 / bpm
    fun beat(n: Double) = n * beatUnits

    /** When the runner reaches [x]. The one fact the whole desert is built on. */
    private fun timeAt(x: Double) = x / Tuning.RUN_SPEED

    /**
     * The phase that puts a cycle at position [at] when the runner reaches [x].
     *
     * Offsets are sines, so [at] reads as: 0.0 at rest and moving out, 0.25 at
     * the far end, 0.5 at rest and coming back, 0.75 at the near end. A block
     * that should be waiting under the landing takes 0.75; a crest that should
     * be rolling at the runner takes 0.5.
     *
     * Everything in this world derives its phase this way rather than choosing
     * one. x advances at a fixed speed, so "where is it when they get here" is
     * arithmetic, and a hand-picked phase is a guess at that arithmetic - the
     * first guess in this game made a level literally unbeatable.
     */
    fun phaseAt(x: Double, at: Double, bars: Double): Double {
        val u = at - timeAt(x) / (barSeconds * bars)
        return ((u % 1.0) + 1.0) % 1.0
    }

    // --- 1. SAND WAVE ----------------------------------------------------
    //
    // A crest of rolling sand, low and narrow, sweeping along the floor. Its
    // phase is not a parameter: it is solved for, so that at the moment the
    // runner meets it the crest is at the centre of its sweep and rolling back
    // TOWARD them. A crest running away travels with the runner and has to be
    // chased across, which is the crossing that costs the most airtime; a crest
    // coming at you is over in half the time, and it is the one a player can see
    // coming.

    val waveWidth = 1.6
    val wavePeriod get() = barSeconds * 4.0

    private fun wavePhase(x: Double) = 0.5 - timeAt(x + waveWidth / 2.0) / wavePeriod

    fun sandWave(x: Double, y: Double, reach: Double) =
        Hazard(HazardKind.SPIKE_UP, x, x + waveWidth, y, y + 0.7,
            Motion(dx = reach, period = wavePeriod, phase = wavePhase(x)), look = Look.SAND_WAVE)

    // --- 2. SHIFTING SAND ------------------------------------------------
    //
    // A stretch of floor breathing up and down, carrying whoever stands on it.
    // Shallow and slow on purpose: a deep fast breath changes the height it
    // hands you faster than a jump takes, and there is no window left in that.

    fun shiftingSand(x0: Double, x1: Double, base: Double, rise: Double,
                     phase: Double = 0.0, bars: Double = 2.0) =
        Solid(x0, x1, base, -40.0, Motion(dy = rise, period = barSeconds * bars, phase = phase),
            surface = Surface.SAND)

    /**
     * A spike that belongs to the sand it stands on - rule 1.
     *
     * The honest way to call this is [riding], handing it the very Motion of
     * the Solid it is standing on, so the two cannot drift apart however either
     * is edited later. The rise/phase/bars form is the same thing spelled out,
     * and is only for a spike whose sand is described inline.
     */
    fun sandSpike(x: Double, base: Double, riding: Motion) =
        Hazard(HazardKind.SPIKE_UP, x, x + 1.0, base, base + 1.0, riding)

    fun sandSpike(x: Double, base: Double, rise: Double,
                  phase: Double = 0.0, bars: Double = 2.0) =
        sandSpike(x, base, Motion(dy = rise, period = barSeconds * bars, phase = phase))

    /** Both words at once: a crest rolling along sand that is breathing under it.
     *  phaseY is set so the vertical curves coincide exactly, so the crest never
     *  leaves the surface however the sand moves. */
    fun waveOnSand(x: Double, base: Double, reach: Double, rise: Double, sandPhase: Double): Hazard {
        val phase = wavePhase(x)
        return Hazard(HazardKind.SPIKE_UP, x, x + waveWidth, base, base + 0.7,
            Motion(dx = reach, dy = rise, period = wavePeriod, phase = phase,
                phaseY = sandPhase - phase), look = Look.SAND_WAVE)
    }

    // --- 3. FALLING RUINS ------------------------------------------------
    //
    // A block of the old temple that drops through the lane on the bar and is
    // scenery the rest of the time.
    //
    // It is 1.3u tall on purpose. The first version was a 2.4u slab, which a
    // 2.6u jump clears by two tenths of a unit once the hitbox shrink is counted
    // - and a two-tenths margin is not an obstacle, it is a frame-perfect duck.
    // Rubble you can jump is rubble the player can answer. What makes it a ruin
    // rather than a spike is that it is only there for part of every cycle, and
    // it says so: it charges where the player can see it before it lands.
    //
    // [downAt] is the x it should be lying across when the runner gets there, and
    // the phase is solved from it rather than chosen.

    fun fallingRuin(x: Double, base: Double, height: Double = 1.3, bars: Double = 2.0,
                    lethalFor: Double = 0.40, downAt: Double = Double.NaN,
                    riding: Motion? = null): Hazard {
        val period = barSeconds * bars
        val phase = if (downAt.isNaN()) 0.0 else solveDown(downAt, period, lethalFor)
        return Hazard(HazardKind.SPIKE_UP, x, x + 1.6, base, base + height,
            motion = riding, blink = Blink(period, lethalFor, phase), look = Look.RUIN)
    }

    // --- 4. ROTATING RELIC -----------------------------------------------
    //
    // The same two numbers as everything else, a quarter turn apart: equal
    // reach on both axes with phaseY = 0.25 is a circle. It is the one desert
    // obstacle with no straight line in it, which is exactly why it is legible -
    // a circle tells you where it will be next from any point on it.

    fun rotatingRelic(x: Double, y: Double, radius: Double, bars: Double = 2.0,
                      phase: Double = 0.0) =
        Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0,
            Motion(dx = radius, dy = radius, period = barSeconds * bars,
                phase = phase, phaseY = 0.25), look = Look.RELIC)

    // --- 5. SAND GEYSER --------------------------------------------------
    //
    // A column of sand that erupts out of the floor on the bar. It says JUMP,
    // and it says it only for part of every cycle.
    //
    // 1.6u tall, so it is a jump and not a prayer. The desert's other timed
    // hazard, the beam below, says the opposite thing - and the two of them
    // together are the whole point of world 2's back half: one obstacle demands
    // the air, the other forbids it, and the player has to find the moment that
    // satisfies both. That moment always exists, and the verifier proves it.
    //
    // And it is one unit wide, like a spike. It was 1.2 for no better reason
    // than that a column of sand felt like it should be fatter, and that fifth
    // of a unit came straight out of the manoeuvre: a jump covers 4.94u and the
    // runner is 0.9u of it, so every extra tenth of obstacle is a tenth off the
    // take-off window. It cost LEVEL 9 its opening - 0.142s where the first
    // eighth of a level should not go under 0.18s - and it was buying nothing.

    fun sandGeyser(x: Double, base: Double, height: Double = 1.6, bars: Double = 2.0,
                   lethalFor: Double = 0.40, upAt: Double = Double.NaN,
                   riding: Motion? = null): Hazard {
        val period = barSeconds * bars
        val phase = if (upAt.isNaN()) 0.0 else solveDown(upAt, period, lethalFor)
        return Hazard(HazardKind.SPIKE_UP, x, x + 1.0, base, base + height,
            motion = riding, blink = Blink(period, lethalFor, phase), look = Look.GEYSER)
    }

    // --- 6. MOVING TEMPLE BLOCK ------------------------------------------
    //
    // A landable block sliding along its own track. It does NOT carry the runner
    // - see Solid - so the runner's x stays exactly RUN_SPEED * time and every
    // proof this game makes about itself survives. What it changes is whether
    // there is anything under the landing, which is enough.

    fun templeBlock(x0: Double, x1: Double, top: Double, reach: Double,
                    bars: Double = 2.0, phase: Double = 0.0) =
        Solid(x0, x1, top, -40.0, Motion(dx = reach, period = barSeconds * bars, phase = phase),
            surface = Surface.TEMPLE)

    /** A block that rides up and down its shaft, and carries whoever is on it. */
    fun templeLift(x0: Double, x1: Double, top: Double, rise: Double,
                   bars: Double = 2.0, phase: Double = 0.0) =
        Solid(x0, x1, top, -40.0, Motion(dy = rise, period = barSeconds * bars, phase = phase),
            surface = Surface.TEMPLE)

    // --- 7. SUN LASER ----------------------------------------------------
    //
    // A beam from the sun, hanging down into the lane from above. It says DO NOT
    // JUMP, for as long as it burns.
    //
    // It stops [tip] above the floor, so running under it is always safe and
    // jumping into it never is. That is what makes it an obstacle at all in a
    // game where the runner cannot stop: a beam across the whole lane would be a
    // wall the player has no answer to, and a deterministic wall is not
    // difficulty, it is a locked door. This one takes something away - the air -
    // and gives it back on a cycle the player can see coming, because it spends
    // the moments before it fires visibly charging.

    fun sunLaser(x: Double, tip: Double, top: Double = 9.0, bars: Double = 2.0,
                 lethalFor: Double = 0.34, onAt: Double = Double.NaN): Hazard {
        val period = barSeconds * bars
        val phase = if (onAt.isNaN()) 0.0 else solveDown(onAt, period, lethalFor)
        return Hazard(HazardKind.SPIKE_DOWN, x, x + 1.2, tip, top,
            blink = Blink(period, lethalFor, phase), look = Look.LASER)
    }

    // --- 8. COLLAPSING BRIDGE --------------------------------------------
    //
    // A span that is only floor for part of every cycle, and fades before it
    // goes. Its phase is DERIVED from the x it is landed on: x advances at a
    // fixed speed, so the moment the runner arrives is a property of where the
    // bridge is, and a phase picked by hand is a guess about arithmetic. The
    // first hand-picked phase in this game made a level unbeatable.
    //
    // [spent] is how much of the ON window is already gone when they arrive, so
    // the span is always visibly on its way out under their feet.

    fun collapsingBridge(x0: Double, x1: Double, top: Double, arriveAt: Double,
                         bars: Double = 2.0, on: Double = 0.60, spent: Double = 0.05) =
        Solid(x0, x1, top, -40.0, null,
            Blink(barSeconds * bars, on, solveArrival(arriveAt, barSeconds * bars, spent)),
            Surface.BRIDGE)

    /**
     * 10. MIRAGE PLATFORM. A bridge that shimmers instead of fading - the same
     * honest machinery underneath, dressed as something that might not be there.
     * It is deliberately NOT a lie: a floor that is simply absent where one is
     * drawn is the random death this game does not do. What makes it a mirage is
     * that it spends most of its cycle gone, so the player has to wait and read
     * it rather than trust it.
     */
    fun miragePlatform(x0: Double, x1: Double, top: Double, arriveAt: Double,
                       bars: Double = 2.0, on: Double = 0.45) =
        Solid(x0, x1, top, -40.0, null,
            Blink(barSeconds * bars, on, solveArrival(arriveAt, barSeconds * bars, 0.04)),
            Surface.MIRAGE)

    // --- 9. WIND BLAST ---------------------------------------------------
    //
    // Vertical only, always. See Wind: sideways air would make x a function of
    // the player's history and the verifier could no longer say whether a level
    // was possible at all.

    fun windBlast(x0: Double, x1: Double, push: Double) = Wind(x0, x1, push)

    // --- the phase solvers -----------------------------------------------

    /** Phase that has a floor switching ON [spent] of its window before the
     *  runner reaches [x]. */
    private fun solveArrival(x: Double, period: Double, spent: Double): Double {
        val u = spent - timeAt(x) / period
        return ((u % 1.0) + 1.0) % 1.0
    }

    /** Phase that has a ruin already down, and staying down, as the runner
     *  reaches [x] - half of its lethal window spent, so it is unambiguously
     *  there rather than arriving or leaving under them. */
    private fun solveDown(x: Double, period: Double, lethalFor: Double): Double {
        val u = lethalFor * 0.5 - timeAt(x) / period
        return ((u % 1.0) + 1.0) % 1.0
    }
}
