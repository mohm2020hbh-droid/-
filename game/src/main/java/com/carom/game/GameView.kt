package com.carom.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import com.carom.core.audio.MusicLibrary
import com.carom.core.level.LevelFormatException
import com.carom.core.level.Worlds
import com.carom.game.audio.MusicPlayer
import com.carom.game.audio.SoundFx
import com.carom.game.screens.GameHost
import com.carom.game.screens.Haptic
import com.carom.game.screens.HomeScreen
import com.carom.game.screens.LevelSelectScreen
import com.carom.game.screens.PlayScreen
import com.carom.game.screens.Screen
import com.carom.game.screens.Sound
import com.carom.game.ui.Palette
import com.carom.game.ui.UiKit
import com.carom.game.ui.UiText
import kotlin.math.max

/**
 * The game's only view. It runs the frame loop, routes input to the current [Screen] and handles
 * navigation. Frames are requested only while something moves; an idle screen draws nothing.
 */
@SuppressLint("ViewConstructor")
class GameView(context: Context, override val app: GameApp) : View(context), GameHost {

    override var kit = UiKit(1f, UiText.forLocale())
        private set

    private var screen: Screen = HomeScreen(this)
    private val soundFx = SoundFx { context.assets.open("sounds/launch_sfx_heartbeat_soft.wav").use { it.readBytes() } }
    private val music = MusicPlayer()
    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }?.takeIf { it.hasVibrator() }
    } catch (e: RuntimeException) {
        null
    }
    private val insets = Rect()
    private var lastFrameNanos = 0L

    /** Screen-change fade, 1 → 0. */
    private var fade = 0f

    internal val currentScreen: Screen get() = screen

    /** Test hook: skip the screen-change fade. */
    internal fun finishTransition() {
        fade = 0f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        kit = UiKit(UiKit.unitFor(w, h), kit.text)
        screen.layout(w, h, insets)
    }

    override fun onApplyWindowInsets(windowInsets: WindowInsets): WindowInsets {
        // Keep UI clear of camera cutouts. System bars are hidden (immersive), so they don't count.
        val cutout = Rect()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val i = windowInsets.getInsets(WindowInsets.Type.displayCutout())
            cutout.set(i.left, i.top, i.right, i.bottom)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            windowInsets.displayCutout?.let { cutout.set(it.safeInsetLeft, it.safeInsetTop, it.safeInsetRight, it.safeInsetBottom) }
        }
        if (cutout != insets) {
            insets.set(cutout)
            if (width > 0) screen.layout(width, height, insets)
            invalidate()
        }
        return super.onApplyWindowInsets(windowInsets)
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0f else ((now - lastFrameNanos) / 1e9f).coerceAtMost(0.1f)
        lastFrameNanos = now

        screen.update(dt)
        fade = max(0f, fade - dt / FADE_SECONDS)

        kit.palette = screen.palette
        canvas.drawColor(kit.palette.background)
        screen.draw(canvas)
        if (fade > 0f) canvas.drawColor(Palette.withAlpha(kit.palette.background, fade))

        if (screen.isAnimating || fade > 0f) {
            postInvalidateOnAnimation()
        } else {
            lastFrameNanos = 0L
            if (screen.idleRedrawMillis > 0L) postInvalidateDelayed(screen.idleRedrawMillis)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        screen.onTouch(event)
        music.setEnabled(app.settings.soundEnabled) // the sound button on the home screen also switches the music
        invalidate()
        return true
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        // Don't let time spent in the background arrive as one giant frame.
        lastFrameNanos = 0L
        if (visibility != VISIBLE) soundFx.roll(0f)
        music.setPaused(visibility != VISIBLE)
    }

    /** Returns false when the back action should leave the game. */
    fun onBack(): Boolean = screen.onBack().also { invalidate() }

    override fun showHome() = switchTo(HomeScreen(this))

    override fun showLevels(focusIndex: Int) = switchTo(LevelSelectScreen(this, focusIndex))

    override fun play(index: Int) {
        if (index !in 0 until app.levels.size) return showLevels(app.levels.size - 1)
        val level = try {
            app.levels.load(index)
        } catch (e: LevelFormatException) {
            Log.e(TAG, "Level ${app.levels.ids[index]} is invalid", e)
            return showLevels(index)
        }
        switchTo(PlayScreen(this, index, level))
    }

    private fun switchTo(next: Screen) {
        soundFx.roll(0f)
        soundFx.stopSpin()
        soundFx.setPitch(1f)
        music.setPitch(1f)
        screen.onExit()
        screen = next
        // The music follows the world (and the kind of level) and carries on across levels that share a track.
        val here = app.progress.currentIndex
        music.setEnabled(app.settings.soundEnabled)
        music.play((next as? PlayScreen)?.track ?: MusicLibrary.trackFor(here, Worlds.worldOf(here), false))
        keepScreenOn = next is PlayScreen
        if (width > 0) next.layout(width, height, insets)
        next.onEnter()
        fade = 1f
        invalidate()
    }

    override fun haptic(kind: Haptic, strength: Double) {
        if (!app.settings.hapticsEnabled) return
        val s = strength.coerceIn(0.0, 1.0)
        when (kind) {
            Haptic.CLICK -> performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            // A short, light tap on every wall hit, firmer for a harder hit.
            Haptic.BOUNCE -> pulse(14L, (50 + 130 * s).toInt(), HapticFeedbackConstants.CLOCK_TICK)
            Haptic.BREAK -> pulse(30L, 180, HapticFeedbackConstants.VIRTUAL_KEY)
            Haptic.EXPLOSION -> pulse(60L, 255, HapticFeedbackConstants.LONG_PRESS)
            Haptic.SUCCESS -> performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
            )
        }
    }

    /** One vibration of [millis] at [amplitude] (1..255), or the view's [fallback] feedback without a motor. */
    private fun pulse(millis: Long, amplitude: Int, fallback: Int) {
        val motor = vibrator ?: return run { performHapticFeedback(fallback) }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                motor.vibrate(VibrationEffect.createOneShot(millis, amplitude.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                motor.vibrate(millis)
            }
        } catch (e: RuntimeException) {
            performHapticFeedback(fallback)
        }
    }

    override fun sound(kind: Sound, strength: Double, pitch: Float) {
        if (!app.settings.soundEnabled) return
        when (kind) {
            Sound.IMPACT -> soundFx.impact(strength, pitch)
            Sound.IMPACT_CONTAINER -> soundFx.containerHit(strength, pitch)
            Sound.PORTAL -> soundFx.portal()
            Sound.SLOW_IN -> soundFx.slowIn()
            Sound.SLOW_OUT -> soundFx.slowOut()
            Sound.EXIT_PARTIAL -> soundFx.exitPartial()
            Sound.LAUNCH -> soundFx.launch(strength)
            Sound.SHATTER -> soundFx.shatter()
            Sound.WIN -> soundFx.win()
            Sound.TAP -> soundFx.tap()
            Sound.SPIN -> soundFx.spin()
            Sound.EXPLOSION -> soundFx.explosion()
            Sound.RESPAWN -> soundFx.respawn()
            Sound.FIZZLE -> soundFx.fizzle()
        }
    }

    override fun soundPitch(scale: Float) {
        soundFx.setPitch(scale)
        music.setPitch(scale)
    }

    override val beatPulse: Float get() = if (app.settings.soundEnabled) music.beatPulse() else 0f

    override fun stopSound(kind: Sound) {
        if (kind == Sound.SPIN) soundFx.stopSpin()
    }

    override fun rolling(level: Float) = soundFx.roll(if (app.settings.soundEnabled) level else 0f)

    /** Frees the audio tracks; the view is not used afterwards. */
    fun release() {
        soundFx.release()
        music.release()
    }

    private companion object {
        const val TAG = "Carom"
        const val FADE_SECONDS = 0.18f
    }
}
