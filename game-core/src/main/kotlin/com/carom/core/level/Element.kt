package com.carom.core.level

import com.carom.core.math.Vec2

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

/** Slides from the element's position to [toX], [toY] and back, once per [period] seconds. */
data class Moving(val toX: Double, val toY: Double, val period: Double, val wave: Wave = Wave.SINE, val phase: Double = 0.0)

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
