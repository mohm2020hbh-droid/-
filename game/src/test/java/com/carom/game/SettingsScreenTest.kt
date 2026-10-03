package com.carom.game

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.carom.core.audio.AudioCue
import com.carom.core.level.LevelRepository
import com.carom.game.screens.GameHost
import com.carom.game.screens.HomeScreen
import com.carom.game.screens.Haptic
import com.carom.game.screens.LevelSelectScreen
import com.carom.game.screens.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Settings → Audio: four sliders, three switches, saved at once, kept across a restart; and the light sounds of the menus. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsScreenTest {

    private class Recorder(private val real: GameView) : GameHost by real {
        val cues = ArrayList<AudioCue>()
        val haptics = ArrayList<Haptic>()
        override fun sound(cue: AudioCue, strength: Double) { cues += cue }
        override fun haptic(kind: Haptic, strength: Double) { haptics += kind }
    }

    private fun newView(store: MapStore = MapStore()): Pair<GameView, MapStore> {
        val app = GameApp(LevelRepository(MemoryLevels()), store)
        val view = GameView(RuntimeEnvironment.getApplication(), app)
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1080, 2400)
        return view to store
    }

    private fun touch(view: GameView, action: Int, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        val e = MotionEvent.obtain(t, t, action, x, y, 0)
        view.dispatchTouchEvent(e)
        e.recycle()
    }

    private fun tap(view: GameView, x: Float, y: Float) {
        touch(view, MotionEvent.ACTION_DOWN, x, y)
        touch(view, MotionEvent.ACTION_UP, x, y)
    }

    private fun settings(view: GameView): SettingsScreen {
        view.showSettings()
        return view.currentScreen as SettingsScreen
    }

    @Test
    fun theSettingsButtonOnTheTitleOpensTheSettingsAndBackComesHome() {
        val (view, _) = newView()
        view.showHome()
        val home = view.currentScreen as HomeScreen
        tap(view, home.settingsButton.bounds.centerX(), home.settingsButton.bounds.centerY())
        assertTrue(view.currentScreen is SettingsScreen)
        assertTrue(view.onBack())
        assertTrue(view.currentScreen is HomeScreen)
    }

    @Test
    fun draggingASliderSetsItSavesItAndTheNextLaunchKeepsIt() {
        val (view, store) = newView()
        val screen = settings(view)
        val music = screen.sliders[1]
        val y = music.bounds.centerY()
        touch(view, MotionEvent.ACTION_DOWN, music.bounds.left + 1f, y)
        touch(view, MotionEvent.ACTION_MOVE, music.bounds.left + music.bounds.width() * 0.5f, y)
        touch(view, MotionEvent.ACTION_UP, music.bounds.left + music.bounds.width() * 0.5f, y)
        val v = view.app.settings.audio.musicVolume
        assertTrue("about half: $v", v in 0.4..0.6)
        assertEquals("the others are untouched", 1.0, view.app.settings.audio.masterVolume, 0.0)

        // the game is closed and opened again on the same saved settings
        val (again, _) = newView(store)
        assertEquals(v, again.app.settings.audio.musicVolume, 1e-9)
    }

    @Test
    fun aSliderDragClampsAtBothEnds() {
        val (view, _) = newView()
        val screen = settings(view)
        val master = screen.sliders[0]
        val y = master.bounds.centerY()
        touch(view, MotionEvent.ACTION_DOWN, master.bounds.left + 5f, y)
        touch(view, MotionEvent.ACTION_MOVE, -400f, y)
        assertEquals(0.0, view.app.settings.audio.masterVolume, 0.0)
        touch(view, MotionEvent.ACTION_MOVE, 5000f, y)
        assertEquals(1.0, view.app.settings.audio.masterVolume, 0.0)
        touch(view, MotionEvent.ACTION_UP, 5000f, y)
    }

    @Test
    fun eachSwitchChangesOnlyItselfAndIsSaved() {
        val (view, store) = newView()
        val screen = settings(view)
        val (musicSwitch, sfxSwitch, vibrationSwitch) = screen.toggles
        tap(view, musicSwitch.bounds.centerX(), musicSwitch.bounds.centerY())
        assertFalse(view.app.settings.audio.musicEnabled)
        assertTrue(view.app.settings.audio.sfxEnabled && view.app.settings.hapticsEnabled)
        tap(view, vibrationSwitch.bounds.centerX(), vibrationSwitch.bounds.centerY())
        assertFalse(view.app.settings.hapticsEnabled)
        assertTrue(view.app.settings.audio.sfxEnabled)
        tap(view, sfxSwitch.bounds.centerX(), sfxSwitch.bounds.centerY())
        assertFalse(view.app.settings.audio.sfxEnabled)

        val (again, _) = newView(store)
        assertFalse(again.app.settings.audio.musicEnabled)
        assertFalse(again.app.settings.audio.sfxEnabled)
        assertFalse(again.app.settings.hapticsEnabled)
        // and back on
        val screen2 = settings(again)
        tap(again, screen2.toggles[0].bounds.centerX(), screen2.toggles[0].bounds.centerY())
        assertTrue(again.app.settings.audio.musicEnabled)
    }

    @Test
    fun letGoOfAnEffectsSliderAndTheSoundItControlsPlaysOnceSoItCanBeJudged() {
        val (view, _) = newView()
        val rec = Recorder(view)
        val screen = SettingsScreen(rec)
        screen.layout(1080, 2400, Rect())
        val sfx = screen.sliders[2]
        val y = sfx.bounds.centerY()
        fun drag(slider: com.carom.game.ui.UiSlider) {
            val yy = slider.bounds.centerY()
            val down = MotionEvent.obtain(1, 1, MotionEvent.ACTION_DOWN, slider.bounds.left + 50f, yy, 0)
            val up = MotionEvent.obtain(2, 2, MotionEvent.ACTION_UP, slider.bounds.left + 300f, yy, 0)
            screen.onTouch(down); screen.onTouch(up); down.recycle(); up.recycle()
        }
        drag(sfx)
        assertEquals(listOf(AudioCue.BOUNCE), rec.cues)
        rec.cues.clear()
        drag(screen.sliders[3])
        assertEquals(listOf(AudioCue.UI_CONFIRM), rec.cues)
        rec.cues.clear()
        drag(screen.sliders[1])
        assertTrue("the music is already playing: no preview", rec.cues.isEmpty())
        assertTrue(y > 0)
    }

    @Test
    fun theTitleAndTheListMakeTheirOwnLightSounds() {
        val (view, _) = newView()
        val rec = Recorder(view)
        val home = HomeScreen(rec)
        home.layout(1080, 2400, Rect())
        fun press(screen: com.carom.game.screens.Screen, x: Float, y: Float) {
            val down = MotionEvent.obtain(1, 1, MotionEvent.ACTION_DOWN, x, y, 0)
            val up = MotionEvent.obtain(2, 2, MotionEvent.ACTION_UP, x, y, 0)
            screen.onTouch(down); screen.onTouch(up); down.recycle(); up.recycle()
        }
        val levels = rec.app.levels
        assertTrue(levels.size >= 1)
        // a plain button, the settings button
        press(home, home.settingsButton.bounds.centerX(), home.settingsButton.bounds.centerY())
        assertEquals(listOf(AudioCue.UI_PRESS), rec.cues)
        rec.cues.clear()
        // the settings' back button
        val settings = SettingsScreen(rec)
        settings.layout(1080, 2400, Rect())
        press(settings, 40f, 100f)
        assertEquals(listOf(AudioCue.UI_BACK), rec.cues)
        rec.cues.clear()
        // a level in the list
        val list = LevelSelectScreen(rec, 0)
        list.layout(1080, 2400, Rect())
        val (cx, cy) = list.cellCenter(0)
        press(list, cx, cy)
        assertTrue(rec.cues.contains(AudioCue.UI_LEVEL_SELECT))
        assertFalse("a level is not a UI press and a play", rec.cues.contains(AudioCue.BOUNCE))
    }
}
