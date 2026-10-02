package com.carom.game.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.carom.core.level.Worlds

/**
 * A world's look, built from two colours only:
 * - [primary] is the environment: the background is its quiet ground and walls are its muted tone.
 * - [accent] is contrast: the ball, the goal, and everything the player touches.
 * Text and lines use [ink]: a soft white on a dark world and the world's own deep tone on a light one, so no other colour appears.
 */
class WorldPalette(val background: Int, val primary: Int, val accent: Int, val ink: Int = Palette.TEXT) {
    /** Whether the ground is light (cream, pale blue, sage...) rather than dark. */
    val isLight: Boolean = Palette.luminance(background) > 0.5f

    val inkDim: Int get() = Palette.withAlpha(ink, 0.55f)
    val line: Int get() = Palette.withAlpha(ink, 0.2f)

    /** The space outside the level on screens whose shape differs from the level's. */
    val void: Int get() = if (isLight) Palette.blend(background, ink, 0.1f) else Palette.blend(background, 0xFF000000.toInt(), 0.35f)
}

object Palette {
    /** The soft white of the deep worlds' text (the light worlds use their own [WorldPalette.ink]). */
    const val TEXT = 0xFFF1F3F6.toInt()

    /**
     * One flat, quiet ground per world, a muted main tone for the obstacles, and a contrasting tone for what the player touches.
     * Most grounds are light and soft (cream, pale blue, sage, lavender, rose); two worlds are deep. Obstacles are always calmer than
     * the ball and the goal, so the eye finds those first.
     */
    private val WORLDS = arrayOf(
        light(0xFFF4EFE6, 0xFFA79ECB, 0xFF2F2A45), // cream + lavender
        light(0xFFE4EEF4, 0xFF8FB4A4, 0xFF223645), // pale blue + sage
        light(0xFFE7EEE2, 0xFFC99A97, 0xFF2E3B2E), // light sage + dusty rose
        light(0xFFF0E8DA, 0xFF7FAAA6, 0xFF2A3A3D), // warm beige + soft teal
        light(0xFFECE7F5, 0xFF8F9CCB, 0xFF2C2A48), // soft lavender + periwinkle
        WorldPalette(0xFF23252B.toInt(), 0xFF8A90A0.toInt(), 0xFFF5F2EA.toInt()), // graphite + bone
        light(0xFFF7EEEB, 0xFFC08D99, 0xFF4A2E3A), // warm white + dusty rose
        WorldPalette(0xFF18253A.toInt(), 0xFFC9A15B.toInt(), 0xFFFFF3DC.toInt()), // dusk blue + amber
    )

    /** A light world: its deep accent is also its ink. */
    private fun light(background: Long, primary: Long, accent: Long) =
        WorldPalette(background.toInt(), primary.toInt(), accent.toInt(), ink = accent.toInt())

    fun forWorld(world: Int): WorldPalette = WORLDS[Math.floorMod(world, WORLDS.size)]

    fun forLevel(levelIndex: Int): WorldPalette = forWorld(Worlds.worldOf(levelIndex))

    /** Palette part-way between [a] and [b], for sliding between worlds. */
    fun lerp(a: WorldPalette, b: WorldPalette, t: Float): WorldPalette =
        WorldPalette(blend(a.background, b.background, t), blend(a.primary, b.primary, t), blend(a.accent, b.accent, t), blend(a.ink, b.ink, t))

    /** Relative brightness of a colour, 0 (black) to 1 (white). */
    fun luminance(color: Int): Float {
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

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
        color = Palette.TEXT // (every screen sets the ink of its world before drawing)
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
