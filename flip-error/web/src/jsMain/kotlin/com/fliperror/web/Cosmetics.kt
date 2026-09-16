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
        else -> 0.0
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
        else -> base
    }

    fun trailAlpha(style: String, age: Double): Double = when (style) {
        "trail.neon" -> age * 0.80
        "trail.spark" -> age * age * 0.9
        "trail.pulse" -> (0.35 + 0.65 * sin(age * PI)) * age * 0.9
        "trail.rainbow" -> age * 0.82
        else -> age * 0.64
    }

    fun trailScale(style: String, age: Double): Double = when (style) {
        "trail.spark" -> 0.14 + 0.42 * age
        "trail.pulse" -> 0.22 + 0.78 * age
        else -> 0.26 + 0.62 * age
    }

    fun categoryLabel(c: Category) = when (c) {
        Category.SHAPE -> "SHAPES"
        Category.COLOR -> "COLORS"
        Category.TRAIL -> "TRAILS"
        Category.FACE -> "FACES"
    }
}
