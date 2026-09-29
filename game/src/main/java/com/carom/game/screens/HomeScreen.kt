package com.carom.game.screens

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import com.carom.game.ui.Icon
import com.carom.game.ui.Palette
import com.carom.game.ui.UiButton
import com.carom.game.ui.WorldPalette
import kotlin.math.min

/** Title screen: continue playing, open the level list, toggle vibration. */
class HomeScreen(host: GameHost) : Screen(host) {

    private val settings = host.app.settings

    /** The title screen wears the colours of the world the player has reached. */
    override val palette: WorldPalette get() = Palette.forLevel(host.app.progress.currentIndex)

    private val playButton = UiButton(UiButton.Style.PRIMARY, kit.text.play, Icon.PLAY) {
        host.play(host.app.progress.currentIndex)
    }
    private val levelsButton = UiButton(UiButton.Style.OUTLINE, kit.text.levels, Icon.GRID) {
        host.showLevels(host.app.progress.currentIndex)
    }
    private val soundButton = UiButton(UiButton.Style.OUTLINE, icon = Icon.SOUND) {
        settings.soundEnabled = !settings.soundEnabled
        updateToggleLabels()
    }
    private val vibrationButton = UiButton(UiButton.Style.OUTLINE, icon = Icon.VIBRATION) {
        settings.hapticsEnabled = !settings.hapticsEnabled
        updateToggleLabels()
        host.haptic(Haptic.CLICK)
    }
    private val buttons = listOf(playButton, levelsButton, soundButton, vibrationButton)

    private var columnX = 0f
    private var titleY = 0f

    init {
        updateToggleLabels()
    }

    private fun updateToggleLabels() {
        soundButton.label = if (settings.soundEnabled) kit.text.soundOn else kit.text.soundOff
        vibrationButton.label = if (settings.hapticsEnabled) kit.text.vibrationOn else kit.text.vibrationOff
    }

    override fun onLayout() {
        columnX = width / 2
        titleY = safe.top + safe.height() * 0.49f
        // Buttons sit in the lower half, where the thumb rests on a phone held upright.
        val w = min(safe.width() - kit.u(64f), kit.u(260f))
        val top = safe.top + safe.height() * 0.63f
        playButton.setCenter(columnX, top, w, kit.u(54f))
        levelsButton.setCenter(columnX, top + kit.u(66f), w, kit.u(50f))
        soundButton.setCenter(columnX, top + kit.u(124f), w, kit.u(44f))
        vibrationButton.setCenter(columnX, top + kit.u(176f), w, kit.u(44f))
    }

    override fun onTouch(e: MotionEvent): Boolean = routeToButtons(e, buttons)

    override fun draw(canvas: Canvas) {
        drawEmblem(canvas)
        kit.title.color = Palette.TEXT
        kit.drawText(canvas, "CAROM", columnX, titleY, kit.title)
        kit.small.color = Palette.TEXT_DIM
        kit.drawText(canvas, kit.text.tagline, columnX, titleY + kit.u(38f), kit.small)
        kit.small.color = Palette.TEXT
        for (b in buttons) b.draw(canvas, kit)
    }

    /** The game in one picture: the ball (with its bounce count), one bounce off a wall, the goal. */
    private fun drawEmblem(canvas: Canvas) {
        val span = min(safe.width() * 0.34f, kit.u(130f))
        val topY = safe.top + safe.height() * 0.16f
        val wallY = safe.top + safe.height() * 0.36f
        val ballR = kit.u(20f)
        val bounceY = wallY - ballR - kit.u(4f)
        val ballX = columnX - span * 0.8f
        val goalX = columnX + span * 0.8f

        // The wall, in the same style as the game's obstacles: a smooth rod lit from above, inside
        // a crisp light edge, with a soft gloss along its top.
        val colors = palette
        val t = kit.u(12f)
        kit.stroke.color = Palette.blend(colors.primary, WHITE, 0.42f)
        kit.stroke.strokeWidth = t
        canvas.drawLine(columnX - span, wallY, columnX + span, wallY, kit.stroke)
        rod.shader = LinearGradient(
            0f, wallY - t / 2, 0f, wallY + t / 2,
            intArrayOf(Palette.blend(colors.primary, WHITE, 0.26f), colors.primary, Palette.blend(colors.primary, BLACK, 0.3f)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        rod.strokeWidth = t - kit.u(1.6f)
        canvas.drawLine(columnX - span, wallY, columnX + span, wallY, rod)
        kit.stroke.color = Palette.withAlpha(WHITE, 0.22f)
        kit.stroke.strokeWidth = t * 0.12f
        canvas.drawLine(columnX - span + t * 0.4f, wallY - t * 0.24f, columnX + span - t * 0.4f, wallY - t * 0.24f, kit.stroke)

        // Dotted path: down to the wall, then up into the goal (a mirror-image V).
        kit.fill.color = Palette.withAlpha(colors.accent, 0.45f)
        val steps = 16
        for (i in 2 until steps - 1) {
            val t = i / steps.toFloat()
            val k = if (t < 0.5f) t * 2 else (1f - t) * 2
            canvas.drawCircle(ballX + (goalX - ballX) * t, topY + (bounceY - topY) * k, kit.u(2f), kit.fill)
        }

        kit.stroke.color = colors.accent
        kit.stroke.strokeWidth = kit.u(5f)
        canvas.drawCircle(goalX, topY, kit.u(24f), kit.stroke)
        kit.fill.color = colors.accent
        canvas.drawCircle(goalX, topY, kit.u(5f), kit.fill)

        // The ball: a small glossy sphere.
        sphere.shader = RadialGradient(
            ballX - 0.35f * ballR, topY - 0.4f * ballR, 1.45f * ballR,
            intArrayOf(Palette.blend(colors.accent, WHITE, 0.75f), colors.accent, Palette.blend(colors.accent, BLACK, 0.22f)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(ballX, topY, ballR, sphere)
        emblemDigit.color = colors.background
        emblemDigit.textSize = ballR * 1.1f
        emblemDigit.getTextBounds("1", 0, 1, digitBounds)
        canvas.drawText("1", ballX, topY - digitBounds.exactCenterY(), emblemDigit)
    }

    private val emblemDigit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val digitBounds = Rect()
    private val rod = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val sphere = Paint(Paint.ANTI_ALIAS_FLAG)

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
    }
}
