package com.pogoascent.android

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.pogoascent.app.AppCore
import com.pogoascent.app.CompletionInfo
import com.pogoascent.audio.AudioBackend
import com.pogoascent.debug.ScenarioKind
import com.pogoascent.haptics.HapticBackend
import com.pogoascent.levels.LevelResult
import com.pogoascent.save.MemorySaveStorage
import com.pogoascent.settings.ControlLayout
import com.pogoascent.ui.ScreenId
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private class SilentAudio : AudioBackend {
  override fun register(clipId: String, pcm: ShortArray, sampleRate: Int) {}
  override fun play(clipId: String, volume: Float, rate: Float, pan: Float, loop: Boolean) = 1
  override fun setVolume(handle: Int, volume: Float) {}
  override fun stop(handle: Int) {}
  override fun stopAll() {}
  override fun release() {}
}

private class SilentHaptics : HapticBackend {
  override val supported = true
  val pulses = ArrayList<Pair<Int, Int>>()
  override fun vibrate(durationMs: Int, amplitude: Int) { pulses += durationMs to amplitude }
  override fun cancel() {}
}

private class RecordingHost : ScreenHost {
  val log = ArrayList<String>()
  override fun navigate(to: ScreenId) { log += "navigate:$to" }
  override fun back() { log += "back" }
  override fun startLevel(levelId: String) { log += "start:$levelId" }
  override fun resume() { log += "resume" }
  override fun restartLevel() { log += "restart" }
  override fun goHome() { log += "home" }
  override fun runScenario(kind: ScenarioKind) { log += "scenario:$kind" }
  override fun resetTest() { log += "resetTest" }
  override fun refresh() { log += "refresh" }
  override fun applyDisplaySettings() { log += "display" }
  override fun exitApp() { log += "exit" }
}

private fun View.all(): List<View> = if (this is ViewGroup) listOf(this) + (0 until childCount).flatMap { getChildAt(it).all() } else listOf(this)
private fun View.buttons(): List<Button> = all().filterIsInstance<Button>()
private fun View.button(contains: String): Button = buttons().firstOrNull { it.text.toString().contains(contains) } ?: error("no button containing '$contains' in ${buttons().map { it.text }}")
private fun View.texts(): List<String> = all().filterIsInstance<TextView>().map { it.text.toString() }

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreensTest {
  private val app: Application get() = RuntimeEnvironment.getApplication()
  private fun core(lang: String = "en", haptics: SilentHaptics? = SilentHaptics()) =
    AppCore(MemorySaveStorage(), SilentAudio(), haptics, { SystemClock.elapsedRealtimeNanos() / 1e9 }, { SystemClock.elapsedRealtime() }, { 20_000L }, { lang })

  @Test fun everyScreenBuildsInEnglishAndArabic() {
    for (lang in listOf("en", "ar")) {
      val core = core(lang)
      val factory = ScreenFactory(app, core, RecordingHost())
      for (id in ScreenId.values()) {
        val v = factory.build(id)
        assertNotNull("$id/$lang", v)
        if (id != ScreenId.PLAYING) assertTrue("$id/$lang has content", v.all().size > 3)
      }
    }
  }

  @Test fun mainMenuButtonsNavigateAndPlayStartsTheFirstLevel() {
    val host = RecordingHost()
    val view = ScreenFactory(app, core(), host).build(ScreenId.MAIN_MENU)
    view.button("Play").performClick()
    view.button("Worlds").performClick()
    view.button("Wardrobe").performClick()
    view.button("Leaderboard").performClick()
    view.button("Settings").performClick()
    view.button("How To Play").performClick()
    view.button("Credits").performClick()
    view.button("Physics Test").performClick()
    view.button("Quit").performClick()
    assertEquals(
      listOf("start:level_01", "navigate:WORLD_SELECT", "navigate:WARDROBE", "navigate:LEADERBOARD", "navigate:SETTINGS", "navigate:HOW_TO_PLAY", "navigate:CREDITS", "navigate:PHYSICS_TEST", "exit"),
      host.log,
    )
  }

  @Test fun theArabicMainMenuIsTranslated() {
    val view = ScreenFactory(app, core("ar"), RecordingHost()).build(ScreenId.MAIN_MENU)
    assertTrue(view.texts().any { it.contains("صعود البوجو") })
    assertTrue(view.buttons().any { it.text.toString().contains("العوالم") })
  }

  @Test fun levelSelectLocksLevelsUntilThePreviousOneIsCompleted() {
    val core = core()
    val host = RecordingHost()
    val factory = ScreenFactory(app, core, host)
    core.selectedWorldId = "world_1"
    var view = factory.build(ScreenId.LEVEL_SELECT)
    assertTrue(view.button("1. First Steps").isEnabled)
    assertFalse(view.button("2. Zigzag Ridge").isEnabled)
    core.progression.recordCompletion(LevelResult("level_01", 55.0, 20, 0, 0, 0, 1.0), 1, 120.0, 1)
    view = factory.build(ScreenId.LEVEL_SELECT)
    assertTrue(view.button("2. Zigzag Ridge").isEnabled)
    assertTrue(view.button("1. First Steps").text.toString().contains("0:55.0"))
    view.button("2. Zigzag Ridge").performClick()
    assertEquals(listOf("start:level_02"), host.log)
  }

  @Test fun worldSelectShowsLockedWorlds() {
    val view = ScreenFactory(app, core(), RecordingHost()).build(ScreenId.WORLD_SELECT)
    assertTrue(view.button("Green Hills").isEnabled)
    assertFalse(view.button("Frozen Peaks").isEnabled)
    assertTrue(view.button("Frozen Peaks").text.toString().contains("Locked"))
  }

  @Test fun audioSlidersChangeAndPersistTheSettings() {
    val core = core()
    val view = ScreenFactory(app, core, RecordingHost()).build(ScreenId.SETTINGS_AUDIO)
    val bars = view.all().filterIsInstance<SeekBar>()
    assertEquals(4, bars.size)
    shadowOf(bars[0]).onSeekBarChangeListener.onProgressChanged(bars[0], 30, true)
    shadowOf(bars[2]).onSeekBarChangeListener.onProgressChanged(bars[2], 100, true)
    assertEquals(0.3, core.save.data.settings.audio.master, 1e-9)
    assertEquals(1.0, core.save.data.settings.audio.sfx, 1e-9)
    assertEquals(0.3, core.audio!!.settings.master, 1e-9)
  }

  @Test fun controlSettingsApplyToTheTouchMapperAndHaptics() {
    val core = core()
    val view = ScreenFactory(app, core, RecordingHost()).build(ScreenId.SETTINGS_CONTROLS)
    val sw = view.all().filterIsInstance<Switch>()
    sw[0].isChecked = true // left-handed
    assertTrue(core.save.data.settings.controls.leftHanded)
    assertTrue(core.touch.settings.leftHanded)
    sw[1].isChecked = false // haptics
    assertFalse(core.haptics!!.enabled)
    view.button("Layout").performClick()
    assertEquals(ControlLayout.SWIPE, core.save.data.settings.controls.layout)
  }

  @Test fun graphicsQualityPresetRewritesTheVideoSettings() {
    val core = core()
    val host = RecordingHost()
    val view = ScreenFactory(app, core, host).build(ScreenId.SETTINGS_GRAPHICS)
    view.button("Quality").performClick() // Medium -> High
    assertEquals(com.pogoascent.settings.Quality.HIGH, core.save.data.settings.video.quality)
    assertEquals(1.0, core.save.data.settings.video.resolutionScale, 0.0)
    assertTrue(host.log.contains("display"))
  }

  @Test fun wardrobePurchaseAndEquipFlow() {
    val core = core()
    core.save.update { it.copy(coins = 100) }
    val host = RecordingHost()
    val factory = ScreenFactory(app, core, host)
    var view = factory.build(ScreenId.WARDROBE)
    assertTrue(view.texts().any { it.contains("100") })
    view.button("Beanie").performClick()
    assertEquals(60, core.save.data.coins)
    assertEquals("hat_beanie", core.wardrobe.equipped(com.pogoascent.customization.ItemCategory.HAT).id)
    view = factory.build(ScreenId.WARDROBE)
    assertFalse("equipped item cannot be tapped again", view.button("Beanie").isEnabled)
    assertFalse("a 400-coin crown is unaffordable", view.button("Crown").isEnabled)
  }

  @Test fun levelCompleteScreenShowsTheResultAndOffersTheNextLevel() {
    val core = core()
    val result = LevelResult("level_01", 61.2, 20, 1, 0, 0, 50.0)
    val summary = core.progression.recordCompletion(result, 1, 120.0, 1)
    core.lastCompletion = CompletionInfo(summary, "First Steps", core.progression.nextLevelAfter("level_01"), emptyList())
    val host = RecordingHost()
    val view = ScreenFactory(app, core, host).build(ScreenId.LEVEL_COMPLETE)
    assertTrue(view.texts().any { it.contains("1:01.2") })
    assertTrue(view.texts().any { it.contains("New best") })
    view.button("Next Level").performClick()
    assertEquals(listOf("start:level_02"), host.log)
  }

  @Test fun leaderboardShowsLocalTimes() {
    val core = core()
    core.progression.recordCompletion(LevelResult("level_01", 61.2, 20, 0, 0, 0, 1.0), 1, 120.0, 1)
    val view = ScreenFactory(app, core, RecordingHost()).build(ScreenId.LEADERBOARD)
    assertTrue(view.texts().any { it.contains("1:01.2") })
  }

  @Test fun pauseAndPhysicsTestButtonsCallTheHost() {
    val host = RecordingHost()
    val f = ScreenFactory(app, core(), host)
    val pause = f.build(ScreenId.PAUSE)
    pause.button("Resume").performClick(); pause.button("Restart").performClick(); pause.button("Main Menu").performClick()
    val test = f.build(ScreenId.PHYSICS_TEST)
    for (b in listOf("Reset", "Test Jump", "Test Boost", "Test Bounce", "Test Fall")) test.button(b).performClick()
    assertEquals(listOf("resume", "restart", "home", "resetTest", "scenario:TEST_JUMP", "scenario:TEST_BOOST", "scenario:TEST_BOUNCE", "scenario:TEST_FALL"), host.log)
  }

  @Test fun resettingProgressNeedsAConfirmation() {
    val core = core()
    core.save.update { it.copy(coins = 50) }
    val host = RecordingHost()
    val f = ScreenFactory(app, core, host)
    f.build(ScreenId.SETTINGS).button("Reset progress").performClick()
    assertEquals(50, core.save.data.coins)
    f.build(ScreenId.SETTINGS).button("tap again").performClick()
    assertEquals(0, core.save.data.coins)
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HudOverlayTest {
  private val app: Application get() = RuntimeEnvironment.getApplication()
  private fun setup(): Triple<AppCore, InputBridge, HudOverlay> {
    val core = AppCore(MemorySaveStorage(), SilentAudio(), SilentHaptics(), { SystemClock.elapsedRealtimeNanos() / 1e9 }, { SystemClock.elapsedRealtime() }, { 1L })
    val input = InputBridge()
    var paused = 0
    val hud = HudOverlay(app, core, input) { paused++ }
    hud.layout(0, 0, 2000, 1000)
    hud.screen = ScreenId.PLAYING
    return Triple(core, input, hud)
  }

  private fun ev(action: Int, vararg pts: Triple<Int, Float, Float>, actionIndex: Int = 0): MotionEvent {
    val props = Array(pts.size) { MotionEvent.PointerProperties().apply { id = pts[it].first; toolType = MotionEvent.TOOL_TYPE_FINGER } }
    val coords = Array(pts.size) { MotionEvent.PointerCoords().apply { x = pts[it].second; y = pts[it].third; pressure = 1f; size = 1f } }
    val a = if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) action or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT) else action
    return MotionEvent.obtain(0, 0, a, pts.size, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
  }

  @Test fun dragLeansAndTheJumpButtonHoldsIndependently() {
    val (core, input, hud) = setup()
    hud.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, Triple(0, 400f, 500f)))
    hud.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, Triple(0, 640f, 500f)))
    assertTrue(input.lean > 0.5)
    assertFalse(input.jump)
    val b = core.touch.jumpButton
    hud.dispatchTouchEvent(ev(MotionEvent.ACTION_POINTER_DOWN, Triple(0, 640f, 500f), Triple(1, b.cx, b.cy), actionIndex = 1))
    assertTrue(input.jump)
    hud.dispatchTouchEvent(ev(MotionEvent.ACTION_POINTER_UP, Triple(0, 640f, 500f), Triple(1, b.cx, b.cy), actionIndex = 1))
    assertFalse(input.jump)
    assertTrue(input.lean > 0.5)
    hud.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, Triple(0, 640f, 500f)))
    assertEquals(0.0, input.lean, 0.0)
  }

  @Test fun theHudIgnoresTouchesOutsideGameplayAndCancelReleasesEverything() {
    val (core, input, hud) = setup()
    hud.screen = ScreenId.SETTINGS
    assertFalse(hud.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, Triple(0, core.touch.jumpButton.cx, core.touch.jumpButton.cy))))
    assertFalse(input.jump)
    hud.screen = ScreenId.PLAYING
    hud.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, Triple(0, core.touch.jumpButton.cx, core.touch.jumpButton.cy)))
    assertTrue(input.jump)
    hud.dispatchTouchEvent(ev(MotionEvent.ACTION_CANCEL, Triple(0, 0f, 0f)))
    assertFalse(input.jump)
  }

  @Test fun theHudDrawsInEveryModeWithoutCrashing() {
    val (core, _, hud) = setup()
    val canvas = Canvas(Bitmap.createBitmap(2000, 1000, Bitmap.Config.ARGB_8888))
    core.controller.startLevel("level_01")
    core.controller.update(1.0 / 60, 2f)
    for (layout in ControlLayout.values()) {
      core.updateSettings { it.copy(controls = it.controls.copy(layout = layout)) }
      for (s in listOf(ScreenId.PLAYING, ScreenId.PAUSE, ScreenId.LEVEL_COMPLETE, ScreenId.MAIN_MENU)) { hud.screen = s; hud.draw(canvas) }
    }
    core.controller.startPhysicsTest()
    core.controller.runScenario(ScenarioKind.TEST_JUMP)
    repeat(400) { core.controller.update(1.0 / 60, 2f) }
    hud.screen = ScreenId.PHYSICS_TEST
    hud.draw(canvas)
    core.updateSettings { it.copy(gameplay = it.gameplay.copy(showHud = false)) }
    core.controller.update(1.0 / 60, 2f)
    hud.draw(canvas)
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlatformBackendsTest {
  private val app: Application get() = RuntimeEnvironment.getApplication()

  @Test fun hapticBackendNeverThrows() {
    val h = AndroidHapticBackend(app)
    h.vibrate(20, 128)
    h.vibrate(20, 255)
    h.cancel()
    assertTrue(h.supported || !h.supported) // value depends on the shadow; the point is no exception
  }

  @Test fun audioBackendWritesValidWavFilesAndToleratesPlayBeforeLoad() {
    val a = AndroidAudioBackend(app)
    val pcm = ShortArray(2205) { (it % 100).toShort() }
    a.register("unit_clip", pcm, 22050)
    val f = File(app.cacheDir, "pogo-audio-v1/unit_clip.wav")
    assertTrue(f.exists())
    val bytes = f.readBytes()
    assertEquals(44 + pcm.size * 2, bytes.size)
    assertEquals("RIFF", String(bytes, 0, 4))
    assertEquals("WAVE", String(bytes, 8, 4))
    a.play("unit_clip", 1f, 1f, 0f, false) // may return -1 until SoundPool finished loading
    a.stop(12345)
    a.setVolume(12345, 0.5f)
    a.stopAll()
    a.release()
  }
}
