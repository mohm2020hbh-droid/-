package com.pogoascent.camera

import com.pogoascent.core.GameJson
import com.pogoascent.core.Resources
import kotlinx.serialization.Serializable

/** All camera tuning in one editable file (`data/camera_config.json`). Units: metres, seconds, degrees. */
@Serializable
data class CameraConfig(
  // follow
  val followTimeX: Double = 0.20,
  val followTimeY: Double = 0.26,
  val deadZoneX: Double = 0.6,
  val deadZoneY: Double = 0.9,
  /** The camera looks this far above the player so more of the climb ahead is visible. */
  val verticalBias: Double = 2.0,
  // look ahead
  val lookAheadXFactor: Double = 0.22,
  val lookAheadXMax: Double = 3.0,
  val lookAheadUpFactor: Double = 0.18,
  val lookAheadUpMax: Double = 3.0,
  val lookAheadDownFactor: Double = 0.32,
  val lookAheadDownMax: Double = 6.0,
  val lookAheadSmoothTime: Double = 0.35,
  // lens
  val distance: Double = 14.5,
  val fovDeg: Double = 52.0,
  val pitchDeg: Double = 5.0,
  val zoomMin: Double = 0.65,
  val zoomMax: Double = 1.6,
  // landing response
  val landingDipPerSpeed: Double = 0.016,
  val landingDipMax: Double = 0.5,
  val landingRecoverTime: Double = 0.28,
  // hard collision shake (trauma model)
  val shakeMaxOffset: Double = 0.32,
  val shakeMaxRollDeg: Double = 1.4,
  val shakeTraumaPerSpeed: Double = 0.03,
  val shakeTraumaMax: Double = 0.85,
  val shakeDecayPerSecond: Double = 1.8,
  val shakeFrequencyHz: Double = 20.0,
  // boost
  val boostZoomOut: Double = 0.12,
  val boostFovKickDeg: Double = 6.0,
  val boostDuration: Double = 0.7,
  // fall camera
  val fallZoomOut: Double = 0.22,
  val fallZoomStartSpeed: Double = 12.0,
  val fallZoomFullSpeed: Double = 34.0,
  val zoomSmoothTime: Double = 0.45,
) {
  fun validate(): List<String> {
    val p = ArrayList<String>()
    fun pos(n: String, v: Double) { if (!(v > 0.0)) p += "$n must be > 0 (was $v)" }
    fun nn(n: String, v: Double) { if (!(v >= 0.0)) p += "$n must be >= 0 (was $v)" }
    pos("followTimeX", followTimeX); pos("followTimeY", followTimeY)
    nn("deadZoneX", deadZoneX); nn("deadZoneY", deadZoneY)
    pos("distance", distance)
    if (!(fovDeg in 20.0..100.0)) p += "fovDeg must be within 20..100"
    if (!(pitchDeg in -30.0..45.0)) p += "pitchDeg must be within -30..45"
    if (!(zoomMin > 0.0 && zoomMax >= zoomMin)) p += "zoomMin/zoomMax invalid"
    nn("shakeMaxOffset", shakeMaxOffset); nn("shakeMaxRollDeg", shakeMaxRollDeg)
    if (!(shakeTraumaMax in 0.0..1.0)) p += "shakeTraumaMax must be within 0..1"
    pos("shakeFrequencyHz", shakeFrequencyHz)
    pos("landingRecoverTime", landingRecoverTime)
    pos("zoomSmoothTime", zoomSmoothTime); pos("lookAheadSmoothTime", lookAheadSmoothTime)
    return p
  }

  companion object {
    const val RESOURCE_PATH = "data/camera_config.json"
    fun fromJson(text: String): CameraConfig {
      val c = GameJson.pretty.decodeFromString(serializer(), text)
      val problems = c.validate()
      require(problems.isEmpty()) { "Invalid CameraConfig: " + problems.joinToString("; ") }
      return c
    }
    fun toJson(c: CameraConfig): String = GameJson.pretty.encodeToString(serializer(), c)
    fun load(): CameraConfig = fromJson(Resources.readText(RESOURCE_PATH))
  }
}

/** Player-facing camera options (Settings → Gameplay). */
data class CameraUserSettings(
  /** 0 = shake off (accessibility) … 1 = full. */
  val shakeIntensity: Double = 1.0,
  /** Zoom multiplier on the base distance; <1 closer, >1 farther. */
  val zoom: Double = 1.0,
)
