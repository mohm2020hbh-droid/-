package com.carom.game.ui

import android.graphics.Canvas
import android.graphics.RectF

/** A canvas-drawn button. The owning screen positions it with [bounds] and routes touches to it. */
class UiButton(
    private val style: Style,
    var label: String = "",
    var icon: Icon? = null,
    val onClick: () -> Unit,
) {
    enum class Style {
        /** Filled accent: the one action the player most likely wants. */
        PRIMARY,

        /** Thin outline: secondary actions. */
        OUTLINE,

        /** Icon only: in-game controls. */
        ICON,
    }

    val bounds = RectF()
    var pressed = false
    var visible = true

    fun setCenter(cx: Float, cy: Float, width: Float, height: Float) {
        bounds.set(cx - width / 2, cy - height / 2, cx + width / 2, cy + height / 2)
    }

    fun hit(x: Float, y: Float, slop: Float): Boolean = visible &&
        x >= bounds.left - slop && x <= bounds.right + slop && y >= bounds.top - slop && y <= bounds.bottom + slop

    fun draw(canvas: Canvas, kit: UiKit) {
        if (!visible) return
        val radius = kit.u(3f)
        val cy = bounds.centerY()
        val content: Int
        when (style) {
            Style.PRIMARY -> {
                val accent = kit.palette.accent
                kit.fill.color = if (pressed) Palette.withAlpha(accent, 0.75f) else accent
                canvas.drawRoundRect(bounds, radius, radius, kit.fill)
                content = kit.palette.background
            }
            Style.OUTLINE -> {
                if (pressed) {
                    kit.fill.color = Palette.withAlpha(Palette.TEXT, 0.1f)
                    canvas.drawRoundRect(bounds, radius, radius, kit.fill)
                }
                kit.stroke.color = Palette.LINE
                kit.stroke.strokeWidth = kit.u(1f)
                canvas.drawRoundRect(bounds, radius, radius, kit.stroke)
                content = Palette.TEXT
            }
            Style.ICON -> {
                if (pressed) {
                    kit.fill.color = Palette.withAlpha(Palette.TEXT, 0.12f)
                    canvas.drawCircle(bounds.centerX(), cy, bounds.height() / 2, kit.fill)
                }
                icon?.let { Icons.draw(canvas, kit, it, bounds.centerX(), cy, bounds.height() * 0.45f, Palette.TEXT) }
                return
            }
        }
        icon?.let {
            val size = bounds.height() * 0.42f
            val rtl = kit.text.language == "ar"
            val ix = if (rtl) bounds.right - bounds.height() * 0.6f else bounds.left + bounds.height() * 0.6f
            Icons.draw(canvas, kit, it, ix, cy, size, content)
        }
        kit.label.color = content
        kit.drawText(canvas, label, bounds.centerX(), cy, kit.label)
        kit.label.color = Palette.TEXT
    }
}
