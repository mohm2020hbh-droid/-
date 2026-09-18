package com.fliperror.core

/**
 * WORLD 3's vocabulary - the eleven things THE ABYSS is built out of.
 *
 * The city was ledges and spikes. The desert was ground that would not hold
 * still. The abyss is neither: it is WATER, and the things in it are alive or
 * nearly so - bubbles that come at you and come apart, crystals that swing down
 * out of the dark, jellies that open and shut, rings of pressure travelling the
 * lane, arms that rise out of the floor. There is not one spike in this world
 * and there is not meant to be.
 *
 * Everything below is still built from the three verbs the engine has - move,
 * blink, and where the floor is - because the fairness proof rests on the world
 * being a pure function of x, and x is RUN_SPEED * time. Nothing here tracks
 * the player's history, so the solver can prove a level of this density
 * possible at all. That is not a limitation the design works around; it is the
 * reason this world can be as dense as it is and still be honest.
 *
 * Four rules hold the kit together, and three of them were paid for in world 2:
 *
 *  1. NOTHING ARRIVES WITHOUT WARNING. Everything that switches on - tentacles,
 *     jellies - spends the moments before it is lethal visibly charging
 *     ([Blink.warmAt]), and a split bubble fades before it comes apart
 *     ([Blink.strengthAt]). Something that simply appears is a coin flip.
 *
 *  2. EVERY SIZE HERE IS SOLVED, NOT CHOSEN. A jump apexes at 2.6u and covers
 *     4.94u, of which the runner is 0.9u, so the take-off window over a thing
 *     W wide and H tall is (time the arc is above H) minus (W + 0.9) / 9.5. A
 *     1.0 x 1.0 spike measures 0.239s that way and 0.238s in the verifier,
 *     which is what makes the arithmetic worth trusting. It is also what
 *     caught the first draft of this kit: a 1.4 x 2.0 arm is 0.068s, an inch
 *     off frame-perfect, and it looked perfectly reasonable written down. So
 *     the arm is 1.0 x 1.5 and 0.177s, the crystal 1.0 x 1.3 and 0.203s, the
 *     jelly 1.0 and 0.204s, the ring 1.0 x 1.2 and 0.216s. Nothing in this
 *     world is wide AND tall.
 *
 *  3. EVERY PHASE IS DERIVED, never chosen. x advances at a fixed speed, so
 *     "where is this thing when they get here" is arithmetic. The first
 *     hand-picked phase in this project made a level literally unbeatable.
 *
 *  4. DENSITY COMES FROM THE CHEAP THINGS. A hazard costs the lane its own
 *     width plus its whole sweep: a bubble occupies 6.3u of the level, a
 *     crystal 1.2u. So the crowded stretches this world is built on are made of
 *     crystals, jellies, walls and arms, and the sweepers get room. Packing
 *     sweepers is how you write a level that measures dense and plays like a
 *     wall.
 */
class Abyss(val bpm: Double) {

    val beatUnits = Tuning.RUN_SPEED * 60.0 / bpm
    val barSeconds = 4.0 * 60.0 / bpm
    fun beat(n: Double) = n * beatUnits

    /** When the runner reaches [x]. The one fact this whole world is built on. */
    private fun timeAt(x: Double) = x / Tuning.RUN_SPEED

    /**
     * The phase that puts a sine cycle at [at] when the runner reaches [x].
     * 0.0 centre and moving out, 0.25 the far end, 0.5 centre coming back,
     * 0.75 the near end - so 0.75 is where a crystal is lowest and an orb is
     * at the bottom of its circle.
     */
    fun phaseAt(x: Double, at: Double, bars: Double): Double {
        val u = at - timeAt(x) / (barSeconds * bars)
        return ((u % 1.0) + 1.0) % 1.0
    }

    /** The phase that has a blinking hazard LETHAL as the runner reaches [x],
     *  with half its dangerous window spent - unambiguously there, not arriving. */
    private fun onAt(x: Double, period: Double, lethalFor: Double): Double {
        val u = lethalFor * 0.5 - timeAt(x) / period
        return ((u % 1.0) + 1.0) % 1.0
    }

    /** And the phase that has it SHUT as they reach [x] - the moment to run through. */
    private fun quietAt(x: Double, period: Double, lethalFor: Double): Double {
        val u = lethalFor + (1.0 - lethalFor) * 0.5 - timeAt(x) / period
        return ((u % 1.0) + 1.0) % 1.0
    }

    // --- 1. CHASING BUBBLE ---------------------------------------------------
    //
    // A glowing bubble that comes at the runner along the floor and has to be
    // jumped. Its phase is solved, not chosen, so that at the moment of the
    // crossing it is sweeping TOWARD them: a bubble running away travels with
    // the runner and has to be chased across, which is the crossing that costs
    // the most airtime, while one coming at you is over in half the time and is
    // the half of the cycle a person can actually read.
    //
    // [size] is the whole point of the variants. A small bubble is a hop; a
    // large one is 1.9u of obstacle and most of a jump. Both are the same
    // object, which is what makes a chain of mixed sizes a rhythm rather than a
    // list of separate problems.

    private fun chasePeriod(bars: Double) = barSeconds * bars

    fun chasingBubble(x: Double, base: Double, size: Double = 1.4,
                      reach: Double = 2.4, bars: Double = 4.0): Hazard {
        val period = chasePeriod(bars)
        return Hazard(HazardKind.SPIKE_UP, x, x + size, base, base + size * 0.95,
            Motion(dx = reach, period = period,
                phase = 0.5 - timeAt(x + size / 2.0) / period), look = Look.BUBBLE)
    }

    // --- 2. SPLIT BUBBLE -----------------------------------------------------
    //
    // One bubble that stops being there at a moment, and two or three small ones
    // that start being there at the same moment, on their own paths.
    //
    // It is a real split rather than a trick of the art: the parent's blink goes
    // off exactly where the children's come on, all of them derived from the
    // same x, so what the player sees and what the collision does are one event.
    // The parent fades for the last quarter of its life ([Blink.strengthAt]),
    // which is the telegraph - by the time it comes apart the player has been
    // told twice.

    fun splitBubble(x: Double, base: Double, at: Double, pieces: Int = 3,
                    bars: Double = 4.0): List<Hazard> {
        val period = barSeconds * bars
        val split = timeAt(at)
        val parentOn = (((split / period) % 1.0) + 1.0) % 1.0
        val on = parentOn.coerceIn(0.06, 0.94)
        val out = ArrayList<Hazard>()
        out += Hazard(HazardKind.SPIKE_UP, x, x + 1.8, base, base + 1.4,
            motion = Motion(dx = 2.0, period = period, phase = 0.5 - timeAt(x) / period),
            blink = Blink(period, on, 0.0), look = Look.BUBBLE)
        // Every child sits ON THE FLOOR. Stacking them upward by half a unit each
        // read well and played badly: the top one needed a second tap to clear,
        // which turns a reading obstacle into a boost obstacle, and the split is
        // meant to be the former.
        for (k in 0 until pieces) {
            val spread = (k - (pieces - 1) / 2.0) * 1.9
            out += Hazard(HazardKind.SPIKE_UP, at + spread, at + spread + 0.9, base, base + 0.9,
                motion = Motion(dx = 1.1 + 0.4 * k, dy = 0.5, period = period * 0.5,
                    phase = phaseAt(at, 0.75, bars * 0.5), phaseY = 0.25),
                blink = Blink(period, 1.0 - on, on), look = Look.BUBBLE)
        }
        return out
    }

    // --- 3. BUBBLE CHAIN -----------------------------------------------------
    //
    // Bubbles in sequence, each a little different from the last - bigger,
    // slower, sitting a little higher - so the answer is a rhythm the player
    // settles into rather than four separate readings.
    //
    // [spacing] is the only number in this kit with a hard floor under it. Each
    // bubble occupies its own width plus its sweep, and the runner needs strip
    // to land on and take off from between them; under about six units the
    // chain stops being a rhythm and becomes one continuous obstacle with no
    // ground in it. The chains get tighter through the world, never shorter
    // than that.

    fun bubbleChain(x: Double, base: Double, count: Int = 3, spacing: Double = 7.0,
                    size: Double = 1.2, grow: Double = 0.16, bars: Double = 4.0): List<Hazard> {
        val out = ArrayList<Hazard>()
        for (k in 0 until count) {
            val bx = x + k * spacing
            // Reach shrinks as size grows, so no bubble in a chain ever occupies
            // more of the lane than the first one did - rule 2, per link.
            val s = size + k * grow
            out += chasingBubble(bx, base, size = s, reach = 1.6 - 0.18 * k, bars = bars)
        }
        return out
    }

    // --- 4. BUBBLE WALL ------------------------------------------------------
    //
    // A formation of bubbles filling the lane floor to ceiling, with ONE opening
    // in it, and the opening is drawn as plainly as the wall is.
    //
    // This is the obstacle world 3 exists for. Everything up to here is answered
    // by being in the air or not being in the air; a wall with a gap at head
    // height is answered by being at a PARTICULAR height, which means the jump
    // has to start in the right place rather than merely at the right time. It
    // also makes the second tap dangerous for the first time in the game: a
    // double jump through this opening puts the runner into the bubbles above it.
    //
    // The geometry is checked, not hoped for. A jump apexes at 2.6u, so with the
    // opening's floor at 1.7 the runner's box is inside the gap from 0.86u after
    // take-off until 3.77u after it - a 2.9u window, of which the wall's own
    // width and the runner's own width take about 1.7. What is left is the ask,
    // and it is about a tenth of a second wide: hard, and nothing like frame
    // perfect. Narrowing [openBottom]/[openTop] is how the later levels spend it.

    fun bubbleWall(x: Double, base: Double, openBottom: Double = 1.7,
                   openTop: Double = 4.0, top: Double = 8.5,
                   width: Double = 0.9): List<Hazard> {
        val out = ArrayList<Hazard>()
        var y = base
        while (y < base + openBottom - 0.01) {
            val h = minOf(1.0, base + openBottom - y)
            out += Hazard(HazardKind.SPIKE_UP, x, x + width, y, y + h, look = Look.BUBBLE)
            y += h
        }
        y = base + openTop
        while (y < top - 0.01) {
            val h = minOf(1.0, top - y)
            out += Hazard(HazardKind.SPIKE_DOWN, x, x + width, y, y + h, look = Look.BUBBLE)
            y += h
        }
        return out
    }

    /**
     * The same formation with its opening on the floor: bubbles from head height
     * up, and clear ground underneath. The answer is to NOT jump, which is the
     * one input no other world has ever asked a FLIP ERROR player to withhold.
     */
    fun bubbleCeiling(x: Double, tip: Double, top: Double = 8.5,
                      width: Double = 0.9): List<Hazard> {
        val out = ArrayList<Hazard>()
        var y = tip
        while (y < top - 0.01) {
            val h = minOf(1.0, top - y)
            out += Hazard(HazardKind.SPIKE_DOWN, x, x + width, y, y + h, look = Look.BUBBLE)
            y += h
        }
        return out
    }

    // --- 5. RISING WAVE ------------------------------------------------------
    //
    // A swell of water travelling the lane. Low, wide and slow, and like the
    // bubble its phase is solved so it is always coming at the runner rather
    // than fleeing them.

    val waveWidth = 1.7

    fun risingWave(x: Double, base: Double, reach: Double = 2.4,
                   bars: Double = 4.0, height: Double = 0.95): Hazard {
        val period = barSeconds * bars
        return Hazard(HazardKind.SPIKE_UP, x, x + waveWidth, base, base + height,
            Motion(dx = reach, period = period,
                phase = 0.5 - timeAt(x + waveWidth / 2.0) / period), look = Look.WAVE)
    }

    // --- 6. ABYSS ORB --------------------------------------------------------
    //
    // The biggest single thing in the water, and the one with no straight line
    // in it. Equal reach on both axes a quarter turn apart IS a circle, and a
    // circle tells you where it goes next from any point on it - which is why
    // the largest hazard in the game is also one of the most readable.
    //
    // The column and the diagonal are the same object with one number changed,
    // and they are here because a world whose orbs all orbit teaches the player
    // one pattern and then repeats it.

    fun abyssOrb(x: Double, y: Double, radius: Double = 2.2, bars: Double = 3.0,
                 phase: Double = 0.0) =
        Hazard(HazardKind.SPIKE_UP, x, x + 1.8, y, y + 1.8,
            Motion(dx = radius, dy = radius, period = barSeconds * bars,
                phase = phase, phaseY = 0.25), look = Look.ORB)

    /**
     * Straight up and down its own shaft. Anchored the way the crystal is: the
     * resting box sits a full [rise] above [base], so the bottom of the shaft is
     * exactly the lane and the top of it is clear over the runner's head.
     * [lowAt] is the x it should be at the bottom for.
     */
    fun orbColumn(x: Double, base: Double, rise: Double = 2.2, bars: Double = 2.0,
                  lowAt: Double = Double.NaN) =
        Hazard(HazardKind.SPIKE_UP, x, x + 1.5, base + rise, base + rise + 1.5,
            Motion(dy = rise, period = barSeconds * bars,
                phase = if (lowAt.isNaN()) 0.75 else phaseAt(lowAt, 0.75, bars)),
            look = Look.ORB)

    /** A diagonal: both axes in step, so it runs corner to corner instead of
     *  round. [nearAt] is the x it should be at the near, low end for. */
    fun orbDiagonal(x: Double, y: Double, reach: Double = 2.0, bars: Double = 2.5,
                    nearAt: Double = Double.NaN) =
        Hazard(HazardKind.SPIKE_UP, x, x + 1.5, y, y + 1.5,
            Motion(dx = reach, dy = reach, period = barSeconds * bars,
                phase = if (nearAt.isNaN()) 0.0 else phaseAt(nearAt, 0.75, bars)),
            look = Look.ORB)

    // --- 7. MOVING CRYSTAL ---------------------------------------------------
    //
    // A shard of the abyss hanging in the lane, swinging down into it and back
    // out. It costs the level 1.2u and nothing else - no sweep, no width to
    // speak of - which makes it the cheapest obstacle in the world and therefore
    // the one the crowded stretches are built out of (rule 4).
    //
    // Its resting box is a full [rise] ABOVE where it will hang, so that at the
    // bottom of its swing it is exactly in the lane and at the top it is clear
    // over the runner's head. [lowAt] solves for which of those the player meets.

    fun crystal(x: Double, base: Double, rise: Double = 2.0, height: Double = 1.3,
                bars: Double = 2.0, lowAt: Double = Double.NaN) =
        Hazard(HazardKind.SPIKE_DOWN, x, x + 1.0, base + rise, base + rise + height,
            Motion(dy = rise, period = barSeconds * bars,
                phase = if (lowAt.isNaN()) 0.75 else phaseAt(lowAt, 0.75, bars)),
            look = Look.CRYSTAL)

    /** A crystal on a horizontal track instead of a vertical one. [nearAt] is the
     *  x it should have swung toward when the runner gets there. */
    fun crystalSlider(x: Double, y: Double, reach: Double = 2.0, bars: Double = 2.5,
                      nearAt: Double = Double.NaN) =
        Hazard(HazardKind.SPIKE_UP, x, x + 1.2, y, y + 1.4,
            Motion(dx = reach, period = barSeconds * bars,
                phase = if (nearAt.isNaN()) 0.5 else phaseAt(nearAt, 0.75, bars)),
            look = Look.CRYSTAL)

    // --- 8. JELLY HAZARD -----------------------------------------------------
    //
    // A geometric thing that opens and shuts on the bar, rising and falling
    // while it does. Open is lethal, shut is scenery, and it spends the moments
    // before it opens visibly swelling ([Blink.warmAt]) - rule 1, and the whole
    // difference between this and a random death.
    //
    // [openAt] is the x it should be open for; [shutAt] the x it should be shut
    // for. Both exist because both are obstacles: one says jump, the other says
    // the floor is briefly yours and the next thing is already coming.

    fun jelly(x: Double, base: Double, rise: Double = 0.5, height: Double = 0.8,
              bars: Double = 2.0, lethalFor: Double = 0.42,
              openAt: Double = Double.NaN, shutAt: Double = Double.NaN): Hazard {
        val period = barSeconds * bars
        val phase = when {
            !openAt.isNaN() -> onAt(openAt, period, lethalFor)
            !shutAt.isNaN() -> quietAt(shutAt, period, lethalFor)
            else -> 0.0
        }
        // The box straddles the floor line at rest and the swell lifts it by
        // [rise]. That is not decoration, it is the fix for two separate bugs.
        // Anchored AT the floor, the open jelly's underside sat 0.775u up at the
        // moment of the crossing while the runner's head reached 0.95 - an
        // overlap of five hundredths of a unit, which is a death nobody can see
        // the reason for. And at its first height its top was 1.98u, which a
        // 2.6u jump clears by so little that the take-off window was 0.080s. It
        // straddles, and it is short: solidly in the lane, top at 1.28u, 0.204s.
        return Hazard(HazardKind.SPIKE_UP, x, x + 1.0, base - 0.3, base + height,
            motion = Motion(dy = rise, period = period, phase = phase),
            blink = Blink(period, lethalFor, phase), look = Look.JELLY)
    }

    // --- 9. PRESSURE RING ----------------------------------------------------
    //
    // A ring of compressed water travelling the lane at the runner. Narrow and
    // quick where the bubble is wide and slow, so the two of them read as
    // different problems even though the engine sees one verb.

    fun pressureRing(x: Double, base: Double, reach: Double = 3.0,
                     bars: Double = 2.0, height: Double = 1.2): Hazard {
        val period = barSeconds * bars
        return Hazard(HazardKind.SPIKE_UP, x, x + 1.0, base, base + height,
            Motion(dx = reach, period = period, phase = 0.5 - timeAt(x + 0.5) / period),
            look = Look.RING)
    }

    /** Rings in a row. They are 1.0u wide and sweep 3.0u, so they get more room
     *  than their width suggests and less than a bubble needs. */
    fun ringChain(x: Double, base: Double, count: Int = 3, spacing: Double = 8.0,
                  reach: Double = 2.4, bars: Double = 2.0): List<Hazard> =
        (0 until count).map { k -> pressureRing(x + k * spacing, base, reach = reach, bars = bars) }

    // --- 10. TENTACLE --------------------------------------------------------
    //
    // An arm that comes up out of the floor on a pattern and goes back down.
    // Two units tall, which a 2.6u jump clears, and it swells where the player
    // can see it for a quarter of a second before it is lethal.
    //
    // The first version of this world had the arm SWEEPING sideways instead, and
    // that was a mistake worth recording: 1.8u of arm plus 2.6u of reach each
    // way is seven units of obstacle in front of a 4.94u jump, which is not a
    // hard jump, it is no jump at all. Coming straight up it costs the lane 1.4u
    // and can be placed anywhere.

    fun tentacle(x: Double, base: Double, height: Double = 1.5, bars: Double = 2.0,
                 lethalFor: Double = 0.40, upAt: Double = Double.NaN,
                 downAt: Double = Double.NaN): Hazard {
        val period = barSeconds * bars
        val phase = when {
            !upAt.isNaN() -> onAt(upAt, period, lethalFor)
            !downAt.isNaN() -> quietAt(downAt, period, lethalFor)
            else -> 0.0
        }
        return Hazard(HazardKind.SPIKE_UP, x, x + 1.0, base, base + height,
            blink = Blink(period, lethalFor, phase), look = Look.TENTACLE)
    }

    // --- 11. FLOATING ABYSS OBJECTS ------------------------------------------
    //
    // Small alien things drifting on paths that are neither a circle nor a line.
    // An eighth of a turn between the axes gives a flattened, tilted loop - the
    // one shape in this world that does not resolve into something familiar,
    // which is the job: they are here to make a timing awkward rather than to
    // be the obstacle the section is about.

    fun floater(x: Double, y: Double, drift: Double = 1.5, rise: Double = 1.0,
                bars: Double = 3.0, phase: Double = 0.0) =
        Hazard(HazardKind.SPIKE_UP, x, x + 0.9, y, y + 0.9,
            Motion(dx = drift, dy = rise, period = barSeconds * bars,
                phase = phase, phaseY = 0.125), look = Look.SHARD)

    /** A drift of them, each a little out of step with the last. */
    fun floaters(x: Double, y: Double, count: Int = 3, spacing: Double = 2.6,
                 bars: Double = 3.0): List<Hazard> =
        (0 until count).map { k ->
            floater(x + k * spacing, y + (k % 2) * 0.8, bars = bars, phase = 0.21 * k)
        }

    /**
     * A bubble holding station in the lane - the one thing in this world that
     * does not move at all.
     *
     * It exists for one job: a finish. Every level's tightest moment is its last
     * few seconds, and that moment has to measure the same from one run to the
     * next or the number the whole ladder is built on means nothing. Three of
     * these in a row, a jump apart, is the slot each level ends on - known
     * geometry, no phase, no sweep, and the only place in world 3 where the
     * answer is pure reflex rather than reading.
     */
    fun stillBubble(x: Double, base: Double, size: Double = 1.0) =
        Hazard(HazardKind.SPIKE_UP, x, x + size, base, base + size, look = Look.BUBBLE)

    /**
     * Floor made of bubbles. It holds - nothing here bursts - and it is the
     * world's way of saying that even the ground is water. Geometry identical to
     * a stone ledge; only the skin changes.
     */
    fun bubbleFloor(x0: Double, x1: Double, top: Double) =
        Solid(x0, x1, top, -40.0, surface = Surface.BUBBLE)
}
