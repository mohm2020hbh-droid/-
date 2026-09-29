package com.carom.game.screens

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import com.carom.core.audio.MusicLibrary
import com.carom.core.audio.TrackSpec
import com.carom.core.game.FlickAim
import com.carom.core.game.GameSession
import com.carom.core.game.GameTuning
import com.carom.core.game.HintRoute
import com.carom.core.game.TouchControl
import com.carom.core.level.ElementKind
import com.carom.core.level.LevelData
import com.carom.core.level.Worlds
import com.carom.game.render.BallShatter
import com.carom.game.render.BoardRenderer
import com.carom.game.render.BoardRenderer.BallStyle
import com.carom.game.render.ElementsRenderer
import com.carom.game.ui.Icon
import com.carom.game.ui.Icons
import com.carom.game.ui.Palette
import com.carom.game.ui.UiButton
import com.carom.game.ui.WorldPalette
import java.util.Locale
import kotlin.math.PI
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
 * Owns the [GameSession] (rules), the touch input and everything drawn on top of the board. The
 * bounces left are shown inside the ball itself.
 *
 * - Throwing: a swipe is read as a delta; delta × sensibility is an impulse, and the impulse is the
 *   ball's velocity. The ball does not go to the finger. A ring round the ball fills with the swipe's
 *   power like a dial, so nothing points anywhere. (The earlier hold-and-throw control is still
 *   there, chosen by [GameTuning.controlMode].)
 * - A wall hit is felt: a hard knock, a short screen shake and a light tap of the vibration motor,
 *   all on the same frame, a little stronger for a harder hit.
 * - Losing costs a second at most: a ball breaks (or fades, if it just stopped), and when the last
 *   one is gone a new ball is back at the start, ready to throw. No screen, no button.
 * - Scoring: the ball stops in the ring, turns into a small fan, spins up with a whir and
 *   explodes; then the next level starts by itself (a level that ends a world, or the last one,
 *   shows its result card instead).
 * - Slow motion: inside a slow-motion zone game time runs at an eighth and every sound drops in pitch.
 * - A level with a path hint shows it, roughly, once the player has failed it enough times.
 */
class PlayScreen(host: GameHost, val index: Int, private val level: LevelData) : Screen(host), GameSession.Listener {

    override val palette: WorldPalette = Palette.forLevel(index)
    private val tuning = GameTuning.DEFAULT
    internal val session = GameSession(level, tuning).also { it.listener = this }
    internal val board = BoardRenderer(level, palette)
    private val elementsView = ElementsRenderer(level, palette, board)

    /** The music for this level: its world's, or the hardcore folder's for a hard level. */
    internal val track: TrackSpec = MusicLibrary.trackFor(index, Worlds.worldOf(index), level.hardcore)

    /** The swipe control: touches in dp in, gestures and impulses out. */
    private val swipe = TouchControl(tuning)
    private var swipeActive = false
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

    /** A level that ends a world, or the last one, shows the result card; any other goes straight to the next. */
    private val showsResult = isLastLevel || opensNewWorld
    private var leftForNext = false
    private val nextButton = UiButton(UiButton.Style.PRIMARY, kit.text.next, Icon.PLAY) { host.play(index + 1) }
    private val replayButton = UiButton(UiButton.Style.OUTLINE, kit.text.restart, Icon.RESTART) { restart() }
    private val menuButton = UiButton(UiButton.Style.OUTLINE, kit.text.levels, Icon.GRID) { host.showLevels(index) }

    /** Seconds since the attempt was won or lost, and seconds on this screen (for looping animations). */
    private var endTime = 0f
    private var clock = 0f

    // Effects come from fixed pools: nothing is created while playing.
    private val ripples = Array(MAX_RIPPLES) { Ripple() }
    private val sparks = Array(MAX_SPARKS) { Spark() }
    private var sparkCursor = 0
    private val random = Random(index)
    private val shape = Path()
    private val hintBox = RectF()

    // The wrapped caption (hint), worked out when it or the screen width changes.
    private var captionKey: String? = null
    private var captionFor = 0f
    private var captionLines: List<String> = emptyList()
    private var captionWidth = 0f

    // Screen shake: how long it has run, for how long, and how far.
    private var shakeTime = 0f
    private var shakeLength = 0f
    private var shakeSize = 0f

    /** Balls breaking; each plays out even as the next ball appears. */
    private val shatters = Array(MAX_SHATTERS) { BallShatter() }
    private var shatterCursor = 0

    // A new ball appearing at the start after a loss, popping in.
    private var respawnTime = -1f

    // Slow motion: how far the slow-motion look has come in (0..1), and the exit's ring flashing when a ball enters.
    private var slowAmount = 0f
    private var exitFlash = 0f

    // Scoring: where the ball entered the ring, and which steps of the fan's show have happened.
    private var entryX = 0f
    private var entryY = 0f
    private var fanSpin = 1f
    private var whirStarted = false
    private var exploded = false
    private var chimed = false

    /** The path hint, worked out the first time it is needed. */
    private val route: HintRoute? by lazy { level.guide?.let { HintRoute.plan(level, it) } }
    private var guideShown = false

    private class Ripple {
        var alive = false
        var x = 0.0
        var y = 0.0
        var age = 0f
    }

    private enum class SparkKind { STREAK, STAR, CHUNK }

    /** A spark: a glowing streak, a twinkling four-pointed star, or a tumbling fragment. Reused, never recreated. */
    private class Spark {
        var alive = false
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        var life = 1f
        var size = 1f
        var color = 0
        var kind = SparkKind.STREAK
        var spin = 0f
        var age = 0f
    }

    private val ended get() = session.state == GameSession.State.WON || session.state == GameSession.State.FAILED

    /** The result card is only for a level that ends a world (or the last); a loss never stops play. */
    private val overlayProgress: Float
        get() = if (session.state != GameSession.State.WON || !showsResult) 0f else ((endTime - WIN_OVERLAY_DELAY) / OVERLAY_FADE).coerceIn(0f, 1f)

    override fun onEnter() {
        host.app.progress.lastPlayedIndex = index
        guideShown = guideDue()
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
        elementsView.layout(kit.unit)

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

    /** Stacks the result buttons under the title. */
    private fun arrangeResultButtons() {
        overlayButtons().forEachIndexed { i, b ->
            b.setCenter(width / 2, height / 2 + kit.u(40f + 60f * i), kit.u(230f), kit.u(48f))
        }
    }

    // ---------------------------------------------------------------- simulation & feedback

    override fun update(dt: Float) {
        clock += dt
        session.advance(dt.toDouble())
        var fastest = 0.0
        for (b in session.balls) if (b.alive && b.body.speed > fastest) fastest = b.body.speed
        host.rolling(if (session.state == GameSession.State.MOVING) (fastest / level.maxSpeed).toFloat() else 0f)
        if (ended) endTime += dt

        // Effects run on game time: in slow motion the sparks, rings, breaks and the shake slow down with the ball.
        val fx = dt * session.timeScale.toFloat()
        if (shakeTime < shakeLength) shakeTime += fx
        for (s in shatters) s.advance(fx)
        if (respawnTime >= 0f) {
            respawnTime += dt
            if (respawnTime > RESPAWN_TIME) respawnTime = -1f
        }
        for (r in ripples) if (r.alive) {
            r.age += fx
            if (r.age > RIPPLE_LIFE) r.alive = false
        }
        val drag = exp(-2.5f * fx)
        for (s in sparks) if (s.alive) {
            s.age += fx
            s.x += s.vx * fx
            s.y += s.vy * fx
            s.vx *= drag
            s.vy *= drag
            if (s.age > s.life) s.alive = false
        }
        val slowTarget = if (session.isSlowMotion) 1f else 0f
        slowAmount += (slowTarget - slowAmount) * (1f - exp(-6f * dt))
        if (slowAmount < 0.002f && slowTarget == 0f) slowAmount = 0f
        exitFlash = max(0f, exitFlash - dt * 2.5f)

        when (session.state) {
            GameSession.State.WON -> {
                updateFan(dt)
                // No card to press: the next level comes by itself after a moment.
                if (!showsResult && !leftForNext && endTime >= tuning.nextLevelDelay) {
                    leftForNext = true
                    host.play(index + 1)
                }
            }
            GameSession.State.FAILED -> if (endTime >= tuning.retryDelay) retry()
            else -> {}
        }
    }

    override val isAnimating: Boolean
        get() = session.state == GameSession.State.MOVING || anyRipple() || anySpark() || anyShatter() ||
            shakeTime < shakeLength || respawnTime >= 0f || session.state == GameSession.State.FAILED || slowAmount > 0f || exitFlash > 0f ||
            (session.state == GameSession.State.WON && (overlayProgress < 1f || !showsResult)) || (guideShown && session.state == GameSession.State.AIMING)

    /** A level with force zones, portals and the like keeps moving even while the player aims (at a gentle rate). */
    override val idleRedrawMillis: Long get() = if (level.elements.any { it.kind != ElementKind.SOLID && it.kind != ElementKind.TOUCH_ZONE }) 33L else 0L

    private fun anyRipple() = ripples.any { it.alive }
    private fun anySpark() = sparks.any { it.alive }
    private fun anyShatter() = shatters.any { it.isPlaying }

    private fun addRipple(x: Double, y: Double) {
        val r = ripples.firstOrNull { !it.alive } ?: ripples.minByOrNull { -it.age } ?: return
        r.alive = true
        r.x = x
        r.y = y
        r.age = 0f
    }

    /** Sets a spark going, in a free slot (or the oldest slot if all are busy). */
    private fun spark(
        x: Float, y: Float, vx: Float, vy: Float, life: Float, size: Float, color: Int,
        kind: SparkKind = SparkKind.STREAK, spin: Float = 0f,
    ) {
        var s: Spark? = null
        for (i in 0 until MAX_SPARKS) {
            val c = sparks[(sparkCursor + i) % MAX_SPARKS]
            if (!c.alive) {
                s = c
                sparkCursor = (sparkCursor + i + 1) % MAX_SPARKS
                break
            }
        }
        if (s == null) {
            s = sparks[sparkCursor]
            sparkCursor = (sparkCursor + 1) % MAX_SPARKS
        }
        s.alive = true
        s.x = x
        s.y = y
        s.vx = vx
        s.vy = vy
        s.life = life
        s.size = size
        s.color = color
        s.kind = kind
        s.spin = spin
        s.age = 0f
    }

    private fun startShatter(x: Float, y: Float, awayX: Float, awayY: Float) {
        val s = shatters.firstOrNull { !it.isPlaying } ?: shatters[shatterCursor].also { shatterCursor = (shatterCursor + 1) % MAX_SHATTERS }
        s.start(x, y, board.ballScreenRadius, awayX, awayY)
    }

    private fun shake(size: Float, length: Float) {
        // A new shake never cuts a stronger one short.
        val left = if (shakeTime < shakeLength) shakeSize * (1f - shakeTime / shakeLength) else 0f
        if (size < left) return
        shakeSize = size
        shakeLength = length
        shakeTime = 0f
    }

    override fun onLaunch() {
        shotsFired++
        guideShown = false
        respawnTime = -1f
        host.haptic(Haptic.CLICK)
        host.sound(Sound.LAUNCH, throwPower)
    }

    override fun onImpulse(ball: Int) {
        host.haptic(Haptic.CLICK)
        host.sound(Sound.LAUNCH, throwPower * 0.6)
    }

    override fun onBounce(impact: GameSession.Impact, bouncesLeft: Int) {
        // The knock: sound, shake and vibration together, on this very frame. The pitch starts low and rises
        // with every bounce the ball has used, so the sound tells how many it has left.
        val s = impact.strength
        val pitch = tuning.bouncePitch(impact.progress).toFloat()
        host.sound(if (impact.kind == ElementKind.BALL_CONTAINER) Sound.IMPACT_CONTAINER else Sound.IMPACT, s, pitch)
        host.haptic(Haptic.BOUNCE, s)
        shake(kit.u(1.5f + 3.5f * s.toFloat()), BOUNCE_SHAKE_TIME)
        addRipple(impact.x, impact.y)
    }

    override fun onWin(x: Double, y: Double) {
        arrangeResultButtons()
        host.app.progress.markCompleted(index)
        host.haptic(Haptic.SUCCESS)
        host.soundPitch(1f)
        entryX = board.x(x)
        entryY = board.y(y)
        endTime = 0f
        // The fan turns the way the ball was curving round the ring's centre.
        val won = session.winnerBall.body
        val turn = (entryX - board.x(level.goal.x)) * won.dirY.toFloat() - (entryY - board.y(level.goal.y)) * won.dirX.toFloat()
        fanSpin = if (turn < 0f) -1f else 1f
        whirStarted = false
        exploded = false
        chimed = false
    }

    /** A ball went into an exit that needs more than one. */
    override fun onExitPartial(count: Int, needed: Int, x: Double, y: Double) {
        host.sound(Sound.EXIT_PARTIAL)
        host.haptic(Haptic.CLICK)
        exitFlash = 1f
        addRipple(level.goal.x, level.goal.y)
    }

    /** One ball is gone: it breaks (out of bounces, or into a deadly zone), or simply fades if it stopped. */
    override fun onBallLost(ball: Int, reason: GameSession.FailReason, x: Double, y: Double) {
        if (reason == GameSession.FailReason.STOPPED) {
            host.sound(Sound.FIZZLE)
            host.haptic(Haptic.BOUNCE, 0.3)
            return
        }
        val hit = session.lastImpact?.takeIf { reason == GameSession.FailReason.OUT_OF_BOUNCES }
        val b = session.balls[ball].body
        val awayX = hit?.nx?.toFloat() ?: -b.dirX.toFloat()
        val awayY = hit?.ny?.toFloat() ?: -b.dirY.toFloat()
        startShatter(board.x(x), board.y(y), awayX, awayY)
        host.sound(Sound.SHATTER, hit?.strength ?: 1.0)
        host.haptic(Haptic.BREAK)
        shake(kit.u(4f), BREAK_SHAKE_TIME)
    }

    /** Every ball is gone: after a moment the level starts again by itself. */
    override fun onFail(reason: GameSession.FailReason, x: Double, y: Double) {
        endTime = 0f
    }

    override fun onPortal(ball: Int, fromX: Double, fromY: Double, toX: Double, toY: Double) {
        host.sound(Sound.PORTAL)
        host.haptic(Haptic.CLICK)
        addRipple(fromX, fromY)
        addRipple(toX, toY)
    }

    override fun onSlowMo(active: Boolean) {
        host.sound(if (active) Sound.SLOW_IN else Sound.SLOW_OUT)
        host.soundPitch(if (active) tuning.slowMoPitch.toFloat() else 1f)
    }

    override fun onBallSpawned(ball: Int, x: Double, y: Double) {
        host.sound(Sound.RESPAWN)
        addRipple(x, y)
    }

    override fun onElement(element: Int, event: GameSession.ElementEvent) {
        val e = session.elements[element]
        when (event) {
            GameSession.ElementEvent.BROKEN -> {
                // A barrier breaks into tumbling fragments.
                val cx = board.x(e.x)
                val cy = board.y(e.y)
                repeat(8) { i ->
                    val a = (i + random.nextFloat()) / 8f * 2f * PI.toFloat()
                    val speed = kit.u(90f + 140f * random.nextFloat())
                    spark(cx, cy, cos(a) * speed, sin(a) * speed, 0.5f + 0.2f * random.nextFloat(), board.ballScreenRadius * 0.28f, palette.primary,
                        kind = SparkKind.CHUNK, spin = (4f + 6f * random.nextFloat()) * if (random.nextBoolean()) 1f else -1f)
                }
                host.sound(Sound.SHATTER, 0.5)
                host.haptic(Haptic.BREAK)
                shake(kit.u(3f), BREAK_SHAKE_TIME)
            }
            GameSession.ElementEvent.SWITCHED -> {
                host.sound(Sound.TAP)
                host.haptic(Haptic.CLICK)
                addRipple(e.x, e.y)
            }
            GameSession.ElementEvent.OPENED -> addRipple(e.x, e.y)
        }
    }

    /** Straight into the next attempt: a new ball at the start, everything as it was, nothing to press. */
    private fun retry() {
        val fails = host.app.progress.recordFail(index)
        session.restart()
        swipe.cancel()
        swipeActive = false
        aim.cancel()
        endTime = 0f
        for (r in ripples) r.alive = false
        respawnTime = 0f
        slowAmount = 0f
        host.soundPitch(1f)
        host.sound(Sound.RESPAWN)
        guideShown = guideDue(fails)
    }

    /** Whether this level's path hint should show: only after more failed attempts than it allows. */
    private fun guideDue(fails: Int = host.app.progress.failCount(index)): Boolean {
        val guide = level.guide ?: return false
        return fails > guide.afterFails && route?.scores == true
    }

    private fun restart() {
        host.stopSound(Sound.SPIN)
        session.restart()
        swipe.cancel()
        swipeActive = false
        aim.cancel()
        endTime = 0f
        for (s in shatters) s.advance(BallShatter.DURATION)
        respawnTime = -1f
        shakeTime = shakeLength
        for (r in ripples) r.alive = false
        for (s in sparks) s.alive = false
        slowAmount = 0f
        host.soundPitch(1f)
        guideShown = guideDue()
    }

    // ---------------------------------------------------------------- scoring: the fan

    /** How far into its spin-up the fan is (0..1); also how blurred it looks. */
    private fun spinUp(t: Float): Float = ((t - MORPH_START) / (EXPLODE - MORPH_START)).coerceIn(0f, 1f)

    /** The fan's turn, in degrees: slow at first, accelerating to about 40 turns a second. */
    private fun fanAngle(t: Float): Float {
        val p = spinUp(t)
        return fanSpin * 360f * 40f * (EXPLODE - MORPH_START) / 3f * p * p * p
    }

    private fun updateFan(dt: Float) {
        val gx = board.x(level.goal.x)
        val gy = board.y(level.goal.y)
        val r = board.ballScreenRadius
        if (!whirStarted && endTime >= MORPH_START) {
            whirStarted = true
            host.sound(Sound.SPIN)
        }
        if (!exploded && endTime >= MORPH_END) {
            // Sparks flung off the blade tips, more and faster as it speeds up.
            val p = spinUp(endTime)
            var n = p * p * 90f * dt + random.nextFloat()
            while (n >= 1f) {
                n -= 1f
                val a = fanAngle(endTime) / 180f * PI.toFloat() + random.nextInt(4) * PI.toFloat() / 2
                val tipX = gx + cos(a) * r * 1.2f
                val tipY = gy + sin(a) * r * 1.2f
                val speed = kit.u(60f + 160f * p)
                spark(tipX, tipY, -sin(a) * fanSpin * speed, cos(a) * fanSpin * speed, 0.25f + 0.2f * random.nextFloat(), kit.u(1.6f), sparkColor())
            }
        }
        if (!exploded && endTime >= EXPLODE) {
            exploded = true
            explode(gx, gy, r)
        }
        if (!chimed && endTime >= EXPLODE + CHIME_DELAY) {
            chimed = true
            host.sound(Sound.WIN)
        }
    }

    /** The fan bursts: a flash, a shock ring (drawn by time), sparks and tumbling fragments. */
    private fun explode(gx: Float, gy: Float, r: Float) {
        host.sound(Sound.EXPLOSION)
        host.haptic(Haptic.EXPLOSION)
        shake(kit.u(9f), EXPLOSION_SHAKE_TIME)
        repeat(36) { i ->
            val a = (i + random.nextFloat()) / 36f * 2f * PI.toFloat()
            val speed = kit.u(200f + 220f * random.nextFloat())
            spark(gx + cos(a) * r * 0.4f, gy + sin(a) * r * 0.4f, cos(a) * speed, sin(a) * speed, 0.4f + 0.4f * random.nextFloat(), kit.u(2.6f), sparkColor())
        }
        repeat(8) { i ->
            val a = (i + 0.5f * random.nextFloat()) / 8f * 2f * PI.toFloat()
            val speed = kit.u(120f + 120f * random.nextFloat())
            spark(
                gx, gy, cos(a) * speed, sin(a) * speed, 0.55f + 0.2f * random.nextFloat(), r * 0.32f, palette.accent,
                kind = SparkKind.CHUNK, spin = (4f + 6f * random.nextFloat()) * if (random.nextBoolean()) 1f else -1f,
            )
        }
        repeat(6) {
            val a = random.nextFloat() * 2f * PI.toFloat()
            val d = r * (1.4f + 1.2f * random.nextFloat())
            spark(gx + cos(a) * d, gy + sin(a) * d, 0f, 0f, 0.5f + 0.3f * random.nextFloat(), kit.u(5f + 4f * random.nextFloat()), Palette.TEXT, kind = SparkKind.STAR)
        }
    }

    private fun sparkColor(): Int = if (random.nextFloat() < 0.55f) palette.accent else Palette.TEXT

    // ---------------------------------------------------------------- input

    override fun onTouch(e: MotionEvent): Boolean {
        if (overlayProgress > 0f) return routeToButtons(e, overlayButtons())
        if (!swipeActive && !aim.isActive && routeToButtons(e, hudButtons)) return true
        return if (tuning.controlMode == GameTuning.ControlMode.SWIPE) onSwipeTouch(e) else onHoldTouch(e)
    }

    /**
     * The reference control: touch, drag, and on release the drag is a delta; delta × sensibility is the impulse.
     * The ball does not move to the finger. A tap (or a drag too short to be a swipe) does nothing.
     */
    private fun onSwipeTouch(e: MotionEvent): Boolean {
        val unit = kit.unit
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> if (session.canSwipe) {
                respawnTime = -1f
                swipe.begin(e.x / unit.toDouble(), e.y / unit.toDouble(), seconds(e))
                swipeActive = true
                return true
            }
            MotionEvent.ACTION_MOVE -> if (swipeActive) {
                swipe.move(e.x / unit.toDouble(), e.y / unit.toDouble())
                return true
            }
            MotionEvent.ACTION_UP -> if (swipeActive) {
                swipeActive = false
                val result = swipe.end(e.x / unit.toDouble(), e.y / unit.toDouble(), seconds(e))
                if (result.gesture == TouchControl.Gesture.SWIPE) {
                    throwPower = (result.length * tuning.touchSensibility / tuning.maxSpeedRef).coerceIn(0.0, 1.0)
                    session.swipe(result.dx, result.dy)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                swipe.cancel()
                swipeActive = false
            }
        }
        return false
    }

    /** The earlier control: the ball follows the finger round its launch zone, and a stroke throws it. */
    private fun onHoldTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (session.state == GameSession.State.AIMING && inLaunchZone(e.x, e.y)) {
                    respawnTime = -1f
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
        if (swipeActive) {
            swipe.cancel()
            swipeActive = false
        } else if (aim.isActive) aim.cancel() else host.showLevels(index)
        return true
    }

    private fun seconds(e: MotionEvent): Double = e.eventTime / 1000.0

    private fun overlayButtons(): List<UiButton> = if (isLastLevel) listOf(replayButton, menuButton) else listOf(nextButton, replayButton, menuButton)

    /** Test hooks: whether the path hint is showing, and whether a lost ball is still on its way back. */
    internal val isGuideShown: Boolean get() = guideShown
    internal val isRespawning: Boolean get() = respawnTime >= 0f

    // ---------------------------------------------------------------- drawing

    override fun draw(canvas: Canvas) {
        val shaking = shakeTime < shakeLength
        if (shaking) {
            val k = (1f - shakeTime / shakeLength).pow(2)
            canvas.drawColor(board.edgeColor)
            canvas.save()
            canvas.translate(shakeSize * k * sin(shakeTime * 2f * PI.toFloat() * 31f), shakeSize * k * 0.8f * cos(shakeTime * 2f * PI.toFloat() * 23f))
        }
        board.drawBackdrop(canvas)
        elementsView.draw(canvas, session, clock, kit.unit)

        val trail = Palette.withAlpha(palette.accent, 0.35f)
        val ghost = session.lastShotPath
        if (ghost.size > 1) board.drawShotPath(canvas, ghost, Float.NaN, Float.NaN, Palette.withAlpha(palette.accent, 0.2f), dashed = true)
        if (guideShown && session.state == GameSession.State.AIMING) drawGuide(canvas)

        val ballX = board.x(session.renderX)
        val ballY = board.y(session.renderY)
        if (session.state == GameSession.State.MOVING) {
            val first = session.balls[0]
            board.drawShotPath(canvas, session.path, if (first.alive) ballX else Float.NaN, ballY, trail, dashed = false)
        } else if (ended) {
            board.drawShotPath(canvas, session.path, Float.NaN, Float.NaN, trail, dashed = false)
        }

        val won = session.state == GameSession.State.WON
        val beat = if (session.state == GameSession.State.MOVING) host.beatPulse else 0f
        board.drawGoal(
            canvas, if (won && endTime >= EXPLODE) ((endTime - EXPLODE) / GOAL_PULSE).coerceAtMost(1f) else 0f,
            beat = max(beat, exitFlash), entered = session.exitCount, needed = level.exitRequired,
        )
        if (won && !exploded) {
            // The ring charges up as the fan spins.
            val p = spinUp(endTime)
            kit.fill.color = Palette.withAlpha(palette.accent, 0.18f * p)
            canvas.drawCircle(board.x(level.goal.x), board.y(level.goal.y), level.goalRadius.toFloat() * board.scale * 1.15f, kit.fill)
        }
        drawAimingAids(canvas)
        drawRipples(canvas)
        for (s in shatters) if (s.isPlaying) s.draw(canvas, palette.accent, kit.fill, kit.stroke)
        drawBalls(canvas)
        if (won && exploded) drawExplosion(canvas, endTime - EXPLODE)
        drawSparks(canvas)
        if (slowAmount > 0f) drawSlowMotion(canvas)
        if (shaking) canvas.restore()

        drawHud(canvas)
        val overlay = overlayProgress
        if (overlay > 0f) drawResult(canvas, overlay)
    }

    /** What shows where the player can act: the swipe dial round the ball (or the launch zone, for the hold control). */
    private fun drawAimingAids(canvas: Canvas) {
        if (tuning.controlMode == GameTuning.ControlMode.HOLD) {
            if (session.state == GameSession.State.AIMING) {
                board.drawLaunchZone(canvas, kit.unit, active = aim.isActive, power = aim.power.toFloat(), ready = aim.isThrowReady)
            }
            return
        }
        val dialPower = if (swipeActive) swipe.power.toFloat() else 0f
        if (session.state == GameSession.State.AIMING && respawnTime < 0f) {
            board.drawSwipeDial(canvas, board.x(session.renderX), board.y(session.renderY), kit.unit, swipeActive, dialPower, swipe.isSwipe)
        } else if (session.state == GameSession.State.MOVING) {
            // A ball in a touch zone can be swiped again: it wears the dial too.
            for (b in session.balls) {
                if (!b.alive || !b.inTouchZone) continue
                board.drawSwipeDial(canvas, board.x(session.renderX(b)), board.y(session.renderY(b)), kit.unit, swipeActive, dialPower, swipe.isSwipe, 0.8f)
            }
        }
    }

    /** Every ball in play (or the fan, once the level is won). */
    private fun drawBalls(canvas: Canvas) {
        when (session.state) {
            GameSession.State.WON -> drawFanStage(canvas, session.winnerBall.left)
            GameSession.State.FAILED -> {} // Broken: only its pieces are left (drawn by the shatters).
            GameSession.State.MOVING -> for (b in session.balls) {
                if (!b.alive) continue
                val style = if (b.left == 0) BallStyle.CRACKED else BallStyle.SOLID
                board.drawBall(canvas, board.x(session.renderX(b)), board.y(session.renderY(b)), 1f, style, b.left, b.alpha.toFloat())
            }
            GameSession.State.AIMING -> drawWaitingBall(canvas, board.x(session.renderX), board.y(session.renderY))
        }
    }

    /** The ball at the start: popping in after a loss, or waiting; held, it is lifted a touch. */
    private fun drawWaitingBall(canvas: Canvas, x: Float, y: Float) {
        val left = session.bouncesLeft
        val style = if (left == 0) BallStyle.CRACKED else BallStyle.SOLID
        if (respawnTime >= 0f) {
            // Popping in with a little overshoot.
            val p = (respawnTime / RESPAWN_TIME).coerceIn(0f, 1f)
            val c = 1.7f
            val q = p - 1f
            val size = (1f + (c + 1f) * q * q * q + c * q * q).coerceAtLeast(0.01f)
            board.drawBall(canvas, x, y, size, style, left)
        } else {
            board.drawBall(canvas, x, y, if (aim.isActive || swipeActive) 1.06f else 1f, style, left)
        }
    }

    /** Slow motion, seen: the edges of the screen close in softly and a thin ring breathes at the border. */
    private fun drawSlowMotion(canvas: Canvas) {
        val a = slowAmount
        kit.stroke.color = Palette.withAlpha(palette.accent, 0.10f * a)
        kit.stroke.strokeWidth = kit.u(28f) * a
        canvas.drawRect(kit.u(14f) * a, kit.u(14f) * a, width - kit.u(14f) * a, height - kit.u(14f) * a, kit.stroke)
        kit.stroke.color = Palette.withAlpha(palette.accent, (0.16f + 0.08f * sin(clock * 2.5f)) * a)
        kit.stroke.strokeWidth = kit.u(1.5f)
        canvas.drawRect(kit.u(6f), kit.u(6f), width - kit.u(6f), height - kit.u(6f), kit.stroke)
    }

    /** Entering, stopping, turning into a fan and spinning up, until it explodes. */
    private fun drawFanStage(canvas: Canvas, left: Int) {
        if (exploded) return
        val gx = board.x(level.goal.x)
        val gy = board.y(level.goal.y)
        val t = endTime
        if (t < ENTER_TIME) {
            val p = t / ENTER_TIME
            val e = 1f - (1f - p) * (1f - p)
            board.drawBall(canvas, entryX + (gx - entryX) * e, entryY + (gy - entryY) * e, 1f, BallStyle.SOLID, left)
            return
        }
        if (t < MORPH_START) {
            board.drawBall(canvas, gx, gy, 1f, BallStyle.SOLID, left)
            return
        }
        val grow = ((t - MORPH_START) / (MORPH_END - MORPH_START)).coerceIn(0f, 1f)
        val p = spinUp(t)
        // Near top speed it strains and trembles a little.
        val tremble = kit.u(1.5f) * p * p * p
        val jx = (random.nextFloat() - 0.5f) * 2f * tremble
        val jy = (random.nextFloat() - 0.5f) * 2f * tremble
        board.drawFan(canvas, gx + jx, gy + jy, 1f - (1f - grow) * (1f - grow), fanAngle(t), p * p, left)
    }

    /** [t] seconds after the fan burst: a bright flash and a shock ring racing outwards. */
    private fun drawExplosion(canvas: Canvas, t: Float) {
        val gx = board.x(level.goal.x)
        val gy = board.y(level.goal.y)
        val r = board.ballScreenRadius
        if (t < FLASH_TIME) {
            val k = t / FLASH_TIME
            kit.fill.color = Palette.withAlpha(Palette.TEXT, 0.85f * (1f - k))
            canvas.drawCircle(gx, gy, r * (0.4f + 1.8f * k), kit.fill)
        }
        if (t < RING_TIME) {
            val k = t / RING_TIME
            val e = 1f - (1f - k) * (1f - k)
            kit.stroke.color = Palette.withAlpha(palette.accent, 0.75f * (1f - k))
            kit.stroke.strokeWidth = kit.u(5f) * (1f - k) + kit.u(1f)
            canvas.drawCircle(gx, gy, r * (1f + 2.4f * e), kit.stroke)
        }
    }

    /**
     * The path hint: a soft corridor along the hinted path with dashes marching the way the ball
     * goes and small chevrons, numbered marks where it bounces, a ring at the start, a target
     * where it reaches the goal, and a glowing outline on the obstacles it passes. Drawn lightly,
     * under the ball, so the level stays readable.
     */
    private fun drawGuide(canvas: Canvas) {
        val path = route ?: return
        val pts = path.points.map { board.x(it.x) to board.y(it.y) }
        if (pts.size < 2) return
        val pulse = 0.5f + 0.5f * sin(clock * 4f)
        for (i in path.nearObstacles) board.drawObstacleOutline(canvas, i, kit.unit, 0.6f + 0.4f * pulse)

        shape.reset()
        shape.moveTo(pts[0].first, pts[0].second)
        for (i in 1 until pts.size) shape.lineTo(pts[i].first, pts[i].second)
        kit.stroke.color = Palette.withAlpha(palette.accent, 0.08f)
        kit.stroke.strokeWidth = board.ballScreenRadius * 0.9f
        canvas.drawPath(shape, kit.stroke)
        kit.stroke.color = Palette.withAlpha(palette.accent, 0.7f)
        kit.stroke.strokeWidth = kit.u(2.5f)
        kit.stroke.pathEffect = DashPathEffect(floatArrayOf(kit.u(10f), kit.u(9f)), -clock * kit.u(40f))
        canvas.drawPath(shape, kit.stroke)
        kit.stroke.pathEffect = null

        // Chevrons pointing along the path.
        val step = kit.u(70f)
        val c = kit.u(6f)
        kit.stroke.color = Palette.withAlpha(palette.accent, 0.85f)
        kit.stroke.strokeWidth = kit.u(2.2f)
        var carry = kit.u(45f)
        for (i in 0 until pts.size - 1) {
            val (ax, ay) = pts[i]
            val (bx, by) = pts[i + 1]
            val len = hypot(bx - ax, by - ay)
            if (len < 1f) continue
            val ux = (bx - ax) / len
            val uy = (by - ay) / len
            var d = carry
            while (d < len - kit.u(20f)) {
                val px = ax + ux * d
                val py = ay + uy * d
                canvas.drawLine(px - ux * c - uy * c, py - uy * c + ux * c, px, py, kit.stroke)
                canvas.drawLine(px - ux * c + uy * c, py - uy * c - ux * c, px, py, kit.stroke)
                d += step
            }
            carry = max(kit.u(20f), d - len)
        }

        // Where it bounces, numbered.
        for (i in 1 until pts.size - 1) {
            val (px, py) = pts[i]
            kit.fill.color = Palette.withAlpha(palette.background, 0.85f)
            canvas.drawCircle(px, py, kit.u(10f), kit.fill)
            kit.stroke.color = palette.accent
            kit.stroke.strokeWidth = kit.u(2f)
            canvas.drawCircle(px, py, kit.u(10f), kit.stroke)
            kit.small.color = palette.accent
            kit.drawText(canvas, i.toString(), px, py, kit.small)
            kit.small.color = Palette.TEXT
        }
        // Start and finish.
        val (sx, sy) = pts.first()
        kit.stroke.color = Palette.withAlpha(palette.accent, 0.45f + 0.35f * pulse)
        kit.stroke.strokeWidth = kit.u(2f)
        canvas.drawCircle(sx, sy, board.ballScreenRadius + kit.u(6f + 4f * pulse), kit.stroke)
        val (ex, ey) = pts.last()
        kit.fill.color = palette.accent
        canvas.drawCircle(ex, ey, kit.u(4.5f), kit.fill)
        kit.stroke.color = Palette.withAlpha(palette.accent, 0.9f)
        canvas.drawCircle(ex, ey, kit.u(10f + 3f * pulse), kit.stroke)
    }

    private fun drawRipples(canvas: Canvas) {
        val r = board.ballScreenRadius
        for (ripple in ripples) {
            if (!ripple.alive) continue
            val t = ripple.age / RIPPLE_LIFE
            kit.stroke.color = Palette.withAlpha(palette.accent, 0.7f * (1f - t))
            kit.stroke.strokeWidth = kit.u(2f)
            canvas.drawCircle(board.x(ripple.x), board.y(ripple.y), r * (1f + t * 1.2f), kit.stroke)
        }
    }

    private fun drawSparks(canvas: Canvas) {
        for (s in sparks) {
            if (!s.alive) continue
            val t = s.age / s.life
            when (s.kind) {
                SparkKind.STAR -> {
                    // Grows in, shines, and fades: a four-pointed star.
                    val k = sin(t * PI.toFloat())
                    val r = s.size * k
                    kit.fill.color = Palette.withAlpha(s.color, 0.9f * k)
                    shape.reset()
                    for (i in 0 until 8) {
                        val a = i * PI.toFloat() / 4
                        val d = if (i % 2 == 0) r else r * 0.28f
                        val px = s.x + cos(a) * d
                        val py = s.y + sin(a) * d
                        if (i == 0) shape.moveTo(px, py) else shape.lineTo(px, py)
                    }
                    shape.close()
                    canvas.drawPath(shape, kit.fill)
                }
                SparkKind.CHUNK -> {
                    // A tumbling fragment of the fan, shrinking away.
                    val a = s.age * s.spin
                    val r = s.size * (1f - 0.6f * t)
                    kit.fill.color = Palette.withAlpha(s.color, 1f - t)
                    shape.reset()
                    for (k in 0..2) {
                        val ang = a + k * 2.094f
                        val px = s.x + cos(ang) * r
                        val py = s.y + sin(ang) * r * 0.6f
                        if (k == 0) shape.moveTo(px, py) else shape.lineTo(px, py)
                    }
                    shape.close()
                    canvas.drawPath(shape, kit.fill)
                }
                SparkKind.STREAK -> {
                    // A short bright streak along its flight, fading out.
                    kit.stroke.color = Palette.withAlpha(s.color, 1f - t)
                    kit.stroke.strokeWidth = s.size * (1f - 0.5f * t)
                    canvas.drawLine(s.x, s.y, s.x - s.vx * 0.06f, s.y - s.vy * 0.06f, kit.stroke)
                }
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

        val caption = when {
            session.state != GameSession.State.AIMING -> null
            guideShown -> kit.text.guideCaption
            shotsFired == 0 -> hint
            else -> null
        }
        if (caption != null) {
            // A hint or the path hint's caption; a long one wraps onto a second line. The path hint's sits up under
            // the level number, clear of the path itself.
            if (caption != captionKey || captionFor != safe.width()) wrapCaption(caption)
            val lineHeight = kit.u(16f)
            val extra = (captionLines.size - 1) * lineHeight
            val w = min(captionWidth + kit.u(28f), safe.width() - kit.u(16f))
            val y = if (guideShown) topBarY + kit.u(46f) + extra / 2 else hintY - extra / 2
            hintBox.set(width / 2 - w / 2, y - kit.u(15f) - extra / 2, width / 2 + w / 2, y + kit.u(15f) + extra / 2)
            kit.fill.color = Palette.withAlpha(palette.background, 0.85f)
            canvas.drawRoundRect(hintBox, kit.u(15f), kit.u(15f), kit.fill)
            kit.stroke.color = if (guideShown) Palette.withAlpha(palette.accent, 0.5f) else Palette.LINE
            kit.stroke.strokeWidth = kit.u(1f)
            canvas.drawRoundRect(hintBox, kit.u(15f), kit.u(15f), kit.stroke)
            kit.small.color = if (guideShown) Palette.TEXT else Palette.TEXT_DIM
            for (i in captionLines.indices) kit.drawText(canvas, captionLines[i], width / 2, y - extra / 2 + i * lineHeight, kit.small)
            kit.small.color = Palette.TEXT
        }
    }

    /** Breaks [text] into lines that fit the screen, at spaces; done when the caption or the screen changes, not every frame. */
    private fun wrapCaption(text: String) {
        val maxWidth = safe.width() - kit.u(56f)
        val lines = ArrayList<String>()
        var current = ""
        for (word in text.split(" ")) {
            val attempt = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && kit.small.measureText(attempt) > maxWidth) {
                lines.add(current)
                current = word
            } else {
                current = attempt
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        captionLines = lines
        captionKey = text
        captionFor = safe.width()
        captionWidth = lines.maxOf { kit.small.measureText(it) }
    }

    private fun drawResult(canvas: Canvas, progress: Float) {
        canvas.drawColor(Palette.withAlpha(palette.background, 0.93f * progress))
        val cx = width / 2
        val lift = (1f - progress) * kit.u(14f)
        val cy = height / 2 + lift
        val color = palette.accent

        kit.stroke.color = color
        kit.stroke.strokeWidth = kit.u(2.5f)
        canvas.drawCircle(cx, cy - kit.u(96f), kit.u(30f), kit.stroke)
        Icons.draw(canvas, kit, Icon.CHECK, cx, cy - kit.u(96f), kit.u(32f), color)

        kit.heading.color = Palette.TEXT
        kit.drawText(canvas, kit.text.levelComplete, cx, cy - kit.u(38f), kit.heading)
        val detail = when {
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
        // Scoring, in seconds after the ball enters the ring.
        const val ENTER_TIME = 0.15f
        const val MORPH_START = 0.3f
        const val MORPH_END = 0.5f
        const val EXPLODE = 1.4f
        const val CHIME_DELAY = 0.5f

        // Fixed sizes of the effect pools.
        const val MAX_SPARKS = 128
        const val MAX_RIPPLES = 16
        const val MAX_SHATTERS = 4
        const val FLASH_TIME = 0.18f
        const val RING_TIME = 0.45f
        const val WIN_OVERLAY_DELAY = 2.1f
        const val OVERLAY_FADE = 0.2f
        const val GOAL_PULSE = 0.6f

        // Losing: the new ball's arrival. (How long a loss shows first is GameTuning.retryDelay.)
        const val RESPAWN_TIME = 0.25f

        // Shakes: a quick flick on a wall hit, a little more for a break, a real jolt for the explosion.
        const val BOUNCE_SHAKE_TIME = 0.13f
        const val BREAK_SHAKE_TIME = 0.16f
        const val EXPLOSION_SHAKE_TIME = 0.3f

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
