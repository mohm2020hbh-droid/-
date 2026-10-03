package com.carom.game.ui

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

enum class Icon { PLAY, RESTART, GRID, BACK, FORWARD, CHECK, CROSS, LOCK, VIBRATION, SOUND, RING, SETTINGS }

/** Simple geometric icons drawn with paths, so the game ships no image assets. */
object Icons {
    private val path = Path()
    private val box = RectF()

    /** Draws [icon] centred at (cx, cy) within a square of [size], in [color]. */
    fun draw(canvas: Canvas, kit: UiKit, icon: Icon, cx: Float, cy: Float, size: Float, color: Int) {
        val s = size
        val stroke = kit.stroke.apply {
            this.color = color
            strokeWidth = s * 0.1f
        }
        val fill = kit.fill.apply { this.color = color }
        path.reset()
        when (icon) {
            Icon.PLAY -> {
                path.moveTo(cx - 0.3f * s, cy - 0.4f * s)
                path.lineTo(cx + 0.42f * s, cy)
                path.lineTo(cx - 0.3f * s, cy + 0.4f * s)
                path.close()
                canvas.drawPath(path, fill)
            }
            Icon.RESTART -> {
                val r = 0.36f * s
                box.set(cx - r, cy - r, cx + r, cy + r)
                canvas.drawArc(box, 40f, 280f, false, stroke)
                // Arrowhead at the arc's end (320°), pointing along the clockwise tangent.
                val a = Math.toRadians(320.0)
                val ex = cx + r * cos(a).toFloat()
                val ey = cy + r * sin(a).toFloat()
                val tx = -sin(a).toFloat()
                val ty = cos(a).toFloat()
                val h = 0.2f * s
                path.moveTo(ex + tx * h, ey + ty * h)
                path.lineTo(ex - ty * h * 0.9f, ey + tx * h * 0.9f)
                path.lineTo(ex + ty * h * 0.9f, ey - tx * h * 0.9f)
                path.close()
                canvas.drawPath(path, fill)
            }
            Icon.GRID -> {
                val c = 0.3f * s
                val g = 0.07f * s
                stroke.strokeWidth = s * 0.08f
                for (i in 0..1) for (j in 0..1) {
                    val l = if (i == 0) cx - g - c else cx + g
                    val t = if (j == 0) cy - g - c else cy + g
                    canvas.drawRect(l, t, l + c, t + c, stroke)
                }
            }
            Icon.BACK -> {
                path.moveTo(cx + 0.14f * s, cy - 0.34f * s)
                path.lineTo(cx - 0.2f * s, cy)
                path.lineTo(cx + 0.14f * s, cy + 0.34f * s)
                canvas.drawPath(path, stroke)
            }
            Icon.FORWARD -> {
                path.moveTo(cx - 0.14f * s, cy - 0.34f * s)
                path.lineTo(cx + 0.2f * s, cy)
                path.lineTo(cx - 0.14f * s, cy + 0.34f * s)
                canvas.drawPath(path, stroke)
            }
            Icon.CHECK -> {
                path.moveTo(cx - 0.34f * s, cy + 0.02f * s)
                path.lineTo(cx - 0.1f * s, cy + 0.26f * s)
                path.lineTo(cx + 0.36f * s, cy - 0.26f * s)
                canvas.drawPath(path, stroke)
            }
            Icon.CROSS -> {
                val d = 0.28f * s
                canvas.drawLine(cx - d, cy - d, cx + d, cy + d, stroke)
                canvas.drawLine(cx + d, cy - d, cx - d, cy + d, stroke)
            }
            Icon.LOCK -> {
                stroke.strokeWidth = s * 0.09f
                val r = 0.2f * s
                box.set(cx - r, cy - 0.42f * s, cx + r, cy - 0.02f * s)
                canvas.drawArc(box, 180f, 180f, false, stroke)
                canvas.drawLine(cx - r, cy - 0.22f * s, cx - r, cy, stroke)
                canvas.drawLine(cx + r, cy - 0.22f * s, cx + r, cy, stroke)
                box.set(cx - 0.32f * s, cy - 0.04f * s, cx + 0.32f * s, cy + 0.4f * s)
                canvas.drawRoundRect(box, 0.06f * s, 0.06f * s, fill)
            }
            Icon.VIBRATION -> {
                stroke.strokeWidth = s * 0.08f
                box.set(cx - 0.16f * s, cy - 0.34f * s, cx + 0.16f * s, cy + 0.34f * s)
                canvas.drawRoundRect(box, 0.06f * s, 0.06f * s, stroke)
                for (side in intArrayOf(-1, 1)) {
                    canvas.drawLine(cx + side * 0.32f * s, cy - 0.16f * s, cx + side * 0.32f * s, cy + 0.16f * s, stroke)
                    canvas.drawLine(cx + side * 0.46f * s, cy - 0.08f * s, cx + side * 0.46f * s, cy + 0.08f * s, stroke)
                }
            }
            Icon.SOUND -> {
                // A small speaker and two sound waves.
                path.moveTo(cx - 0.4f * s, cy - 0.12f * s)
                path.lineTo(cx - 0.24f * s, cy - 0.12f * s)
                path.lineTo(cx - 0.04f * s, cy - 0.32f * s)
                path.lineTo(cx - 0.04f * s, cy + 0.32f * s)
                path.lineTo(cx - 0.24f * s, cy + 0.12f * s)
                path.lineTo(cx - 0.4f * s, cy + 0.12f * s)
                path.close()
                canvas.drawPath(path, fill)
                stroke.strokeWidth = s * 0.08f
                for (r in floatArrayOf(0.2f, 0.36f)) {
                    box.set(cx - 0.06f * s - r * s, cy - r * s, cx - 0.06f * s + r * s, cy + r * s)
                    canvas.drawArc(box, -45f, 90f, false, stroke)
                }
            }
            Icon.SETTINGS -> {
                // Three sliders, each a line with a round knob.
                stroke.strokeWidth = s * 0.08f
                val knobs = floatArrayOf(0.12f, -0.14f, 0.2f)
                for (i in 0..2) {
                    val y = cy + (i - 1) * 0.3f * s
                    canvas.drawLine(cx - 0.4f * s, y, cx + 0.4f * s, y, stroke)
                    canvas.drawCircle(cx + knobs[i] * s, y, 0.1f * s, fill)
                }
            }
            Icon.RING -> {
                stroke.strokeWidth = s * 0.12f
                canvas.drawCircle(cx, cy, 0.3f * s, stroke)
                canvas.drawCircle(cx, cy, 0.09f * s, fill)
            }
        }
    }
}
