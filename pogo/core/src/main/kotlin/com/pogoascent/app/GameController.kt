package com.pogoascent.app

import com.pogoascent.audio.AmbientDirector
import com.pogoascent.audio.AmbientGenerator
import com.pogoascent.audio.MusicGenerator
import com.pogoascent.audio.MusicStyles
import com.pogoascent.camera.CameraUserSettings
import com.pogoascent.debug.AutoPilot
import com.pogoascent.debug.PhysicsTestScene
import com.pogoascent.debug.ScenarioKind
import com.pogoascent.feedback.FeedbackRouter
import com.pogoascent.levels.GameSession
import com.pogoascent.levels.LevelLoader
import com.pogoascent.levels.ResultSummary
import com.pogoascent.levels.SessionPhase
import com.pogoascent.levels.WorldDef
import com.pogoascent.particles.ParticleSystem
import com.pogoascent.player.DebugSnapshot
import com.pogoascent.player.PlayerInput
import com.pogoascent.render.DynamicScene
import com.pogoascent.render.Mat4
import com.pogoascent.render.Palette
import com.pogoascent.render.RenderBatches
import com.pogoascent.render.SceneBuilder
import com.pogoascent.render.StaticMesh
import com.pogoascent.ui.HudModel
import com.pogoascent.ui.HudState

/** Shown on the Level Complete screen. */
class CompletionInfo(val summary: ResultSummary, val levelName: String, val nextLevelId: String?, val newItems: List<String>)

interface GameListener {
  fun onLevelComplete(info: CompletionInfo)
}

/** Everything the OpenGL layer needs to draw one frame, filled by [GameController.update]. */
class RenderFrame {
  /** Increments whenever [staticMesh]/[scenery] change so the renderer re-uploads them. */
  @Volatile var sceneVersion = 0
  var staticMesh: StaticMesh? = null
  var scenery: RenderBatches? = null
  val dynamic = RenderBatches(640, 256, 640)
  val viewProj = FloatArray(16)
  var camX = 0f; var camY = 0f; var camZ = 20f
  var skyTop = floatArrayOf(0.35f, 0.65f, 0.95f)
  var skyBottom = floatArrayOf(0.85f, 0.94f, 1f)
  var fog = floatArrayOf(0.8f, 0.9f, 1f)
  var light = floatArrayOf(1f, 0.97f, 0.88f)
  var hasScene = false
  var showShadows = true
  var showEffects = true
}

/**
 * Glue between the game logic and a renderer. **Owned by the render/GL thread**: it advances the simulation, routes events to
 * audio / haptics / particles, builds the per-frame scene data and publishes HUD / debug snapshots (volatile) for the UI thread.
 * It contains no Android or OpenGL types, so the whole flow is exercised by JVM tests.
 */
class GameController(private val core: AppCore) {
  val frame = RenderFrame()
  val input = PlayerInput()
  var listener: GameListener? = null

  var session: GameSession? = null; private set
  var testScene: PhysicsTestScene? = null; private set
  var world: WorldDef? = null; private set
  var paused = false
  val inDemo: Boolean get() = demoPilot != null

  @Volatile var hud: HudState? = null; private set
  @Volatile var debug: DebugSnapshot? = null; private set
  @Volatile var debugStatus: String = ""; private set
  @Volatile var lastScenarioText: String = ""; private set

  val particles = ParticleSystem(ParticleSystem.loadPresets())
  private val router = FeedbackRouter({ core.physics }, core.audio, core.haptics, particles)
  private val hudModel = HudModel { core.save.data.settings.gameplay }
  private val ambient = core.audio?.let { AmbientDirector(it) }
  private var builder: SceneBuilder? = null
  private var dynamicScene: DynamicScene? = null
  private var demoPilot: AutoPilot? = null
  private var completionHandled = false
  private var clock = 0.0
  private val view = FloatArray(16)
  private val proj = FloatArray(16)
  private var currentMusic: String? = null
  private var currentAmbience: String? = null

  // ---- lifecycle ----------------------------------------------------------------------------------

  /** Start playing [levelId]. With [demo] the validated autopilot route plays it (main-menu backdrop). */
  fun startLevel(levelId: String, demo: Boolean = false) {
    val data = LevelLoader.loadData(levelId)
    val w = core.worlds.worldOfLevel(levelId) ?: core.worlds.worlds.first()
    val level = LevelLoader.load(data, core.surfaces)
    val s = GameSession(level, core.physics, core.cameraConfig)
    attach(s, w, SceneBuilder(level, Palette(w.palette)))
    testScene = null
    completionHandled = false
    paused = false
    demoPilot = if (demo) runCatching { AutoPilot(s, DemoRoute.load(levelId)) }.getOrNull() else null
    hudModel.hint = if (demo) "" else hintFor(levelId)
    startAudioFor(w)
  }

  fun startPhysicsTest() {
    val scene = PhysicsTestScene(core.surfaces, core.physics)
    testScene = scene
    val w = core.worlds.worlds.first()
    attach(scene.session, w, SceneBuilder(scene.session.level, Palette(w.palette)))
    demoPilot = null
    completionHandled = true
    paused = false
    hudModel.hint = ""
    startAudioFor(w)
  }

  /** After the app returns from the background the audio loops were stopped; start them again. */
  fun restartAudio() {
    val w = world ?: return
    currentMusic = null; currentAmbience = null
    startAudioFor(w)
  }

  fun runScenario(kind: ScenarioKind) { testScene?.start(kind) }
  fun resetPhysicsTest() { testScene?.reset() }

  private fun attach(s: GameSession, w: WorldDef, b: SceneBuilder) {
    session?.let { old -> old.removeListener(::onEvent) }
    session = s
    world = w
    builder = b
    dynamicScene = DynamicScene(b)
    s.addListener(::onEvent)
    particles.clear()
    frame.staticMesh = b.staticMesh
    frame.scenery = b.scenery
    val p = b.palette
    frame.skyTop = p.skyTop; frame.skyBottom = p.skyBottom; frame.fog = p.fog; frame.light = p.light
    frame.hasScene = true
    frame.sceneVersion++
    applySettings()
    s.camera.snapTo(s.player.x, s.player.y)
  }

  /** Back to "no level" (e.g. leaving to a screen that does not need the game view). */
  fun stop() {
    session?.removeListener(::onEvent)
    session = null; testScene = null; demoPilot = null
    frame.hasScene = false
    core.audio?.stopMusic(); core.audio?.stopAmbient()
    currentMusic = null; currentAmbience = null
    hud = null; debug = null
  }

  fun restart() {
    val s = session ?: return
    val t = testScene
    if (t != null) { t.reset(); return }
    s.resetLevel()
    completionHandled = false
    paused = false
    particles.clear()
  }

  private fun hintFor(levelId: String): String {
    if (!core.save.data.settings.gameplay.tutorialHints) return ""
    return if (levelId == core.worlds.levelOrder.firstOrNull()) "hint_lean" else ""
  }

  private fun startAudioFor(w: WorldDef) {
    val audio = core.audio ?: return
    if (currentMusic != w.musicStyle) {
      audio.playMusic("music_${w.musicStyle}", MusicGenerator.render(MusicStyles.get(w.musicStyle)))
      currentMusic = w.musicStyle
    }
    if (currentAmbience != w.ambience) {
      audio.playAmbient("ambient_${w.ambience}", AmbientGenerator.render(w.ambience))
      ambient?.setAmbience(w.ambience)
      currentAmbience = w.ambience
    }
  }

  /** Re-apply settings (audio levels, haptics, camera comfort, particle density, controls) after any change. */
  fun applySettings() {
    val st = core.save.data.settings
    session?.cameraUser = CameraUserSettings(shakeIntensity = st.gameplay.cameraShake, zoom = st.gameplay.cameraZoom)
    particles.density = st.video.particleDensity
    particles.enabled = st.video.effects
    frame.showShadows = st.video.shadows
    frame.showEffects = st.video.effects
    core.applyAudioAndHaptics()
  }

  // ---- per frame ----------------------------------------------------------------------------------

  private fun onEvent(e: com.pogoascent.player.GameEvent) {
    router.onEvent(e)
    if (e.type == com.pogoascent.player.EventType.BOOST) dynamicScene?.flashBoost()
  }

  /** Advance the game by [frameDt] real seconds and prepare [frame] for drawing at the given aspect ratio. */
  fun update(frameDt: Double, aspect: Float) {
    if (core.settingsDirty) { core.settingsDirty = false; applySettings() }
    val s = session ?: return
    val dt = frameDt.coerceIn(0.0, 0.1)
    hudModel.onFrame(frameDt)
    if (!paused) {
      clock += dt
      val pilot = demoPilot
      val test = testScene
      if (pilot != null) {
        // fixed-step the autopilot so its timing matches the validated route
        var left = dt + demoCarry
        val step = core.physics.fixedDt
        while (left >= step) { pilot.drive(); s.update(step); left -= step }
        demoCarry = left
        if (pilot.finished || s.phase == SessionPhase.COMPLETE || s.resets > 0) restartDemo()
      } else if (test != null) {
        test.manualInput.set(input.lean, input.jumpHeld)
        test.update(dt)
        debugStatus = test.status
        test.lastResult?.let { lastScenarioText = it.toString() }
      } else {
        s.input.set(input.lean, input.jumpHeld)
        s.update(dt)
      }
      router.onChargeLevel(s.player.charge, s.player.charging)
      particles.update(dt)
      core.audio?.let { it.listenerX = s.camera.targetX; it.listenerY = s.camera.targetY }
      ambient?.update(dt, s.camera.targetX, s.camera.targetY)
      if (s.phase == SessionPhase.COMPLETE && !completionHandled && !inDemo && testScene == null) handleCompletion(s)
    }
    hud = hudModel.state(s)
    debug = s.player.debugSnapshot()
    buildFrame(s, aspect)
  }

  private var demoCarry = 0.0

  private fun restartDemo() {
    val id = session?.level?.data?.id ?: return
    demoCarry = 0.0
    startLevel(id, demo = true)
  }

  private fun handleCompletion(s: GameSession) {
    completionHandled = true
    val r = s.result ?: return
    val data = s.level.data
    val summary = core.progression.recordCompletion(r, data.difficulty, data.parTimeSec, core.epochDay())
    val granted = core.wardrobe.grantProgressUnlocks()
    core.audio?.trigger("goal_fanfare")
    listener?.onLevelComplete(CompletionInfo(summary, data.name.ifBlank { data.id }, core.progression.nextLevelAfter(data.id), granted))
  }

  /** Abandoning a run (back to menu) keeps the partial progress and stats. */
  fun abandonRun() {
    val s = session ?: return
    if (testScene != null || inDemo || s.phase == SessionPhase.COMPLETE) return
    core.progression.recordAttempt(s.level.data.id, s.bestProgress, s.player.jumpCount, s.player.boostCount, s.falls, s.resets, s.timeSec)
  }

  private fun buildFrame(s: GameSession, aspect: Float) {
    val dyn = dynamicScene ?: return
    val cam = s.camera
    cam.viewMatrix(view)
    cam.projectionMatrix(proj, aspect)
    Mat4.multiply(frame.viewProj, proj, view)
    frame.camX = cam.eyeX.toFloat(); frame.camY = cam.eyeY.toFloat(); frame.camZ = cam.eyeZ.toFloat()
    dyn.build(s, core.wardrobe.style(), clock, 1.0 / 60)
    // dynamic scene writes into its own batches; copy into the shared frame batches (particles appended after)
    frame.dynamic.clear()
    copyBatch(dyn.batches.cube, frame.dynamic.cube)
    copyBatch(dyn.batches.cylinder, frame.dynamic.cylinder)
    copyBatch(dyn.batches.sphere, frame.dynamic.sphere)
    if (frame.showEffects) particles.render(frame.dynamic)
  }

  private fun copyBatch(src: com.pogoascent.render.InstanceBatch, dst: com.pogoascent.render.InstanceBatch) {
    val n = minOf(src.count, dst.capacity)
    dst.copyFrom(src, n)
  }
}
