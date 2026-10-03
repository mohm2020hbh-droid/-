package com.carom.game.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.util.Locale


/**
 * A row with a name and a slider (0..1), drawn in the game's own flat style: a thin track, the part up to the value in the contrast colour,
 * a round knob. The owning screen positions it with [bounds] (the whole row) and routes touches to it.
 */
class UiSlider(var label: String, private val get: () -> Double, private val set: (Double) -> Unit) {
    val bounds = RectF()
    var dragging = false
        private set

    private val track = RectF()

    /** Where the knob can go, left and right, in pixels (set by [place]). */
    private var left = 0f
    private var right = 0f
    private var trackY = 0f

    fun place(cx: Float, cy: Float, width: Float, height: Float, kit: UiKit) {
        bounds.set(cx - width / 2, cy - height / 2, cx + width / 2, cy + height / 2)
        left = bounds.left + kit.u(10f)
        right = bounds.right - kit.u(10f)
        trackY = bounds.bottom - kit.u(14f)
    }

    fun hit(x: Float, y: Float, slop: Float): Boolean = x >= bounds.left - slop && x <= bounds.right + slop && y >= bounds.top - slop && y <= bounds.bottom + slop

    fun down(x: Float) {
        dragging = true
        move(x)
    }

    fun move(x: Float) {
        if (!dragging) return
        set(((x - left) / (right - left)).toDouble().coerceIn(0.0, 1.0))
    }

    fun up() {
        dragging = false
    }

    fun draw(canvas: Canvas, kit: UiKit) {
        val value = get().toFloat().coerceIn(0f, 1f)
        val rtl = kit.text.language == "ar"
        val textY = bounds.top + kit.u(11f)
        kit.label.color = kit.palette.ink
        kit.label.textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
        kit.drawText(canvas, label, if (rtl) bounds.right - kit.u(10f) else bounds.left + kit.u(10f), textY, kit.label)
        kit.small.color = kit.palette.inkDim
        kit.small.textAlign = if (rtl) Paint.Align.LEFT else Paint.Align.RIGHT
        kit.drawText(canvas, String.format(Locale.ROOT, "%d%%", Math.round(value * 100)), if (rtl) bounds.left + kit.u(10f) else bounds.right - kit.u(10f), textY, kit.small)
        kit.label.textAlign = Paint.Align.CENTER
        kit.small.textAlign = Paint.Align.CENTER
        kit.small.color = kit.palette.ink

        val h = kit.u(3f)
        track.set(left, trackY - h / 2, right, trackY + h / 2)
        kit.fill.color = kit.palette.line
        canvas.drawRoundRect(track, h / 2, h / 2, kit.fill)
        val x = left + (right - left) * value
        track.set(left, trackY - h / 2, x, trackY + h / 2)
        kit.fill.color = kit.palette.accent
        canvas.drawRoundRect(track, h / 2, h / 2, kit.fill)
        // The knob: a disc in the contrast colour with a quiet ring when it is held.
        if (dragging) {
            kit.fill.color = Palette.withAlpha(kit.palette.accent, 0.18f)
            canvas.drawCircle(x, trackY, kit.u(15f), kit.fill)
        }
        kit.fill.color = kit.palette.accent
        canvas.drawCircle(x, trackY, kit.u(8f), kit.fill)
    }
}

/** A row with a name and an ON / OFF switch, in the same flat style. */
class UiToggle(var label: String, private val get: () -> Boolean, private val set: (Boolean) -> Unit) {
    val bounds = RectF()
    var pressed = false

    private val pill = RectF()

    fun place(cx: Float, cy: Float, width: Float, height: Float) {
        bounds.set(cx - width / 2, cy - height / 2, cx + width / 2, cy + height / 2)
    }

    fun hit(x: Float, y: Float, slop: Float): Boolean = x >= bounds.left - slop && x <= bounds.right + slop && y >= bounds.top - slop && y <= bounds.bottom + slop

    fun toggle() = set(!get())

    fun draw(canvas: Canvas, kit: UiKit) {
        val on = get()
        val rtl = kit.text.language == "ar"
        val cy = bounds.centerY()
        if (pressed) {
            kit.fill.color = Palette.withAlpha(kit.palette.ink, 0.06f)
            canvas.drawRoundRect(bounds, kit.u(3f), kit.u(3f), kit.fill)
        }
        kit.label.color = kit.palette.ink
        kit.label.textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
        kit.drawText(canvas, label, if (rtl) bounds.right - kit.u(10f) else bounds.left + kit.u(10f), cy, kit.label)
        kit.label.textAlign = Paint.Align.CENTER

        val w = kit.u(46f)
        val h = kit.u(24f)
        val px = if (rtl) bounds.left + kit.u(10f) + w / 2 else bounds.right - kit.u(10f) - w / 2
        pill.set(px - w / 2, cy - h / 2, px + w / 2, cy + h / 2)
        if (on) {
            kit.fill.color = kit.palette.accent
            canvas.drawRoundRect(pill, h / 2, h / 2, kit.fill)
        } else {
            kit.stroke.color = kit.palette.line
            kit.stroke.strokeWidth = kit.u(1.4f)
            canvas.drawRoundRect(pill, h / 2, h / 2, kit.stroke)
        }
        val knobR = h / 2 - kit.u(4f)
        val knobX = if (on) pill.right - h / 2 else pill.left + h / 2
        kit.fill.color = if (on) kit.palette.background else kit.palette.inkDim
        canvas.drawCircle(knobX, cy, knobR, kit.fill)
    }
}

