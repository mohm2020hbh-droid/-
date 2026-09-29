package com.carom.game.screens

import android.graphics.Canvas
import android.view.MotionEvent
import com.carom.game.ui.Icon
import com.carom.game.ui.Palette
import com.carom.game.ui.UiButton

/** Title screen: continue playing, open the level list, toggle vibration. */
class HomeScreen(host: GameHost) : Screen(host) {

    private val settings = host.app.settings

    private val playButton = UiButton(UiButton.Style.PRIMARY, kit.text.play, Icon.PLAY) {
        host.play(host.app.progress.currentIndex)
    }
    private val levelsButton = UiButton(UiButton.Style.OUTLINE, kit.text.levels, Icon.GRID) {
        host.showLevels(host.app.progress.currentIndex)
    }
    private val vibrationButton = UiButton(UiButton.Style.OUTLINE, icon = Icon.VIBRATION) {
        settings.hapticsEnabled = !settings.hapticsEnabled
        updateVibrationLabel()
        host.haptic(Haptic.CLICK)
    }
    private val buttons = listOf(playButton, levelsButton, vibrationButton)

    private var columnX = 0f
    private var titleY = 0f

    init {
        updateVibrationLabel()
    }

    private fun updateVibrationLabel() {
        vibrationButton.label = if (settings.hapticsEnabled) kit.text.vibrationOn else kit.text.vibrationOff
    }

    override fun onLayout() {
        columnX = safe.left + (safe.width() * 0.34f)
        titleY = safe.top + safe.height() * 0.28f
        val w = kit.u(160f)
        playButton.setCenter(columnX, titleY + kit.u(78f), w, kit.u(32f))
        levelsButton.setCenter(columnX, titleY + kit.u(118f), w, kit.u(32f))
        vibrationButton.setCenter(columnX, titleY + kit.u(158f), w, kit.u(28f))
    }

    override fun onTouch(e: MotionEvent): Boolean = routeToButtons(e, buttons)

    override fun draw(canvas: Canvas) {
        kit.title.color = Palette.TEXT
        kit.drawText(canvas, "CAROM", columnX, titleY, kit.title)
        kit.small.color = Palette.TEXT_DIM
        kit.drawText(canvas, kit.text.tagline, columnX, titleY + kit.u(30f), kit.small)
        kit.small.color = Palette.TEXT
        for (b in buttons) b.draw(canvas, kit)
        drawEmblem(canvas)
    }

    /** The game in one picture: a ball, one bounce off a wall, into the goal. */
    private fun drawEmblem(canvas: Canvas) {
        val left = columnX + kit.u(120f)
        val right = safe.right - kit.u(30f)
        if (right - left < kit.u(140f)) return
        val cx = (left + right) / 2
        val span = minOf((right - left) / 2, kit.u(120f))
        val topY = safe.top + safe.height() * 0.3f
        val wallY = safe.top + safe.height() * 0.74f
        val ballR = kit.u(8f)
        val bounceY = wallY - ballR - kit.u(2f)
        val ballX = cx - span * 0.8f
        val goalX = cx + span * 0.8f

        kit.stroke.color = Palette.wallColor(0)
        kit.stroke.strokeWidth = kit.u(4f)
        canvas.drawLine(cx - span, wallY, cx + span, wallY, kit.stroke)

        // Dotted path: down to the wall, then up into the goal (a mirror-image V).
        kit.fill.color = Palette.withAlpha(Palette.BALL, 0.5f)
        val steps = 14
        for (i in 1 until steps) {
            val t = i / steps.toFloat()
            val k = if (t < 0.5f) t * 2 else (1f - t) * 2
            val x = ballX + (goalX - ballX) * t
            val y = topY + (bounceY - topY) * k
            canvas.drawCircle(x, y, kit.u(1.6f), kit.fill)
        }

        kit.stroke.color = Palette.ACCENT
        kit.stroke.strokeWidth = kit.u(4f)
        canvas.drawCircle(goalX, topY, kit.u(17f), kit.stroke)
        kit.fill.color = Palette.ACCENT
        canvas.drawCircle(goalX, topY, kit.u(4f), kit.fill)

        kit.fill.color = Palette.BALL
        canvas.drawCircle(ballX, topY, ballR, kit.fill)
        kit.fill.color = Palette.BACKGROUND
        canvas.drawCircle(ballX, topY, ballR * 0.32f, kit.fill)
    }
}
