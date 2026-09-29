package com.carom.game.screens

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import com.carom.core.game.FlickAim
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
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * A level being played. The level fills the screen: its edges are the screen's edges (invisible,
 * but the ball bounces off them), with the level number, restart and level-list buttons floating
 * over the top and the hint over the bottom.
 *
 * Owns the [GameSession] (rules), the hold-and-throw input and everything drawn on top of the
 * board. The bounces left are shown inside the ball itself.
 *
 * Throwing: the ball sits in a launch zone (a soft ringed disc). The player touches anywhere in
 * it, and the ball follows the finger around the zone; a stroke throws it the way the stroke went,
 * as hard as the stroke was long (and a little harder for a quick one). There is no arrow: the ball
 * moves with the finger, and the zone's ring fills like a dial with the power.
 *
 * Scoring: the ball swirls into the ring and settles in its centre in a shower of sparks.
 */
class PlayScreen(host: GameHost, val index: Int, private val level: LevelData) : Screen(host), GameSession.Listener {

    override val palette: WorldPalette = Palette.forLevel(index)
    internal val session = GameSession(level).also { it.listener = this }
    internal val board = BoardRenderer(level, palette)

    private var aim = FlickAim(maxStroke = 1.0, restDistance = 1.0)

    /** Where the ball was when the finger went down; it follows the finger from there. */
    private var holdX = 0.0
    private var holdY = 0.0
    private var throwPower = 0.0
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
    private val sparks = ArrayList<Spark>()
    private val random = Random(index)
    private val star = Path()
    private val hintBox = RectF()

    /** The ball breaking, when the attempt ended by running out of bounces. */
    private var shatter: BallShatter? = null

    // The winning swirl: where the ball entered the ring (relative to its centre) and which way it turns.
    private var swirlRadius = 0f
    private var swirlAngle = 0f
    private var swirlTurn = 1f
    private var trailTimer = 0f
    private var settled = false

    private class Ripple(val x: Double, val y: Double) {
        var age = 0f
    }

    /** A spark: a short glowing streak flying outwards, or ([twinkle]) a small four-pointed star in place. */
    private class Spark(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        val life: Float, val size: Float, val color: Int, val twinkle: Boolean = false,
    ) {
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

    override fun onExit() = board.release()

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
        board.layout(scale, left, top, width.toInt(), height.toInt())

        val button = kit.u(48f)
        levelsButton.setCenter(safe.right - kit.u(8f) - button / 2, topBarY, button, button)
        restartButton.setCenter(safe.right - kit.u(12f) - button * 1.5f, topBarY, button, button)

        // Throwing is sized in screen terms so it feels the same on every device.
        aim = FlickAim(
            maxStroke = (kit.u(MAX_STROKE) / scale).toDouble(),
            restDistance = (kit.u(REST_DISTANCE) / scale).toDouble(),
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
        session.advance(dt.toDouble())
        host.rolling(if (session.state == GameSession.State.MOVING) (session.ball.speed / level.maxSpeed).toFloat() else 0f)
        if (ended) endTime += dt
        ripples.forEach { it.age += dt }
        ripples.removeAll { it.age > RIPPLE_LIFE }
        if (session.state == GameSession.State.WON) updateSwirl(dt)
        val drag = exp(-2.5f * dt)
        for (s in sparks) {
            s.age += dt
            s.x += s.vx * dt
            s.y += s.vy * dt
            s.vx *= drag
            s.vy *= drag
        }
        sparks.removeAll { it.age > it.life }
    }

    override val isAnimating: Boolean
        get() = session.state == GameSession.State.MOVING || ripples.isNotEmpty() || sparks.isNotEmpty() ||
            (ended && overlayProgress < 1f)

    override fun onLaunch() {
        shotsFired++
        host.haptic(Haptic.CLICK)
        host.sound(Sound.LAUNCH, throwPower)
    }

    override fun onBounce(impact: GameSession.Impact, bouncesLeft: Int) {
        ripples += Ripple(impact.x, impact.y)
        host.haptic(Haptic.BOUNCE)
        host.sound(Sound.IMPACT, impact.strength, step = session.bouncesUsed - 1)
    }

    override fun onWin(x: Double, y: Double) {
        arrangeResultButtons()
        host.app.progress.markCompleted(index)
        host.haptic(Haptic.SUCCESS)
        host.sound(Sound.WIN)
        // The ball enters the ring where it crossed it and keeps turning the way it was going.
        val dx = board.x(x) - board.x(level.goal.x)
        val dy = board.y(y) - board.y(level.goal.y)
        swirlRadius = hypot(dx, dy)
        swirlAngle = atan2(dy, dx)
        val turn = dx * session.ball.dirY.toFloat() - dy * session.ball.dirX.toFloat()
        swirlTurn = if (turn < 0f) -1f else 1f
        settled = false
        burst(count = 20, fromRing = true)
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
        shatter = null
        ripples.clear()
        sparks.clear()
    }

    // ---------------------------------------------------------------- the winning swirl

    /** Where the swirling ball is at [t] seconds after scoring, relative to the goal's centre, and its size. */
    private fun swirl(t: Float): Triple<Float, Float, Float> {
        val p = (t / SWIRL_TIME).coerceIn(0f, 1f)
        val r = swirlRadius * (1f - p).pow(1.3f)
        val a = swirlAngle + swirlTurn * SWIRL_TURNS * 2f * PI.toFloat() * (1f - (1f - p) * (1f - p))
        val size = 1f - 0.28f * (1f - (1f - p) * (1f - p))
        return Triple(cos(a) * r, sin(a) * r, size)
    }

    private fun updateSwirl(dt: Float) {
        val gx = board.x(level.goal.x)
        val gy = board.y(level.goal.y)
        if (endTime < SWIRL_TIME) {
            // A trail of small sparkles behind the circling ball.
            trailTimer += dt
            while (trailTimer > 0.04f) {
                trailTimer -= 0.04f
                val (bx, by) = swirl(endTime)
                val a = random.nextFloat() * 2f * PI.toFloat()
                val speed = kit.u(15f + 30f * random.nextFloat())
                sparks += Spark(gx + bx, gy + by, cos(a) * speed, sin(a) * speed, 0.35f + 0.2f * random.nextFloat(), kit.u(1.8f), sparkColor())
            }
        } else if (!settled) {
            // Settled in the centre: a last bright burst and a few twinkles round the ring.
            settled = true
            burst(count = 16, fromRing = false)
            val r = level.goalRadius.toFloat() * board.scale
            repeat(6) {
                val a = random.nextFloat() * 2f * PI.toFloat()
                val d = r * (0.9f + 0.5f * random.nextFloat())
                sparks += Spark(gx + cos(a) * d, gy + sin(a) * d, 0f, 0f, 0.5f + 0.25f * random.nextFloat(), kit.u(5f + 3f * random.nextFloat()), Palette.TEXT, twinkle = true)
            }
        }
    }

    /** Sparks flying out: from all round the ring, or from the centre. */
    private fun burst(count: Int, fromRing: Boolean) {
        val gx = board.x(level.goal.x)
        val gy = board.y(level.goal.y)
        val r = level.goalRadius.toFloat() * board.scale
        repeat(count) { i ->
            val a = (i + random.nextFloat()) / count * 2f * PI.toFloat()
            val start = if (fromRing) r * 0.95f else r * 0.3f
            val speed = kit.u(if (fromRing) 90f + 130f * random.nextFloat() else 60f + 90f * random.nextFloat())
            // A little sideways in the swirl's direction, so the sparks spin off the ring.
            val vx = cos(a) - swirlTurn * sin(a) * 0.35f
            val vy = sin(a) + swirlTurn * cos(a) * 0.35f
            sparks += Spark(gx + cos(a) * start, gy + sin(a) * start, vx * speed, vy * speed, 0.45f + 0.35f * random.nextFloat(), kit.u(2.4f), sparkColor())
        }
    }

    private fun sparkColor(): Int = if (random.nextFloat() < 0.55f) palette.accent else Palette.TEXT

    // ---------------------------------------------------------------- input

    override fun onTouch(e: MotionEvent): Boolean {
        if (overlayProgress > 0f) return routeToButtons(e, overlayButtons())
        if (!aim.isActive && routeToButtons(e, hudButtons)) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (session.state == GameSession.State.AIMING && inLaunchZone(e.x, e.y)) {
                    holdX = session.ball.x
                    holdY = session.ball.y
                    aim.begin(board.worldX(e.x), board.worldY(e.y), seconds(e))
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> if (aim.isActive) {
                // Every point the finger passed through, so a stroke is measured faithfully.
                for (h in 0 until e.historySize) {
                    aim.move(board.worldX(e.getHistoricalX(h)), board.worldY(e.getHistoricalY(h)), e.getHistoricalEventTime(h) / 1000.0)
                }
                aim.move(board.worldX(e.x), board.worldY(e.y), seconds(e))
                session.placeBall(holdX + aim.offsetX, holdY + aim.offsetY)
                return true
            }
            MotionEvent.ACTION_UP -> if (aim.isActive) {
                val thrown = aim.release(board.worldX(e.x), board.worldY(e.y), seconds(e))
                session.placeBall(holdX + aim.offsetX, holdY + aim.offsetY)
                if (thrown != null) {
                    throwPower = thrown.power
                    session.launch(thrown.dirX, thrown.dirY, thrown.power)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> aim.cancel()
        }
        return false
    }

    /** The ball can be picked up anywhere in its launch zone, or anywhere on (and just around) it. */
    private fun inLaunchZone(sx: Float, sy: Float): Boolean {
        val zone = hypot(sx - board.x(level.ball.x), sy - board.y(level.ball.y)) <= board.zoneScreenRadius + kit.u(12f)
        val onBall = hypot(sx - board.x(session.ball.x), sy - board.y(session.ball.y)) <= max(board.ballScreenRadius * 1.6f, kit.u(34f))
        return zone || onBall
    }

    override fun onBack(): Boolean {
        if (aim.isActive) aim.cancel() else host.showLevels(index)
        return true
    }

    private fun seconds(e: MotionEvent): Double = e.eventTime / 1000.0

    private fun overlayButtons(): List<UiButton> = when {
        session.state == GameSession.State.FAILED -> listOf(retryButton, menuButton)
        isLastLevel -> listOf(replayButton, menuButton)
        else -> listOf(nextButton, replayButton, menuButton)
    }

    // ---------------------------------------------------------------- drawing

    override fun draw(canvas: Canvas) {
        board.drawBackdrop(canvas)

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

        val pulse = if (session.state == GameSession.State.WON && endTime >= SWIRL_TIME) (endTime - SWIRL_TIME) / GOAL_PULSE else 0f
        board.drawGoal(canvas, if (pulse in 0f..1f) pulse else 0f)
        if (session.state == GameSession.State.AIMING) {
            board.drawLaunchZone(canvas, kit.unit, active = aim.isActive, power = aim.power.toFloat(), ready = aim.isThrowReady)
        }
        drawRipples(canvas)
        drawBall(canvas, ballX, ballY)
        drawSparks(canvas)
        drawHud(canvas)

        val overlay = overlayProgress
        if (overlay > 0f) drawResult(canvas, overlay)
    }

    private fun drawBall(canvas: Canvas, x: Float, y: Float) {
        val left = session.bouncesLeft
        // With no bounces left the ball shows cracks: the next wall breaks it.
        val style = if (left == 0) BallStyle.CRACKED else BallStyle.SOLID
        when (session.state) {
            GameSession.State.WON -> {
                val (sx, sy, size) = swirl(endTime)
                board.drawBall(canvas, board.x(level.goal.x) + sx, board.y(level.goal.y) + sy, size, BallStyle.SOLID, left)
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
            GameSession.State.MOVING -> board.drawBall(canvas, x, y, 1f, style, left)
            // Held, the ball is lifted a touch.
            GameSession.State.AIMING -> board.drawBall(canvas, x, y, if (aim.isActive) 1.06f else 1f, style, left)
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

    private fun drawSparks(canvas: Canvas) {
        for (s in sparks) {
            val t = s.age / s.life
            if (s.twinkle) {
                // Grows in, shines, and fades: a four-pointed star.
                val k = sin(t * PI.toFloat())
                val r = s.size * k
                kit.fill.color = Palette.withAlpha(s.color, 0.9f * k)
                star.reset()
                for (i in 0 until 8) {
                    val a = i * PI.toFloat() / 4
                    val d = if (i % 2 == 0) r else r * 0.28f
                    val px = s.x + cos(a) * d
                    val py = s.y + sin(a) * d
                    if (i == 0) star.moveTo(px, py) else star.lineTo(px, py)
                }
                star.close()
                canvas.drawPath(star, kit.fill)
            } else {
                // A short bright streak along its flight, fading out.
                kit.stroke.color = Palette.withAlpha(s.color, 1f - t)
                kit.stroke.strokeWidth = s.size * (1f - 0.5f * t)
                canvas.drawLine(s.x, s.y, s.x - s.vx * 0.06f, s.y - s.vy * 0.06f, kit.stroke)
            }
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
        /** The swirl into the ring, then a moment to enjoy it, before the result appears. */
        const val SWIRL_TIME = 0.95f
        const val SWIRL_TURNS = 1.6f
        const val WIN_OVERLAY_DELAY = 1.45f
        const val FAIL_OVERLAY_DELAY = 0.5f

        /** Long enough to watch the ball break before the result appears. */
        const val BREAK_OVERLAY_DELAY = 0.7f
        const val OVERLAY_FADE = 0.2f
        const val GOAL_PULSE = 0.6f
        const val RIPPLE_LIFE = 0.45f

        /** Stroke length for a full-power throw, in UI units. */
        const val MAX_STROKE = 110f

        /** A finger moving less than this (UI units) for a moment is resting. */
        const val REST_DISTANCE = 3f

        /** Finger speeds at release (UI units per second) where a flick starts and stops adding power. */
        const val FLICK_START = 900f
        const val FLICK_FULL = 2600f
        const val FLICK_BOOST = 0.25
    }
}
