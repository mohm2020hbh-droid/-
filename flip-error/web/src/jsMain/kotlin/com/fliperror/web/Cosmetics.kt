package com.fliperror.web

import com.fliperror.core.Category
import com.fliperror.core.Face
import org.w3c.dom.CanvasRenderingContext2D
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The look of the runner, and the one place it is decided.
 *
 * The shop preview and the game draw through exactly these functions, so a card
 * in the shop cannot promise a shape the level does not deliver. Everything here
 * is appearance: nothing reads a hitbox, a speed or a jump.
 */
object Palette {
    // GDD 11.2, unchanged and non-negotiable: hot red belongs to death alone.
    const val HAZARD = "#ff2e63"
    const val HAZARD_DIM = "#6b0f28"

    const val SAFE = "#2ef0ff"          // solid ground: electric blue-cyan
    const val SAFE_FILL = "#08192b"
    const val FINISH = "#7cffb2"
    const val COIN = "#ffd93d"
    const val BOOST = "#ffffff"         // the second jump's flash, used nowhere else
    const val INK = "#05060f"

    /** Player colours. Bright, saturated, and none of them near hazard red. */
    private val playerColours = mapOf(
        "color.yellow" to "#ffd329",
        "color.cyan" to "#22e7ff",
        "color.pink" to "#ff3ba7",
        "color.green" to "#39ff9e",
        "color.violet" to "#b07bff",
        "color.orange" to "#ff9130",
    )

    fun player(id: String) = playerColours[id] ?: playerColours.getValue("color.yellow")

    /** The same hue, dimmed, for the inside of the shape. */
    fun playerFill(id: String) = when (id) {
        "color.cyan" -> "#03222b"
        "color.pink" -> "#2b0620"
        "color.green" -> "#042a1c"
        "color.violet" -> "#1a0f2e"
        "color.orange" -> "#2b1403"
        else -> "#2a2003"
    }
}

/**
 * A world's colour scheme. Level 1 keeps the cyan-and-yellow the game was built
 * in; later worlds shift the environment while the two colours that carry
 * meaning - hazard red and the player's own - stay exactly where they are.
 */
/** What kind of place a world is. The renderer draws a different scene for each. */
enum class Scene { CITY, DESERT }

class Theme(
    val skyTop: String, val skyMid: String, val skyLow: String,
    val far: String, val mid: String, val near: String,
    val horizon: String, val billboard: String,
    /** A third sign colour, so the city is not lit by two bulbs. */
    val accent: String = "#ff3ba7",
    val scene: Scene = Scene.CITY,
    /** The desert's sun, and the light everything in it is lit by. */
    val sun: String = "#ffb03a",
    val sunCore: String = "#fff1c4",
) {
    companion object {
        private val WORLDS = listOf(
            // --- WORLD 1: NEON CITY, night, cyan and yellow ------------------
            Theme("#0b1030", "#151a4a", "#05060f", "#20276e", "#2a3f9c", "#3a6fd0",
                "#2ef0ff", "#ffd329", "#ff3ba7", Scene.CITY),
            // --- WORLD 2: NEON DESERT, a sun going down on broken ruins -------
            // Burning orange through to deep purple, with electric cyan kept for
            // the technical parts so the eye still knows what is machinery.
            Theme("#2b0b3a", "#7a1c44", "#1a0616", "#43102f", "#7a2a2c", "#b8481f",
                "#ff8a1f", "#ff2e8b", "#ffd166", Scene.DESERT, "#ff9a2a", "#ffe9a8"),
            // --- WORLD 3: deeper into the waste, hotter and angrier -----------
            Theme("#35061f", "#8c1630", "#1a0410", "#530f22", "#8f2320", "#cc4a12",
                "#ff6a12", "#ff2e63", "#ffb03a", Scene.DESERT, "#ff5a18", "#fff0c0"),
            // --- WORLD 4: green + cyan ----------------------------------------
            Theme("#04231f", "#063a33", "#05060f", "#0a5348", "#0d7a63", "#12b089",
                "#39ff9e", "#2ef0ff", "#7cffb2", Scene.CITY),
            // --- WORLD 5: orange + red ----------------------------------------
            Theme("#2a0f06", "#43180a", "#05060f", "#5e2410", "#8c3a15", "#c25a1c",
                "#ff9130", "#ffd329", "#ff3ba7", Scene.CITY),
            // --- WORLD 6: electric white + blue -------------------------------
            Theme("#0a1230", "#122048", "#05060f", "#1d3570", "#2a53a8", "#3f84e0",
                "#dff4ff", "#2ef0ff", "#8b5cff", Scene.CITY),
        )

        /** Six levels to a world: 1-6 the city, 7-12 the desert, and on. */
        const val LEVELS_PER_WORLD = 6
        fun worldOf(id: Int) = ((id - 1) / LEVELS_PER_WORLD) + 1
        fun forLevel(id: Int) = WORLDS[(worldOf(id) - 1).coerceIn(0, WORLDS.size - 1)]
    }
}

object Art {

    /** The runner's silhouette, centred on the origin, as a path ready to fill or stroke. */
    fun shapePath(ctx: CanvasRenderingContext2D, id: String, size: Double) {
        val h = size / 2
        ctx.beginPath()
        when (id) {
            "shape.triangle" -> {
                val r = size * 0.62
                for (k in 0 until 3) {
                    val a = -PI / 2 + k * 2 * PI / 3
                    val x = cos(a) * r; val y = sin(a) * r * 0.92
                    if (k == 0) ctx.moveTo(x, y) else ctx.lineTo(x, y)
                }
                ctx.closePath()
            }
            "shape.circle" -> ctx.arc(0.0, 0.0, h, 0.0, PI * 2)
            "shape.diamond" -> {
                ctx.moveTo(0.0, -h * 1.15); ctx.lineTo(h * 0.95, 0.0)
                ctx.lineTo(0.0, h * 1.15); ctx.lineTo(-h * 0.95, 0.0); ctx.closePath()
            }
            "shape.star" -> {
                for (k in 0 until 10) {
                    val r = if (k % 2 == 0) h * 1.22 else h * 0.52
                    val a = -PI / 2 + k * PI / 5
                    val x = cos(a) * r; val y = sin(a) * r
                    if (k == 0) ctx.moveTo(x, y) else ctx.lineTo(x, y)
                }
                ctx.closePath()
            }
            "shape.hexagon" -> {
                for (k in 0 until 6) {
                    val a = k * PI / 3
                    val x = cos(a) * h * 1.12; val y = sin(a) * h * 1.12
                    if (k == 0) ctx.moveTo(x, y) else ctx.lineTo(x, y)
                }
                ctx.closePath()
            }
            "shape.octagon" -> {
                for (k in 0 until 8) {
                    val a = PI / 8 + k * PI / 4
                    val x = cos(a) * h * 1.10; val y = sin(a) * h * 1.10
                    if (k == 0) ctx.moveTo(x, y) else ctx.lineTo(x, y)
                }
                ctx.closePath()
            }
            "shape.bolt" -> {
                ctx.moveTo(h * 0.16, -h * 1.20)
                ctx.lineTo(-h * 0.95, h * 0.12)
                ctx.lineTo(-h * 0.10, h * 0.12)
                ctx.lineTo(-h * 0.22, h * 1.20)
                ctx.lineTo(h * 0.95, -h * 0.16)
                ctx.lineTo(h * 0.10, -h * 0.16)
                ctx.closePath()
            }
            "shape.arrow" -> {
                ctx.moveTo(h * 1.20, 0.0)
                ctx.lineTo(h * 0.10, -h * 1.05)
                ctx.lineTo(h * 0.10, -h * 0.42)
                ctx.lineTo(-h * 1.15, -h * 0.42)
                ctx.lineTo(-h * 1.15, h * 0.42)
                ctx.lineTo(h * 0.10, h * 0.42)
                ctx.lineTo(h * 0.10, h * 1.05)
                ctx.closePath()
            }
            "shape.crystal" -> {
                ctx.moveTo(0.0, -h * 1.30)
                ctx.lineTo(h * 0.86, -h * 0.30)
                ctx.lineTo(h * 0.52, h * 1.18)
                ctx.lineTo(-h * 0.52, h * 1.18)
                ctx.lineTo(-h * 0.86, -h * 0.30)
                ctx.closePath()
            }
            "shape.ring" -> {
                ctx.arc(0.0, 0.0, h * 1.10, 0.0, PI * 2)
                ctx.moveTo(h * 0.52, 0.0)
                ctx.arc(0.0, 0.0, h * 0.52, 0.0, PI * 2, anticlockwise = true)
            }
            "shape.cross" -> {
                val a = h * 0.40
                val b = h * 1.15
                ctx.moveTo(-a, -b); ctx.lineTo(a, -b); ctx.lineTo(a, -a); ctx.lineTo(b, -a)
                ctx.lineTo(b, a); ctx.lineTo(a, a); ctx.lineTo(a, b); ctx.lineTo(-a, b)
                ctx.lineTo(-a, a); ctx.lineTo(-b, a); ctx.lineTo(-b, -a); ctx.lineTo(-a, -a)
                ctx.closePath()
            }
            // --- the three the desert gives back ------------------------------
            // Each is built the same way as every other shape here: one closed
            // path around the origin, sized so the drawn silhouette still sits in
            // the same box the hitbox uses. A cosmetic that changed the runner's
            // outline would change the game, and nothing in this shop does that.
            "shape.scarab" -> {
                ctx.moveTo(0.0, -h * 1.10)
                ctx.quadraticCurveTo(h * 0.86, -h * 0.80, h * 0.74, 0.0)
                ctx.quadraticCurveTo(h * 1.16, h * 0.18, h * 0.62, h * 0.52)
                ctx.quadraticCurveTo(h * 0.42, h * 1.16, 0.0, h * 1.02)
                ctx.quadraticCurveTo(-h * 0.42, h * 1.16, -h * 0.62, h * 0.52)
                ctx.quadraticCurveTo(-h * 1.16, h * 0.18, -h * 0.74, 0.0)
                ctx.quadraticCurveTo(-h * 0.86, -h * 0.80, 0.0, -h * 1.10)
                ctx.closePath()
            }
            "shape.ankh" -> {
                val r = h * 0.46
                val stem = h * 0.22
                ctx.arc(0.0, -h * 0.52, r, 0.0, PI * 2)
                ctx.moveTo(-stem, -h * 0.06)
                ctx.lineTo(-h * 1.02, -h * 0.06); ctx.lineTo(-h * 1.02, h * 0.30)
                ctx.lineTo(-stem, h * 0.30); ctx.lineTo(-stem, h * 1.18)
                ctx.lineTo(stem, h * 1.18); ctx.lineTo(stem, h * 0.30)
                ctx.lineTo(h * 1.02, h * 0.30); ctx.lineTo(h * 1.02, -h * 0.06)
                ctx.lineTo(stem, -h * 0.06)
                ctx.closePath()
            }
            "shape.sun" -> {
                val outer = h * 1.18
                val inner = h * 0.66
                for (k in 0 until 24) {
                    val a = -PI / 2 + k * PI / 12
                    val rr = if (k % 2 == 0) outer else inner
                    val px = cos(a) * rr; val py = sin(a) * rr
                    if (k == 0) ctx.moveTo(px, py) else ctx.lineTo(px, py)
                }
                ctx.closePath()
            }
            "shape.cat" -> {
                val r = size * 0.16
                ctx.moveTo(-h + r, -h * 0.72)
                ctx.lineTo(-h * 0.62, -h * 0.72)
                ctx.lineTo(-h * 0.50, -h * 1.30)                 // left ear
                ctx.lineTo(-h * 0.08, -h * 0.72)
                ctx.lineTo(h * 0.08, -h * 0.72)
                ctx.lineTo(h * 0.50, -h * 1.30)                  // right ear
                ctx.lineTo(h * 0.62, -h * 0.72)
                ctx.lineTo(h - r, -h * 0.72)
                ctx.quadraticCurveTo(h, -h * 0.72, h, -h * 0.72 + r)
                ctx.lineTo(h, h - r); ctx.quadraticCurveTo(h, h, h - r, h)
                ctx.lineTo(-h + r, h); ctx.quadraticCurveTo(-h, h, -h, h - r)
                ctx.lineTo(-h, -h * 0.72 + r)
                ctx.quadraticCurveTo(-h, -h * 0.72, -h + r, -h * 0.72)
                ctx.closePath()
            }
            else -> {                                            // shape.square
                val r = size * 0.14
                ctx.moveTo(-h + r, -h); ctx.lineTo(h - r, -h); ctx.quadraticCurveTo(h, -h, h, -h + r)
                ctx.lineTo(h, h - r); ctx.quadraticCurveTo(h, h, h - r, h)
                ctx.lineTo(-h + r, h); ctx.quadraticCurveTo(-h, h, -h, h - r)
                ctx.lineTo(-h, -h + r); ctx.quadraticCurveTo(-h, -h, -h + r, -h)
                ctx.closePath()
            }
        }
    }

    /**
     * GDD 2.2: the face is a readability element. Two of its four states are
     * therefore fixed whatever the player bought - death is always X eyes, and
     * the second jump always has its own wide-eyed read. Only the running and
     * jumping faces carry a style.
     */
    /** Where a face sits inside each silhouette. A triangle has no room down low. */
    fun faceOffset(shape: String, s: Double) = when (shape) {
        "shape.triangle" -> s * 0.10
        "shape.star" -> s * 0.02
        "shape.cat" -> s * 0.06
        "shape.crystal" -> s * 0.06
        "shape.arrow" -> -s * 0.02
        else -> 0.0
    }

    /** Some silhouettes have less room for a face than a square does. */
    fun faceScale(shape: String) = when (shape) {
        "shape.triangle" -> 0.78
        "shape.bolt" -> 0.62
        "shape.ring" -> 0.0            // a ring has nowhere to put one
        "shape.cross" -> 0.66
        "shape.arrow" -> 0.72
        "shape.star" -> 0.70
        "shape.sun" -> 0.62
        "shape.ankh" -> 0.58
        "shape.scarab" -> 0.82
        else -> 1.0
    }

    fun face(ctx: CanvasRenderingContext2D, style: String, state: Face, s: Double, colour: String) {
        val eye = s * 0.13
        val ey = -s * 0.10
        ctx.fillStyle = colour
        ctx.strokeStyle = colour
        when (state) {
            Face.DEAD -> {
                ctx.strokeStyle = Palette.HAZARD
                ctx.lineWidth = s * 0.07
                for (sgn in listOf(-1.0, 1.0)) {
                    val ox = sgn * s * 0.22
                    ctx.beginPath()
                    ctx.moveTo(ox - eye, ey - eye); ctx.lineTo(ox + eye, ey + eye)
                    ctx.moveTo(ox + eye, ey - eye); ctx.lineTo(ox - eye, ey + eye)
                    ctx.stroke()
                }
                ctx.beginPath(); ctx.arc(0.0, s * 0.24, s * 0.17, 1.15 * PI, 1.85 * PI); ctx.stroke()
            }
            Face.DOUBLE -> {
                ctx.beginPath()
                ctx.arc(-s * 0.23, ey - s * 0.02, eye * 0.85, 0.0, PI * 2)
                ctx.arc(s * 0.23, ey - s * 0.02, eye * 0.85, 0.0, PI * 2)
                ctx.fill()
                ctx.beginPath(); ctx.ellipse(0.0, s * 0.15, s * 0.13, s * 0.20, 0.0, 0.0, PI * 2); ctx.fill()
            }
            else -> styledFace(ctx, style, state == Face.JUMP, s, eye, ey)
        }
    }

    private fun styledFace(
        ctx: CanvasRenderingContext2D, style: String, jumping: Boolean,
        s: Double, eye: Double, ey: Double,
    ) {
        when (style) {
            "face.cool" -> {
                // one visor, so it stays one clear shape at phone size
                ctx.fillRect(-s * 0.34, ey - eye * 0.75, s * 0.68, eye * 1.5)
                ctx.beginPath()
                if (jumping) ctx.ellipse(0.0, s * 0.18, s * 0.11, s * 0.15, 0.0, 0.0, PI * 2)
                else ctx.ellipse(s * 0.10, s * 0.17, s * 0.12, s * 0.06, 0.0, 0.0, PI * 2)
                ctx.fill()
            }
            "face.sleepy" -> {
                ctx.lineWidth = s * 0.06
                for (sgn in listOf(-1.0, 1.0)) {
                    ctx.beginPath()
                    ctx.arc(sgn * s * 0.22, ey, eye * 1.1, if (jumping) 1.1 * PI else 0.05 * PI,
                        if (jumping) 1.9 * PI else 0.95 * PI)
                    ctx.stroke()
                }
                ctx.beginPath()
                ctx.ellipse(0.0, s * 0.18, s * 0.07, s * (if (jumping) 0.13 else 0.07), 0.0, 0.0, PI * 2)
                ctx.fill()
            }
            "face.grin" -> {
                ctx.fillRect(-s * 0.24 - eye / 2, ey - eye * 0.7, eye, eye * 1.4)
                ctx.fillRect(s * 0.24 - eye / 2, ey - eye * 0.7, eye, eye * 1.4)
                ctx.lineWidth = s * 0.075
                ctx.beginPath()
                ctx.arc(0.0, s * 0.02, s * 0.26, 0.1 * PI, 0.9 * PI)
                ctx.stroke()
                if (!jumping) {
                    ctx.lineWidth = s * 0.05
                    ctx.beginPath(); ctx.moveTo(-s * 0.26, s * 0.10); ctx.lineTo(s * 0.26, s * 0.10); ctx.stroke()
                }
            }
            else -> {                                             // face.classic
                if (jumping) {
                    ctx.fillRect(-s * 0.24 - eye / 2, ey - eye * 0.8, eye, eye * 1.5)
                    ctx.fillRect(s * 0.24 - eye / 2, ey - eye * 0.8, eye, eye * 1.5)
                    ctx.beginPath(); ctx.ellipse(0.0, s * 0.12, s * 0.15, s * 0.17, 0.0, 0.0, PI * 2); ctx.fill()
                } else {
                    ctx.fillRect(-s * 0.22 - eye / 2, ey - eye / 2, eye, eye)
                    ctx.fillRect(s * 0.22 - eye / 2, ey - eye / 2, eye, eye)
                    ctx.lineWidth = s * 0.07
                    ctx.beginPath(); ctx.arc(0.0, s * 0.06, s * 0.20, 0.15 * PI, 0.85 * PI); ctx.stroke()
                }
            }
        }
    }

    /** How a trail style paints ghost number [i] of a trail, [age] from 1 (new) to 0. */
    fun trailColour(style: String, base: String, age: Double, i: Int): String = when (style) {
        "trail.neon" -> Palette.SAFE
        "trail.spark" -> if (i % 2 == 0) Palette.COIN else base
        "trail.pulse" -> if (age > 0.55) Palette.BOOST else base
        "trail.rainbow" -> {
            val hue = ((i * 26) % 360)
            "hsl($hue, 100%, 62%)"
        }
        // The desert's two. SANDSTORM goes pale and grainy as it falls behind,
        // like grit losing the light; EMBER cools from white through orange to
        // deep red, which is the sun going down over the whole trail at once.
        "trail.sand" -> if (i % 3 == 0) "#ffe9a8" else "#ff9a2a"
        "trail.ember" -> {
            val hue = 4 + 44 * age
            val light = 44 + 34 * age
            "hsl($hue, 96%, $light%)"
        }
        else -> base
    }

    fun trailAlpha(style: String, age: Double): Double = when (style) {
        "trail.neon" -> age * 0.80
        "trail.spark" -> age * age * 0.9
        "trail.pulse" -> (0.35 + 0.65 * sin(age * PI)) * age * 0.9
        "trail.rainbow" -> age * 0.82
        "trail.sand" -> age * age * 0.72
        "trail.ember" -> (0.30 + 0.70 * age) * 0.86
        else -> age * 0.64
    }

    fun trailScale(style: String, age: Double): Double = when (style) {
        "trail.spark" -> 0.14 + 0.42 * age
        "trail.pulse" -> 0.22 + 0.78 * age
        "trail.sand" -> 0.10 + 0.50 * age            // it scatters rather than shrinks
        "trail.ember" -> 0.30 + 0.70 * age
        else -> 0.26 + 0.62 * age
    }

    fun categoryLabel(c: Category) = when (c) {
        Category.SHAPE -> "SHAPES"
        Category.COLOR -> "COLORS"
        Category.TRAIL -> "TRAILS"
        Category.FACE -> "FACES"
    }
}
