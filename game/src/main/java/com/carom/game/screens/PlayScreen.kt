package com.carom.game.screens

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import com.carom.core.game.GameSession
import com.carom.core.game.SlingshotAim
import com.carom.core.level.LevelData
import com.carom.game.render.BoardRenderer
import com.carom.game.ui.Icon
import com.carom.game.ui.Icons
import com.carom.game.ui.Palette
import com.carom.game.ui.UiButton
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A level being played. Owns the [GameSession] (rules), the slingshot input and everything drawn
 * on top of the board: HUD, feedback effects and the result overlay.
 */
class PlayScreen(host: GameHost, val index: Int, private val level: LevelData) : Screen(host), GameSession.Listener {

    internal val session = GameSession(level).also { it.listener = this }
    internal val board = BoardRenderer(level, Palette.wallColor(index))
    private val boardRect = RectF()

    private var aim = SlingshotAim(maxPull = 1.0)
    private var grabRadius = 1.0
    private var fingerX = 0f
    private var fingerY = 0f
    private var shotsFired = 0
    private val hint: String? = level.hintFor(kit.text.language)

    private val restartButton = UiButton(UiButton.Style.ICON, icon = Icon.RESTART) { restart() }
    private val levelsButton = UiButton(UiButton.Style.ICON, icon = Icon.GRID) { host.showLevels(index) }
    private val hudButtons = listOf(restartButton, levelsButton)
    private var levelLabelX = 0f
    private var levelLabelY = 0f
    private var counterX = 0f
    private var counterY = 0f

    private val isLastLevel = index == host.app.levels.size - 1
    private val nextButton = UiButton(UiButton.Style.PRIMARY, kit.text.next, Icon.PLAY) { host.play(index + 1) }
    private val retryButton = UiButton(UiButton.Style.PRIMARY, kit.text.retry, Icon.RESTART) { restart() }
    private val replayButton = UiButton(UiButton.Style.OUTLINE, kit.text.restart, Icon.RESTART) { restart() }
    private val menuButton = UiButton(UiButton.Style.OUTLINE, kit.text.levels, Icon.GRID) { host.showLevels(index) }

    /** Seconds since the attempt was won or lost. */
    private var endTime = 0f
    private var counterPulse = 0f
    private val ripples = ArrayList<Ripple>()
    private val particles = ArrayList<Particle>()
    private val shape = Path()
    private val hintBox = RectF()

    private class Ripple(val x: Double, val y: Double) {
        var age = 0f
    }

    private class Particle(var x: Float, var y: Float, val vx: Float, val vy: Float, val spin: Float, val color: Int) {
        var age = 0f
    }

    private val ended get() = session.state == GameSession.State.WON || session.state == GameSession.State.FAILED
    private val overlayProgress: Float
        get() {
            if (!ended) return 0f
            val delay = if (session.state == GameSession.State.WON) WIN_OVERLAY_DELAY else FAIL_OVERLAY_DELAY
            return ((endTime - delay) / OVERLAY_FADE).coerceIn(0f, 1f)
        }

    override fun onEnter() {
        host.app.progress.lastPlayedIndex = index
    }

    // ---------------------------------------------------------------- layout

    override fun onLayout() {
        val lw = level.width.toFloat()
        val lh = level.height.toFloat()
        val margin = kit.u(12f)
        val sideColumn = kit.u(78f)
        val topStrip = kit.u(46f)
        // HUD beside the board (wide phones) or above it (16:9 and tablets): whichever lets the
        // board be bigger.
        val scaleSide = min((safe.width() - 2 * sideColumn) / lw, (safe.height() - 2 * margin) / lh)
        val scaleTop = min((safe.width() - 2 * margin) / lw, (safe.height() - topStrip - margin) / lh)
        val hudBeside = scaleSide >= scaleTop
        val scale = max(scaleSide, scaleTop)
        val left = safe.left + (safe.width() - lw * scale) / 2
        val top = if (hudBeside) {
            safe.top + (safe.height() - lh * scale) / 2
        } else {
            safe.top + topStrip + (safe.height() - topStrip - margin - lh * scale) / 2
        }
        board.layout(scale, left, top)
        boardRect.set(left, top, left + lw * scale, top + lh * scale)

        val button = kit.u(40f)
        if (hudBeside) {
            val leftX = (safe.left + boardRect.left) / 2
            val rightX = (boardRect.right + safe.right) / 2
            levelLabelX = leftX
            levelLabelY = boardRect.top + kit.u(20f)
            counterX = leftX
            counterY = boardRect.top + kit.u(52f)
            restartButton.setCenter(rightX, boardRect.top + kit.u(20f), button, button)
            levelsButton.setCenter(rightX, boardRect.top + kit.u(66f), button, button)
        } else {
            val y = safe.top + topStrip / 2
            levelLabelX = boardRect.left + kit.u(16f)
            levelLabelY = y
            counterX = boardRect.left + kit.u(60f)
            counterY = y
            levelsButton.setCenter(boardRect.right - kit.u(16f), y, button, button)
            restartButton.setCenter(boardRect.right - kit.u(62f), y, button, button)
        }

        // Aiming is sized in screen terms so it feels the same on every device and level size.
        aim = SlingshotAim(maxPull = (kit.u(105f) / scale).toDouble())
        grabRadius = (max(level.ballRadius.toFloat() * scale * 2.6f, kit.u(26f)) / scale).toDouble()

        arrangeResultButtons()
    }

    /** Stacks the result buttons that apply to the current outcome under the title. */
    private fun arrangeResultButtons() {
        overlayButtons().forEachIndexed { i, b ->
            b.setCenter(width / 2, height / 2 + kit.u(28f + 38f * i), kit.u(150f), kit.u(30f))
        }
    }

    // ---------------------------------------------------------------- simulation & feedback

    override fun update(dt: Float) {
        session.advance(dt.toDouble())
        if (ended) endTime += dt
        counterPulse = max(0f, counterPulse - dt * 4f)
        ripples.forEach { it.age += dt }
        ripples.removeAll { it.age > RIPPLE_LIFE }
        for (p in particles) {
            p.age += dt
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
        particles.removeAll { it.age > PARTICLE_LIFE }
    }

    override val isAnimating: Boolean
        get() = session.state == GameSession.State.MOVING || ripples.isNotEmpty() || particles.isNotEmpty() ||
            counterPulse > 0f || (ended && overlayProgress < 1f)

    override fun onLaunch() {
        shotsFired++
        host.haptic(Haptic.CLICK)
    }

    override fun onBounce(x: Double, y: Double, bouncesLeft: Int) {
        ripples += Ripple(x, y)
        counterPulse = 1f
        host.haptic(Haptic.BOUNCE)
    }

    override fun onWin(x: Double, y: Double) {
        arrangeResultButtons()
        host.app.progress.markCompleted(index)
        host.haptic(Haptic.SUCCESS)
        val gx = board.x(level.goal.x)
        val gy = board.y(level.goal.y)
        val colors = intArrayOf(Palette.ACCENT, Palette.BALL, Palette.wallColor(index))
        for (i in 0 until 16) {
            val a = i / 16.0 * Math.PI * 2 + (i % 3) * 0.3
            val speed = kit.u(60f + (i * 37 % 50))
            particles += Particle(
                gx, gy, (cos(a) * speed).toFloat(), (sin(a) * speed).toFloat(),
                if (i % 2 == 0) 4f else -3f, colors[i % colors.size],
            )
        }
    }

    override fun onFail(reason: GameSession.FailReason, x: Double, y: Double) {
        arrangeResultButtons()
        host.haptic(Haptic.FAILURE)
    }

    private fun restart() {
        session.restart()
        aim.cancel()
        endTime = 0f
        counterPulse = 0f
        ripples.clear()
        particles.clear()
    }

    // ---------------------------------------------------------------- input

    override fun onTouch(e: MotionEvent): Boolean {
        if (overlayProgress > 0f) return routeToButtons(e, overlayButtons())
        if (!aim.isActive && routeToButtons(e, hudButtons)) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (session.state == GameSession.State.AIMING &&
                    aim.tryBegin(board.worldX(e.x), board.worldY(e.y), session.ball.x, session.ball.y, grabRadius)
                ) {
                    fingerX = e.x
                    fingerY = e.y
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> if (aim.isActive) {
                aim.drag(board.worldX(e.x), board.worldY(e.y))
                fingerX = e.x
                fingerY = e.y
                return true
            }
            MotionEvent.ACTION_UP -> if (aim.isActive) {
                aim.release(session)
                return true
            }
            MotionEvent.ACTION_CANCEL -> aim.cancel()
        }
        return false
    }

    override fun onBack(): Boolean {
        if (aim.isActive) aim.cancel() else host.showLevels(index)
        return true
    }

    private fun overlayButtons(): List<UiButton> = when {
        session.state == GameSession.State.FAILED -> listOf(retryButton, menuButton)
        isLastLevel -> listOf(replayButton, menuButton)
        else -> listOf(nextButton, replayButton, menuButton)
    }

    // ---------------------------------------------------------------- drawing

    override fun draw(canvas: Canvas) {
        board.drawGeometry(canvas)

        val ghost = session.lastShotPath
        if (ghost.size > 1) board.drawShotPath(canvas, ghost, Float.NaN, Float.NaN, Palette.withAlpha(Palette.BALL, 0.22f), dashed = true)

        val ballX = board.x(session.renderX)
        val ballY = board.y(session.renderY)
        if (session.state == GameSession.State.MOVING) {
            board.drawShotPath(canvas, session.path, ballX, ballY, Palette.withAlpha(Palette.BALL, 0.4f), dashed = false)
        } else if (ended) {
            board.drawShotPath(canvas, session.path, Float.NaN, Float.NaN, Palette.withAlpha(Palette.BALL, 0.4f), dashed = false)
        }

        val scoring = session.state == GameSession.State.WON && endTime < GOAL_PULSE
        board.drawGoal(canvas, if (scoring) endTime / GOAL_PULSE else 0f)
        drawRipples(canvas)

        if (aim.isActive) {
            kit.stroke.color = Palette.LINE
            kit.stroke.strokeWidth = kit.u(1.2f)
            canvas.drawLine(ballX, ballY, fingerX, fingerY, kit.stroke)
            board.drawAim(
                canvas, ballX, ballY, aim.dirX.toFloat(), aim.dirY.toFloat(),
                aim.power.toFloat(), aim.isShotReady, kit.unit,
            )
        }
        drawBall(canvas, ballX, ballY)
        drawParticles(canvas)
        drawHud(canvas)

        val overlay = overlayProgress
        if (overlay > 0f) drawResult(canvas, overlay)
    }

    private fun drawBall(canvas: Canvas, x: Float, y: Float) {
        when (session.state) {
            GameSession.State.WON -> {
                // Glide into the centre of the goal and settle.
                val t = (endTime / 0.3f).coerceIn(0f, 1f)
                val ease = 1f - (1f - t) * (1f - t)
                val gx = board.x(level.goal.x)
                val gy = board.y(level.goal.y)
                board.drawBall(canvas, x + (gx - x) * ease, y + (gy - y) * ease, 1f - 0.3f * ease, Palette.BALL)
            }
            GameSession.State.FAILED -> {
                val shake = if (endTime < 0.3f) sin(endTime * 70f) * kit.u(2.5f) * (1f - endTime / 0.3f) else 0f
                board.drawBall(canvas, x + shake, y, 1f, Palette.DANGER)
            }
            else -> board.drawBall(canvas, x, y, 1f, Palette.BALL)
        }
    }

    private fun drawRipples(canvas: Canvas) {
        val r = level.ballRadius.toFloat() * board.scale
        for (ripple in ripples) {
            val t = ripple.age / RIPPLE_LIFE
            kit.stroke.color = Palette.withAlpha(Palette.BALL, 0.7f * (1f - t))
            kit.stroke.strokeWidth = kit.u(1.5f)
            canvas.drawCircle(board.x(ripple.x), board.y(ripple.y), r * (1f + t * 1.8f), kit.stroke)
        }
    }

    private fun drawParticles(canvas: Canvas) {
        val s = kit.u(4f)
        for (p in particles) {
            val t = p.age / PARTICLE_LIFE
            val a = p.age * p.spin
            kit.fill.color = Palette.withAlpha(p.color, 1f - t)
            shape.reset()
            for (k in 0..2) {
                val ang = a + k * 2.094f
                val px = p.x + cos(ang) * s
                val py = p.y + sin(ang) * s
                if (k == 0) shape.moveTo(px, py) else shape.lineTo(px, py)
            }
            shape.close()
            canvas.drawPath(shape, kit.fill)
        }
    }

    private fun drawHud(canvas: Canvas) {
        kit.number.color = Palette.TEXT
        kit.drawText(canvas, String.format(Locale.ROOT, "%02d", index + 1), levelLabelX, levelLabelY, kit.number)

        val warn = session.state == GameSession.State.MOVING && session.bouncesLeft == 0
        val color = if (warn) Palette.DANGER else Palette.TEXT
        val grow = 1f + 0.35f * counterPulse
        Icons.draw(canvas, kit, Icon.RING, counterX - kit.u(10f), counterY, kit.u(15f) * grow, color)
        kit.number.color = color
        kit.number.textSize = kit.u(15f) * grow
        kit.drawText(canvas, session.bouncesLeft.toString(), counterX + kit.u(8f), counterY, kit.number)
        kit.number.textSize = kit.u(15f)
        kit.number.color = Palette.TEXT

        for (b in hudButtons) b.draw(canvas, kit)

        if (hint != null && shotsFired == 0 && session.state == GameSession.State.AIMING) {
            val y = boardRect.top + kit.u(24f)
            val w = kit.small.measureText(hint) + kit.u(24f)
            hintBox.set(boardRect.centerX() - w / 2, y - kit.u(11f), boardRect.centerX() + w / 2, y + kit.u(11f))
            kit.fill.color = Palette.withAlpha(Palette.BACKGROUND, 0.85f)
            canvas.drawRoundRect(hintBox, kit.u(11f), kit.u(11f), kit.fill)
            kit.small.color = Palette.TEXT_DIM
            kit.drawText(canvas, hint, boardRect.centerX(), y, kit.small)
            kit.small.color = Palette.TEXT
        }
    }

    private fun drawResult(canvas: Canvas, progress: Float) {
        canvas.drawColor(Palette.withAlpha(Palette.BACKGROUND, 0.93f * progress))
        val cx = width / 2
        val lift = (1f - progress) * kit.u(12f)
        val cy = height / 2 + lift
        val won = session.state == GameSession.State.WON
        val color = if (won) Palette.BALL else Palette.DANGER

        kit.stroke.color = color
        kit.stroke.strokeWidth = kit.u(2f)
        canvas.drawCircle(cx, cy - kit.u(66f), kit.u(22f), kit.stroke)
        Icons.draw(canvas, kit, if (won) Icon.CHECK else Icon.CROSS, cx, cy - kit.u(66f), kit.u(24f), color)

        kit.heading.color = Palette.TEXT
        kit.drawText(canvas, if (won) kit.text.levelComplete else kit.text.levelFailed, cx, cy - kit.u(24f), kit.heading)
        val detail = when {
            !won && session.failReason == GameSession.FailReason.STOPPED -> kit.text.ballStopped
            !won -> kit.text.outOfBounces
            isLastLevel -> kit.text.allComplete
            else -> null
        }
        if (detail != null) {
            kit.small.color = Palette.TEXT_DIM
            kit.drawText(canvas, detail, cx, cy - kit.u(4f), kit.small)
            kit.small.color = Palette.TEXT
        }

        for (b in overlayButtons()) {
            b.bounds.offset(0f, lift)
            b.draw(canvas, kit)
            b.bounds.offset(0f, -lift)
        }
    }

    private companion object {
        const val WIN_OVERLAY_DELAY = 0.65f
        const val FAIL_OVERLAY_DELAY = 0.5f
        const val OVERLAY_FADE = 0.2f
        const val GOAL_PULSE = 0.6f
        const val RIPPLE_LIFE = 0.45f
        const val PARTICLE_LIFE = 0.9f
    }
}
