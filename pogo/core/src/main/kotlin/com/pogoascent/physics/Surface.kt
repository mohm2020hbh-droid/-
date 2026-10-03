package com.pogoascent.physics

import com.pogoascent.core.GameJson
import com.pogoascent.core.Resources
import kotlinx.serialization.Serializable

/** Authored material class of a collider (the task's surface list). Ground/Slope/Wall are also derived per contact from the normal. */
@Serializable
enum class SurfaceType { NORMAL_GROUND, SLOPE, WALL, PLATFORM, BOUNCE, MOVING_PLATFORM, HAZARD, SPECIAL, GOAL }

@Serializable
enum class SurfaceEffect { NONE, ICE, STICKY, SPEED_PAD }

/**
 * One material. Everything gameplay-relevant about a surface is data: friction, bounce, hazard flag,
 * launch-speed multiplier, special effect and the ids of its sound / particle / haptic feedback.
 * Evidence grade of all default numbers: D (see PHYSICS_MASTER.md).
 */
@Serializable
data class SurfaceDef(
  val id: String,
  val type: SurfaceType = SurfaceType.NORMAL_GROUND,
  val friction: Double = 0.9,
  val bounce: Double = 0.0,
  /** A touch sends the player back to the last checkpoint. */
  val hazard: Boolean = false,
  /** Multiplies the launch speed of a jump that starts on this surface. */
  val velocityMultiplier: Double = 1.0,
  /** Bounce pads: a foot touching this surface is thrown along the normal with at least this speed (m/s); 0 = off. */
  val launchSpeed: Double = 0.0,
  val effect: SurfaceEffect = SurfaceEffect.NONE,
  val sound: String = "surface_stone",
  val particle: String = "dust",
  val haptic: String = "landing",
  /** Palette slot used by the renderer ("ground", "platform", "wall", "bounce", "hazard", "special", "goal", "moving"). */
  val colorRole: String = "ground",
)

@Serializable
data class SurfaceFile(val surfaces: List<SurfaceDef>)

class SurfaceCatalog(list: List<SurfaceDef>) {
  private val map: Map<String, SurfaceDef> = list.associateBy { it.id }
  val all: List<SurfaceDef> = list

  operator fun get(id: String): SurfaceDef = map[id] ?: error("Unknown surface id '$id'. Known: ${map.keys.sorted()}")
  fun find(id: String): SurfaceDef? = map[id]

  companion object {
    const val RESOURCE_PATH = "data/surfaces.json"
    fun load(): SurfaceCatalog =
      SurfaceCatalog(GameJson.pretty.decodeFromString(SurfaceFile.serializer(), Resources.readText(RESOURCE_PATH)).surfaces)
  }
}
