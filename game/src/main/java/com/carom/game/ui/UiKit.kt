package com.carom.game.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.carom.core.level.Worlds

/**
 * A world's look, built from two colours only:
 * - [primary] is the environment: the background is its deepest shade and walls are its soft tint.
 * - [accent] is contrast: the ball, the goal, and everything the player touches.
 * Text uses neutral white, so no other colour ever appears on screen.
 */
class WorldPalette(val background: Int, val primary: Int, val accent: Int) {
    /** The space outside the level on screens whose shape differs from the level's. */
    val void: Int get() = Palette.blend(background, 0xFF000000.toInt(), 0.35f)
}

object Palette {
    /** Neutral text, shared by every world. */
    const val TEXT = 0xFFF1F3F6.toInt()
    val TEXT_DIM: Int = withAlpha(TEXT, 0.55f)
    val LINE: Int = withAlpha(TEXT, 0.2f)

    /** Calm, deep grounds with a soft main colour and a light contrast colour. */
    private val WORLDS = arrayOf(
        WorldPalette(0xFF0E1A2B.toInt(), 0xFF5E82B0.toInt(), 0xFFF2F5F9.toInt()), // blue + white
        WorldPalette(0xFF19142B.toInt(), 0xFF8674B8.toInt(), 0xFFF4F1FA.toInt()), // violet + white
        WorldPalette(0xFF0D211D.toInt(), 0xFF5E9A86.toInt(), 0xFFEFF6F2.toInt()), // green + white
        WorldPalette(0xFF24150F.toInt(), 0xFFC5835A.toInt(), 0xFFFBF2EA.toInt()), // orange + white
        WorldPalette(0xFF08121F.toInt(), 0xFF34597F.toInt(), 0xFF86D5E6.toInt()), // deep blue + cyan
    )

    fun forWorld(world: Int): WorldPalette = WORLDS[Math.floorMod(world, WORLDS.size)]

    fun forLevel(levelIndex: Int): WorldPalette = forWorld(Worlds.worldOf(levelIndex))

    /** Palette part-way between [a] and [b], for sliding between worlds. */
    fun lerp(a: WorldPalette, b: WorldPalette, t: Float): WorldPalette =
        WorldPalette(blend(a.background, b.background, t), blend(a.primary, b.primary, t), blend(a.accent, b.accent, t))

    fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)

    fun blend(from: Int, to: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * t.coerceIn(0f, 1f)).toInt() shl shift
        }
        return ch(24) or ch(16) or ch(8) or ch(0)
    }
}

/**
 * Shared paints and measurements for the canvas-drawn UI. [unit] is one "UI unit": the game is
 * designed on a 360×640 portrait grid (one unit ≈ 1dp on a typical phone), so the interface keeps
 * its proportions on every screen size.
 */
class UiKit(val unit: Float, val text: UiText) {

    fun u(v: Float): Float = v * unit

    /** Colours of the screen being drawn; set by the view before each frame. */
    var palette: WorldPalette = Palette.forWorld(0)

    val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    val title = textPaint(LIGHT, 40f, 0.42f)
    val heading = textPaint(LIGHT, 22f, 0.28f)
    val number = textPaint(LIGHT, 20f, 0.06f)
    val label = textPaint(MEDIUM, 14f, 0.2f)
    val small = textPaint(REGULAR, 12f, 0.16f)

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

    companion object {
        /** The design grid: a portrait screen is 360×640 units. */
        fun unitFor(width: Int, height: Int): Float = minOf(width / 360f, height / 640f)

        private val LIGHT: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        private val REGULAR: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        private val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
}
