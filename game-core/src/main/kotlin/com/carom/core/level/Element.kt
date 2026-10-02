package com.carom.core.level

import com.carom.core.math.Vec2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/** How a level element reacts to the ball: the reference game's "collision type". */
enum class ElementKind(val defaultShape: Shape, val physical: Boolean) {
    /** A plain solid barrier. */
    SOLID(Shape.RECT, true),

    /** A barrier that breaks after [Element.value] counted hits. */
    DESTRUCTIBLE(Shape.RECT, true),

    /** A barrier that a switch turns on and off ([Element.switched] is on by default). */
    SWITCHABLE(Shape.RECT, true),

    /** A barrier that lets out [Element.value] more balls the first time it is hit, with its own bounce sound. */
    BALL_CONTAINER(Shape.RECT, true),

    /** A zone that kills the ball. */
    DEATH(Shape.RECT, false),

    /** A zone that drains the ball's speed smoothly. */
    SLOWER(Shape.RECT, false),

    /** A zone that pushes the ball along its direction, all the time it is inside. */
    BOOSTER(Shape.RECT, false),

    /** A zone that pushes the ball away from its centre. */
    REPULSIVE(Shape.CIRCLE, false),

    /** A zone that pulls the ball towards its centre. */
    ATTRACTIVE(Shape.CIRCLE, false),

    /** A gate to the portal named by [Element.link]. */
    PORTAL(Shape.CIRCLE, false),

    /** A zone where the player can swipe to push the ball again, and where a slow ball is not dead. */
    TOUCH_ZONE(Shape.CIRCLE, false),

    /** A touch zone that also runs game time in slow motion while the ball is in it. */
    SLOWMO_ZONE(Shape.CIRCLE, false),

    /** A trigger that flips its [Element.channel] each time the ball enters it. */
    SWITCH(Shape.CIRCLE, false);

    companion object {
        /** Reads a kind by its name, forgiving about case, spaces and underscores ("DeathZone" = "death_zone" = DEATH). */
        fun parse(name: String): ElementKind? {
            val key = name.lowercase().filter { it.isLetter() }
            return when (key) {
                "solid" -> SOLID
                "destructible" -> DESTRUCTIBLE
                "switchable" -> SWITCHABLE
                "ballcontainer", "container" -> BALL_CONTAINER
                "death", "deathzone" -> DEATH
                "slower", "slow" -> SLOWER
                "booster", "boost" -> BOOSTER
                "repulsive", "repulsor" -> REPULSIVE
                "attractive", "attractor" -> ATTRACTIVE
                "portal" -> PORTAL
                "touchzone", "touch" -> TOUCH_ZONE
                "slowmo", "slowmotouchzone", "slowmozone", "slowmotion" -> SLOWMO_ZONE
                "switch" -> SWITCH
                else -> null
            }
        }
    }
}

enum class Shape { RECT, CIRCLE }

/** The shape of a periodic change: a smooth back-and-forth, a straight back-and-forth, or one smooth trip. */
enum class Wave { SINE, LINEAR, ONCE }

/** Where the motion goes: straight to a point, round a circle, or along a chain of points. */
enum class Route { LINE, CIRCLE, POLYLINE }

/** How a motion repeats: out and back again, or round and round (a circle's orbit, a closed chain of points). */
enum class Cycle { PINGPONG, LOOP }

/**
 * Which clock a motion runs on: [LAUNCH] starts at the first throw (so the pattern is the same for every throw of an
 * attempt), [LEVEL] starts when the level does and runs while the player aims too, so the player can watch it and pick the moment.
 */
enum class Clock { LAUNCH, LEVEL }

/**
 * A deterministic motion of the element's centre; the same time always gives the same place, with no random numbers.
 *
 *  - [Route.LINE]: from the element's position to [toX], [toY] and back.
 *  - [Route.POLYLINE]: from the element's position through [points]; [Cycle.PINGPONG] goes there and back, [Cycle.LOOP]
 *    closes the chain and goes round it.
 *  - [Route.CIRCLE]: round ([centerX], [centerY]) at [radius], from [startAngle] degrees (0 = to the right, 90 = down);
 *    [Cycle.LOOP] orbits for ever, [Cycle.PINGPONG] swings [sweep] degrees and back. [clockwise] gives the direction.
 *
 * One cycle lasts [period] seconds. [delay] is how long it stands at its start before moving at all, [phase] (a fraction
 * of a cycle) is where in the cycle it starts, [pause] is how long it rests at each end of a ping-pong, [speedVar] (-1..1,
 * exclusive) makes it faster at one end of each leg than the other, and [wave] eases the ends of a straight ping-pong
 * ([Wave.SINE]), keeps its speed ([Wave.LINEAR]) or makes one trip and stays ([Wave.ONCE]).
 */
data class Moving(
    val toX: Double,
    val toY: Double,
    val period: Double,
    val wave: Wave = Wave.SINE,
    val phase: Double = 0.0,
    val route: Route = Route.LINE,
    val cycle: Cycle = Cycle.PINGPONG,
    val delay: Double = 0.0,
    val pause: Double = 0.0,
    val speedVar: Double = 0.0,
    val clock: Clock = Clock.LAUNCH,
    val points: List<Vec2> = emptyList(),
    val centerX: Double = 0.0,
    val centerY: Double = 0.0,
    val radius: Double = 0.0,
    val startAngle: Double = 0.0,
    val sweep: Double = 180.0,
    val clockwise: Boolean = true,
) {
    /** The place at [time] seconds on the motion's clock, written into [out]; [startX], [startY] is where the element is placed in the level. */
    fun at(time: Double, startX: Double, startY: Double, out: DoubleArray) {
        atCycle(if (time < delay) phase else (time - delay) / period + phase, startX, startY, out)
    }

    /** The place at position [s] in the cycle (whole numbers are the start of a cycle). */
    fun atCycle(s: Double, startX: Double, startY: Double, out: DoubleArray) {
        when (route) {
            Route.LINE -> {
                val f = legShape(legProgress(s))
                out[0] = startX + (toX - startX) * f
                out[1] = startY + (toY - startY) * f
            }
            Route.POLYLINE -> {
                val q = if (cycle == Cycle.LOOP) vary(frac(s)) else legShape(legProgress(s))
                alongChain(q, startX, startY, out)
            }
            Route.CIRCLE -> {
                val dir = if (clockwise) 1.0 else -1.0
                val angle = if (cycle == Cycle.LOOP) startAngle + dir * 360.0 * vary(frac(s)) else startAngle + dir * sweep * legShape(legProgress(s))
                val a = Math.toRadians(angle)
                out[0] = centerX + radius * cos(a)
                out[1] = centerY + radius * sin(a)
            }
        }
    }

    /** The route itself (not the timing), for drawing its track faintly: [n] + 1 points (x, y) written into [out], from the start to the far end (all the way round for a loop). */
    fun track(n: Int, startX: Double, startY: Double, out: DoubleArray) {
        for (i in 0..n) {
            val f = i.toDouble() / n
            when (route) {
                Route.LINE -> { out[2 * i] = startX + (toX - startX) * f; out[2 * i + 1] = startY + (toY - startY) * f }
                Route.POLYLINE -> {
                    alongChain(f, startX, startY, tmp2)
                    out[2 * i] = tmp2[0]; out[2 * i + 1] = tmp2[1]
                }
                Route.CIRCLE -> {
                    val dir = if (clockwise) 1.0 else -1.0
                    val a = Math.toRadians(startAngle + dir * (if (cycle == Cycle.LOOP) 360.0 else sweep) * f)
                    out[2 * i] = centerX + radius * cos(a); out[2 * i + 1] = centerY + radius * sin(a)
                }
            }
        }
    }

    private val tmp2 = DoubleArray(2)

    private fun frac(u: Double) = u - floor(u)

    /** Smooth change of speed within a leg that keeps going the same way: q + a sin(2 pi q) / (2 pi), for q in 0..1. */
    private fun vary(q: Double) = if (speedVar == 0.0) q else q + speedVar * sin(2 * Math.PI * q) / (2 * Math.PI)

    /** Progress 0..1 along the route's out-leg at cycle position [s] (back again on the way home), with the rests at each end. */
    private fun legProgress(s: Double): Double {
        if (wave == Wave.ONCE) return vary(s.coerceIn(0.0, 1.0))
        val u = frac(s)
        val d = pause / period
        val m = (1.0 - 2 * d) / 2
        val q = when {
            u < d -> 0.0
            u < d + m -> (u - d) / m
            u < 2 * d + m -> 1.0
            else -> 1.0 - (u - 2 * d - m) / m
        }
        return vary(q.coerceIn(0.0, 1.0))
    }

    private fun legShape(q: Double): Double = when (wave) {
        Wave.SINE -> 0.5 - 0.5 * cos(Math.PI * q)
        Wave.LINEAR -> q
        Wave.ONCE -> q * q * (3 - 2 * q)
    }

    /** The place a fraction [q] of the way along the chain start, [points] (closed back to the start for a loop). */
    private fun alongChain(q: Double, startX: Double, startY: Double, out: DoubleArray) {
        val closed = cycle == Cycle.LOOP
        val n = points.size + if (closed) 2 else 1
        fun px(i: Int) = if (i == 0 || i == n - 1 && closed) startX else points[i - 1].x
        fun py(i: Int) = if (i == 0 || i == n - 1 && closed) startY else points[i - 1].y
        var total = 0.0
        for (i in 0 until n - 1) total += hypot(px(i + 1) - px(i), py(i + 1) - py(i))
        var left = q.coerceIn(0.0, 1.0) * total
        for (i in 0 until n - 1) {
            val len = hypot(px(i + 1) - px(i), py(i + 1) - py(i))
            if (left <= len || i == n - 2) {
                val f = if (len > 0.0) (left / len).coerceIn(0.0, 1.0) else 0.0
                out[0] = px(i) + (px(i + 1) - px(i)) * f
                out[1] = py(i) + (py(i + 1) - py(i)) * f
                return
            }
            left -= len
        }
    }
}

/** Grows or shrinks from the element's scale to [toX] × [toY] and back, once per [period] seconds. */
data class Scaling(val toX: Double, val toY: Double, val period: Double, val wave: Wave = Wave.SINE, val phase: Double = 0.0)

/** Turns at [speed] degrees per second (negative turns the other way), starting [phase] degrees on. */
data class Rotating(val speed: Double, val phase: Double = 0.0)

/**
 * One thing in a level that is not a plain wall: a solid with rules, a force zone, a portal... The fields
 * are the reference game's per-element data (position, rotation, scale, collision type, generic value,
 * force, generic vector, snap, moving / scaling / rotating, death trigger, physical, switched) with
 * a few named extras for the relations between elements.
 *
 * Animation (moving, scaling, rotating) runs on game time from the first throw, so every attempt at a
 * level sees the same obstacle in the same place at the same moment.
 */
data class Element(
    /** Name other elements refer to (portal links). Empty if nobody refers to it. */
    val id: String,
    val kind: ElementKind,
    val shape: Shape,
    /** Centre, in world units. */
    val x: Double,
    val y: Double,
    /** Degrees, clockwise (y points down). */
    val rotation: Double = 0.0,
    /** Full width and height of a rectangle; for a circle, [scaleX] is the diameter. */
    val scaleX: Double,
    val scaleY: Double,
    /** Destructible: hits to break. Ball container: balls to release. */
    val value: Double = 0.0,
    /** Force in ref units: booster, attractive and repulsive strength; slower drain rate; the speed of released balls. */
    val force: Double = 0.0,
    /** A direction: the booster's push, or a portal's exit direction (rotated with the element). Zero = not set. */
    val vector: Vec2 = Vec2(0.0, 0.0),
    /** If set, animated positions snap to this grid. */
    val snap: Vec2? = null,
    val moving: Moving? = null,
    val scaling: Scaling? = null,
    val rotating: Rotating? = null,
    /** Touching it kills the ball. */
    val deathTrigger: Boolean = kind == ElementKind.DEATH,
    /** It blocks the ball (walls, barriers) rather than only sensing it. */
    val physical: Boolean = kind.physical,
    /** A switch flips it on and off. */
    val switched: Boolean = kind == ElementKind.SWITCHABLE,
    /** The switch line: a [ElementKind.SWITCH] flips its channel; switched elements of the same channel follow. */
    val channel: Int = 0,
    /** Whether it starts active. */
    val active: Boolean = true,
    /** Portal: the id of the portal on the other side. */
    val link: String = "",
    /** Portal: speed multiplier on exit. */
    val boost: Double = 1.0,
    /** Portal: fraction of speed lost on exit. */
    val decay: Double = 0.0,
    /** Portal: how fast it turns for the eye, degrees per second (it never affects the ball). */
    val angularSpeed: Double = 0.0,
    /** Ball container: bounces the released balls have (default: the level's). */
    val ballBounces: Int = -1,
    /** Whether a hit on it uses up a bounce (walls do; a switchable barrier that is off is not hit at all). */
    val countsAsBounce: Boolean = true,
) {
    val animated: Boolean get() = moving != null || scaling != null || rotating != null
    val isZone: Boolean get() = !physical
}
