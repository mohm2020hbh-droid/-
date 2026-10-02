package com.carom.game.screens

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import com.carom.game.GameApp
import com.carom.game.ui.Palette
import com.carom.game.ui.UiButton
import com.carom.game.ui.UiKit
import com.carom.game.ui.WorldPalette

enum class Haptic { CLICK, BOUNCE, BREAK, EXPLOSION, SUCCESS }

enum class Sound {
    IMPACT, SHATTER, WIN, TAP, SPIN, EXPLOSION, RESPAWN, FIZZLE,

    /** A ball entering a portal. */
    PORTAL,

    /** A ball losing speed in a clock. */
    CLOCK,

    /** A ball reached an exit that still needs more. */
    EXIT_PARTIAL,
}

/** What screens can ask of the game shell: navigation, feedback, shared state. */
interface GameHost {
    val app: GameApp
    val kit: UiKit
    fun showHome()
    fun showLevels(focusIndex: Int)
    fun play(index: Int)
    /** A vibration; [strength] (0..1) scales a bounce's. */
    fun haptic(kind: Haptic, strength: Double = 1.0)

    /**
     * Plays [kind]. [strength] (0..1) is how hard the ball hit (impacts). (The ball makes no sound of its own: not when
     * it is thrown and not while it flies; only a collision does.)
     */
    fun sound(kind: Sound, strength: Double = 1.0)

    /** The playback speed of all sound and music: 1 normally, about a third in slow motion. */
    fun soundPitch(scale: Float)

    /** 0..1, 1 on a beat of the music and fading after it: a pulse for pictures (never for the rules). */
    val beatPulse: Float

    /** Cuts [kind] short if it is playing (a spin-up interrupted by a restart). */
    fun stopSound(kind: Sound)
}

/**
 * One full-screen state (home, level select, play). Screens draw everything themselves on a
 * canvas and only redraw while [isAnimating], so an idle screen costs no battery.
 */
abstract class Screen(protected val host: GameHost) {
    protected val kit: UiKit get() = host.kit

    protected var width = 0f
        private set
    protected var height = 0f
        private set

    /** Area clear of display cutouts, in pixels. */
    protected val safe = RectF()

    private var pressedButton: UiButton? = null

    fun layout(width: Int, height: Int, insets: Rect) {
        this.width = width.toFloat()
        this.height = height.toFloat()
        safe.set(insets.left.toFloat(), insets.top.toFloat(), (width - insets.right).toFloat(), (height - insets.bottom).toFloat())
        onLayout()
    }

    protected abstract fun onLayout()

    /** The world colours this screen is drawn in (the view paints its background first). */
    open val palette: WorldPalette get() = Palette.forWorld(0)

    abstract fun draw(canvas: Canvas)

    open fun update(dt: Float) {}

    open val isAnimating: Boolean get() = false

    /**
     * While nothing moves, redraw every this many milliseconds anyway (0 = never): for a screen with a gentle
     * looping animation, like a force zone, that does not need a full frame rate.
     */
    open val idleRedrawMillis: Long get() = 0L

    abstract fun onTouch(e: MotionEvent): Boolean

    /** Handles the system back action. Returning false lets the app close. */
    open fun onBack(): Boolean = false

    open fun onEnter() {}

    /** The screen is being replaced; free anything large it holds. */
    open fun onExit() {}

    /** Press/release handling shared by every screen's buttons. Returns true if a button took the event. */
    protected fun routeToButtons(e: MotionEvent, buttons: List<UiButton>): Boolean {
        val slop = kit.u(4f)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedButton = buttons.firstOrNull { it.hit(e.x, e.y, slop) }?.also { it.pressed = true }
                return pressedButton != null
            }
            MotionEvent.ACTION_MOVE -> pressedButton?.let {
                it.pressed = it.hit(e.x, e.y, slop)
                return true
            }
            MotionEvent.ACTION_UP -> pressedButton?.let {
                val clicked = it.hit(e.x, e.y, slop)
                it.pressed = false
                pressedButton = null
                if (clicked) {
                    host.haptic(Haptic.CLICK)
                    it.onClick()
                    host.sound(Sound.TAP)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> pressedButton?.let {
                it.pressed = false
                pressedButton = null
                return true
            }
        }
        return false
    }
}
