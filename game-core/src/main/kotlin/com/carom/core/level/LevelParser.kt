package com.carom.core.level

import com.carom.core.math.Vec2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Turns level JSON into [LevelData]. Only `ball`, `goal` and `bounces` are required:
 *
 * ```
 * {
 *   "name": "First Bounce",           // optional, defaults to the id
 *   "bounces": 1,
 *   "ball": [450, 1600],
 *   "goal": [450, 400],
 *   "size": [900, 2000],              // optional
 *   "goalRadius": 48,                 // optional
 *   "speed": 2400, "friction": 600,   // optional launch power and deceleration
 *   "border": true,                   // optional: the edges bounce the ball (never drawn)
 *   "launchZone": 130,                // optional: how far the player may move the ball before a throw
 *   "hint": {"en": "...", "ar": "..."}, // optional teaching text (a plain string means English)
 *   "obstacles": [
 *     {"type": "wall", "points": [800, 0, 800, 600], "thickness": 30, "closed": false},
 *     {"type": "rect", "x": 300, "y": 300, "w": 200, "h": 40, "angle": 45, "round": 14},
 *     {"type": "poly", "points": [1000, 900, 1200, 600, 1400, 900]}
 *   ]
 * }
 * ```
 */
object LevelParser {

    fun parse(id: String, json: String): LevelData {
        val root = JsonReader.parse(json) as? Map<*, *> ?: throw LevelFormatException("$id: root must be an object")
        try {
            val size = root["size"]?.let { point(it, "size") } ?: Vec2(LevelDefaults.WIDTH, LevelDefaults.HEIGHT)
            val wallThickness = root.number("wallThickness") ?: LevelDefaults.WALL_THICKNESS
            val bounces = root.number("bounces") ?: throw LevelFormatException("'bounces' is required")
            if (bounces < 0 || bounces != Math.floor(bounces)) throw LevelFormatException("'bounces' must be a whole number ≥ 0")
            val obstacles = (root["obstacles"] as? List<*>).orEmpty().mapIndexed { i, o ->
                val obj = o as? Map<*, *> ?: throw LevelFormatException("obstacle #$i must be an object")
                parseObstacle(obj, wallThickness, "obstacle #$i")
            }
            return LevelData(
                id = id,
                name = root["name"] as? String ?: id,
                width = positive(size.x, "size"),
                height = positive(size.y, "size"),
                ball = point(root["ball"] ?: throw LevelFormatException("'ball' is required"), "ball"),
                goal = point(root["goal"] ?: throw LevelFormatException("'goal' is required"), "goal"),
                goalRadius = positive(root.number("goalRadius") ?: LevelDefaults.GOAL_RADIUS, "goalRadius"),
                bounces = bounces.toInt(),
                maxSpeed = positive(root.number("speed") ?: LevelDefaults.MAX_SPEED, "speed"),
                friction = (root.number("friction") ?: LevelDefaults.FRICTION).also {
                    if (it < 0) throw LevelFormatException("'friction' must be ≥ 0")
                },
                border = root["border"] as? Boolean ?: true,
                wallThickness = positive(wallThickness, "wallThickness"),
                ballRadius = positive(root.number("ballRadius") ?: LevelDefaults.BALL_RADIUS, "ballRadius"),
                obstacles = obstacles,
                launchZone = (root.number("launchZone") ?: LevelDefaults.LAUNCH_ZONE).also {
                    if (it < 0) throw LevelFormatException("'launchZone' must be ≥ 0")
                },
                hint = parseHint(root["hint"]),
            )
        } catch (e: LevelFormatException) {
            throw LevelFormatException("$id: ${e.message}")
        }
    }

    /** The one place obstacle types are named. A new type is a new branch here. */
    private fun parseObstacle(obj: Map<*, *>, defaultThickness: Double, where: String): Obstacle =
        when (val type = obj["type"]) {
            "wall" -> {
                val points = points(obj["points"], "$where.points")
                if (points.size < 2) throw LevelFormatException("$where: a wall needs at least 2 points")
                Wall(
                    points = points,
                    closed = obj["closed"] as? Boolean ?: false,
                    thickness = positive(obj.number("thickness") ?: defaultThickness, "$where.thickness"),
                )
            }
            "poly" -> {
                val points = points(obj["points"], "$where.points")
                if (points.size < 3) throw LevelFormatException("$where: a polygon needs at least 3 points")
                Block(points, rounding(obj, where))
            }
            "rect" -> {
                val x = obj.number("x") ?: throw LevelFormatException("$where: 'x' is required")
                val y = obj.number("y") ?: throw LevelFormatException("$where: 'y' is required")
                val w = positive(obj.number("w") ?: 0.0, "$where.w")
                val h = positive(obj.number("h") ?: 0.0, "$where.h")
                Block(rotatedRect(x, y, w, h, obj.number("angle") ?: 0.0), rounding(obj, where))
            }
            else -> throw LevelFormatException("$where: unknown type '$type'")
        }

    /** Corner rounding of a block; 0 keeps sharp corners. */
    private fun rounding(obj: Map<*, *>, where: String): Double =
        (obj.number("round") ?: LevelDefaults.BLOCK_ROUNDING).also {
            if (it < 0) throw LevelFormatException("$where.round must be ≥ 0")
        }

    private fun parseHint(value: Any?): Map<String, String> = when (value) {
        null -> emptyMap()
        is String -> mapOf("en" to value)
        is Map<*, *> -> value.entries.associate { (k, v) ->
            (k as String) to (v as? String ?: throw LevelFormatException("'hint.$k' must be a string"))
        }
        else -> throw LevelFormatException("'hint' must be a string or an object of strings")
    }

    /** Corners of a w×h rectangle at (x, y), rotated by [degrees] about its centre. */
    private fun rotatedRect(x: Double, y: Double, w: Double, h: Double, degrees: Double): List<Vec2> {
        val cx = x + w / 2
        val cy = y + h / 2
        val r = Math.toRadians(degrees)
        val c = cos(r)
        val s = sin(r)
        return listOf(-w / 2 to -h / 2, w / 2 to -h / 2, w / 2 to h / 2, -w / 2 to h / 2).map { (ox, oy) ->
            Vec2(cx + ox * c - oy * s, cy + ox * s + oy * c)
        }
    }

    private fun Map<*, *>.number(key: String): Double? = when (val v = this[key]) {
        null -> null
        is Double -> v
        else -> throw LevelFormatException("'$key' must be a number")
    }

    private fun point(value: Any, name: String): Vec2 {
        val list = value as? List<*>
        if (list == null || list.size != 2 || list.any { it !is Double }) {
            throw LevelFormatException("'$name' must be [x, y]")
        }
        return Vec2(list[0] as Double, list[1] as Double)
    }

    private fun points(value: Any?, name: String): List<Vec2> {
        val list = value as? List<*>
        if (list == null || list.size % 2 != 0 || list.any { it !is Double }) {
            throw LevelFormatException("'$name' must be a flat list [x1, y1, x2, y2, ...]")
        }
        return (list.indices step 2).map { Vec2(list[it] as Double, list[it + 1] as Double) }
    }

    private fun positive(v: Double, name: String): Double {
        if (v <= 0.0) throw LevelFormatException("'$name' must be > 0")
        return v
    }
}
