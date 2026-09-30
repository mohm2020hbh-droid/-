package com.carom.game.render

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.carom.core.game.ElementRuntime
import com.carom.core.game.GameSession
import com.carom.core.level.ElementKind
import com.carom.core.level.LevelData
import com.carom.core.level.Shape
import com.carom.game.ui.Palette
import com.carom.game.ui.WorldPalette
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws a level's elements — barriers that move or break, force zones, portals, switches — in the world's two
 * colours and the same soft, minimal style as the walls. Each is drawn where the simulation has it now, so a
 * sliding or turning barrier is seen exactly where the ball would hit it. Zones are light and translucent, with
 * a little motion that shows what they do (chevrons run along a booster, rings fall into an attractor and
 * spread out of a repulsor). Nothing is allocated per frame.
 */
class ElementsRenderer(private val level: LevelData, private val palette: WorldPalette, private val board: BoardRenderer) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val box = RectF()
    private val path = Path()
    private var dashed: DashPathEffect? = null
    private var dotted: DashPathEffect? = null
    private var bodies: Array<Shader?> = emptyArray()

    private val light = Palette.blend(palette.primary, WHITE, 0.26f)
    private val dark = Palette.blend(palette.primary, BLACK, 0.3f)
    private val rim = Palette.blend(palette.primary, WHITE, 0.42f)

    /** Rebuilds what depends on the screen scale; call after the board is laid out. */
    fun layout(unit: Float) {
        dashed = DashPathEffect(floatArrayOf(7f * unit, 7f * unit), 0f)
        dotted = DashPathEffect(floatArrayOf(1.5f * unit, 8f * unit), 0f)
        bodies = Array(level.elements.size) { i ->
            val e = level.elements[i]
            if (!e.physical) null else {
                val h = (if (e.shape == Shape.CIRCLE) e.scaleX else e.scaleY).toFloat() * board.scale / 2
                LinearGradient(0f, -h, 0f, h, intArrayOf(light, palette.primary, dark), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            }
        }
    }

    /** Draws every element at time [clock] (seconds of screen time, for the looping motion). */
    fun draw(canvas: Canvas, session: GameSession, clock: Float, unit: Float) {
        val elements = session.elements
        // Zones first, so barriers and balls sit on top of them.
        for (i in elements.indices) if (!elements[i].data.physical) drawZone(canvas, elements[i], session, clock, unit)
        for (i in elements.indices) if (elements[i].data.physical) drawBarrier(canvas, elements[i], i, unit)
    }

    // ---------------------------------------------------------------- barriers

    private fun drawBarrier(canvas: Canvas, e: ElementRuntime, index: Int, unit: Float) {
        val s = board.scale
        val w = e.sx.toFloat() * s
        val h = (if (e.data.shape == Shape.CIRCLE) e.sx else e.sy).toFloat() * s
        canvas.save()
        canvas.translate(board.x(e.x), board.y(e.y))
        canvas.rotate(e.rotation.toFloat())
        val corner = min(18f * s, min(w, h) / 2f)
        box.set(-w / 2, -h / 2, w / 2, h / 2)
        val circle = e.data.shape == Shape.CIRCLE

        if (!e.active) {
            // Switched off or broken: only a faint dashed outline where it will be again.
            if (e.data.kind != ElementKind.DESTRUCTIBLE) {
                line.pathEffect = dashed
                line.color = Palette.withAlpha(palette.accent, 0.28f)
                line.strokeWidth = 1.5f * unit
                if (circle) canvas.drawCircle(0f, 0f, w / 2, line) else canvas.drawRoundRect(box, corner, corner, line)
                line.pathEffect = null
            }
            canvas.restore()
            return
        }

        // Body: a round-cornered panel shaded top to bottom inside a light edge, like the blocks.
        fill.shader = null
        fill.color = rim
        if (circle) canvas.drawCircle(0f, 0f, w / 2, fill) else canvas.drawRoundRect(box, corner, corner, fill)
        val rimW = max(1f, 1.4f * s)
        box.inset(rimW, rimW)
        fill.shader = bodies.getOrNull(index)
        if (circle) canvas.drawCircle(0f, 0f, w / 2 - rimW, fill) else canvas.drawRoundRect(box, max(0f, corner - rimW), max(0f, corner - rimW), fill)
        fill.shader = null

        when (e.data.kind) {
            ElementKind.DESTRUCTIBLE -> drawCracks(canvas, w, h, e.hitsLeft, e.data.value.toInt().coerceAtLeast(1), unit)
            ElementKind.BALL_CONTAINER -> drawContainerMark(canvas, min(w, h), e, unit)
            ElementKind.SWITCHABLE -> {
                // A double edge marks a barrier a switch controls.
                line.color = Palette.withAlpha(palette.accent, 0.55f)
                line.strokeWidth = 1.5f * unit
                box.inset(3f * unit, 3f * unit)
                canvas.drawRoundRect(box, max(0f, corner - 3f * unit), max(0f, corner - 3f * unit), line)
            }
            else -> {}
        }
        canvas.restore()
    }

    /** Fine jagged cracks across a breakable barrier; more of them as it takes hits. */
    private fun drawCracks(canvas: Canvas, w: Float, h: Float, left: Int, total: Int, unit: Float) {
        val damage = 1f - left.toFloat() / total
        val lines = 2 + (damage * 3f).toInt()
        line.color = Palette.withAlpha(palette.background, 0.7f)
        line.strokeWidth = max(1f, 1.6f * unit)
        path.reset()
        for (k in 0 until lines) {
            val x = (-0.32f + 0.64f * (k + 0.5f) / lines) * w
            path.moveTo(x, -h / 2)
            path.lineTo(x + h * 0.12f, -h * 0.12f)
            path.lineTo(x - h * 0.08f, h * 0.1f)
            path.lineTo(x + h * 0.06f, h / 2)
        }
        canvas.drawPath(path, line)
    }

    /**
     * A ball container: a round mark inside, with small dots for the balls it holds, and (when it lets its balls out along a
     * fixed line) a small chevron on its rim that points the way they will go.
     */
    private fun drawContainerMark(canvas: Canvas, size: Float, e: ElementRuntime, unit: Float) {
        line.color = Palette.withAlpha(palette.background, if (e.spent) 0.35f else 0.8f)
        line.strokeWidth = max(1f, 2f * unit)
        canvas.drawCircle(0f, 0f, size * 0.24f, line)
        if (!e.spent) {
            val n = e.data.value.toInt().coerceIn(1, 4)
            fill.color = Palette.withAlpha(palette.background, 0.8f)
            for (i in 0 until n) canvas.drawCircle((i - (n - 1) / 2f) * size * 0.14f, 0f, size * 0.04f, fill)
            val v = e.data.vector
            val len = hypot(v.x, v.y).toFloat()
            if (len > 0f) {
                val ux = v.x.toFloat() / len
                val uy = v.y.toFloat() / len
                val cx = ux * size * 0.37f
                val cy = uy * size * 0.37f
                val a = size * 0.07f
                path.reset()
                path.moveTo(cx - ux * a - uy * a, cy - uy * a + ux * a)
                path.lineTo(cx + ux * a, cy + uy * a)
                path.lineTo(cx - ux * a + uy * a, cy - uy * a - ux * a)
                canvas.drawPath(path, line)
            }
        }
    }

    // ---------------------------------------------------------------- zones

    private fun drawZone(canvas: Canvas, e: ElementRuntime, session: GameSession, clock: Float, unit: Float) {
        if (!e.active && e.data.kind != ElementKind.PORTAL) return
        val s = board.scale
        val w = e.sx.toFloat() * s
        val h = (if (e.data.shape == Shape.CIRCLE) e.sx else e.sy).toFloat() * s
        val circle = e.data.shape == Shape.CIRCLE
        val corner = min(24f * s, min(w, h) / 2f)
        canvas.save()
        canvas.translate(board.x(e.x), board.y(e.y))
        canvas.rotate(e.rotation.toFloat())
        box.set(-w / 2, -h / 2, w / 2, h / 2)
        val accent = palette.accent
        when (e.data.kind) {
            ElementKind.BOOSTER -> {
                shape(canvas, circle, w, corner, Palette.withAlpha(accent, 0.09f), Palette.withAlpha(accent, 0.3f), unit, dash = false)
                drawChevrons(canvas, e, w, h, clock, unit)
            }
            ElementKind.ATTRACTIVE, ElementKind.REPULSIVE -> {
                shape(canvas, circle, w, corner, Palette.withAlpha(accent, 0.06f), Palette.withAlpha(accent, 0.22f), unit, dash = true)
                val inward = e.data.kind == ElementKind.ATTRACTIVE
                val r = w / 2
                for (k in 0 until 3) {
                    val p = (clock * 0.55f + k / 3f) % 1f
                    val radius = if (inward) r * (1f - p) else r * p
                    line.color = Palette.withAlpha(accent, 0.5f * sin(p * PI.toFloat()))
                    line.strokeWidth = 2f * unit
                    canvas.drawCircle(0f, 0f, max(1f, radius), line)
                }
                fill.color = Palette.withAlpha(accent, 0.5f)
                canvas.drawCircle(0f, 0f, 3.5f * unit, fill)
            }
            ElementKind.SLOWER -> {
                shape(canvas, circle, w, corner, Palette.withAlpha(palette.primary, 0.16f), Palette.withAlpha(accent, 0.2f), unit, dash = true)
                // Rows of fine dots: the ground gone thick.
                line.pathEffect = dotted
                line.color = Palette.withAlpha(accent, 0.5f)
                line.strokeWidth = 2.2f * unit
                val rows = 3
                path.reset()
                for (i in 0 until rows) {
                    val y = (-0.5f + (i + 1f) / (rows + 1)) * h
                    path.moveTo(-w / 2 + 6f * unit, y)
                    path.lineTo(w / 2 - 6f * unit, y)
                }
                canvas.drawPath(path, line)
                line.pathEffect = null
            }
            ElementKind.DEATH -> {
                shape(canvas, circle, w, corner, Palette.withAlpha(palette.background, 0.55f), Palette.withAlpha(accent, 0.55f), unit, dash = true)
                canvas.save()
                if (circle) {
                    path.reset()
                    path.addCircle(0f, 0f, w / 2, Path.Direction.CW)
                    canvas.clipPath(path)
                } else {
                    canvas.clipRect(box)
                }
                line.color = Palette.withAlpha(accent, 0.3f)
                line.strokeWidth = 2f * unit
                val step = 22f * unit
                var d = -h - w
                while (d < w + h) {
                    canvas.drawLine(d, -h / 2 - step, d + h + 2 * step, h / 2 + step, line)
                    d += step
                }
                canvas.restore()
            }
            ElementKind.PORTAL -> drawPortal(canvas, e, w, clock, unit)
            ElementKind.TOUCH_ZONE, ElementKind.SLOWMO_ZONE -> {
                line.pathEffect = dashed
                line.color = Palette.withAlpha(accent, 0.32f)
                line.strokeWidth = 1.5f * unit
                if (circle) canvas.drawCircle(0f, 0f, w / 2, line) else canvas.drawRoundRect(box, corner, corner, line)
                line.pathEffect = null
                fill.color = Palette.withAlpha(accent, if (e.data.kind == ElementKind.SLOWMO_ZONE) 0.06f else 0.035f)
                if (circle) canvas.drawCircle(0f, 0f, w / 2, fill) else canvas.drawRoundRect(box, corner, corner, fill)
                if (e.data.kind == ElementKind.SLOWMO_ZONE) drawClockTicks(canvas, w / 2, clock, unit)
            }
            ElementKind.SWITCH -> {
                val on = session.channelOn(e.data.channel)
                shape(canvas, circle, w, corner, Palette.withAlpha(accent, 0.07f), Palette.withAlpha(accent, 0.4f), unit, dash = false)
                fill.color = if (on) accent else Palette.withAlpha(accent, 0.3f)
                canvas.drawCircle(0f, 0f, w * (if (on) 0.16f else 0.1f), fill)
            }
            else -> {}
        }
        canvas.restore()
    }

    private fun shape(canvas: Canvas, circle: Boolean, w: Float, corner: Float, fillColor: Int, edge: Int, unit: Float, dash: Boolean) {
        fill.color = fillColor
        line.color = edge
        line.strokeWidth = 1.5f * unit
        line.pathEffect = if (dash) dashed else null
        if (circle) {
            canvas.drawCircle(0f, 0f, w / 2, fill)
            canvas.drawCircle(0f, 0f, w / 2, line)
        } else {
            canvas.drawRoundRect(box, corner, corner, fill)
            canvas.drawRoundRect(box, corner, corner, line)
        }
        line.pathEffect = null
    }

    /** Chevrons that run along the booster's push, fading in and out at its ends. */
    private fun drawChevrons(canvas: Canvas, e: ElementRuntime, w: Float, h: Float, clock: Float, unit: Float) {
        val v = e.data.vector
        val angle = if (v.x == 0.0 && v.y == 0.0) 0f else Math.toDegrees(atan2(v.y, v.x)).toFloat()
        canvas.save()
        canvas.rotate(angle)
        val gap = 34f * unit
        val c = min(h, 90f * unit) * 0.2f
        val span = max(w, h)
        line.strokeWidth = 2.4f * unit
        val count = floor(span / gap).toInt() + 1
        val shift = (clock * 60f * unit) % gap
        for (i in 0 until count) {
            val x = -span / 2 + i * gap + shift
            val edge = (1f - (2f * x / span) * (2f * x / span)).coerceIn(0f, 1f)
            line.color = Palette.withAlpha(palette.accent, 0.7f * edge)
            canvas.drawLine(x - c, -c, x, 0f, line)
            canvas.drawLine(x - c, c, x, 0f, line)
        }
        canvas.restore()
    }

    /** A portal: a bright ring, two arcs turning against each other, and a soft glow. */
    private fun drawPortal(canvas: Canvas, e: ElementRuntime, w: Float, clock: Float, unit: Float) {
        val r = w / 2
        fill.color = Palette.withAlpha(palette.accent, 0.12f)
        canvas.drawCircle(0f, 0f, r, fill)
        line.color = Palette.withAlpha(palette.accent, 0.85f)
        line.strokeWidth = 3f * unit
        canvas.drawCircle(0f, 0f, r - 1.5f * unit, line)
        val speed = if (e.data.angularSpeed != 0.0) e.data.angularSpeed.toFloat() else 90f
        box.set(-r * 0.66f, -r * 0.66f, r * 0.66f, r * 0.66f)
        line.strokeWidth = 2.4f * unit
        line.color = Palette.withAlpha(palette.accent, 0.6f)
        canvas.drawArc(box, clock * speed, 100f, false, line)
        canvas.drawArc(box, clock * speed + 180f, 100f, false, line)
        box.set(-r * 0.36f, -r * 0.36f, r * 0.36f, r * 0.36f)
        line.color = Palette.withAlpha(palette.accent, 0.4f)
        canvas.drawArc(box, -clock * speed * 1.4f, 120f, false, line)
        fill.color = Palette.withAlpha(palette.accent, 0.6f)
        canvas.drawCircle(0f, 0f, 3f * unit, fill)
    }

    /** Twelve tick marks round a slow-motion zone, a hand sweeping slowly among them. */
    private fun drawClockTicks(canvas: Canvas, r: Float, clock: Float, unit: Float) {
        line.strokeWidth = 1.5f * unit
        line.color = Palette.withAlpha(palette.accent, 0.4f)
        for (i in 0 until 12) {
            val a = i * PI.toFloat() / 6f
            val inner = r - (if (i % 3 == 0) 14f else 8f) * unit
            canvas.drawLine(cos(a) * inner, sin(a) * inner, cos(a) * (r - 3f * unit), sin(a) * (r - 3f * unit), line)
        }
        val hand = clock * 0.6f
        line.color = Palette.withAlpha(palette.accent, 0.55f)
        canvas.drawLine(0f, 0f, cos(hand) * r * 0.55f, sin(hand) * r * 0.55f, line)
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
    }
}
