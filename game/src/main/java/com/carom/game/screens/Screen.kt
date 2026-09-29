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

enum class Haptic { CLICK, BOUNCE, SUCCESS, FAILURE }

/** What screens can ask of the game shell: navigation, feedback, shared state. */
interface GameHost {
    val app: GameApp
    val kit: UiKit
    fun showHome()
    fun showLevels(focusIndex: Int)
    fun play(index: Int)
    fun haptic(kind: Haptic)
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

    abstract fun onTouch(e: MotionEvent): Boolean

    /** Handles the system back action. Returning false lets the app close. */
    open fun onBack(): Boolean = false

    open fun onEnter() {}

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
