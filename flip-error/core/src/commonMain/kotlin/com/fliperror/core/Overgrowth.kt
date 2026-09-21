package com.fliperror.core

/**
 * WORLD 5's vocabulary - the eleven things OVERGROWTH is built out of.
 *
 * The city asked WHEN to jump. The desert asked WHERE THE GROUND WOULD BE. The
 * abyss asked WHETHER TO JUMP AT ALL. The machine asked WHAT IS THIS PART DOING
 * RIGHT NOW. The forest asks the last one: WHAT IS THIS THING ABOUT TO DO,
 * BECAUSE IT IS ALIVE AND IT HAS BEEN DOING IT SINCE BEFORE YOU GOT HERE.
 *
 * That is not the same question as world 4's. A machine part is at a position on
 * a stroke and you read the position. A living thing has a BEHAVIOUR - it opens,
 * it waits, it snaps, it opens again - and what the player learns is the shape
 * of the behaviour rather than the state. So the parts here mostly do not move
 * at all: they are still, then they are not, and the quarter second before they
 * are is the whole game. Every one of them swells, brightens or bends first
 * ([Blink.warmAt]), because a plant that simply became lethal would be the one
 * thing this project has refused to ship for five worlds.
 *
 * Underneath it is still Motion and Blink and nothing else, which is why the
 * solver proves this world without being extended: a flower's cycle is a pure
 * function of level time, and level time is x / RUN_SPEED because the runner
 * never stops. Same level, same line, same forest, every run.
 *
 * Four rules, all inherited and all paid for:
 *
 *  1. NOTHING ARRIVES WITHOUT WARNING. Every part that switches on warms first.
 *
 *  2. EVERY SIZE IS SOLVED, NOT CHOSEN. The take-off window over a thing W wide
 *     and H tall is (the time the arc spends above H) minus (W + 0.9) / 9.5,
 *     which measures a 1.0 x 1.0 spike at 0.239s against the verifier's 0.238s.
 *     So: flower 1.0 x 1.4 and 0.191s, vine 1.1 x 1.4 and 0.182s, root 1.0 x
 *     1.5 and 0.177s, pod 1.2 x 1.3 and 0.185s, spore 1.7 x 0.95 and 0.182s,
 *     seed 1.2 x 1.3 and 0.185s, bloom 1.0 x 1.5 and 0.177s, pulse 1.4 x 1.1
 *     and 0.192s. Nothing in this forest is wide AND tall.
 *
 *  3. EVERY PHASE IS DERIVED. [phaseAt] answers "what is this doing when they
 *     get here", and nothing here guesses at it.
 *
 *  4. NOTHING CLOSES ALL THE WAY. A curtain, a wall or a bloom that leaves no
 *     line through it is not difficulty, it is a locked door - the runner cannot
 *     stop. Every one of them leaves either the floor or an opening, and the
 *     verifier confirms it on every build.
 */
class Overgrowth(val bpm: Double) {

    val beatUnits = Tuning.RUN_SPEED * 60.0 / bpm
    val barSeconds = 4.0 * 60.0 / bpm
    fun beat(n: Double) = n * beatUnits

    /** When the runner reaches [x]. The one fact the whole forest is built on. */
    private fun timeAt(x: Double) = x / Tuning.RUN_SPEED

    /**
     * The phase that puts a sine cycle at [at] when the runner reaches [x].
     * 0.0 is the middle going out, 0.25 the far end, 0.5 the middle coming
     * back, 0.75 the near end.
     */
    fun phaseAt(x: Double, at: Double, bars: Double): Double {
        val u = at - timeAt(x) / (barSeconds * bars)
        return ((u % 1.0) + 1.0) % 1.0
    }

    /** The phase that has a living thing LETHAL as the runner reaches [x], half
     *  its dangerous window spent - unambiguously shut, not closing. */
    private fun liveAt(x: Double, period: Double, lethalFor: Double): Double {
        val u = lethalFor * 0.5 - timeAt(x) / period
        return ((u % 1.0) + 1.0) % 1.0
    }

    /** And the phase that has it OPEN as they reach [x] - the moment to be there. */
    private fun openAtPhase(x: Double, period: Double, lethalFor: Double): Double {
        val u = lethalFor + (1.0 - lethalFor) * 0.5 - timeAt(x) / period
        return ((u % 1.0) + 1.0) % 1.0
    }

    // --- A. SNAP FLOWER ------------------------------------------------------
    //
    // A mouth in the floor. Open, waiting, and then it shuts, and what shuts is
    // the lane. Its cycle in the order the player reads it:
    //
    //   OPEN      petals wide, nothing in the way
    //   WARNING   the throat brightens - [Blink.warmAt], drawn, a quarter second
    //   SNAP      shut, and lethal for [lethalFor] of the cycle
    //   OPEN      again, and the player has learned where it sits in the bar
    //
    // [snapAt] is the x it should be shut for; [openAt] the x it should be open
    // for. Both are obstacles: one says jump, the other says the floor is yours
    // for exactly as long as it takes the next thing to arrive.

    fun snapFlower(x: Double, base: Double, size: Double = 1.0, bars: Double = 2.0,
                   lethalFor: Double = 0.36, snapAt: Double = Double.NaN,
                   openAt: Double = Double.NaN): Hazard {
        val period = barSeconds * bars
        val phase = when {
            !snapAt.isNaN() -> liveAt(snapAt, period, lethalFor)
            !openAt.isNaN() -> openAtPhase(openAt, period, lethalFor)
            else -> 0.0
        }
        return Hazard(HazardKind.SPIKE_UP, x, x + size, base, base + size * 1.4,
            blink = Blink(period, lethalFor, phase), look = Look.FLOWER)
    }

    /** Two mouths, out of step, so the safe moment moves between them. The second
     *  one is derived to be shut half a cycle after the first, which at this speed
     *  is a real distance rather than a phase number. */
    fun flowerPair(x: Double, base: Double, apart: Double = 7.0, bars: Double = 2.0): List<Hazard> {
        val half = barSeconds * bars * 0.5 * Tuning.RUN_SPEED
        return listOf(
            snapFlower(x, base, bars = bars, snapAt = x),
            snapFlower(x + apart, base, bars = bars, snapAt = x + apart + half),
        )
    }

    // --- B. VINE SWEEP -------------------------------------------------------
    //
    // A vine on a long arc, crossing the lane and coming back. Wide and slow,
    // because rule 2 does not bend for anything: a heavy thing moving fast
    // leaves no manoeuvre. [nearAt] is the x it should have swung to.

    fun vineSweep(x: Double, base: Double, reach: Double = 2.4, bars: Double = 3.0,
                  height: Double = 1.4, nearAt: Double = Double.NaN) =
        Hazard(HazardKind.SPIKE_UP, x, x + 1.1, base, base + height,
            Motion(dx = reach, period = barSeconds * bars,
                phase = if (nearAt.isNaN()) 0.75 else phaseAt(nearAt, 0.75, bars)),
            look = Look.VINE)

    /** The same vine hung to cross at head height instead of at the floor. It
     *  cannot touch a runner who stays down and it takes the whole stretch away
     *  from one who does not. */
    fun vineSweepHigh(x: Double, base: Double, reach: Double = 2.2, bars: Double = 3.0,
                      nearAt: Double = Double.NaN) =
        Hazard(HazardKind.SPIKE_UP, x, x + 1.1, base + 1.6, base + 3.0,
            Motion(dx = reach, period = barSeconds * bars,
                phase = if (nearAt.isNaN()) 0.75 else phaseAt(nearAt, 0.75, bars)),
            look = Look.VINE)

    // --- C. ROOT RISE --------------------------------------------------------
    //
    // A root that comes up through the floor. The ground pulses first - the warm
    // window - and then it is there. Same shape as the abyss arm and the desert
    // geyser, which is the point: this game has one honest way of making
    // something appear, and every world uses it.

    fun rootRise(x: Double, base: Double, height: Double = 1.5, bars: Double = 2.0,
                 lethalFor: Double = 0.38, upAt: Double = Double.NaN,
                 downAt: Double = Double.NaN): Hazard {
        val period = barSeconds * bars
        val phase = when {
            !upAt.isNaN() -> liveAt(upAt, period, lethalFor)
            !downAt.isNaN() -> openAtPhase(downAt, period, lethalFor)
            else -> 0.0
        }
        return Hazard(HazardKind.SPIKE_UP, x, x + 1.0, base, base + height,
            blink = Blink(period, lethalFor, phase), look = Look.ROOT)
    }

    /** Roots in sequence, each on a slightly longer cycle than the last so they
     *  come up as a wave - and every one of them up when the runner reaches it,
     *  which is what makes a run of roots a run of jumps. */
    fun rootRun(x: Double, base: Double, count: Int = 3, spacing: Double = 8.0,
                height: Double = 1.5, bars: Double = 2.0,
                barsStep: Double = 0.25): List<Hazard> =
        (0 until count).map { k ->
            val rx = x + k * spacing
            rootRise(rx, base, height = height, bars = bars + barsStep * k, upAt = rx)
        }

    // --- D. THORN POD --------------------------------------------------------
    //
    // A pod that swells and bursts. What is drawn when it is quiet is a small
    // closed pod; what is drawn and what kills when it is not is the whole thorn
    // structure, and they are the same box - the hazard simply is not there in
    // between, the way a geyser is not there between eruptions.

    // [size] scales the whole pod. A full one is 1.2 by 1.3, which is a 0.185s
    // take-off; the smaller ones exist for the opening minute of a level, where
    // the ladder holds every window at 0.18 or wider and the difference between
    // a fair first pod and a failing one is a tenth of a unit.
    fun thornPod(x: Double, base: Double, bars: Double = 2.0, lethalFor: Double = 0.34,
                 size: Double = 1.0, burstAt: Double = Double.NaN): Hazard {
        val period = barSeconds * bars
        val phase = if (burstAt.isNaN()) 0.0 else liveAt(burstAt, period, lethalFor)
        return Hazard(HazardKind.SPIKE_UP, x, x + 1.2 * size, base, base + 1.3 * size,
            blink = Blink(period, lethalFor, phase), look = Look.THORN)
    }

    // --- E. SPORE STREAM -----------------------------------------------------
    //
    // A drift of spores crossing the lane: low, wide and slower than anything
    // else in the forest. It is the world's answer to a moving cloud, and the
    // reason it works as an obstacle is the same reason a desert crest does -
    // its phase is solved so it is coming at the runner rather than fleeing
    // them, which is the half of the cycle a person can read.

    val sporeWidth = 1.7

    fun sporeStream(x: Double, base: Double, reach: Double = 3.0, bars: Double = 4.0,
                    height: Double = 0.95): Hazard {
        val period = barSeconds * bars
        return Hazard(HazardKind.SPIKE_UP, x, x + sporeWidth, base, base + height,
            Motion(dx = reach, period = period,
                phase = 0.5 - timeAt(x + sporeWidth / 2.0) / period), look = Look.SPORE)
    }

    // --- F. FALLING SEED -----------------------------------------------------
    //
    // A seed out of the canopy, landing in the same place every time. The glow
    // on the floor under it is the warning and it is drawn from the same
    // [Blink.warmAt] the physics reads, so the mark and the danger cannot drift
    // apart however either is edited.

    fun fallingSeed(x: Double, base: Double, bars: Double = 2.0, lethalFor: Double = 0.36,
                    downAt: Double = Double.NaN): Hazard {
        val period = barSeconds * bars
        val phase = if (downAt.isNaN()) 0.0 else liveAt(downAt, period, lethalFor)
        return Hazard(HazardKind.SPIKE_UP, x, x + 1.2, base, base + 1.3,
            blink = Blink(period, lethalFor, phase), look = Look.SEED)
    }

    // --- G. CARNIVOROUS BLOOM ------------------------------------------------
    //
    // A bloom that grows UP out of the ground and becomes a wall of petals, then
    // sinks back. Unlike the snap flower it does not switch - it travels, so
    // there is no instant at which it appears, only an instant at which it is
    // tall enough to matter. The resting box sits a full height BELOW the floor
    // and the motion lifts it out, which is how a thing that grows is drawn
    // honestly with a box.

    fun carnivorousBloom(x: Double, base: Double, height: Double = 1.5, bars: Double = 2.5,
                         openAt: Double = Double.NaN, shutAt: Double = Double.NaN): Hazard {
        val phase = when {
            !openAt.isNaN() -> phaseAt(openAt, 0.25, bars)
            !shutAt.isNaN() -> phaseAt(shutAt, 0.75, bars)
            else -> 0.25
        }
        return Hazard(HazardKind.SPIKE_UP, x, x + 1.0, base - height, base,
            Motion(dy = height, period = barSeconds * bars, phase = phase), look = Look.BLOOM)
    }

    /** Blooms in a chain, each opening a little after the last. */
    fun bloomChain(x: Double, base: Double, count: Int = 3, spacing: Double = 8.0,
                   bars: Double = 2.5): List<Hazard> =
        (0 until count).map { k -> carnivorousBloom(x + k * spacing, base, bars = bars,
            openAt = x + k * spacing) }

    // --- H. ROOT WALL --------------------------------------------------------
    //
    // Roots braiding into a wall across the lane with one gap in it, and the
    // whole thing grows and retreats on a cycle. It is this world's precision
    // obstacle: the gap is at head height, so the answer is not "be in the air",
    // it is "be at THIS height", which means the jump has to start in the right
    // place and not merely at the right moment. A second tap through that gap is
    // fatal, which makes it the one place in the forest where the boost is wrong.
    //
    // The geometry is the one the abyss proved: with the opening's floor at 1.7
    // the runner's box is inside the gap from 0.86u after take-off until 3.77u
    // after it, and the wall's own width and the runner's take about 1.7 of
    // that. What is left is the ask, and it is about a tenth of a second.

    fun rootWall(x: Double, base: Double, openBottom: Double = 1.7, openTop: Double = 4.0,
                 top: Double = 8.5, width: Double = 0.9, bars: Double = 2.5,
                 lethalFor: Double = 0.55, grownAt: Double = Double.NaN): List<Hazard> {
        val period = barSeconds * bars
        val phase = if (grownAt.isNaN()) 0.0 else liveAt(grownAt, period, lethalFor)
        val blink = Blink(period, lethalFor, phase)
        val out = ArrayList<Hazard>()
        var y = base
        while (y < base + openBottom - 0.01) {
            val h = minOf(1.0, base + openBottom - y)
            out += Hazard(HazardKind.SPIKE_UP, x, x + width, y, y + h,
                blink = blink, look = Look.ROOT)
            y += h
        }
        y = base + openTop
        while (y < top - 0.01) {
            val h = minOf(1.0, top - y)
            out += Hazard(HazardKind.SPIKE_DOWN, x, x + width, y, y + h,
                blink = blink, look = Look.ROOT)
            y += h
        }
        return out
    }

    // --- I. VINE CURTAIN -----------------------------------------------------
    //
    // Vines hanging across a stretch, some still and some swaying. They stop
    // above a running head and they never reach the floor, so the line through
    // is always the ground - and every one of them takes the air away from a
    // stretch the player would otherwise have jumped through without looking.

    fun vineCurtain(x0: Double, x1: Double, base: Double, tip: Double = 1.35,
                    spacing: Double = 1.6, sway: Double = 0.9,
                    bars: Double = 3.0): List<Hazard> {
        val out = ArrayList<Hazard>()
        var x = x0
        var k = 0
        while (x < x1 - 0.01) {
            // Every vine moves, and they do not move alike: the sweepers take
            // the full [sway] and the ones between them barely drift. A curtain
            // with half its vines nailed in place reads as a fence - the point of
            // hanging them is that the whole thing is alive, and the player picks
            // the line through by watching which ones are actually going
            // anywhere. Each phase is derived from its own x, so no two swing
            // together and the curtain never pulses like a metronome.
            val amp = if (k % 2 == 1) sway else sway * 0.28
            out += Hazard(HazardKind.SPIKE_DOWN, x, x + 0.7, base + tip, base + 7.0,
                motion = Motion(dx = amp, period = barSeconds * (bars + 0.2 * (k % 3)),
                    phase = phaseAt(x, 0.75, bars + 0.2 * (k % 3))),
                look = Look.VINE)
            x += spacing; k++
        }
        return out
    }

    // --- J. PULSE PLANT ------------------------------------------------------
    //
    // A plant that beats. Between beats it is scenery; on the beat the air
    // around it is lethal out to a radius, and the core brightens first. Wider
    // than it is tall on purpose - it is a shockwave, not a spike, and the shape
    // of the box should say so.

    fun pulsePlant(x: Double, base: Double, bars: Double = 1.5, lethalFor: Double = 0.30,
                   pulseAt: Double = Double.NaN): Hazard {
        val period = barSeconds * bars
        val phase = if (pulseAt.isNaN()) 0.0 else liveAt(pulseAt, period, lethalFor)
        return Hazard(HazardKind.SPIKE_UP, x, x + 1.4, base, base + 1.1,
            blink = Blink(period, lethalFor, phase), look = Look.PULSE)
    }

    // --- K. SEED ROLL --------------------------------------------------------
    //
    // A seed that landed somewhere up the slope and is coming back down it. Same
    // verb as the vine, different body: heavier, lower, and it rolls at the
    // runner rather than sweeping across them.

    fun seedRoll(x: Double, base: Double, reach: Double = 2.4, bars: Double = 3.0) =
        Hazard(HazardKind.SPIKE_UP, x, x + 1.3, base, base + 1.25,
            Motion(dx = reach, period = barSeconds * bars,
                phase = 0.5 - timeAt(x + 0.65) / (barSeconds * bars)), look = Look.SEED)

    // --- the ground ----------------------------------------------------------

    /** Living floor: moss over root. The forest's ordinary ground. */
    fun moss(x0: Double, x1: Double, top: Double) =
        Solid(x0, x1, top, -40.0, surface = Surface.MOSS)

    /** A branch that breathes - floor that rises and falls and carries whoever is
     *  standing on it, which the engine allows because vertical movement does not
     *  touch the runner's x. */
    fun breathingBough(x0: Double, x1: Double, top: Double, rise: Double = 0.8,
                       bars: Double = 3.0, lowAt: Double = Double.NaN) =
        Solid(x0, x1, top, -40.0,
            Motion(dy = rise, period = barSeconds * bars,
                phase = if (lowAt.isNaN()) 0.75 else phaseAt(lowAt, 0.75, bars)),
            surface = Surface.MOSS)

    /**
     * A fallen seed, lying in the lane and not doing anything at all.
     *
     * Every level ends on three of them a jump apart. It is the one place in the
     * forest where nothing is on a cycle, because a level's tightest moment has
     * to measure the same from one run to the next or the number the whole
     * ladder is built on means nothing.
     */
    fun fallenSeed(x: Double, base: Double, size: Double = 1.0) =
        Hazard(HazardKind.SPIKE_UP, x, x + size, base, base + size, look = Look.SEED)
}
