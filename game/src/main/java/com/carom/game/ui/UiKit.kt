package com.carom.game.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

/** The game's few colours. Dark, calm background; one accent; the ball and walls stand out. */
object Palette {
    const val BACKGROUND = 0xFF10141C.toInt()
    const val LINE = 0xFF2C3545.toInt()
    const val TEXT = 0xFFE7EAF0.toInt()
    const val TEXT_DIM = 0xFF7E889B.toInt()
    const val ACCENT = 0xFFF4C152.toInt()
    const val ON_ACCENT = 0xFF171A21.toInt()
    const val BALL = 0xFF6FE3B4.toInt()
    const val DANGER = 0xFFEE6E68.toInt()

    const val LEVELS_PER_CHAPTER = 15

    /** Each chapter of 15 levels gets its own wall colour. */
    private val WALLS = intArrayOf(0xFFE6D9BF.toInt(), 0xFF9FC9EE.toInt(), 0xFFC9B9F2.toInt(), 0xFFF2B5A2.toInt())

    fun wallColor(levelIndex: Int): Int = WALLS[(levelIndex / LEVELS_PER_CHAPTER) % WALLS.size]

    fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)
}

/**
 * Shared paints and measurements for the canvas-drawn UI. [unit] is one "UI unit": the screen is
 * designed as 640×360 units, so the interface keeps its proportions on every screen size.
 */
class UiKit(val unit: Float, val text: UiText) {

    fun u(v: Float): Float = v * unit

    val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    val title = textPaint(LIGHT, 30f, 0.42f)
    val heading = textPaint(LIGHT, 17f, 0.3f)
    val number = textPaint(LIGHT, 15f, 0.06f)
    val label = textPaint(MEDIUM, 9.5f, 0.24f)
    val small = textPaint(REGULAR, 8.5f, 0.2f)

    private fun textPaint(typeface: Typeface, size: Float, spacing: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = u(size)
        letterSpacing = spacing * text.letterSpacing
        textAlign = Paint.Align.CENTER
        color = Palette.TEXT
    }

    /** Draws [value] vertically centred on [cy]. */
    fun drawText(canvas: Canvas, value: String, x: Float, cy: Float, paint: Paint) {
        val fm = paint.fontMetrics
        canvas.drawText(value, x, cy - (fm.ascent + fm.descent) / 2f, paint)
    }

    private companion object {
        val LIGHT: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        val REGULAR: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
}
