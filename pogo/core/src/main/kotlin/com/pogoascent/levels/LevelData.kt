package com.pogoascent.levels

import com.pogoascent.core.GameJson
import kotlinx.serialization.Serializable

/** Sinusoidal kinematic motion of a solid (see [com.pogoascent.physics.Mover]). */
@Serializable
data class MoverDef(val ax: Double = 0.0, val ay: Double = 0.0, val period: Double = 4.0, val phase: Double = 0.0)

/**
 * One solid piece of level geometry. `kind = "box"` uses x/y (centre), w/h and rot (degrees);
 * `kind = "poly"` uses [points] (absolute, convex, any winding) for ramps/wedges.
 */
@Serializable
data class SolidDef(
  val id: String,
  val kind: String = "box",
  val x: Double = 0.0,
  val y: Double = 0.0,
  val w: Double = 1.0,
  val h: Double = 1.0,
  val rot: Double = 0.0,
  val points: List<List<Double>> = emptyList(),
  /** Surface id from surfaces.json. If empty, the list the solid sits in decides (platforms→stone, hazards→hazard…). */
  val surface: String = "",
  val oneWay: Boolean = false,
  val mover: MoverDef? = null,
)

@Serializable
data class PointDef(val x: Double = 0.0, val y: Double = 0.0)

@Serializable
data class CheckpointDef(val id: String, val x: Double, val y: Double, val w: Double = 3.0, val h: Double = 3.0, val respawnX: Double = x, val respawnY: Double = y)

@Serializable
data class DecorDef(
  val kind: String = "box", // box | pillar | cloud | tree | crystal
  val x: Double = 0.0,
  val y: Double = 0.0,
  val z: Double = -6.0,
  val w: Double = 2.0,
  val h: Double = 2.0,
  val role: String = "accent",
)

/** [y] is the TOP SURFACE of the goal platform; the trigger zone extends [h] metres above it. */
@Serializable
data class GoalDef(val x: Double, val y: Double, val w: Double = 6.0, val h: Double = 0.8)

@Serializable
data class LevelRules(
  /** Height lost below the best point since the last checkpoint that counts as one "fall". */
  val fallLossHeight: Double = 8.0,
  /** Seconds airborne and motionless before the level resets the rider to the last checkpoint. */
  val stuckSeconds: Double = 3.0,
)

/** A level, completely described by data. Edit the JSON – no code changes. */
@Serializable
data class LevelData(
  val id: String,
  val name: String = "",
  val world: String = "world_1",
  val index: Int = 1,
  val theme: String = "meadow",
  /** 1 (easy) … 10 (hard). Used for UI and level ordering checks. */
  val difficulty: Int = 1,
  val parTimeSec: Double = 90.0,
  val start: PointDef = PointDef(),
  val goal: GoalDef,
  val killY: Double = -12.0,
  val platforms: List<SolidDef> = emptyList(),
  val obstacles: List<SolidDef> = emptyList(),
  val hazards: List<SolidDef> = emptyList(),
  val movers: List<SolidDef> = emptyList(),
  val bounceObjects: List<SolidDef> = emptyList(),
  val checkpoints: List<CheckpointDef> = emptyList(),
  val decor: List<DecorDef> = emptyList(),
  val rules: LevelRules = LevelRules(),
  /** Free-text design notes (what the level teaches). */
  val notes: String = "",
) {
  /** All solids with their default surface resolved. */
  fun allSolids(): List<SolidDef> {
    fun fill(list: List<SolidDef>, def: String) = list.map { if (it.surface.isEmpty()) it.copy(surface = def) else it }
    return fill(platforms, "stone") + fill(obstacles, "wall") + fill(hazards, "hazard") + fill(movers, "moving") + fill(bounceObjects, "bounce")
  }

  companion object {
    fun fromJson(text: String): LevelData = GameJson.pretty.decodeFromString(serializer(), text)
    fun toJson(level: LevelData): String = GameJson.pretty.encodeToString(serializer(), level)
  }
}
