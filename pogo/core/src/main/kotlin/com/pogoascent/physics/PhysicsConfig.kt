package com.pogoascent.physics

import com.pogoascent.core.GameJson
import com.pogoascent.core.Resources
import kotlinx.serialization.Serializable

/**
 * The single, central place for every physics tuning constant (SI units: metres, seconds, radians –
 * angles that are easier to read in degrees are suffixed `Deg`).
 *
 * **Evidence grade of every default is D** (no analysis data was present in the repo – see DECISIONS.md D-001).
 * Metadata (unit, source, confidence, notes) for each field lives in [PhysicsParams]; a unit test makes
 * sure the two never drift apart. Values are loaded from `data/physics_config.json`; missing keys fall back
 * to the defaults below, so a JSON file may override just a few values.
 */
@Serializable
data class PhysicsConfig(
  // ---- Timestep ---------------------------------------------------------------------------------
  val fixedTimestepHz: Double = 120.0,
  val maxFrameDelta: Double = 0.1,
  val maxStepsPerFrame: Int = 8,
  val maxSubstepTravel: Double = 0.08,

  // ---- Gravity & speed limits -------------------------------------------------------------------
  val gravity: Double = 22.0,
  val fallGravityMultiplier: Double = 1.0,
  val terminalVelocity: Double = 42.0,
  val maxSpeed: Double = 48.0,
  val fallSpeed: Double = 12.0,

  // ---- Jump charge / release --------------------------------------------------------------------
  val jumpChargeTime: Double = 0.85,
  val jumpChargeCurve: Double = 1.0,
  val jumpPowerMin: Double = 6.5,
  val jumpPower: Double = 17.0,
  val chargeCompression: Double = 0.35,
  val jumpBufferTime: Double = 0.08,
  val maxLaunchAngleFromNormalDeg: Double = 80.0,
  val launchNormalBlend: Double = 0.0,
  val momentumInherit: Double = 0.85,

  // ---- Ground control ---------------------------------------------------------------------------
  val maxLeanAngleDeg: Double = 75.0,
  val turnSpeed: Double = 3.2,
  val acceleration: Double = 18.0,
  val deceleration: Double = 26.0,
  val groundContactNormalMinY: Double = 0.35,
  val friction: Double = 1.0,
  val slideKineticRatio: Double = 0.8,

  // ---- Air control ------------------------------------------------------------------------------
  val airControl: Double = 1.0,
  val rotationSpeed: Double = 7.0,
  val airTurnAccel: Double = 40.0,
  val airAngularDrag: Double = 2.0,
  val maxAngularVelocity: Double = 12.0,
  val airHorizontalAccel: Double = 0.0,
  val airLinearDrag: Double = 0.02,

  // ---- Landing / bounce -------------------------------------------------------------------------
  val bounce: Double = 0.55,
  val passiveBounce: Double = 0.0,
  val bounceMinSpeed: Double = 2.0,
  val landingTangentialRetention: Double = 0.55,
  val maxLandingTiltDeg: Double = 55.0,
  val slipRestitution: Double = 0.25,
  val slipSpinFactor: Double = 0.8,

  // ---- Collision response (body) ----------------------------------------------------------------
  val collisionRestitutionScale: Double = 1.0,
  val inertiaFactor: Double = 0.35,
  val hardCollisionSpeed: Double = 8.0,
  val penetrationSlop: Double = 0.005,

  // ---- Boost jump -------------------------------------------------------------------------------
  val boostThreshold: Double = 285.0,
  val boostPower: Double = 1.35,
  val boostWindow: Double = 1.2,
  val boostAngularBonus: Double = 0.15,

  // ---- Player collision shape (metres, measured along the stick axis from the centre of mass) ---
  val tipOffset: Double = 0.90,
  val tipRadius: Double = 0.10,
  val springOffset: Double = 0.45,
  val springRadius: Double = 0.12,
  val torsoRadius: Double = 0.28,
  val headOffset: Double = 0.70,
  val headRadius: Double = 0.30,
) {
  val fixedDt: Double get() = 1.0 / fixedTimestepHz
  val maxLeanAngleRad: Double get() = Math.toRadians(maxLeanAngleDeg)
  val boostThresholdRad: Double get() = Math.toRadians(boostThreshold)
  val maxLandingTiltRad: Double get() = Math.toRadians(maxLandingTiltDeg)
  val maxLaunchAngleFromNormalRad: Double get() = Math.toRadians(maxLaunchAngleFromNormalDeg)

  /** Moment of inertia (mass = 1). */
  val inertia: Double get() = inertiaFactor * tipOffset * tipOffset

  /** Speed that a full-charge launch reaches: used to normalise stored spring charge. */
  fun launchSpeedForCharge(charge: Double): Double {
    val c = charge.coerceIn(0.0, 1.0)
    return jumpPowerMin + (jumpPower - jumpPowerMin) * Math.pow(c, jumpChargeCurve)
  }

  /** Human-readable problems with this configuration; empty when it is usable. */
  fun validate(): List<String> {
    val problems = ArrayList<String>()
    fun positive(name: String, v: Double) { if (!(v > 0.0) || v.isInfinite()) problems += "$name must be > 0 (was $v)" }
    fun nonNegative(name: String, v: Double) { if (!(v >= 0.0) || v.isInfinite()) problems += "$name must be >= 0 (was $v)" }
    fun unit(name: String, v: Double) { if (!(v in 0.0..1.0)) problems += "$name must be within 0..1 (was $v)" }
    positive("fixedTimestepHz", fixedTimestepHz)
    if (fixedTimestepHz < 30.0 || fixedTimestepHz > 480.0) problems += "fixedTimestepHz should be within 30..480"
    positive("maxFrameDelta", maxFrameDelta)
    if (maxStepsPerFrame < 1) problems += "maxStepsPerFrame must be >= 1"
    positive("maxSubstepTravel", maxSubstepTravel)
    positive("gravity", gravity)
    positive("fallGravityMultiplier", fallGravityMultiplier)
    positive("terminalVelocity", terminalVelocity)
    positive("maxSpeed", maxSpeed)
    positive("fallSpeed", fallSpeed)
    positive("jumpChargeTime", jumpChargeTime)
    positive("jumpChargeCurve", jumpChargeCurve)
    positive("jumpPowerMin", jumpPowerMin)
    positive("jumpPower", jumpPower)
    if (jumpPower < jumpPowerMin) problems += "jumpPower must be >= jumpPowerMin"
    if (jumpPower > maxSpeed) problems += "jumpPower must be <= maxSpeed"
    nonNegative("chargeCompression", chargeCompression)
    if (chargeCompression >= tipOffset) problems += "chargeCompression must be < tipOffset"
    nonNegative("jumpBufferTime", jumpBufferTime)
    if (!(maxLaunchAngleFromNormalDeg in 1.0..89.9)) problems += "maxLaunchAngleFromNormalDeg must be within 1..89.9"
    unit("launchNormalBlend", launchNormalBlend)
    nonNegative("momentumInherit", momentumInherit)
    if (!(maxLeanAngleDeg in 1.0..120.0)) problems += "maxLeanAngleDeg must be within 1..120"
    positive("turnSpeed", turnSpeed)
    positive("acceleration", acceleration)
    positive("deceleration", deceleration)
    if (!(groundContactNormalMinY in -1.0..1.0)) problems += "groundContactNormalMinY must be within -1..1"
    nonNegative("friction", friction)
    unit("slideKineticRatio", slideKineticRatio)
    nonNegative("airControl", airControl)
    positive("rotationSpeed", rotationSpeed)
    positive("airTurnAccel", airTurnAccel)
    nonNegative("airAngularDrag", airAngularDrag)
    positive("maxAngularVelocity", maxAngularVelocity)
    if (rotationSpeed > maxAngularVelocity) problems += "rotationSpeed must be <= maxAngularVelocity"
    nonNegative("airHorizontalAccel", airHorizontalAccel)
    nonNegative("airLinearDrag", airLinearDrag)
    nonNegative("bounce", bounce)
    nonNegative("passiveBounce", passiveBounce)
    nonNegative("bounceMinSpeed", bounceMinSpeed)
    unit("landingTangentialRetention", landingTangentialRetention)
    if (!(maxLandingTiltDeg in 1.0..179.0)) problems += "maxLandingTiltDeg must be within 1..179"
    nonNegative("slipRestitution", slipRestitution)
    nonNegative("slipSpinFactor", slipSpinFactor)
    nonNegative("collisionRestitutionScale", collisionRestitutionScale)
    positive("inertiaFactor", inertiaFactor)
    nonNegative("hardCollisionSpeed", hardCollisionSpeed)
    nonNegative("penetrationSlop", penetrationSlop)
    positive("boostThreshold", boostThreshold)
    positive("boostPower", boostPower)
    nonNegative("boostWindow", boostWindow)
    nonNegative("boostAngularBonus", boostAngularBonus)
    positive("tipOffset", tipOffset)
    positive("tipRadius", tipRadius)
    positive("springOffset", springOffset)
    positive("springRadius", springRadius)
    positive("torsoRadius", torsoRadius)
    positive("headOffset", headOffset)
    positive("headRadius", headRadius)
    return problems
  }

  companion object {
    const val RESOURCE_PATH = "data/physics_config.json"

    fun fromJson(text: String): PhysicsConfig {
      val cfg = GameJson.pretty.decodeFromString(serializer(), text)
      val problems = cfg.validate()
      require(problems.isEmpty()) { "Invalid PhysicsConfig: " + problems.joinToString("; ") }
      return cfg
    }

    fun toJson(config: PhysicsConfig): String = GameJson.pretty.encodeToString(serializer(), config)

    /** Loads the bundled config. */
    fun load(): PhysicsConfig = fromJson(Resources.readText(RESOURCE_PATH))
  }
}
