package com.fliperror.core

/**
 * WORLD 3's vocabulary - the things THE ABYSS is built out of.
 *
 * World 1 asked WHEN to jump. World 2 asked WHERE THE GROUND WOULD BE. World 3
 * asks a third question, and it is the one that makes it harder than either:
 * WHETHER TO JUMP AT ALL.
 *
 * That is the whole design. Up to here every obstacle has been answered by
 * pressing the button at the right moment, so the game has only ever taught the
 * player to look for the moment. The abyss has obstacles that punish the press
 * itself - a slab dropping through the exact air a jump would occupy, a corridor
 * whose ceiling closes while the floor is clear, a bubble that must be run at and
 * not leapt. Learning to NOT TAP is a different skill from tapping well, and it
 * is why this world reads as a step change rather than as more of the same.
 *
 * Everything below is built from the three verbs the engine already has - move,
 * blink, push - because the fairness proof rests on the world being a pure
 * function of x, and x is RUN_SPEED * time. Nothing here tracks the player's
 * history. The hunting orb "follows" by following x, which is not a choice it
 * makes; the rest is sines and duty cycles. That is what lets the solver prove a
 * level of this complexity possible at all.
 */
class Abyss(val bpm: Double) {

    val beatUnits = Tuning.RUN_SPEED * 60.0 / bpm
    val barSeconds = 4.0 * 60.0 / bpm
    fun beat(n: Double) = n * beatUnits

    private fun timeAt(x: Double) = x / Tuning.RUN_SPEED

    /** The phase that puts a cycle at [at] when the runner reaches [x]. 0.0 is
     *  the near end of a sweep, 0.25 the far end, 0.5 coming back, 0.75 nearest. */
    fun phaseAt(x: Double, at: Double, bars: Double): Double {
        val u = at - timeAt(x) / (barSeconds * bars)
        return ((u % 1.0) + 1.0) % 1.0
    }

    /** The phase that has a timed hazard ON as the runner reaches [x], with half
     *  its lethal window spent, so it is unambiguously there rather than arriving. */
    private fun onAt(x: Double, period: Double, lethalFor: Double): Double {
        val u = lethalFor * 0.5 - timeAt(x) / period
        return ((u % 1.0) + 1.0) % 1.0
    }

    /** And the phase that has it QUIET as they reach [x] - the gap to run through. */
    private fun quietAt(x: Double, period: Double, lethalFor: Double): Double {
        val u = lethalFor + (1.0 - lethalFor) * 0.5 - timeAt(x) / period
        return ((u % 1.0) + 1.0) % 1.0
    }

    // --- 1. CHASING BUBBLE ---------------------------------------------------
    //
    // A bubble that comes at the runner and has to be jumped. Its phase is
    // derived so it is sweeping TOWARD them at the crossing - coming at you it
    // is over in half the time, and it is the half of the cycle a player can
    // read. 1.5u across and 1.4u tall: wide enough to be a presence, small
    // enough that a 4.94u jump is still a jump rather than a prayer.

    val bubbleW = 1.5
    private val chasePeriod get() = barSeconds * 4.0

    fun chasingBubble(x: Double, base: Double, reach: Double = 2.4) =
        Hazard(HazardKind.SPIKE_UP, x, x + bubbleW, base, base + 1.4,
            Motion(dx = reach, period = chasePeriod,
                phase = 0.5 - timeAt(x + bubbleW / 2.0) / chasePeriod), look = Look.BUBBLE)

    // --- 2/3. SPLIT BUBBLE ---------------------------------------------------
    //
    // One bubble that stops being there at a moment, and two or three small ones
    // that start being there at the same moment, on their own paths.
    //
    // It is a genuine split rather than a trick of the art: the parent's blink
    // goes off exactly where the children's come on, all four derived from the
    // same x, so what the player sees and what the collision does are the same
    // event. The solver needs no new idea to check it - it is four boxes whose
    // presence is a function of time, which is what every hazard here already is.

    fun splitBubble(x: Double, base: Double, at: Double, pieces: Int = 3,
                    bars: Double = 4.0): List<Hazard> {
        val period = barSeconds * bars
        val split = timeAt(at)
        // The parent is present up to the split and gone after it.
        val parentOn = ((split / period) % 1.0 + 1.0) % 1.0
        val out = ArrayList<Hazard>()
        out += Hazard(HazardKind.SPIKE_UP, x, x + bubbleW * 1.4, base, base + 1.6,
            motion = Motion(dx = 2.0, period = period, phase = 0.5 - timeAt(x) / period),
            blink = Blink(period, parentOn.coerceIn(0.06, 0.94), 0.0), look = Look.BUBBLE)
        // The children appear where it ended, each on a different path.
        for (k in 0 until pieces) {
            val spread = (k - (pieces - 1) / 2.0) * 1.9
            // All the children sit on the floor. Stacking them upward by half a
            // unit each read well and played badly: the top one needed a second
            // tap to clear, which made a split a boost obstacle rather than a
            // reading one, and it is meant to be the latter.
            out += Hazard(HazardKind.SPIKE_UP, at + spread, at + spread + 0.9,
                base, base + 0.9,
                motion = Motion(dx = 1.1 + 0.4 * k, dy = 0.5,
                    period = period * 0.5, phase = phaseAt(at, 0.75, bars * 0.5), phaseY = 0.25),
                blink = Blink(period, 1.0 - parentOn.coerceIn(0.06, 0.94),
                    parentOn.coerceIn(0.06, 0.94)), look = Look.BUBBLE)
        }
        return out
    }

    // --- 4/11. ORBS ----------------------------------------------------------
    //
    // An abyss orb rides a circle - equal reach on both axes a quarter turn
    // apart IS a circle - and it is the largest single thing in the world. A
    // hunting orb is the same shape smaller and faster, and it "follows" the
    // runner only in the sense that its own sweep carries it along x. It cannot
    // do anything else: the runner's y is the one thing in this game that
    // depends on their history, and a hazard that chased it would put the
    // solver out of a job.

    fun abyssOrb(x: Double, y: Double, radius: Double = 2.2, bars: Double = 3.0,
                 phase: Double = 0.0) =
        Hazard(HazardKind.SPIKE_UP, x, x + 1.8, y, y + 1.8,
            Motion(dx = radius, dy = radius, period = barSeconds * bars,
                phase = phase, phaseY = 0.25), look = Look.ORB)

    fun huntingOrb(x: Double, y: Double, reach: Double = 3.2, bars: Double = 1.5) =
        Hazard(HazardKind.SPIKE_UP, x, x + 1.0, y, y + 1.0,
            Motion(dx = reach, dy = 0.9, period = barSeconds * bars,
                phase = phaseAt(x, 0.5, bars), phaseY = 0.25), look = Look.ORB)

    // --- 5. CURRENT BURST ----------------------------------------------------
    //
    // Water shoving the runner, and VERTICALLY - see Wind. Sideways is the one
    // direction that cannot exist here: the runner's x is exactly RUN_SPEED *
    // time and every proof this game makes about itself is built on that, so a
    // horizontal current would make x a function of the player's history and the
    // solver could no longer say whether a level was possible at all.

    fun currentBurst(x0: Double, x1: Double, push: Double) = Wind(x0, x1, push)

    // --- 7/8. WALLS ----------------------------------------------------------
    //
    // A slab rising out of the floor, and its opposite coming down from the
    // ceiling. The second is the one that changes how this world plays: it drops
    // through exactly the air a jump would use, so the answer is to keep running.
    // Every obstacle in worlds 1 and 2 is answered by tapping at the right
    // moment; this one is answered by not tapping at all, and that is a skill
    // the game has never asked for before.

    fun risingWall(x: Double, base: Double, height: Double = 1.6, bars: Double = 2.0,
                   upAt: Double = Double.NaN, lethalFor: Double = 0.42): Hazard {
        val period = barSeconds * bars
        return Hazard(HazardKind.SPIKE_UP, x, x + 1.2, base, base + height,
            blink = Blink(period, lethalFor,
                if (upAt.isNaN()) 0.0 else onAt(upAt, period, lethalFor)), look = Look.WALL)
    }

    fun descendingWall(x: Double, tip: Double, top: Double = 9.0, bars: Double = 2.0,
                       downAt: Double = Double.NaN, lethalFor: Double = 0.40): Hazard {
        val period = barSeconds * bars
        return Hazard(HazardKind.SPIKE_DOWN, x, x + 1.4, tip, top,
            blink = Blink(period, lethalFor,
                if (downAt.isNaN()) 0.0 else onAt(downAt, period, lethalFor)), look = Look.WALL)
    }

    // --- 9/16. CORRIDORS -----------------------------------------------------
    //
    // A run with a ceiling that closes and opens. Built as a line of descending
    // slabs on one cycle, so the safe opening travels with the runner rather
    // than being a hole they have to find. The rotating tunnel is the same idea
    // with the phase advancing along its length, which reads as a turn.

    fun lightCorridor(x0: Double, x1: Double, tip: Double, bars: Double = 2.0,
                      openAt: Double = Double.NaN, lethalFor: Double = 0.38): List<Hazard> {
        val period = barSeconds * bars
        val phase = if (openAt.isNaN()) 0.0 else quietAt(openAt, period, lethalFor)
        val out = ArrayList<Hazard>()
        var x = x0
        while (x < x1 - 0.01) {
            out += Hazard(HazardKind.SPIKE_DOWN, x, x + 1.4, tip, 9.0,
                blink = Blink(period, lethalFor, phase), look = Look.WALL)
            x += 1.4
        }
        return out
    }

    fun rotatingTunnel(x0: Double, x1: Double, tip: Double, bars: Double = 2.0,
                       openAt: Double = Double.NaN, turn: Double = 0.06): List<Hazard> {
        val period = barSeconds * bars
        val base = if (openAt.isNaN()) 0.0 else quietAt(openAt, period, 0.36)
        val out = ArrayList<Hazard>()
        var x = x0
        var k = 0
        while (x < x1 - 0.01) {
            out += Hazard(HazardKind.SPIKE_DOWN, x, x + 1.4, tip, 9.0,
                blink = Blink(period, 0.36, base + k * turn), look = Look.WALL)
            x += 1.4; k++
        }
        return out
    }

    // --- 10. ELECTRIC CURRENT ------------------------------------------------
    //
    // A lit lane that is lethal for part of its cycle. It stops short of the
    // floor, so running under it is always possible and jumping into it never
    // is - the same contract the desert's beams keep, for the same reason: a
    // hazard that fills the lane is a locked door, not a difficulty.

    fun electricCurrent(x: Double, tip: Double, bars: Double = 1.5,
                        onAtX: Double = Double.NaN, lethalFor: Double = 0.34): Hazard {
        val period = barSeconds * bars
        return Hazard(HazardKind.SPIKE_DOWN, x, x + 1.2, tip, 9.0,
            blink = Blink(period, lethalFor,
                if (onAtX.isNaN()) 0.0 else onAt(onAtX, period, lethalFor)), look = Look.LASER)
    }

    // --- 12. ABYSS TENTACLE --------------------------------------------------
    //
    // An arm that reaches in and withdraws on a long cycle. Wide and slow, which
    // is the same rule the desert's crests are built on: a jump covers 4.94u and
    // the runner is 0.9u of it, so anything much past two units of obstacle
    // stops leaving a manoeuvre.

    // Its phase is DERIVED, like every other mover in this game: at the moment
    // the runner reaches it the arm is at the far end of its sweep, withdrawn out
    // of the lane. Hand-set to zero it could be at the near end instead, and then
    // its swept width - 1.8u of arm plus 2.6u each way - is seven units of
    // obstacle in front of a 4.94u jump, which is not a hard jump, it is no jump
    // at all. Withdrawn, it is something to run past and watch come back.
    fun tentacle(x: Double, base: Double, reach: Double = 2.6, bars: Double = 3.0,
                 phase: Double = Double.NaN) =
        Hazard(HazardKind.SPIKE_UP, x, x + 1.8, base, base + 1.8,
            Motion(dx = reach, period = barSeconds * bars,
                phase = if (phase.isNaN()) phaseAt(x, 0.25, bars) else phase),
            look = Look.TENTACLE)

    // --- 13/15. BUBBLE FLOOR AND BRIDGE --------------------------------------
    //
    // Floor made of bubbles: some hold, some burst. A burst one is a span whose
    // blink phase is derived from the x it is landed on, so it is already fading
    // under the runner's feet when they arrive and the pressure is to keep
    // moving rather than to guess.

    fun bubbleFloor(x0: Double, x1: Double, top: Double) =
        Solid(x0, x1, top, -40.0, surface = Surface.BUBBLE)

    fun burstingBubble(x0: Double, x1: Double, top: Double, arriveAt: Double,
                       bars: Double = 2.0, on: Double = 0.55) =
        Solid(x0, x1, top, -40.0, null,
            Blink(barSeconds * bars, on, blinkPhaseFor(arriveAt, barSeconds * bars, 0.05)),
            Surface.BUBBLE)

    // --- 14. FLOATING MINES --------------------------------------------------
    //
    // Small, still-ish, and on a patrol you can read: up, down, up. They are the
    // world's punctuation - something to thread between rather than jump.

    fun mine(x: Double, y: Double, rise: Double = 1.4, bars: Double = 1.5,
             phase: Double = 0.0) =
        Hazard(HazardKind.SPIKE_UP, x, x + 0.8, y, y + 0.8,
            Motion(dy = rise, period = barSeconds * bars, phase = phase), look = Look.MINE)
}
