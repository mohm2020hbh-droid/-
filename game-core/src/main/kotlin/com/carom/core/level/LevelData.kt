package com.carom.core.level

import com.carom.core.math.Vec2

/**
 * Everything that defines a level. Levels are pure data: the game builds the physics world and
 * the picture from this, so adding a level never needs new code.
 *
 * Coordinates are world units with the origin at the top-left and y pointing down.
 */
data class LevelData(
    /** Stable identifier (the file name). Progress is saved against it, not against the position. */
    val id: String,
    val name: String,
    val width: Double,
    val height: Double,
    val ball: Vec2,
    val goal: Vec2,
    /** The ball scores when its centre enters this circle. */
    val goalRadius: Double,
    /** Wall hits allowed. Hitting a wall with none left ends the attempt. */
    val bounces: Int,
    /** Launch speed at full power, in units per second. */
    val maxSpeed: Double,
    /** Constant deceleration in units per second² on top of the drag; 0 (the default) means none. */
    val friction: Double,
    /** Whether the level's edges (the screen's edges) bounce the ball. They are never drawn. */
    val border: Boolean,
    /** Default thickness of walls. */
    val wallThickness: Double,
    val ballRadius: Double,
    val obstacles: List<Obstacle>,
    /**
     * The control zone: where the player may move the ball before throwing it (and clear of walls). It is the boundary
     * the dashed line marks. A [ControlZone.Circle] of the ball's own size fixes the start point.
     */
    val zone: ControlZone = ControlZone.band(width, height, ball.y),
    /** Optional teaching text by language code ("en", "ar", ...). */
    val hint: Map<String, String> = emptyMap(),
    /** Optional path hint for a hard level, shown only to a player who keeps failing it. */
    val guide: Guide? = null,
    /** Things in the level that are more than a wall: force zones, portals, breakable and moving barriers... */
    val elements: List<Element> = emptyList(),
    /** Balls that must reach the exit before the level is won. */
    val exitRequired: Int = 1,
    /** Linear drag of the ball, per second: its speed decays as e^(−drag·t). */
    val drag: Double = LevelDefaults.DRAG,
    /** A level meant to be hard: it gets the intense music. */
    val hardcore: Boolean = false,
) {
    /**
     * Distance a full-power shot travels before it stops: speed / drag with drag alone, and less with friction
     * added (infinite when neither slows the ball).
     */
    val maxReach: Double
        get() = when {
            friction > 0.0 && drag <= 0.0 -> maxSpeed * maxSpeed / (2.0 * friction)
            friction > 0.0 -> minOf(maxSpeed * maxSpeed / (2.0 * friction), maxSpeed / drag)
            drag > 0.0 -> maxSpeed / drag
            else -> Double.POSITIVE_INFINITY
        }

    /** The hint in [language], falling back to English, or null if the level has none. */
    fun hintFor(language: String): String? = hint[language] ?: hint["en"]
}

/**
 * A hint for a hard level: a full-power throw at [angle] degrees (clockwise from +x), from [from]
 * or else from the level's start, that scores. Its path is shown, roughly, once the player has
 * failed the level more than [afterFails] times.
 */
data class Guide(val afterFails: Int, val angle: Double, val from: Vec2? = null)

/**
 * Level geometry. The hierarchy is sealed so that adding a new kind makes the compiler point at
 * every place that must handle it (parser, physics builder, renderer).
 */
sealed interface Obstacle

/** A thick line or chain of lines. With [closed] the last point connects back to the first. */
data class Wall(
    val points: List<Vec2>,
    val closed: Boolean,
    val thickness: Double,
) : Obstacle

/**
 * A solid filled polygon (rectangles and triangles are polygons too). Points in order.
 *
 * Its corners are rounded by [rounding], so no block ever has a sharp point: the solid is [core]
 * (the outline pulled in by [radius]) grown by [radius] in every direction. It fills the same
 * outline as [points] except at the corners, and physics and drawing both use this exact shape.
 * Only convex outlines can be rounded this way; other outlines keep sharp corners.
 */
data class Block(val points: List<Vec2>, val rounding: Double = LevelDefaults.BLOCK_ROUNDING) : Obstacle {
    /** The polygon whose edges carry the rounding. */
    val core: List<Vec2>

    /** The rounding actually applied (smaller than [rounding] on blocks too small for it). */
    val radius: Double

    init {
        var r = rounding
        var inset: List<Vec2>? = null
        // Halve the rounding until the outline is big enough to take it.
        while (r >= 1.0) {
            inset = Polygons.insetConvex(points, r)
            if (inset != null) break
            r /= 2
        }
        core = inset ?: points
        radius = if (inset != null) r else 0.0
    }
}

/**
 * Levels are portrait 9:20 — the shape of today's phones held upright — so the level's edges are
 * the screen's edges. The ball is the thing the player holds: large (its diameter is about 13% of
 * the width) so it is easy to see, grab and read the bounce count inside it.
 */
object LevelDefaults {
    const val WIDTH = 900.0
    const val HEIGHT = 2000.0
    const val BALL_RADIUS = 60.0
    const val GOAL_RADIUS = 84.0
    const val WALL_THICKNESS = 44.0
    const val BLOCK_ROUNDING = 18.0
    const val MAX_SPEED = 2400.0

    /** No constant friction: the ball slows down by drag alone, as in the reference. */
    const val FRICTION = 0.0

    /** The reference ball's linear drag. */
    const val DRAG = 0.25

    /**
     * A level that sets no control zone gets a band across the whole level starting this far above the ball's start
     * (like the reference's dashed line). A level whose puzzle such a band would short-cut, because a shot from
     * somewhere else in it needs fewer bounces than the level is built around, sets a smaller zone.
     */
    const val ZONE_ABOVE_BALL = 260.0
}
