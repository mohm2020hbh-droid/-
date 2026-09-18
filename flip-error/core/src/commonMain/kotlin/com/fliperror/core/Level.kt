package com.fliperror.core

/** Axis-aligned box in world units. y is up; y0 is the bottom edge, y1 the top. */
data class Box(val x0: Double, val y0: Double, val x1: Double, val y1: Double) {
    fun overlaps(o: Box) = x0 < o.x1 && x1 > o.x0 && y0 < o.y1 && y1 > o.y0
    /** Shrink towards the centre by [scale] (1.0 = unchanged). */
    fun shrink(scale: Double): Box {
        val mx = (x1 - x0) * (1 - scale) / 2.0
        val my = (y1 - y0) * (1 - scale) / 2.0
        return Box(x0 + mx, y0 + my, x1 - mx, y1 - my)
    }
}

enum class HazardKind { SPIKE_UP, SPIKE_DOWN }

/**
 * What a hazard is made of.
 *
 * Art only. [HazardKind] stays what the physics reads - which way the thing
 * points, and therefore which side of it the runner has to be on - and this
 * says what the renderer should draw there. Keeping them apart is what lets
 * world 2 add ten new obstacles without touching a line of collision code, or
 * changing what a death is called.
 */
enum class Look { SPIKE, SAND_WAVE, RUIN, RELIC, GEYSER, LASER, BOULDER }

/** What a surface is made of. Art only, exactly as [Look] is. */
enum class Surface { STONE, SAND, TEMPLE, BRIDGE, MIRAGE }

/**
 * A hazard that will not stay still.
 *
 * Position is a pure function of level time, and level time is x / RUN_SPEED
 * because the runner never stops or slows. So a moving hazard is still fully
 * determined by where the player is, which is the property the level verifier
 * is built on: it can prove a moving level fair exactly as it proves a static
 * one, with no change to the search.
 *
 * The path is a sine on purpose. It is slowest at the two ends, so the hazard
 * hangs for a moment where the player has to read it, and it has no corners for
 * a player to be surprised by - GDD 11: learnable, never random.
 */
data class Motion(
    val dx: Double = 0.0,
    val dy: Double = 0.0,
    /** Seconds for one full there-and-back. */
    val period: Double = 2.0,
    /** Where in the cycle this hazard starts, 0..1. */
    val phase: Double = 0.0,
    /**
     * Extra phase on the vertical axis only. At 0 the path is a straight line;
     * at 0.25 the two axes are a quarter turn apart and the path is a circle,
     * which is how a rotating relic is built out of the same two numbers.
     */
    val phaseY: Double = 0.0,
) {
    private fun wave(u: Double) = kotlin.math.sin(u * 2.0 * kotlin.math.PI)
    fun offsetX(t: Double) = if (dx == 0.0) 0.0 else dx * wave(t / period + phase)
    fun offsetY(t: Double) = if (dy == 0.0) 0.0 else dy * wave(t / period + phase + phaseY)
    val reachX get() = kotlin.math.abs(dx)
    val reachY get() = kotlin.math.abs(dy)
}

/**
 * A platform that blinks out and back. Solid for [onFraction] of every [period].
 * Like everything else that moves here, it is a function of level time.
 */
data class Blink(val period: Double, val onFraction: Double = 0.6, val phase: Double = 0.0) {
    fun solidAt(t: Double): Boolean {
        val u = ((t / period + phase) % 1.0 + 1.0) % 1.0
        return u < onFraction
    }
    /** 0 at the moment it vanishes, 1 when it is firmly there. Drives the warning. */
    fun strengthAt(t: Double): Double {
        val u = ((t / period + phase) % 1.0 + 1.0) % 1.0
        if (u >= onFraction) return 0.0
        val left = (onFraction - u) / onFraction
        return kotlin.math.min(1.0, left * 4.0)
    }

    /**
     * How close this is to switching ON, 0..1, in the moments before it does.
     *
     * This is the whole difference between a laser and an unfair death. A beam
     * that simply appears is a coin flip; a beam that spends a quarter of a
     * second visibly charging is a timing problem. The renderer draws this, the
     * physics ignores it.
     */
    fun warmAt(t: Double): Double {
        val u = ((t / period + phase) % 1.0 + 1.0) % 1.0
        if (u < onFraction) return 0.0
        val toGo = 1.0 - u
        val warn = kotlin.math.min(0.28, (1.0 - onFraction) * 0.6)
        if (toGo > warn) return 0.0
        return 1.0 - toGo / warn
    }
}

/**
 * The phase that has a blinking floor switch ON just before the runner arrives
 * at [x], with [spent] of its ON window already gone when they land.
 *
 * A blink phase is never a taste decision, it is arithmetic. The runner's x is
 * exactly RUN_SPEED * time, so the instant they touch a platform is a property
 * of where that platform IS - and a phase picked by hand is a guess at that
 * sum. Guessing it wrong has cost this project twice: once outright, with a
 * LEVEL 6 that no sequence of taps could finish, and once quietly, with a
 * LEVEL 5 whose blinker happened to be dark at the arrival its geometry
 * implied, so the only way across was a second tap in the last frames of its
 * window - which reads from the player's chair as a wall, not as a mistake.
 *
 * [spent] is how much of the ON window is already gone when they get there, so
 * a derived floor is always visibly on its way out from the moment it is landed
 * on, and the pressure is to keep moving rather than to guess.
 */
fun blinkPhaseFor(x: Double, period: Double, spent: Double = 0.05): Double {
    val u = spent - (x / Tuning.RUN_SPEED) / period
    return ((u % 1.0) + 1.0) % 1.0
}

/**
 * A solid block. Landable on top, lethal to run into from the side.
 *
 * A vertical mover carries whoever is standing on it; a horizontal one does not.
 * That asymmetry is deliberate and load-bearing: the runner's x must stay exactly
 * RUN_SPEED * time, because the entire fairness proof rests on the world being a
 * function of where the player is. A conveyor would break it. A lift does not,
 * because y is already part of what the search tracks.
 */
data class Solid(
    val x0: Double, val x1: Double, val top: Double, val bottom: Double = -40.0,
    val motion: Motion? = null,
    val blink: Blink? = null,
    val surface: Surface = Surface.STONE,
) {
    val box get() = Box(x0, bottom, x1, top)
    val moves get() = motion != null
    val blinks get() = blink != null

    fun offsetX(t: Double) = motion?.offsetX(t) ?: 0.0
    fun offsetY(t: Double) = motion?.offsetY(t) ?: 0.0
    fun topAt(t: Double) = top + offsetY(t)
    fun bottomAt(t: Double) = bottom + offsetY(t)
    fun x0At(t: Double) = x0 + offsetX(t)
    fun x1At(t: Double) = x1 + offsetX(t)
    fun presentAt(t: Double) = blink?.solidAt(t) ?: true
}

data class Hazard(
    val kind: HazardKind,
    val x0: Double, val x1: Double, val y0: Double, val y1: Double,
    val motion: Motion? = null,
    /** Present for part of every cycle: a geyser, a beam, a falling block. */
    val blink: Blink? = null,
    val look: Look = Look.SPIKE,
) {
    /** GDD fairness law 3: the killing box is 15% smaller than the drawing. */
    private val restingHit: Box = Box(x0, y0, x1, y1).shrink(Tuning.HAZARD_HITBOX_SCALE)
    private val restingDraw: Box = Box(x0, y0, x1, y1)
    val moves get() = motion != null

    private fun shift(b: Box, t: Double): Box {
        val m = motion ?: return b
        val ox = m.offsetX(t)
        val oy = m.offsetY(t)
        return Box(b.x0 + ox, b.y0 + oy, b.x1 + ox, b.y1 + oy)
    }

    fun hitBoxAt(t: Double): Box = shift(restingHit, t)
    fun drawBoxAt(t: Double): Box = shift(restingDraw, t)

    val pulses get() = blink != null
    /** Lethal right now? An off-cycle geyser is scenery. */
    fun activeAt(t: Double) = blink?.solidAt(t) ?: true
    /** 0..1 in the moments before it becomes lethal. Drawn, never collided with. */
    fun warmAt(t: Double) = blink?.warmAt(t) ?: 0.0
}

/**
 * A column of moving air. It pushes the runner UP or DOWN, never sideways.
 *
 * Sideways was the obvious version and it is the one that cannot exist here:
 * the runner's x is exactly RUN_SPEED * time, and every proof this game makes
 * about its own fairness is built on that. A horizontal gust would make x a
 * function of the player's history instead, and the verifier could no longer
 * say whether a level was possible. Vertical air changes how high a jump goes
 * without touching how far it reaches, so the invariant survives and the
 * mechanic still reads as wind.
 */
data class Wind(val x0: Double, val x1: Double, val push: Double) {
    fun covers(px0: Double, px1: Double) = px1 > x0 && px0 < x1
}

/**
 * A stretch of level the player sees through a sandstorm.
 *
 * It is WEATHER, not a hazard: it has no box, it cannot kill anyone, and the
 * verifier does not know it exists. That is deliberate and it is the only
 * honest way to ship reduced visibility in a game that promises you always know
 * why you died. A storm that could kill you would be a hazard you cannot see,
 * which is the definition of the random death this project does not do.
 *
 * What it dims is the SCENERY - the sky, the dunes, the ruins - and the light in
 * the air between them. Hazards, ground and the runner are drawn after it and at
 * full strength, because readability outranks the effect (GDD 11). The storm
 * makes the world feel enormous and hostile without ever taking away the one
 * thing the player needs to see.
 */
data class Storm(val x0: Double, val x1: Double, val strength: Double = 0.7) {
    /** 0 outside, rising to [strength] in the middle - a storm has edges you
     *  can watch yourself run into, rather than a wall you cross. */
    fun at(x: Double): Double {
        if (x <= x0 || x >= x1) return 0.0
        val span = x1 - x0
        val edge = kotlin.math.min(span * 0.22, 14.0)
        val into = kotlin.math.min(x - x0, x1 - x)
        return strength * kotlin.math.min(1.0, into / edge)
    }
}

data class Star(val x: Double, val y: Double) {
    val box get() = Box(x - 0.45, y - 0.45, x + 0.45, y + 0.45)
}

data class Level(
    val id: Int,
    val name: String,
    val subtitle: String,
    val bpm: Double,
    val solids: List<Solid>,
    val hazards: List<Hazard>,
    val stars: List<Star>,
    val finishX: Double,
    val startY: Double = 0.0,
    val winds: List<Wind> = emptyList(),
    val storms: List<Storm> = emptyList(),
) {
    /** Level length in seconds at the level's run speed. */
    val durationSeconds: Double get() = finishX / Tuning.RUN_SPEED

    // Each level carries its own tempo, and its geometry is laid out on it.
    // Tuning.BPM is the grid levels 1 and 2 were built on and cannot move
    // without moving their spikes; later levels simply run faster.
    val beat: Double get() = 60.0 / bpm
    val bar: Double get() = 4.0 * beat
    val beatUnits: Double get() = Tuning.RUN_SPEED * beat
    val barUnits: Double get() = 4.0 * beatUnits

    @PublishedApi internal val solidsSorted = solids.sortedBy { it.x0 }
    @PublishedApi internal val hazardsSorted = hazards.sortedBy { it.x0 }
    /** How far any hazard can wander sideways. The near-search widens by this. */
    @PublishedApi internal val hazardReachX = hazards.maxOfOrNull { it.motion?.reachX ?: 0.0 } ?: 0.0
    @PublishedApi internal val solidReachX = solids.maxOfOrNull { it.motion?.reachX ?: 0.0 } ?: 0.0

    /**
     * Visit every solid whose x-range can touch [x0,x1].
     * Callback form on purpose: this runs 240 times a second and a mobile
     * frame budget has no room for allocating a fresh list each step.
     */
    /** Widened by [solidReachX] so a platform that has slid towards the runner is
     *  still visited; the caller decides where it actually is right now. */
    inline fun forEachSolidNear(x0: Double, x1: Double, action: (Solid) -> Unit) {
        val lo = x0 - solidReachX
        val hi = x1 + solidReachX
        for (i in solidsSorted.indices) {
            val s = solidsSorted[i]
            if (s.x0 > hi) break
            if (s.x1 >= lo) action(s)
        }
    }

    /** Widened by [hazardReachX] so a hazard that has slid towards the runner is
     *  still visited; the caller decides where it actually is right now. */
    inline fun forEachHazardNear(x0: Double, x1: Double, action: (Hazard) -> Unit) {
        val lo = x0 - hazardReachX
        val hi = x1 + hazardReachX
        for (i in hazardsSorted.indices) {
            val h = hazardsSorted[i]
            if (h.x0 > hi) break
            if (h.x1 >= lo) action(h)
        }
    }
}
