package com.pogoascent

import com.pogoascent.app.AppCore
import com.pogoascent.app.CompletionInfo
import com.pogoascent.app.DemoRoute
import com.pogoascent.app.GameListener
import com.pogoascent.debug.ScenarioKind
import com.pogoascent.levels.LevelLoader
import com.pogoascent.levels.LevelValidator
import com.pogoascent.levels.SessionPhase
import com.pogoascent.save.MemorySaveStorage
import com.pogoascent.settings.ControlLayout
import com.pogoascent.ui.ScreenId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFlowTest {
  private var nowSec = 0.0
  private var nowMs = 0L
  private fun app(storage: MemorySaveStorage = MemorySaveStorage(), audio: FakeAudio? = FakeAudio(), haptics: FakeHaptics? = FakeHaptics(), lang: String = "en") =
    AppCore(storage, audio, haptics, { nowSec }, { nowMs }, { 20_000L }, { lang })

  /** Drives the controller like the render loop: 60 fps frames. */
  private fun frames(core: AppCore, n: Int, aspect: Float = 16f / 9f, dt: Double = 1.0 / 60) {
    repeat(n) { nowSec += dt; nowMs += (dt * 1000).toLong(); core.controller.update(dt, aspect) }
  }

  @Test fun theDemoRouteForTheBackdropIsValidAndCompletesTheLevel() {
    val steps = DemoRoute.load("level_01")
    assertTrue(steps.size >= 10)
    val core = app()
    core.controller.startLevel("level_01", demo = true)
    assertTrue(core.controller.inDemo)
    var done = false
    var n = 0
    core.controller.session!!.addListener { if (it.type == com.pogoascent.player.EventType.GOAL) done = true }
    while (!done && n++ < 60 * 400) frames(core, 1)
    assertTrue("the backdrop autopilot reaches the goal (n=$n)", done)
  }

  @Test fun aFullPlayFlowRecordsProgressUnlocksTheNextLevelAndSaves() {
    val storage = MemorySaveStorage()
    val core = app(storage)
    var completed: CompletionInfo? = null
    core.controller.listener = object : GameListener { override fun onLevelComplete(info: CompletionInfo) { completed = info } }
    core.controller.startLevel("level_01")
    assertFalse(core.controller.inDemo)

    // a skilled player = the validated route driven through the same input path as touch (lean + hold/release)
    val data = LevelLoader.loadData("level_01")
    val report = LevelValidator.validate(data, core.surfaces, core.physics)
    val pilot = com.pogoascent.debug.AutoPilot(core.controller.session!!, report.path)
    var n = 0
    while (completed == null && n++ < 120 * 600) {
      pilot.drive()
      val s = core.controller.session!!
      core.controller.input.set(s.input.lean, s.input.jumpHeld)
      frames(core, 1, dt = core.physics.fixedDt) // one physics tick per pilot decision, like the validator
    }
    val done = completed
    assertNotNull("level completes", done)
    assertEquals(SessionPhase.COMPLETE, core.controller.session!!.phase)
    done!!
    assertTrue(done.summary.firstCompletion && done.summary.coinsEarned > 0)
    assertEquals("level_02", done.nextLevelId)
    assertTrue(core.progression.isLevelUnlocked("level_02"))
    assertTrue("progress persisted immediately", storage.main!!.contains("level_01"))
    assertTrue("a leaderboard entry exists", core.menu.leaderboard("level_01").size == 1)
    assertTrue(core.controller.hud!!.progressText.startsWith("100"))
  }

  @Test fun pausingFreezesTheWorldAndAbandoningKeepsPartialProgress() {
    val core = app()
    core.controller.startLevel("level_01")
    core.controller.input.set(0.0, true)
    frames(core, 30)
    val charge = core.controller.session!!.player.charge
    assertTrue(charge > 0.3)
    core.controller.paused = true
    frames(core, 120)
    assertEquals(charge, core.controller.session!!.player.charge, 1e-9)
    core.controller.paused = false
    core.controller.input.set(0.0, false)
    frames(core, 200)
    core.controller.abandonRun()
    assertEquals(1, core.save.data.levels.getValue("level_01").attempts)
    assertFalse(core.save.data.levels.getValue("level_01").completed)
    assertTrue(core.save.data.stats.totalJumps >= 1)
  }

  @Test fun settingsReachTheRunningGameAndAreSaved() {
    val storage = MemorySaveStorage()
    val core = app(storage)
    core.controller.startLevel("level_01")
    core.updateSettings { it.copy(video = it.video.copy(particleDensity = 0.25, effects = false, shadows = false), gameplay = it.gameplay.copy(cameraShake = 0.0, cameraZoom = 1.4), audio = it.audio.copy(master = 0.1), controls = it.controls.copy(layout = ControlLayout.SWIPE, haptics = false)) }
    frames(core, 2)
    assertEquals(0.25, core.controller.particles.density, 0.0)
    assertFalse(core.controller.particles.enabled)
    assertFalse(core.controller.frame.showShadows)
    assertEquals(0.0, core.controller.session!!.cameraUser.shakeIntensity, 0.0)
    assertEquals(1.4, core.controller.session!!.cameraUser.zoom, 0.0)
    assertEquals(0.1, core.audio!!.settings.master, 0.0)
    assertFalse(core.haptics!!.enabled)
    assertEquals(ControlLayout.SWIPE, core.touch.settings.layout)
    // a new process sees the same settings
    val again = app(storage)
    assertEquals(0.25, again.save.data.settings.video.particleDensity, 0.0)
  }

  @Test fun theFrameForTheRendererIsAlwaysFiniteAndWithinBatchCapacity() {
    val core = app()
    core.controller.startLevel("level_03")
    core.controller.input.set(0.5, true)
    for (i in 0 until 600) {
      if (i == 90) core.controller.input.set(0.5, false)
      if (i == 150) core.controller.input.set(-0.8, true)
      if (i == 240) core.controller.input.set(0.0, false)
      frames(core, 1)
      val f = core.controller.frame
      for (v in f.viewProj) assertTrue(v.isFinite())
      assertTrue(f.dynamic.cube.count <= f.dynamic.cube.capacity)
    }
    assertTrue(core.controller.frame.dynamic.totalInstances > 20)
    assertNotNull(core.controller.frame.staticMesh)
  }

  @Test fun physicsTestSceneRunsInsideTheController() {
    val core = app()
    core.controller.startPhysicsTest()
    assertNotNull(core.controller.testScene)
    core.controller.runScenario(ScenarioKind.TEST_JUMP)
    var n = 0
    while (core.controller.lastScenarioText.isEmpty() && n++ < 60 * 20) frames(core, 1)
    assertTrue(core.controller.lastScenarioText.contains("launchSpeed"))
    assertNotNull(core.controller.debug)
    core.controller.resetPhysicsTest()
    frames(core, 1)
    assertEquals("Manual", core.controller.debugStatus)
  }

  @Test fun musicAndAmbienceStartPerWorldAndSoundsFireDuringPlay() {
    val audio = FakeAudio()
    val core = app(audio = audio)
    core.controller.startLevel("level_01")
    assertTrue(audio.plays.any { it.loop && it.clip.startsWith("music_") })
    assertTrue(audio.plays.any { it.loop && it.clip.startsWith("ambient_") })
    core.controller.input.set(0.0, true); frames(core, 60)
    core.controller.input.set(0.0, false); frames(core, 120)
    assertTrue("launch sound played", audio.plays.any { it.clip == "launch" })
  }

  @Test fun languageFollowsTheDeviceUntilOverridden() {
    assertEquals("Play", app(lang = "en").str("play"))
    assertEquals("العب", app(lang = "ar").str("play"))
    val c = app(lang = "ar")
    c.updateSettings { it.copy(gameplay = it.gameplay.copy(language = "en")) }
    assertEquals("Play", c.str("play"))
  }

  @Test fun navigationDrivesTheScreensTheWayTheActivityDoes() {
    val core = app()
    val n = core.navigator
    n.push(ScreenId.WORLD_SELECT); n.push(ScreenId.LEVEL_SELECT); n.startPlaying()
    assertEquals(ScreenId.PLAYING, n.current)
    n.back(); assertEquals(ScreenId.PAUSE, n.current)
    assertTrue(n.contains(ScreenId.PLAYING))
    n.push(ScreenId.SETTINGS); n.back(); assertEquals(ScreenId.PAUSE, n.current)
    n.home(); assertFalse(n.contains(ScreenId.PLAYING))
  }
}
