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
 *   "speed": 2400, "drag": 0.25,      // optional top speed, and how fast a free ball loses speed (1/s)
 *   "friction": 0,                    // optional extra constant deceleration (units/s²)
 *   "exitRequired": 1,                // optional: balls that must reach the exit
 *   "hardcore": false,                // optional: a hard level (intense music)
 *   "border": true,                   // optional: the edges bounce the ball (never drawn)
 *   "launchZone": 260,                // optional: how far the player may move the ball before a throw
 *   "hint": {"en": "...", "ar": "..."}, // optional teaching text (a plain string means English)
 *   "guide": {"afterFails": 10, "angle": 271.5}, // optional path hint for players who keep failing
 *   "obstacles": [
 *     {"type": "wall", "points": [800, 0, 800, 600], "thickness": 30, "closed": false},
 *     {"type": "rect", "x": 300, "y": 300, "w": 200, "h": 40, "angle": 45, "round": 14},
 *     {"type": "poly", "points": [1000, 900, 1200, 600, 1400, 900]}
 *   ],
 *   "elements": [                     // optional: things with rules (see [parseElement])
 *     {"kind": "booster", "pos": [450, 900], "scale": [300, 120], "rotation": -90, "force": 6.3},
 *     {"kind": "portal", "id": "a", "link": "b", "pos": [200, 500], "scale": [140, 140]},
 *     {"kind": "portal", "id": "b", "link": "a", "pos": [700, 1500], "scale": [140, 140], "boost": 1.1}
 *   ]
 * }
 * ```
 *
 * An element takes: `kind` (solid, destructible, switchable, ballContainer, death, slower, booster,
 * repulsive, attractive, portal, touchZone, slowMo, switch), `pos` [x, y] (its centre), `scale` [w, h]
 * (a circle's diameter is w; `radius` also works), `rotation` degrees, `value` (generic value), `force`,
 * `vector` (generic vector), `snap` [gx, gy], `moving` {to, period, wave, phase}, `scaling` {to, period,
 * wave, phase}, `rotating` {speed, phase}, `deathTrigger`, `physical`, `switched`, `channel`, `active`,
 * and for portals `link`, `boost`, `decay`, `angularSpeed`. The reference's names (collisionType,
 * genericValue, genericVector, isDeathTrigger, isPhysical, isSwitched) are accepted too.
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
            val elements = (root["elements"] as? List<*>).orEmpty().mapIndexed { i, o ->
                val obj = o as? Map<*, *> ?: throw LevelFormatException("element #$i must be an object")
                parseElement(obj, "element #$i")
            }
            val exitRequired = root.number("exitRequired") ?: 1.0
            if (exitRequired < 1 || exitRequired != Math.floor(exitRequired)) throw LevelFormatException("'exitRequired' must be a whole number ≥ 1")
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
                guide = root["guide"]?.let { parseGuide(it) },
                elements = elements,
                exitRequired = exitRequired.toInt(),
                drag = (root.number("drag") ?: LevelDefaults.DRAG).also {
                    if (it < 0) throw LevelFormatException("'drag' must be ≥ 0")
                },
                hardcore = root["hardcore"] as? Boolean ?: false,
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

    /** One entry of `elements`. This is the one place element fields are named. */
    private fun parseElement(obj: Map<*, *>, where: String): Element {
        val kindName = (obj["kind"] ?: obj["collisionType"]) as? String ?: throw LevelFormatException("$where: 'kind' is required")
        val kind = ElementKind.parse(kindName) ?: throw LevelFormatException("$where: unknown kind '$kindName'")
        val shape = when (val s = (obj["shape"] as? String)?.lowercase()) {
            null -> kind.defaultShape
            "rect", "box" -> Shape.RECT
            "circle" -> Shape.CIRCLE
            else -> throw LevelFormatException("$where: unknown shape '$s'")
        }
        val pos = (obj["pos"] ?: obj["position"])?.let { point(it, "$where.pos") }
            ?: Vec2(
                obj.number("x") ?: throw LevelFormatException("$where: 'pos' is required"),
                obj.number("y") ?: throw LevelFormatException("$where: 'pos' is required"),
            )
        val scale = (obj["scale"] ?: obj["size"])?.let { point(it, "$where.scale") }
            ?: obj.number("radius")?.let { Vec2(it * 2, it * 2) }
            ?: Vec2(obj.number("w") ?: DEFAULT_ZONE, obj.number("h") ?: obj.number("w") ?: DEFAULT_ZONE)
        fun flag(vararg keys: String, default: Boolean): Boolean = keys.firstNotNullOfOrNull { obj[it] as? Boolean } ?: default
        fun num(vararg keys: String, default: Double): Double = keys.firstNotNullOfOrNull { obj.number(it) } ?: default
        val moving = if (flag("isMoving", default = true)) (obj["moving"] as? Map<*, *>)?.let { m ->
            val to = m["to"]?.let { point(it, "$where.moving.to") } ?: throw LevelFormatException("$where.moving.to is required")
            Moving(to.x, to.y, positive(m.number("period") ?: 4.0, "$where.moving.period"), wave(m["wave"], where), m.number("phase") ?: 0.0)
        } else null
        val scaling = if (flag("isScaling", default = true)) (obj["scaling"] as? Map<*, *>)?.let { m ->
            val to = m["to"]?.let { point(it, "$where.scaling.to") } ?: throw LevelFormatException("$where.scaling.to is required")
            Scaling(to.x, to.y, positive(m.number("period") ?: 4.0, "$where.scaling.period"), wave(m["wave"], where), m.number("phase") ?: 0.0)
        } else null
        val rotating = if (flag("isRotating", default = true)) (obj["rotating"] as? Map<*, *>)?.let { m ->
            Rotating(m.number("speed") ?: throw LevelFormatException("$where.rotating.speed is required"), m.number("phase") ?: 0.0)
        } else null
        val channel = num("channel", default = 0.0)
        if (channel != Math.floor(channel)) throw LevelFormatException("$where.channel must be a whole number")
        return Element(
            id = obj["id"] as? String ?: "",
            kind = kind,
            shape = shape,
            x = pos.x,
            y = pos.y,
            rotation = num("rotation", "angle", default = 0.0),
            scaleX = positive(scale.x, "$where.scale"),
            scaleY = positive(scale.y, "$where.scale"),
            value = num("value", "genericValue", default = 0.0),
            force = num("force", default = 0.0),
            vector = (obj["vector"] ?: obj["genericVector"])?.let { point(it, "$where.vector") } ?: Vec2(0.0, 0.0),
            snap = obj["snap"]?.let { point(it, "$where.snap") },
            moving = moving,
            scaling = scaling,
            rotating = rotating,
            deathTrigger = flag("deathTrigger", "isDeathTrigger", default = kind == ElementKind.DEATH),
            physical = flag("physical", "isPhysical", default = kind.physical),
            switched = flag("switched", "isSwitched", default = kind == ElementKind.SWITCHABLE),
            channel = channel.toInt(),
            active = flag("active", default = true),
            link = obj["link"] as? String ?: "",
            boost = num("boost", default = 1.0),
            decay = num("decay", default = 0.0),
            angularSpeed = num("angularSpeed", default = 0.0),
            ballBounces = num("ballBounces", default = -1.0).toInt(),
            countsAsBounce = flag("countsAsBounce", default = true),
        )
    }

    private fun wave(value: Any?, where: String): Wave = when (val w = (value as? String)?.lowercase()) {
        null, "sine", "pingpong", "smooth" -> Wave.SINE
        "linear", "triangle" -> Wave.LINEAR
        "once" -> Wave.ONCE
        else -> throw LevelFormatException("$where: unknown wave '$w'")
    }

    private const val DEFAULT_ZONE = 200.0

    /** Corner rounding of a block; 0 keeps sharp corners. */
    private fun rounding(obj: Map<*, *>, where: String): Double =
        (obj.number("round") ?: LevelDefaults.BLOCK_ROUNDING).also {
            if (it < 0) throw LevelFormatException("$where.round must be ≥ 0")
        }

    private fun parseGuide(value: Any): Guide {
        val obj = value as? Map<*, *> ?: throw LevelFormatException("'guide' must be an object")
        val after = obj.number("afterFails") ?: DEFAULT_GUIDE_AFTER
        if (after < 0 || after != Math.floor(after)) throw LevelFormatException("'guide.afterFails' must be a whole number ≥ 0")
        return Guide(
            afterFails = after.toInt(),
            angle = obj.number("angle") ?: throw LevelFormatException("'guide.angle' is required"),
            from = obj["from"]?.let { point(it, "guide.from") },
        )
    }

    private const val DEFAULT_GUIDE_AFTER = 10.0

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
