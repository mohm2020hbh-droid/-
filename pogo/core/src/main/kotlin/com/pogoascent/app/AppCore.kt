package com.pogoascent.app

import com.pogoascent.audio.AudioBackend
import com.pogoascent.audio.AudioCatalog
import com.pogoascent.audio.AudioManager
import com.pogoascent.camera.CameraConfig
import com.pogoascent.customization.ItemCatalog
import com.pogoascent.customization.Wardrobe
import com.pogoascent.haptics.HapticBackend
import com.pogoascent.haptics.HapticManager
import com.pogoascent.levels.LevelLoader
import com.pogoascent.levels.Progression
import com.pogoascent.levels.WorldCatalog
import com.pogoascent.physics.PhysicsConfig
import com.pogoascent.physics.SurfaceCatalog
import com.pogoascent.save.SaveManager
import com.pogoascent.save.SaveStorage
import com.pogoascent.settings.GameSettings
import com.pogoascent.touch.TouchControlMapper
import com.pogoascent.ui.MenuModel
import com.pogoascent.ui.Navigator
import com.pogoascent.ui.UiStrings

/**
 * Composition root of the game: catalogs, save file, progression, wardrobe, audio, haptics, navigation and the
 * [GameController]. Platform pieces (storage, audio, vibration, clocks) are injected, so the Android app and the JVM tests
 * run the identical logic. See [GameController] for the threading rule (game logic = render thread, UI = UI thread).
 */
class AppCore(
  storage: SaveStorage,
  audioBackend: AudioBackend?,
  hapticBackend: HapticBackend?,
  private val clockSec: () -> Double,
  clockMs: () -> Long,
  private val epochDayProvider: () -> Long,
  private val deviceLanguage: () -> String = { "en" },
) {
  val surfaces: SurfaceCatalog = SurfaceCatalog.load()
  var physics: PhysicsConfig = PhysicsConfig.load()
  val cameraConfig: CameraConfig = CameraConfig.load()
  val worlds: WorldCatalog = WorldCatalog.load()
  val items: ItemCatalog = ItemCatalog.load()
  val save = SaveManager(storage).also { it.load() }
  val progression = Progression(worlds, save)
  val wardrobe = Wardrobe(items, save, worlds, progression)
  val audio: AudioManager? = audioBackend?.let { AudioManager(AudioCatalog.load(), it, clockSec) }
  val haptics: HapticManager? = hapticBackend?.let { HapticManager(HapticManager.loadDefs(), it, clockMs) }
  val navigator = Navigator()
  val menu = MenuModel(worlds, progression, save) { LevelLoader.loadData(it) }
  val touch = TouchControlMapper(save.data.settings.controls)
  val controller = GameController(this)

  /** Set from any thread when settings changed; the render thread re-applies them at the start of the next frame. */
  @Volatile var settingsDirty = true

  @Volatile var selectedWorldId: String = worlds.worlds.first().id
  @Volatile var currentLevelId: String? = null
  @Volatile var lastCompletion: CompletionInfo? = null

  init {
    applyAudioAndHaptics()
    touch.onPress = { haptics?.trigger("ui_click") }
  }

  fun epochDay(): Long = epochDayProvider()

  fun language(): String {
    val l = save.data.settings.gameplay.language
    return if (l == "auto") (if (deviceLanguage().startsWith("ar")) "ar" else "en") else l
  }

  fun str(key: String): String = UiStrings.get(key, language())

  fun applyAudioAndHaptics() {
    val s = save.data.settings
    audio?.settings = s.audio
    haptics?.let { it.enabled = s.controls.haptics; it.intensityScale = s.controls.hapticIntensity }
  }

  /** Change settings from the UI (saved immediately; the render thread applies them next frame). */
  fun updateSettings(change: (GameSettings) -> GameSettings) {
    save.update { it.copy(settings = change(it.settings).sanitized()) }
    touch.apply(save.data.settings.controls)
    applyAudioAndHaptics()
    settingsDirty = true
  }

  fun uiClick() { audio?.trigger("ui_click"); haptics?.trigger("ui_click") }
  fun uiBack() { audio?.trigger("ui_back") }
  fun uiToggle() { audio?.trigger("ui_toggle") }

  fun continueLevelId(): String = progression.continueLevel()

  /** Wipe progress but keep nothing (Settings → Reset progress). */
  fun resetProgress() { save.reset(); applyAudioAndHaptics(); touch.apply(save.data.settings.controls); settingsDirty = true }
}
