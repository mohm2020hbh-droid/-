package com.pogoascent.player

import com.pogoascent.physics.SurfaceDef

enum class EventType {
  CHARGE_START, CHARGE_FULL, LAUNCH, LAND, SLIP, BOUNCE, COLLISION, HARD_COLLISION,
  BOOST_ARMED, BOOST, BOOST_EXPIRED, FALL, HAZARD, CHECKPOINT, GOAL, KILL_FLOOR,
}

/**
 * Something that happened in the simulation. [magnitude] is event specific (impact speed, launch speed…).
 * Audio, haptics, particles, camera and progression all react to these – the player never calls them directly.
 */
class GameEvent(
  val type: EventType,
  val x: Double,
  val y: Double,
  val magnitude: Double = 0.0,
  val nx: Double = 0.0,
  val ny: Double = 1.0,
  val surface: SurfaceDef? = null,
  val tag: String? = null,
) {
  override fun toString() = "GameEvent($type @%.2f,%.2f mag=%.2f%s)".format(x, y, magnitude, if (tag != null) " tag=$tag" else "")
}

fun interface EventSink { fun emit(event: GameEvent) }

/** High level player state, derived from the simulation (for animation, audio, HUD). */
enum class PlayerState { GROUNDED, CHARGING, AIRBORNE, TUMBLING }

/** Snapshot of everything the Physics Test Scene displays. */
class DebugSnapshot(
  val vx: Double, val vy: Double, val speed: Double,
  val horizontalSpeed: Double, val verticalSpeed: Double,
  val angularVelocity: Double, val angleDeg: Double,
  val jumpPower: Double, val charge: Double,
  val gravity: Double, val grounded: Boolean, val airControl: Double,
  val collisionNx: Double, val collisionNy: Double,
  val boostAngleDeg: Double, val boostPower: Double, val boostArmed: Boolean,
  val height: Double, val state: PlayerState,
)
