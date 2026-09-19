package com.fliperror.core

/**
 * WORLD 4's vocabulary - the parts THE MACHINE is built out of.
 *
 * The city asked WHEN to jump. The desert asked WHERE THE GROUND WOULD BE. The
 * abyss asked WHETHER TO JUMP AT ALL. The machine asks a fourth thing, and it is
 * the one that makes it the hardest world: WHAT IS THIS PART DOING RIGHT NOW,
 * AND WHAT WILL IT BE DOING WHEN I GET THERE.
 *
 * Every other world's obstacles are things that happen TO the runner. These are
 * mechanisms, and a mechanism has a cycle: up, warning, down, impact, retract.
 * The player is not reading an obstacle, they are reading a machine part, and a
 * machine part tells you what comes next if you know what it is. That is why the
 * world can be this dense and still be learnable - and it is why nothing in it
 * is allowed to be random, ever.
 *
 * THE CYCLE SYSTEM. There is no clock in this file and no scheduler. Every part
 * is a pure function of level time, and level time is x / RUN_SPEED because the
 * runner never stops - so "what is this piston doing when they arrive" is
 * arithmetic, not a simulation. Same input, same level, same result, always,
 * and the solver can prove any of it possible without a new idea. A piston is
 * [Motion]; a shutter is [Motion]; a gate, a vent and a rail are [Blink]. Two
 * primitives, eleven mechanisms.
 *
 * Four rules, all of them paid for in the three worlds before this one:
 *
 *  1. NOTHING ARRIVES WITHOUT WARNING. A part that switches on spends the
 *     moments before it is lethal visibly charging ([Blink.warmAt]); a part that
 *     moves is visibly on its way. There is no instant invisible piston in here.
 *
 *  2. EVERY SIZE IS SOLVED, NOT CHOSEN. A jump apexes at 2.6u and covers 4.94u,
 *     of which the runner is 0.9u, so the take-off window over a thing W wide
 *     and H tall is (the time the arc spends above H) minus (W + 0.9) / 9.5. A
 *     1.0 x 1.0 spike measures 0.239s that way and 0.238s in the verifier. Every
 *     lane-blocking part below is sized from that sum, not from what looked
 *     right: ram 1.0 x 1.3 and 0.203s, vent 1.0 x 1.4 and 0.190s, rail
 *     1.1 x 1.1 and 0.216s, tooth 0.9 x 1.2 and 0.219s.
 *
 *  3. EVERY PHASE IS DERIVED. [phaseAt] answers "where is this when they get
 *     here", and nothing in this file guesses at it. The first hand-picked phase
 *     in this project made a level literally unbeatable.
 *
 *  4. NOTHING CLOSES ALL THE WAY. A press whose two halves meet is not a
 *     difficulty, it is a locked door - the runner cannot stop, so a lane with
 *     no answer is a lane nobody crosses. [crushingWall] is built out of two
 *     plates a half-cycle apart for exactly that reason, and the verifier
 *     proves the gap every time it runs.
 */
class Clockwork(val bpm: Double) {

    val beatUnits = Tuning.RUN_SPEED * 60.0 / bpm
    val barSeconds = 4.0 * 60.0 / bpm
    fun beat(n: Double) = n * beatUnits

    /** When the runner reaches [x]. The one fact the whole machine is built on. */
    private fun timeAt(x: Double) = x / Tuning.RUN_SPEED

    /**
     * The phase that puts a cycle at [at] when the runner reaches [x]. 0.0 is
     * the middle going out, 0.25 the far end, 0.5 the middle coming back, 0.75
     * the near end - so 0.75 is a ram at the bottom of its stroke and a tooth at
     * the bottom of its circle.
     */
    fun phaseAt(x: Double, at: Double, bars: Double): Double {
        val u = at - timeAt(x) / (barSeconds * bars)
        return ((u % 1.0) + 1.0) % 1.0
    }

    /** The phase that has a switching part LIVE as they reach [x], half its
     *  dangerous window spent - unambiguously on, rather than arriving. */
    private fun liveAt(x: Double, period: Double, lethalFor: Double): Double {
        val u = lethalFor * 0.5 - timeAt(x) / period
        return ((u % 1.0) + 1.0) % 1.0
    }

    /** And the phase that has it OFF as they reach [x] - the window to run through. */
    private fun offAt(x: Double, period: Double, lethalFor: Double): Double {
        val u = lethalFor + (1.0 - lethalFor) * 0.5 - timeAt(x) / period
        return ((u % 1.0) + 1.0) % 1.0
    }

    // --- 1. GEAR -------------------------------------------------------------
    //
    // Something enormous turning, and what can touch the runner is its TEETH.
    //
    // That is the whole of the honesty here. A gear drawn as a disc and
    // collided with as a box is the oldest unfair obstacle there is; this one
    // is a set of teeth on a circle, each its own box, and the renderer draws
    // the rim they sit on and nothing else that kills. What is lethal is what
    // is drawn, tooth for tooth.
    //
    // The hub sits [radius] above the floor, so the bottom of the circle is the
    // lane and the top of it is out of the way. [lowAt] is the x a tooth should
    // be at the bottom for - which is the moment the player has to be over it.
    //
    // THREE TEETH, AND A RADIUS OF AT LEAST 2.6. That is not a style choice, it
    // is the only geometry that works, and the first draft of this world got it
    // wrong twice. While one tooth is at the bottom the next one is 120 degrees
    // round, which puts it at 1.5 radii up: at r = 2.3 that is 3.45u, a jump
    // tops the runner out at 3.55u, and the two overlap by a hundredth of a unit
    // - so the "safe" arc over the gear measured 0.063s and looked completely
    // reasonable in the level file. At r = 2.8 the neighbour sits at 4.2u and a
    // single jump passes cleanly under it while a second tap still does not.
    // Four teeth is worse at any radius: 90 degrees round is only 1.0 radius up,
    // which is inside the arc however big the gear gets.

    fun gear(x: Double, base: Double, radius: Double = 2.8, teeth: Int = 3,
             bars: Double = 3.0, lowAt: Double = Double.NaN): List<Hazard> {
        val period = barSeconds * bars
        val at = if (lowAt.isNaN()) x else lowAt
        // offsetY is a quarter turn behind offsetX, so asking for the bottom of
        // the circle also puts that tooth directly over x - the two facts the
        // player needs are the same fact.
        val zero = phaseAt(at, 0.5, bars)
        return (0 until teeth).map { k ->
            val step = k.toDouble() / teeth
            Hazard(HazardKind.SPIKE_UP, x, x + 0.9, base + radius, base + radius + 1.2,
                Motion(dx = radius, dy = radius, period = period,
                    phase = zero + step, phaseY = 0.25), look = Look.GEAR)
        }
    }

    /**
     * Two gears turning against each other, their teeth a half-step apart, so
     * the safe window travels between them instead of sitting still. The
     * player learns the RELATIONSHIP rather than either cycle - which is the
     * closest this game gets to a puzzle without adding a button.
     */
    fun gearLock(x: Double, base: Double, apart: Double = 9.0, radius: Double = 2.8,
                 teeth: Int = 3, bars: Double = 3.0): List<Hazard> {
        val a = gear(x, base, radius, teeth, bars, lowAt = x)
        // The second one is at the TOP of its circle as the runner meets it, so
        // the two are never in the lane together and the safe moment walks from
        // one to the other. Half a cycle is half a cycle of LEVEL, which at this
        // speed is a real distance - asking for the bottom two radii later, as
        // the first draft did, put the tooth an eighth of a turn from the floor
        // and squeezed the crossing to 0.021s.
        val halfCycle = barSeconds * bars * 0.5 * Tuning.RUN_SPEED
        val b = gear(x + apart, base, radius, teeth, bars, lowAt = x + apart + halfCycle)
        return a + b
    }

    // --- 2. PISTON -----------------------------------------------------------
    //
    // A ram on a shaft. Its cycle, in the order the player reads it:
    //
    //   UP        the ram is at the top of its stroke, the lane is clear
    //   WARNING   it starts down, visibly, and the shaft lamp comes on
    //   DOWN      it enters the lane
    //   IMPACT    the bottom of the stroke, the moment it is lethal at floor level
    //   RETRACT   back up, the lane opens
    //
    // None of that is a state machine. It is one sine, and the renderer reads
    // its height to decide which of those words to draw. [downAt] is the x the
    // ram should be at the bottom for; [upAt] the x it should be clear for.

    fun piston(x: Double, base: Double, stroke: Double = 2.2, length: Double = 1.3,
               bars: Double = 2.0, downAt: Double = Double.NaN,
               upAt: Double = Double.NaN): Hazard {
        val phase = when {
            !downAt.isNaN() -> phaseAt(downAt, 0.75, bars)
            !upAt.isNaN() -> phaseAt(upAt, 0.25, bars)
            else -> 0.75
        }
        // The resting box hangs a full stroke above where it will land, so the
        // bottom of the stroke is exactly the lane and the top is over the
        // runner's head. Same anchoring the abyss crystal uses, same reason.
        return Hazard(HazardKind.SPIKE_DOWN, x, x + 1.0, base + stroke, base + stroke + length,
            Motion(dy = stroke, period = barSeconds * bars, phase = phase), look = Look.PISTON)
    }

    /**
     * Rams in a row, every one of them at the bottom of its stroke as the runner
     * reaches it - so a bank is [count] jumps, not one.
     *
     * The first version took a phase offset to make them "fire in sequence",
     * which quietly undid the derivation: shifted off their solved phase the
     * rams were up when the runner arrived, the bank was free to run straight
     * through, and LEVEL 21 had thirty-two units of nothing in the middle of it.
     * What makes a bank read as a wave is [barsStep]: each ram runs on a slightly
     * longer cycle than the last, so they are visibly out of step with each other
     * while every one of them is still down when it matters.
     */
    fun pistonBank(x: Double, base: Double, count: Int = 3, spacing: Double = 8.0,
                   bars: Double = 2.0, barsStep: Double = 0.25): List<Hazard> =
        (0 until count).map { k ->
            val px = x + k * spacing
            piston(px, base, bars = bars + barsStep * k, downAt = px)
        }

    // --- 3. SHUTTER GATE -----------------------------------------------------
    //
    // A gate panel that comes down across the lane and goes back up: OPEN,
    // CLOSING, CLOSED, OPENING.
    //
    // It stops [tip] above the deck when it is fully down, which is the rule
    // every hanging hazard in this game keeps and the reason it is an obstacle
    // rather than a wall: shut, it can be run under; it cannot be JUMPED under.
    // So a shutter does not ask when to jump, it asks when not to - and a level
    // that puts one just after something that must be jumped is asking the
    // player to read two cycles against each other.

    fun shutterGate(x: Double, base: Double, tip: Double = 1.25, drop: Double = 2.4,
                    width: Double = 1.4, bars: Double = 2.0,
                    shutAt: Double = Double.NaN, openAt: Double = Double.NaN): Hazard {
        val phase = when {
            !shutAt.isNaN() -> phaseAt(shutAt, 0.75, bars)
            !openAt.isNaN() -> phaseAt(openAt, 0.25, bars)
            else -> 0.75
        }
        return Hazard(HazardKind.SPIKE_DOWN, x, x + width,
            base + tip + drop, base + tip + drop + 6.0,
            Motion(dy = drop, period = barSeconds * bars, phase = phase), look = Look.SHUTTER)
    }

    /** Gates on different cycles, so their open moments drift past each other.
     *  Two bars against three is six bars before the pattern repeats, which is
     *  longer than the stretch it sits on - so it never looks like a loop. */
    fun gateRun(x: Double, base: Double, count: Int = 2, spacing: Double = 9.0,
                bars: Double = 2.0, barsStep: Double = 1.0): List<Hazard> =
        (0 until count).map { k ->
            val gx = x + k * spacing
            shutterGate(gx, base, bars = bars + barsStep * k, openAt = gx)
        }

    // --- 4. CHAIN SWEEP ------------------------------------------------------
    //
    // A weight on the end of a swing, crossing the lane and coming back. Wide
    // and slow, because rule 2 is not negotiable: a heavy thing moving fast
    // leaves no manoeuvre, only luck. [nearAt] is the x it should have swung to
    // when the runner arrives.

    fun chainSweep(x: Double, base: Double, reach: Double = 2.4, bars: Double = 3.0,
                   height: Double = 1.4, nearAt: Double = Double.NaN) =
        Hazard(HazardKind.SPIKE_UP, x, x + 1.1, base, base + height,
            Motion(dx = reach, period = barSeconds * bars,
                phase = if (nearAt.isNaN()) 0.75 else phaseAt(nearAt, 0.75, bars)),
            look = Look.CHAIN)

    /** A high sweep: the same weight hung so it crosses at head height instead of
     *  at the deck. It takes the air rather than the floor. */
    fun chainSweepHigh(x: Double, base: Double, reach: Double = 2.2, bars: Double = 3.0,
                       nearAt: Double = Double.NaN) =
        Hazard(HazardKind.SPIKE_UP, x, x + 1.1, base + 1.6, base + 3.0,
            Motion(dx = reach, period = barSeconds * bars,
                phase = if (nearAt.isNaN()) 0.75 else phaseAt(nearAt, 0.75, bars)),
            look = Look.CHAIN)

    // --- 5. STEAM BURST ------------------------------------------------------
    //
    // A vent in the deck. WARNING, then BURST, then it dies back. The warning is
    // [Blink.warmAt] and it is drawn, not heard - this world has no ambience and
    // a vent that only announced itself in sound would be announcing itself to
    // nobody.

    fun steamBurst(x: Double, base: Double, height: Double = 1.4, bars: Double = 2.0,
                   lethalFor: Double = 0.38, upAt: Double = Double.NaN,
                   downAt: Double = Double.NaN): Hazard {
        val period = barSeconds * bars
        val phase = when {
            !upAt.isNaN() -> liveAt(upAt, period, lethalFor)
            !downAt.isNaN() -> offAt(downAt, period, lethalFor)
            else -> 0.0
        }
        return Hazard(HazardKind.SPIKE_UP, x, x + 1.0, base, base + height,
            blink = Blink(period, lethalFor, phase), look = Look.STEAM)
    }

    // --- 6. ROTATING CYLINDER ------------------------------------------------
    //
    // A drum with arms on it - the gear's long cousin, fewer teeth and more
    // reach, so the safe window between arms is a real gap rather than a slot.
    // Same circle, same derivation, and deliberately not "physics": it is one
    // sine per axis and it will do the same thing on the ten thousandth run.

    fun rotatingCylinder(x: Double, base: Double, radius: Double = 3.0, arms: Int = 3,
                         bars: Double = 4.0, lowAt: Double = Double.NaN): List<Hazard> {
        val period = barSeconds * bars
        val at = if (lowAt.isNaN()) x else lowAt
        val zero = phaseAt(at, 0.5, bars)
        return (0 until arms).map { k ->
            Hazard(HazardKind.SPIKE_UP, x, x + 1.0, base + radius, base + radius + 1.5,
                Motion(dx = radius, dy = radius, period = period,
                    phase = zero + k.toDouble() / arms, phaseY = 0.25), look = Look.CYLINDER)
        }
    }

    // --- 7. CRUSHING WALL ----------------------------------------------------
    //
    // A press: a plate coming down and a plate coming up. It is the one part of
    // this machine that could be built as a death sentence, so it is not:
    //
    // The two plates are a HALF CYCLE apart. When the top one is in the lane the
    // bottom one is flush with the deck, and when the bottom one is up the top
    // one is at the ceiling. There is never an instant with no answer - run when
    // the ceiling is down, jump when the floor is up - and the verifier confirms
    // it on every build rather than taking this comment's word for it.

    fun crushingWall(x: Double, base: Double, bars: Double = 2.0,
                     floorUpAt: Double = Double.NaN): List<Hazard> {
        val period = barSeconds * bars
        val at = if (floorUpAt.isNaN()) x else floorUpAt
        val phase = phaseAt(at, 0.25, bars)
        val bottom = Hazard(HazardKind.SPIKE_UP, x, x + 1.1, base - 1.3, base + 0.1,
            Motion(dy = 1.3, period = period, phase = phase), look = Look.CRUSHER)
        // THE SAME phase, not the opposite one. Both plates ride a sine, so
        // giving the top plate phase + 0.5 puts it at the bottom of its travel
        // exactly when the bottom plate is at the top of its - the two of them
        // in the lane together, with a corridor between 1.2 and 1.4 that is
        // narrower than the runner. That is the locked door rule 4 exists to
        // forbid, and the verifier duly reported the crossings around it at
        // 0.038s. On the same phase they alternate, which is what a press does:
        // the plate comes up out of the deck while the head is at the ceiling,
        // and the head comes down while the deck is flush.
        val top = Hazard(HazardKind.SPIKE_DOWN, x, x + 1.1, base + 3.5, base + 6.5,
            Motion(dy = 2.3, period = period, phase = phase), look = Look.CRUSHER)
        return listOf(bottom, top)
    }

    // --- 8. ENERGY RAIL ------------------------------------------------------
    //
    // A live bar across the deck. OFF, CHARGING, ACTIVE, OFF. Low and narrow, so
    // it is a jump and not a wall, and it charges where it can be seen.

    fun energyRail(x: Double, base: Double, bars: Double = 1.5, lethalFor: Double = 0.36,
                   onAt: Double = Double.NaN, offAtX: Double = Double.NaN): Hazard {
        val period = barSeconds * bars
        val phase = when {
            !onAt.isNaN() -> liveAt(onAt, period, lethalFor)
            !offAtX.isNaN() -> offAt(offAtX, period, lethalFor)
            else -> 0.0
        }
        return Hazard(HazardKind.SPIKE_UP, x, x + 1.1, base, base + 1.1,
            blink = Blink(period, lethalFor, phase), look = Look.RAIL)
    }

    // --- 9. DROPPING BOLT ----------------------------------------------------
    //
    // A component that comes out of the works above and lands in the lane, on
    // the bar, in the same place every time. The floor marker under it is the
    // warning and it is drawn from the same [Blink.warmAt] the physics reads.

    fun droppingBolt(x: Double, base: Double, height: Double = 1.3, bars: Double = 2.0,
                     lethalFor: Double = 0.38, downAt: Double = Double.NaN): Hazard {
        val period = barSeconds * bars
        val phase = if (downAt.isNaN()) 0.0 else liveAt(downAt, period, lethalFor)
        return Hazard(HazardKind.SPIKE_UP, x, x + 1.2, base, base + height,
            blink = Blink(period, lethalFor, phase), look = Look.BOLT)
    }

    // --- 10. ROTATING PLATFORM -----------------------------------------------
    //
    // Deck plate on an arm, going round. Vertical movement carries whoever is
    // standing on it and horizontal movement does not - that asymmetry is the
    // engine's and it is load bearing, because the runner's x has to stay
    // exactly RUN_SPEED * time or nothing here can be proved at all.

    fun rotatingPlatform(x0: Double, x1: Double, top: Double, radius: Double = 1.6,
                         bars: Double = 3.0, lowAt: Double = Double.NaN) =
        Solid(x0, x1, top, -40.0,
            Motion(dx = radius, dy = radius, period = barSeconds * bars,
                phase = if (lowAt.isNaN()) 0.75 else phaseAt(lowAt, 0.75, bars),
                phaseY = 0.25), surface = Surface.PLATE)

    /** Deck plate on a shaft: straight up and down, and it carries you. */
    fun liftPlate(x0: Double, x1: Double, top: Double, rise: Double = 1.2,
                  bars: Double = 2.0, lowAt: Double = Double.NaN) =
        Solid(x0, x1, top, -40.0,
            Motion(dy = rise, period = barSeconds * bars,
                phase = if (lowAt.isNaN()) 0.75 else phaseAt(lowAt, 0.75, bars)),
            surface = Surface.PLATE)

    // --- 11. CONVEYOR FLOOR --------------------------------------------------
    //
    // A belt, and an honest one.
    //
    // A conveyor that carried the runner is the one mechanic this game cannot
    // have. The runner's x is exactly RUN_SPEED * time; every proof FLIP ERROR
    // makes about its own fairness rests on that, and a belt that moved them
    // would make x a function of their history - at which point the solver can
    // no longer say whether a level is possible, which is not a trade worth one
    // obstacle. So this belt does what a moving platform in world 2 does: the
    // DECK ITSELF travels, the runner does not travel with it, and what changes
    // is where the ground is when they come down. The plates run under their
    // feet, the section reads as a belt, and the proof survives.

    // A belt has ENDS, and they move with it, which is the part that has to be
    // designed rather than assumed. The gap before it is widest when the belt
    // has slid away, so [landAt] derives the phase to put the belt at its
    // nearest at the moment the runner arrives - otherwise a 4.20u crossing is a
    // 6.00u crossing on half its cycles and the take-off window collapses to
    // 0.017s, which is what the first draft of LEVEL 21 measured. At the far end
    // the deck that follows has to START a full reach inside the belt's own
    // travel, or the belt slides out from under the join and leaves a hole in
    // the floor that nothing warns anybody about.

    fun conveyorFloor(x0: Double, x1: Double, top: Double, reach: Double = 2.0,
                      bars: Double = 3.0, landAt: Double = Double.NaN) =
        Solid(x0, x1, top, -40.0,
            Motion(dx = reach, period = barSeconds * bars,
                phase = if (landAt.isNaN()) 0.75 else phaseAt(landAt, 0.75, bars)),
            surface = Surface.CONVEYOR)

    /** Still deck plate. The machine's ordinary floor, so a level can say
     *  "this part is not moving" in the same language as everything else. */
    fun deck(x0: Double, x1: Double, top: Double) =
        Solid(x0, x1, top, -40.0, surface = Surface.PLATE)

    /** A part bolted down in the lane, not moving at all - the one thing in the
     *  machine that is simply in the way. Three of them a jump apart is the slot
     *  every level finishes on, where the answer is reflex and the geometry is
     *  known to the frame. */
    fun boltedPart(x: Double, base: Double, size: Double = 1.0) =
        Hazard(HazardKind.SPIKE_UP, x, x + size, base, base + size, look = Look.BOLT)
}
