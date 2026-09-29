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
    /** Constant deceleration in units per second²; 0 means the ball never slows down. */
    val friction: Double,
    /** Whether the level is enclosed by a rectangular wall along its edges. */
    val border: Boolean,
    /** Thickness of the border wall and the default thickness of walls. */
    val wallThickness: Double,
    val ballRadius: Double,
    val obstacles: List<Obstacle>,
    /** Optional teaching text by language code ("en", "ar", ...). */
    val hint: Map<String, String> = emptyMap(),
) {
    /** Distance a full-power shot travels before friction stops it (infinite without friction). */
    val maxReach: Double
        get() = if (friction > 0.0) maxSpeed * maxSpeed / (2.0 * friction) else Double.POSITIVE_INFINITY

    /** The hint in [language], falling back to English, or null if the level has none. */
    fun hintFor(language: String): String? = hint[language] ?: hint["en"]
}

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

/** A solid filled polygon (rectangles and triangles are polygons too). Points in order. */
data class Block(val points: List<Vec2>) : Obstacle

object LevelDefaults {
    const val WIDTH = 1600.0
    const val HEIGHT = 900.0
    const val BALL_RADIUS = 22.0
    const val GOAL_RADIUS = 48.0
    const val WALL_THICKNESS = 14.0
    const val MAX_SPEED = 2400.0
    const val FRICTION = 600.0
}
