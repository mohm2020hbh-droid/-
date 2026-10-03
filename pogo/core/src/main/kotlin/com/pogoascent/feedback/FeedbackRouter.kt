package com.pogoascent.feedback

import com.pogoascent.audio.AudioManager
import com.pogoascent.haptics.HapticManager
import com.pogoascent.particles.ParticleSystem
import com.pogoascent.physics.PhysicsConfig
import com.pogoascent.player.EventType
import com.pogoascent.player.GameEvent

/**
 * Single place that decides what each gameplay event looks, sounds and feels like. Surfaces carry their own sound /
 * particle / haptic ids (surfaces.json); everything else is mapped here. Any sink may be null (e.g. no vibrator).
 */
class FeedbackRouter(
  private val physics: () -> PhysicsConfig,
  private val audio: AudioManager?,
  private val haptics: HapticManager?,
  private val particles: ParticleSystem?,
) {
  private var lastChargeStep = -1

  /** Called every frame with the current spring charge to play the rising "charge tick" cue. */
  fun onChargeLevel(charge: Double, charging: Boolean) {
    if (!charging) { lastChargeStep = -1; return }
    val step = (charge * 10).toInt()
    if (step != lastChargeStep && step > 0) {
      lastChargeStep = step
      audio?.trigger("charge_tick", 0.6 + 0.4 * charge)
      // rising pitch is done with the same event at higher intensity; pitch variation would hide the rise
    }
  }

  fun onEvent(e: GameEvent) {
    val c = physics()
    val speedNorm = (e.magnitude / c.jumpPower).coerceIn(0.0, 1.5)
    when (e.type) {
      EventType.LAUNCH -> {
        audio?.trigger("jump_launch", 0.5 + 0.5 * speedNorm.coerceAtMost(1.0), e.x, e.y)
        haptics?.trigger("jump", 0.5 + 0.5 * speedNorm)
        particles?.emit(if (e.tag == "boost") "boost" else e.surface?.particle ?: "dust", e.x, e.y, e.nx, e.ny, 0.6)
      }
      EventType.CHARGE_FULL -> { audio?.trigger("charge_full"); haptics?.trigger("charge_full") }
      EventType.LAND -> {
        val s = e.surface
        audio?.trigger(s?.sound ?: "land_soft", 0.35 + 0.65 * speedNorm.coerceAtMost(1.0), e.x, e.y)
        if (speedNorm > 0.75) audio?.trigger("land_hard", speedNorm, e.x, e.y)
        haptics?.trigger(s?.haptic ?: "landing", 0.4 + speedNorm)
        particles?.emit(s?.particle ?: "dust", e.x, e.y, e.nx, e.ny, 0.4 + speedNorm)
      }
      EventType.SLIP -> {
        audio?.trigger("slip", speedNorm, e.x, e.y)
        haptics?.trigger("hard_collision", 0.6)
        particles?.emit("spark", e.x, e.y, e.nx, e.ny, 0.7)
      }
      EventType.BOUNCE -> {
        audio?.trigger("bounce_pad", 0.6 + 0.4 * speedNorm.coerceAtMost(1.0), e.x, e.y)
        haptics?.trigger("bounce")
        particles?.emit("bounce", e.x, e.y, e.nx, e.ny, 1.0)
      }
      EventType.COLLISION -> {
        audio?.trigger("collision_soft", (e.magnitude / c.hardCollisionSpeed).coerceIn(0.2, 1.0), e.x, e.y)
      }
      EventType.HARD_COLLISION -> {
        audio?.trigger("collision_hard", (e.magnitude / (c.hardCollisionSpeed * 2)).coerceIn(0.5, 1.2), e.x, e.y)
        haptics?.trigger("hard_collision", (e.magnitude / (c.hardCollisionSpeed * 2)).coerceIn(0.5, 1.2))
        particles?.emit("hard_impact", e.x, e.y, e.nx, e.ny, 1.0)
      }
      EventType.BOOST_ARMED -> { audio?.trigger("boost_armed"); haptics?.trigger("boost_armed") }
      EventType.BOOST -> {
        audio?.trigger("jump_boost")
        haptics?.trigger("boost")
        particles?.emit("boost", e.x, e.y, 0.0, 1.0, 1.2)
      }
      EventType.FALL -> { audio?.trigger("fall_whoosh"); haptics?.trigger("fall") }
      EventType.HAZARD -> {
        audio?.trigger("hazard_hit")
        haptics?.trigger("hard_collision")
        particles?.emit("spark", e.x, e.y, 0.0, 1.0, 1.0)
      }
      EventType.KILL_FLOOR -> audio?.trigger("failure_respawn")
      EventType.CHECKPOINT -> {
        audio?.trigger("checkpoint")
        haptics?.trigger("checkpoint")
        particles?.emit("goal", e.x, e.y, 0.0, 1.0, 0.5)
      }
      EventType.GOAL -> {
        audio?.trigger("goal_fanfare")
        haptics?.trigger("goal")
        particles?.emit("goal", e.x, e.y, 0.0, 1.0, 1.5)
      }
      EventType.CHARGE_START, EventType.BOOST_EXPIRED -> {}
    }
  }
}
