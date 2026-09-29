package com.carom.game.screens

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import com.carom.core.game.DragAim
import com.carom.core.game.GameSession
import com.carom.core.level.LevelData
import com.carom.core.level.Worlds
import com.carom.game.render.BallShatter
import com.carom.game.render.BoardRenderer
import com.carom.game.render.BoardRenderer.BallStyle
import com.carom.game.ui.Icon
import com.carom.game.ui.Icons
import com.carom.game.ui.Palette
import com.carom.game.ui.UiButton
import com.carom.game.ui.WorldPalette
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A level being played. The level fills the screen: its edges are the screen's edges (invisible,
 * but the ball bounces off them), with the level number, restart and level-list buttons floating
 * over the top and the hint over the bottom.
 *
 * Owns the [GameSession] (rules), the drag-to-launch input and everything drawn on top of the
 * board. The bounces left are shown inside the ball itself.
 *
 * Launching: the ball sits in a launch zone (a faint ringed disc). The player touches anywhere in
 * it, drags the way the ball should go and lets go; the ball flies in the drag's direction, with
 * power from the drag's length (and a little extra for a quick flick). There is no arrow: while
 * dragging, the ball leans towards the finger and the zone's ring fills like a dial with the power.
 */
class PlayScreen(host: GameHost, val index: Int, private val level: LevelData) : Screen(host), GameSession.Listener {

    override val palette: WorldPalette = Palette.forLevel(index)
    internal val session = GameSession(level).also { it.listener = this }
    internal val board = BoardRenderer(level, palette)
    private val boardRect = RectF()

    private var aim = DragAim(maxDrag = 1.0)
    private var grabRadius = 1.0

    /** Radius of the launch zone on screen, in pixels. */
    private var zoneRadius = 1f

    /** How far the drawn ball leans towards the finger while held, and the lean it launched with. */
    private var leanX = 0f
    private var leanY = 0f
    private var launchLeanX = 0f
    private var launchLeanY = 0f
    private var sinceLaunch = 0f
    private var shotsFired = 0
    private val hint: String? = level.hintFor(kit.text.language)
    private val levelNumber = String.format(Locale.ROOT, "%02d", index + 1)

    private val restartButton = UiButton(UiButton.Style.ICON, icon = Icon.RESTART) { restart() }
    private val levelsButton = UiButton(UiButton.Style.ICON, icon = Icon.GRID) { host.showLevels(index) }
    private val hudButtons = listOf(restartButton, levelsButton)
    private var topBarY = 0f
    private var hintY = 0f

    private val isLastLevel = index == host.app.levels.size - 1
    private val opensNewWorld = !isLastLevel && Worlds.worldOf(index + 1) != Worlds.worldOf(index)
    private val nextButton = UiButton(UiButton.Style.PRIMARY, kit.text.next, Icon.PLAY) { host.play(index + 1) }
    private val retryButton = UiButton(UiButton.Style.PRIMARY, kit.text.retry, Icon.RESTART) { restart() }
    private val replayButton = UiButton(UiButton.Style.OUTLINE, kit.text.restart, Icon.RESTART) { restart() }
    private val menuButton = UiButton(UiButton.Style.OUTLINE, kit.text.levels, Icon.GRID) { host.showLevels(index) }

    /** Seconds since the attempt was won or lost. */
    private var endTime = 0f
    private val ripples = ArrayList<Ripple>()
    private val particles = ArrayList<Particle>()
    private val shape = Path()
    private val hintBox = RectF()

    /** The ball breaking, when the attempt ended by running out of bounces. */
    private var shatter: BallShatter? = null

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
            val delay = when {
                session.state == GameSession.State.WON -> WIN_OVERLAY_DELAY
                shatter != null -> BREAK_OVERLAY_DELAY
                else -> FAIL_OVERLAY_DELAY
            }
            return ((endTime - delay) / OVERLAY_FADE).coerceIn(0f, 1f)
        }

    override fun onEnter() {
        host.app.progress.lastPlayedIndex = index
    }

    // ---------------------------------------------------------------- layout

    override fun onLayout() {
        topBarY = safe.top + kit.u(32f)
        hintY = safe.bottom - kit.u(34f)

        // The level is shaped like a phone (9:20), so on most phones it maps exactly onto the
        // whole screen and its edges are the screen's edges. On other shapes it is centred.
        val lw = level.width.toFloat()
        val lh = level.height.toFloat()
        val scale = min(width / lw, height / lh)
        val left = (width - lw * scale) / 2
        val top = (height - lh * scale) / 2
        board.layout(scale, left, top)
        boardRect.set(left, top, left + lw * scale, top + lh * scale)

        val button = kit.u(48f)
        levelsButton.setCenter(safe.right - kit.u(8f) - button / 2, topBarY, button, button)
        restartButton.setCenter(safe.right - kit.u(12f) - button * 1.5f, topBarY, button, button)

        // Aiming is sized in screen terms so it feels the same on every device. The launch zone
        // reaches well past the ball's edge, so it is easy to catch with a thumb, and a touch a
        // little outside its ring still counts.
        zoneRadius = max(board.ballScreenRadius * 1.9f, kit.u(46f))
        grabRadius = ((zoneRadius + kit.u(6f)) / scale).toDouble()
        aim = DragAim(
            maxDrag = (kit.u(MAX_DRAG) / scale).toDouble(),
            flickStart = (kit.u(FLICK_START) / scale).toDouble(),
            flickFull = (kit.u(FLICK_FULL) / scale).toDouble(),
            flickBoost = FLICK_BOOST,
        )

        arrangeResultButtons()
    }

    /** Stacks the result buttons that apply to the current outcome under the title. */
    private fun arrangeResultButtons() {
        overlayButtons().forEachIndexed { i, b ->
            b.setCenter(width / 2, height / 2 + kit.u(40f + 60f * i), kit.u(230f), kit.u(48f))
        }
    }

    // ---------------------------------------------------------------- simulation & feedback

    override fun update(dt: Float) {
        if (session.state == GameSession.State.MOVING) sinceLaunch += dt
        session.advance(dt.toDouble())
        if (ended) endTime += dt
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
            (ended && overlayProgress < 1f)

    override fun onLaunch() {
        shotsFired++
        sinceLaunch = 0f
        launchLeanX = leanX
        launchLeanY = leanY
        leanX = 0f
        leanY = 0f
        host.haptic(Haptic.CLICK)
    }

    override fun onBounce(impact: GameSession.Impact, bouncesLeft: Int) {
        ripples += Ripple(impact.x, impact.y)
        host.haptic(Haptic.BOUNCE)
        host.sound(Sound.IMPACT, impact.strength)
    }

    override fun onWin(x: Double, y: Double) {
        arrangeResultButtons()
        host.app.progress.markCompleted(index)
        host.haptic(Haptic.SUCCESS)
        val gx = board.x(level.goal.x)
        val gy = board.y(level.goal.y)
        val colors = intArrayOf(palette.accent, palette.primary)
        for (i in 0 until 16) {
            val a = i / 16.0 * Math.PI * 2 + (i % 3) * 0.3
            val speed = kit.u(70f + (i * 37 % 60))
            particles += Particle(
                gx, gy, (cos(a) * speed).toFloat(), (sin(a) * speed).toFloat(),
                if (i % 2 == 0) 4f else -3f, colors[i % colors.size],
            )
        }
    }

    override fun onFail(reason: GameSession.FailReason, x: Double, y: Double) {
        arrangeResultButtons()
        host.haptic(Haptic.FAILURE)
        val hit = session.lastImpact
        if (reason == GameSession.FailReason.OUT_OF_BOUNCES && hit != null) {
            // The last allowed bounce is spent: the ball breaks against the wall it hit.
            shatter = BallShatter(board.x(x), board.y(y), board.ballScreenRadius, hit.nx.toFloat(), hit.ny.toFloat())
            host.sound(Sound.SHATTER, hit.strength)
        }
    }

    private fun restart() {
        session.restart()
        aim.cancel()
        endTime = 0f
        leanX = 0f
        leanY = 0f
        shatter = null
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
                    aim.tryBegin(board.worldX(e.x), board.worldY(e.y), session.ball.x, session.ball.y, grabRadius, seconds(e))
                ) {
                    updateLean()
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> if (aim.isActive) {
                // Every point the finger passed through, so a flick's speed is measured faithfully.
                for (h in 0 until e.historySize) {
                    aim.drag(board.worldX(e.getHistoricalX(h)), board.worldY(e.getHistoricalY(h)), e.getHistoricalEventTime(h) / 1000.0)
                }
                aim.drag(board.worldX(e.x), board.worldY(e.y), seconds(e))
                updateLean()
                return true
            }
            MotionEvent.ACTION_UP -> if (aim.isActive) {
                aim.drag(board.worldX(e.x), board.worldY(e.y), seconds(e))
                updateLean()
                if (!aim.release(session, time = seconds(e))) {
                    leanX = 0f
                    leanY = 0f
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                aim.cancel()
                leanX = 0f
                leanY = 0f
            }
        }
        return false
    }

    override fun onBack(): Boolean {
        if (aim.isActive) {
            aim.cancel()
            leanX = 0f
            leanY = 0f
        } else {
            host.showLevels(index)
        }
        return true
    }

    private fun seconds(e: MotionEvent): Double = e.eventTime / 1000.0

    /** The held ball follows the finger a little, to show it is being dragged (never an arrow). */
    private fun updateLean() {
        val lean = min(aim.dragLength.toFloat() * board.scale * 0.2f, board.ballScreenRadius * 0.3f)
        leanX = aim.dirX.toFloat() * lean
        leanY = aim.dirY.toFloat() * lean
    }

    private fun overlayButtons(): List<UiButton> = when {
        session.state == GameSession.State.FAILED -> listOf(retryButton, menuButton)
        isLastLevel -> listOf(replayButton, menuButton)
        else -> listOf(nextButton, replayButton, menuButton)
    }

    // ---------------------------------------------------------------- drawing

    override fun draw(canvas: Canvas) {
        drawOutside(canvas)
        board.drawGeometry(canvas)

        val trail = Palette.withAlpha(palette.accent, 0.35f)
        val ghost = session.lastShotPath
        if (ghost.size > 1) board.drawShotPath(canvas, ghost, Float.NaN, Float.NaN, Palette.withAlpha(palette.accent, 0.2f), dashed = true)

        val ballX = board.x(session.renderX)
        val ballY = board.y(session.renderY)
        if (session.state == GameSession.State.MOVING) {
            board.drawShotPath(canvas, session.path, ballX, ballY, trail, dashed = false)
        } else if (ended) {
            board.drawShotPath(canvas, session.path, Float.NaN, Float.NaN, trail, dashed = false)
        }

        val scoring = session.state == GameSession.State.WON && endTime < GOAL_PULSE
        board.drawGoal(canvas, if (scoring) endTime / GOAL_PULSE else 0f)
        drawRipples(canvas)
        if (session.state == GameSession.State.AIMING) {
            board.drawLaunchZone(
                canvas, ballX, ballY, zoneRadius, kit.unit,
                active = aim.isActive, power = aim.power.toFloat(), ready = aim.isShotReady,
            )
        }
        drawBall(canvas, ballX, ballY)
        drawParticles(canvas)
        drawHud(canvas)

        val overlay = overlayProgress
        if (overlay > 0f) drawResult(canvas, overlay)
    }

    /**
     * On screens shaped differently from the level, the space outside it is a slightly deeper
     * shade, so the player can see where the invisible edges are. On a 9:20 phone there is none.
     */
    private fun drawOutside(canvas: Canvas) {
        if (boardRect.width() >= width - 1f && boardRect.height() >= height - 1f) return
        kit.fill.color = palette.void
        canvas.drawRect(0f, 0f, width, boardRect.top, kit.fill)
        canvas.drawRect(0f, boardRect.bottom, width, height, kit.fill)
        canvas.drawRect(0f, boardRect.top, boardRect.left, boardRect.bottom, kit.fill)
        canvas.drawRect(boardRect.right, boardRect.top, width, boardRect.bottom, kit.fill)
    }

    private fun drawBall(canvas: Canvas, x: Float, y: Float) {
        val left = session.bouncesLeft
        // With no bounces left the ball shows cracks: the next wall breaks it.
        val style = if (left == 0) BallStyle.CRACKED else BallStyle.SOLID
        when (session.state) {
            GameSession.State.WON -> {
                // Glide into the centre of the goal and settle.
                val t = (endTime / 0.3f).coerceIn(0f, 1f)
                val ease = 1f - (1f - t) * (1f - t)
                val gx = board.x(level.goal.x)
                val gy = board.y(level.goal.y)
                board.drawBall(canvas, x + (gx - x) * ease, y + (gy - y) * ease, 1f - 0.3f * ease, BallStyle.SOLID, left)
            }
            GameSession.State.FAILED -> {
                val broken = shatter
                if (broken != null) {
                    broken.draw(canvas, endTime, palette.accent, kit.fill, kit.stroke)
                } else {
                    // The ball ran out of speed: it empties and gives a small shake.
                    val shake = if (endTime < 0.3f) sin(endTime * 70f) * kit.u(2.5f) * (1f - endTime / 0.3f) else 0f
                    board.drawBall(canvas, x + shake, y, 1f, BallStyle.HOLLOW, left)
                }
            }
            GameSession.State.MOVING -> {
                // The lean the ball was let go with melts into its flight.
                val k = max(0f, 1f - sinceLaunch / LEAN_SETTLE)
                board.drawBall(canvas, x + launchLeanX * k, y + launchLeanY * k, 1f, style, left)
            }
            GameSession.State.AIMING -> board.drawBall(canvas, x + leanX, y + leanY, 1f, style, left)
        }
    }

    private fun drawRipples(canvas: Canvas) {
        val r = board.ballScreenRadius
        for (ripple in ripples) {
            val t = ripple.age / RIPPLE_LIFE
            kit.stroke.color = Palette.withAlpha(palette.accent, 0.7f * (1f - t))
            kit.stroke.strokeWidth = kit.u(2f)
            canvas.drawCircle(board.x(ripple.x), board.y(ripple.y), r * (1f + t * 1.2f), kit.stroke)
        }
    }

    private fun drawParticles(canvas: Canvas) {
        val s = kit.u(5f)
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
        // "LEVEL 07" at the top left, aligned with the board's edge (number first in Arabic, so
        // it still reads word-then-number right to left).
        val gap = kit.u(8f)
        val word = kit.small.measureText(kit.text.level)
        val digits = kit.number.measureText(levelNumber)
        val left = safe.left + kit.u(20f)
        val arabic = kit.text.language == "ar"
        val wordX = if (arabic) left + digits + gap + word / 2 else left + word / 2
        val numberX = if (arabic) left + digits / 2 else left + word + gap + digits / 2
        kit.small.color = Palette.TEXT_DIM
        kit.drawText(canvas, kit.text.level, wordX, topBarY, kit.small)
        kit.small.color = Palette.TEXT
        kit.number.color = Palette.TEXT
        kit.drawText(canvas, levelNumber, numberX, topBarY, kit.number)

        for (b in hudButtons) b.draw(canvas, kit)

        if (hint != null && shotsFired == 0 && session.state == GameSession.State.AIMING) {
            val w = min(kit.small.measureText(hint) + kit.u(28f), safe.width() - kit.u(16f))
            hintBox.set(width / 2 - w / 2, hintY - kit.u(15f), width / 2 + w / 2, hintY + kit.u(15f))
            kit.fill.color = Palette.withAlpha(palette.background, 0.85f)
            canvas.drawRoundRect(hintBox, kit.u(15f), kit.u(15f), kit.fill)
            kit.stroke.color = Palette.LINE
            kit.stroke.strokeWidth = kit.u(1f)
            canvas.drawRoundRect(hintBox, kit.u(15f), kit.u(15f), kit.stroke)
            kit.small.color = Palette.TEXT_DIM
            kit.drawText(canvas, hint, width / 2, hintY, kit.small)
            kit.small.color = Palette.TEXT
        }
    }

    private fun drawResult(canvas: Canvas, progress: Float) {
        canvas.drawColor(Palette.withAlpha(palette.background, 0.93f * progress))
        val cx = width / 2
        val lift = (1f - progress) * kit.u(14f)
        val cy = height / 2 + lift
        val won = session.state == GameSession.State.WON
        val color = palette.accent

        kit.stroke.color = color
        kit.stroke.strokeWidth = kit.u(2.5f)
        canvas.drawCircle(cx, cy - kit.u(96f), kit.u(30f), kit.stroke)
        Icons.draw(canvas, kit, if (won) Icon.CHECK else Icon.CROSS, cx, cy - kit.u(96f), kit.u(32f), color)

        kit.heading.color = Palette.TEXT
        kit.drawText(canvas, if (won) kit.text.levelComplete else kit.text.levelFailed, cx, cy - kit.u(38f), kit.heading)
        val detail = when {
            !won && session.failReason == GameSession.FailReason.STOPPED -> kit.text.ballStopped
            !won -> kit.text.outOfBounces
            isLastLevel -> kit.text.allComplete
            opensNewWorld -> kit.text.worldUnlocked(Worlds.worldOf(index + 1) + 1)
            else -> null
        }
        if (detail != null) {
            kit.small.color = Palette.TEXT_DIM
            kit.drawText(canvas, detail, cx, cy - kit.u(12f), kit.small)
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

        /** Long enough to watch the ball break before the result appears. */
        const val BREAK_OVERLAY_DELAY = 0.7f
        const val OVERLAY_FADE = 0.2f
        const val GOAL_PULSE = 0.6f
        const val RIPPLE_LIFE = 0.45f
        const val PARTICLE_LIFE = 0.9f
        const val LEAN_SETTLE = 0.08f

        /** Drag length for a full-power shot, in UI units. */
        const val MAX_DRAG = 120f

        /** Finger speeds at release (UI units per second) where a flick starts and stops adding power. */
        const val FLICK_START = 900f
        const val FLICK_FULL = 2600f
        const val FLICK_BOOST = 0.25
    }
}
