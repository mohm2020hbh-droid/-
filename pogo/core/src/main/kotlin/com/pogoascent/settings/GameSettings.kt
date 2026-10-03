package com.pogoascent.settings

import kotlinx.serialization.Serializable

enum class Quality(val label: String) { LOW("Low"), MEDIUM("Medium"), HIGH("High") }

enum class ControlLayout(val label: String) {
  /** Left thumb drags a horizontal lean slider, right thumb holds the jump button. */
  SLIDER("Lean slider + jump button"),
  /** Dragging anywhere on the left half leans; right half is the jump button (no visible slider). */
  SWIPE("Swipe to lean"),
  /** Whole screen: drag = lean, hold with a second finger = jump. */
  FULLSCREEN("Full-screen gesture"),
}

@Serializable
data class VideoSettings(
  val quality: Quality = Quality.MEDIUM,
  /** Render resolution as a fraction of the screen (0.5..1.0). */
  val resolutionScale: Double = 0.85,
  val fpsCap: Int = 60,
  val effects: Boolean = true,
  /** 0..1 share of the particle budget in use. */
  val particleDensity: Double = 0.7,
  val shadows: Boolean = true,
) {
  fun sanitized() = copy(
    resolutionScale = resolutionScale.coerceIn(0.5, 1.0),
    fpsCap = if (fpsCap <= 30) 30 else 60,
    particleDensity = particleDensity.coerceIn(0.0, 1.0),
  )

  companion object {
    fun forQuality(q: Quality): VideoSettings = when (q) {
      Quality.LOW -> VideoSettings(q, 0.65, 60, effects = false, particleDensity = 0.35, shadows = false)
      Quality.MEDIUM -> VideoSettings(q, 0.85, 60, effects = true, particleDensity = 0.7, shadows = true)
      Quality.HIGH -> VideoSettings(q, 1.0, 60, effects = true, particleDensity = 1.0, shadows = true)
    }
  }
}

@Serializable
data class AudioSettings(
  val master: Double = 0.8,
  val music: Double = 0.55,
  val sfx: Double = 0.9,
  val ambient: Double = 0.5,
) {
  fun sanitized() = copy(master = master.coerceIn(0.0, 1.0), music = music.coerceIn(0.0, 1.0), sfx = sfx.coerceIn(0.0, 1.0), ambient = ambient.coerceIn(0.0, 1.0))
}

@Serializable
data class ControlSettings(
  val layout: ControlLayout = ControlLayout.SLIDER,
  /** Lean sensitivity multiplier (0.5..2). Higher = a shorter drag reaches full lean. */
  val sensitivity: Double = 1.0,
  /** Fraction of the slider travel around the centre that counts as "no lean" (0..0.4). */
  val deadzone: Double = 0.08,
  val leftHanded: Boolean = false,
  val buttonSize: Double = 1.0,
  val buttonOpacity: Double = 0.65,
  val haptics: Boolean = true,
  val hapticIntensity: Double = 1.0,
) {
  fun sanitized() = copy(
    sensitivity = sensitivity.coerceIn(0.5, 2.0),
    deadzone = deadzone.coerceIn(0.0, 0.4),
    buttonSize = buttonSize.coerceIn(0.7, 1.5),
    buttonOpacity = buttonOpacity.coerceIn(0.2, 1.0),
    hapticIntensity = hapticIntensity.coerceIn(0.0, 1.0),
  )
}

@Serializable
data class GameplaySettings(
  val showHud: Boolean = true,
  val showTimer: Boolean = true,
  val showFps: Boolean = false,
  val cameraShake: Double = 1.0,
  val cameraZoom: Double = 1.0,
  val tutorialHints: Boolean = true,
) {
  fun sanitized() = copy(cameraShake = cameraShake.coerceIn(0.0, 1.0), cameraZoom = cameraZoom.coerceIn(0.65, 1.6))
}

@Serializable
data class GameSettings(
  val video: VideoSettings = VideoSettings(),
  val audio: AudioSettings = AudioSettings(),
  val controls: ControlSettings = ControlSettings(),
  val gameplay: GameplaySettings = GameplaySettings(),
) {
  fun sanitized() = GameSettings(video.sanitized(), audio.sanitized(), controls.sanitized(), gameplay.sanitized())
}
