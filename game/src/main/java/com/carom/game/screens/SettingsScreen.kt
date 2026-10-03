package com.carom.game.screens

import android.graphics.Canvas
import android.view.MotionEvent
import com.carom.core.audio.AudioCue
import com.carom.game.ui.Icon
import com.carom.game.ui.Palette
import com.carom.game.ui.UiButton
import com.carom.game.ui.UiSlider
import com.carom.game.ui.UiToggle
import com.carom.game.ui.WorldPalette
import kotlin.math.min

/**
 * Settings, with one page for now: Audio. Four sliders (master, music, effects, interface) and three switches (music, sound effects,
 * vibration), all saved the moment they change and all heard at once. It is drawn like the rest of the game: flat, quiet, the world's two
 * colours, the same fonts and buttons.
 */
class SettingsScreen(host: GameHost) : Screen(host) {

    private val settings = host.app.settings
    private val audio = settings.audio

    /** The settings wear the colours of the world the player has reached, like the title screen. */
    override val palette: WorldPalette get() = Palette.forLevel(host.app.progress.currentIndex)

    private val backButton = UiButton(UiButton.Style.ICON, icon = Icon.BACK, cue = AudioCue.UI_BACK) { host.showHome() }
    private val buttons = listOf(backButton)

    private val master = UiSlider(kit.text.masterVolume, { audio.masterVolume }, { audio.masterVolume = it })
    private val music = UiSlider(kit.text.musicVolume, { audio.musicVolume }, { audio.musicVolume = it })
    private val sfx = UiSlider(kit.text.sfxVolume, { audio.sfxVolume }, { audio.sfxVolume = it })
    private val ui = UiSlider(kit.text.uiVolume, { audio.uiVolume }, { audio.uiVolume = it })
    internal val sliders = listOf(master, music, sfx, ui)

    private val musicSwitch = UiToggle(kit.text.music, { audio.musicEnabled }, { audio.musicEnabled = it })
    private val sfxSwitch = UiToggle(kit.text.soundEffects, { audio.sfxEnabled }, { audio.sfxEnabled = it })
    private val vibrationSwitch = UiToggle(kit.text.vibration, { settings.hapticsEnabled }, { settings.hapticsEnabled = it })
    internal val toggles = listOf(musicSwitch, sfxSwitch, vibrationSwitch)

    private var activeSlider: UiSlider? = null
    private var activeToggle: UiToggle? = null
    private var columnX = 0f
    private var headerY = 0f
    private var ruleY = 0f

    override fun onLayout() {
        columnX = width / 2
        headerY = safe.top + kit.u(44f)
        val w = min(safe.width() - kit.u(56f), kit.u(300f))
        var y = headerY + kit.u(78f)
        for (s in sliders) {
            s.place(columnX, y, w, kit.u(52f), kit)
            y += kit.u(58f)
        }
        ruleY = y - kit.u(14f)
        y += kit.u(10f)
        for (t in toggles) {
            t.place(columnX, y, w, kit.u(42f))
            y += kit.u(46f)
        }
        backButton.setCenter(safe.left + kit.u(32f), headerY, kit.u(48f), kit.u(48f))
    }

    override fun onTouch(e: MotionEvent): Boolean {
        if (activeSlider == null && activeToggle == null && routeToButtons(e, buttons)) return true
        val slop = kit.u(4f)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                sliders.firstOrNull { it.hit(e.x, e.y, slop) }?.let {
                    activeSlider = it
                    it.down(e.x)
                    return true
                }
                toggles.firstOrNull { it.hit(e.x, e.y, slop) }?.let {
                    activeToggle = it
                    it.pressed = true
                    host.sound(AudioCue.UI_PRESS)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                activeSlider?.let {
                    it.move(e.x)
                    return true
                }
                activeToggle?.let {
                    it.pressed = it.hit(e.x, e.y, slop)
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                activeSlider?.let {
                    it.up()
                    activeSlider = null
                    preview(it)
                    return true
                }
                activeToggle?.let {
                    it.pressed = false
                    activeToggle = null
                    if (it.hit(e.x, e.y, slop)) {
                        it.toggle()
                        host.haptic(Haptic.CLICK)
                    }
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                activeSlider?.up()
                activeSlider = null
                activeToggle?.pressed = false
                activeToggle = null
            }
        }
        return false
    }

    /** Lets go of a slider: the sound it controls plays once, so the level can be judged by ear. (The music is already playing.) */
    private fun preview(slider: UiSlider) {
        when (slider) {
            master, sfx -> host.sound(AudioCue.BOUNCE, 0.6)
            ui -> host.sound(AudioCue.UI_CONFIRM)
            else -> {}
        }
    }

    override fun onBack(): Boolean {
        host.showHome()
        return true
    }

    override fun draw(canvas: Canvas) {
        backButton.draw(canvas, kit)
        kit.heading.color = kit.palette.ink
        kit.drawText(canvas, kit.text.settings, columnX, headerY, kit.heading)
        kit.small.color = kit.palette.inkDim
        kit.drawText(canvas, kit.text.audio, columnX, headerY + kit.u(30f), kit.small)
        kit.small.color = kit.palette.ink
        for (s in sliders) s.draw(canvas, kit)
        kit.stroke.color = kit.palette.line
        kit.stroke.strokeWidth = kit.u(1f)
        val w = sliders.first().bounds.width()
        canvas.drawLine(columnX - w / 2 + kit.u(10f), ruleY, columnX + w / 2 - kit.u(10f), ruleY, kit.stroke)
        for (t in toggles) t.draw(canvas, kit)
    }
}
