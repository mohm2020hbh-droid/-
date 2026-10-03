package com.pogoascent.levels

import com.pogoascent.core.GameJson
import com.pogoascent.core.Resources
import kotlinx.serialization.Serializable

/** Colours are "#RRGGBB". Roles match [com.pogoascent.physics.SurfaceDef.colorRole]. */
@Serializable
data class PaletteDef(
  val skyTop: String = "#6FB6FF",
  val skyBottom: String = "#D6EEFF",
  val fog: String = "#CFE8FF",
  val ground: String = "#6E8B3D",
  val platform: String = "#A0794A",
  val wall: String = "#7C7F86",
  val bounce: String = "#FF7AB6",
  val hazard: String = "#E2453C",
  val special: String = "#59C9E6",
  val goal: String = "#FFD43B",
  val moving: String = "#8E7CC3",
  val accent: String = "#F4A261",
  val player: String = "#3A86FF",
  val light: String = "#FFFFFF",
)

@Serializable
data class WorldDef(
  val id: String,
  val name: String,
  val order: Int,
  val theme: String,
  val palette: PaletteDef,
  val environment: String,
  val obstacleLanguage: String,
  val musicStyle: String,
  val ambience: String,
  /** What this world teaches, following the intended curve. */
  val difficultyFocus: String,
  /** Difficulty (1..10) range of its levels. */
  val difficultyMin: Int,
  val difficultyMax: Int,
  /** Total stars/levels the player must have completed in the PREVIOUS worlds to unlock this one. */
  val unlockLevelsRequired: Int,
  val levels: List<String>,
)

@Serializable
data class WorldsFile(val worlds: List<WorldDef>)

class WorldCatalog(val worlds: List<WorldDef>) {
  private val byId = worlds.associateBy { it.id }
  operator fun get(id: String): WorldDef = byId[id] ?: error("Unknown world '$id'")
  fun find(id: String): WorldDef? = byId[id]

  fun worldOfLevel(levelId: String): WorldDef? = worlds.firstOrNull { levelId in it.levels }

  /** Flat play order of every level in every world. */
  val levelOrder: List<String> get() = worlds.sortedBy { it.order }.flatMap { it.levels }

  companion object {
    const val RESOURCE_PATH = "data/worlds.json"
    fun load(): WorldCatalog = WorldCatalog(GameJson.pretty.decodeFromString(WorldsFile.serializer(), Resources.readText(RESOURCE_PATH)).worlds.sortedBy { it.order })
  }
}
