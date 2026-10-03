package com.pogoascent.android

import android.app.Activity
import android.graphics.Color
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import com.pogoascent.app.AppCore
import com.pogoascent.app.CompletionInfo
import com.pogoascent.app.GameListener
import com.pogoascent.debug.ScenarioKind
import com.pogoascent.save.FileSaveStorage
import com.pogoascent.ui.ScreenId
import java.io.File
import java.util.Locale

/**
 * Single-activity app: [GLSurfaceView] (game) at the bottom, [HudOverlay] (HUD + touch controls) above it and a container
 * for the menu / overlay screens on top. Game logic runs on the GL thread; every UI → game call goes through
 * [GLSurfaceView.queueEvent]. Navigation rules live in [com.pogoascent.ui.Navigator].
 */
class MainActivity : Activity(), ScreenHost, GameListener {
  private lateinit var core: AppCore
  private lateinit var glView: GLSurfaceView
  private lateinit var renderer: GameRenderer
  private lateinit var hud: HudOverlay
  private lateinit var container: FrameLayout
  private lateinit var factory: ScreenFactory
  private val input = InputBridge()
  private var firstLevel = "level_01"

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    if (Build.VERSION.SDK_INT >= 28) {
      window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
    }
    core = AppCore(
      storage = FileSaveStorage(File(filesDir, "save.json")),
      audioBackend = AndroidAudioBackend(this),
      hapticBackend = AndroidHapticBackend(this),
      clockSec = { SystemClock.elapsedRealtimeNanos() / 1e9 },
      clockMs = { SystemClock.elapsedRealtime() },
      epochDayProvider = { System.currentTimeMillis() / 86_400_000L },
      deviceLanguage = { Locale.getDefault().language },
    )
    firstLevel = core.worlds.levelOrder.first()
    core.controller.listener = this
    factory = ScreenFactory(this, core, this)

    renderer = GameRenderer(core, input)
    glView = GLSurfaceView(this).apply {
      setEGLContextClientVersion(3)
      setEGLConfigChooser(8, 8, 8, 0, 16, 0)
      preserveEGLContextOnPause = true
      setRenderer(renderer)
      renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }
    hud = HudOverlay(this, core, input) { pauseGame() }
    container = FrameLayout(this)

    val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
    root.addView(glView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    root.addView(hud, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    root.addView(container, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    setContentView(root)
    glView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyDisplaySettings() }

    glView.queueEvent { core.controller.startLevel(firstLevel, demo = true) }
    show(ScreenId.MAIN_MENU)
    applyDisplaySettings()
  }

  // ---- screen management ----------------------------------------------------------------------

  private fun show(screen: ScreenId) {
    hud.screen = screen
    hud.resetTouches()
    container.removeAllViews()
    val overlayDim = when (screen) {
      ScreenId.PLAYING, ScreenId.PHYSICS_TEST -> 0
      ScreenId.PAUSE, ScreenId.LEVEL_COMPLETE -> 120
      else -> 70
    }
    container.setBackgroundColor(Color.argb(overlayDim, 0, 0, 0))
    if (screen != ScreenId.PLAYING) {
      val v = factory.build(screen)
      v.layoutDirection = if (core.language() == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
      container.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }
    container.isClickable = screen != ScreenId.PLAYING && screen != ScreenId.PHYSICS_TEST
    val pause = screen == ScreenId.PAUSE || screen == ScreenId.LEVEL_COMPLETE
    glView.queueEvent {
      core.controller.paused = pause || (core.navigator.contains(ScreenId.PAUSE) && screen != ScreenId.PLAYING)
      if (!core.navigator.contains(ScreenId.PLAYING) && screen != ScreenId.PHYSICS_TEST && !core.controller.inDemo) core.controller.startLevel(firstLevel, demo = true)
    }
    if (pause) core.save.save()
  }

  override fun navigate(to: ScreenId) {
    core.navigator.push(to)
    if (to == ScreenId.PHYSICS_TEST) glView.queueEvent { core.controller.startPhysicsTest() }
    show(to)
  }

  override fun back() {
    val wasPhysics = core.navigator.current == ScreenId.PHYSICS_TEST
    if (!core.navigator.back()) { finish(); return }
    if (wasPhysics) glView.queueEvent { core.controller.startLevel(firstLevel, demo = true) }
    show(core.navigator.current)
  }

  override fun startLevel(levelId: String) {
    core.navigator.startPlaying()
    core.currentLevelId = levelId
    glView.queueEvent { core.controller.startLevel(levelId) }
    show(ScreenId.PLAYING)
  }

  override fun resume() {
    core.navigator.back() // pops PAUSE
    show(core.navigator.current)
  }

  override fun restartLevel() {
    if (core.navigator.current != ScreenId.PLAYING) core.navigator.startPlaying()
    glView.queueEvent { core.controller.restart() }
    show(ScreenId.PLAYING)
  }

  override fun goHome() {
    glView.queueEvent {
      core.controller.abandonRun()
      core.controller.startLevel(firstLevel, demo = true)
    }
    core.navigator.home()
    core.save.save()
    show(ScreenId.MAIN_MENU)
  }

  override fun runScenario(kind: ScenarioKind) { glView.queueEvent { core.controller.runScenario(kind) } }
  override fun resetTest() { glView.queueEvent { core.controller.resetPhysicsTest() } }
  override fun refresh() { show(core.navigator.current) }
  override fun exitApp() { core.save.save(); finish() }

  private fun pauseGame() {
    if (core.navigator.current == ScreenId.PLAYING) { core.navigator.back(); show(core.navigator.current) }
    else if (core.navigator.current == ScreenId.PHYSICS_TEST) goHome()
  }

  override fun applyDisplaySettings() {
    val v = core.save.data.settings.video
    renderer.fpsCap = v.fpsCap
    val w = glView.width
    val h = glView.height
    if (w > 0 && h > 0) {
      val target = (w * v.resolutionScale).toInt().coerceAtLeast(320) to (h * v.resolutionScale).toInt().coerceAtLeast(180)
      // fixed-size surface = hardware-scaled lower resolution; full size when scale is 1
      if (v.resolutionScale >= 0.99) glView.holder.setSizeFromLayout() else glView.holder.setFixedSize(target.first, target.second)
    }
  }

  // ---- game → UI ------------------------------------------------------------------------------

  override fun onLevelComplete(info: CompletionInfo) {
    runOnUiThread {
      core.lastCompletion = info
      core.navigator.push(ScreenId.LEVEL_COMPLETE)
      show(ScreenId.LEVEL_COMPLETE)
    }
  }

  // ---- lifecycle ------------------------------------------------------------------------------

  override fun onPause() {
    super.onPause()
    core.controller.paused = true
    core.audio?.paused = true
    core.save.save()
    glView.onPause()
    if (core.navigator.current == ScreenId.PLAYING) { core.navigator.back(); show(ScreenId.PAUSE) }
  }

  override fun onResume() {
    super.onResume()
    glView.onResume()
    core.audio?.paused = false
    glView.queueEvent { core.controller.restartAudio() }
    immersive()
  }

  override fun onDestroy() {
    super.onDestroy()
    core.save.save()
    core.audio?.release()
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) immersive()
  }

  @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
  override fun onBackPressed() {
    core.uiBack()
    back()
  }

  @Suppress("DEPRECATION")
  private fun immersive() {
    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
      View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or
      View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
  }
}
