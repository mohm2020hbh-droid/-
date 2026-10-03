package com.pogoascent.save

import com.pogoascent.settings.GameSettings
import kotlinx.serialization.Serializable

@Serializable
data class LevelProgress(
  val completed: Boolean = false,
  /** Best (lowest) completion time in milliseconds; 0 = none yet. */
  val bestTimeMs: Long = 0,
  /** Fewest jumps used in a completed run; 0 = none yet. */
  val bestJumps: Int = 0,
  /** Most boost jumps landed in a single completed run. */
  val mostBoosts: Int = 0,
  val bestProgress: Double = 0.0,
  val attempts: Int = 0,
  val completions: Int = 0,
)

@Serializable
data class PlayerStats(
  val totalJumps: Long = 0,
  val totalBoosts: Long = 0,
  val totalFalls: Long = 0,
  val totalResets: Long = 0,
  val playTimeSec: Double = 0.0,
)

@Serializable
data class LeaderboardEntry(val timeMs: Long, val jumps: Int, val boosts: Int, val epochDay: Long)

/** Everything that survives closing the app. Bump [CURRENT_VERSION] and extend [SaveMigrator] when the shape changes. */
@Serializable
data class SaveData(
  val version: Int = CURRENT_VERSION,
  val levels: Map<String, LevelProgress> = emptyMap(),
  val coins: Int = 0,
  val unlockedItems: Set<String> = emptySet(),
  /** category id → equipped item id. */
  val equipped: Map<String, String> = emptyMap(),
  val settings: GameSettings = GameSettings(),
  val stats: PlayerStats = PlayerStats(),
  /** levelId → top-10 local times (ascending). */
  val leaderboard: Map<String, List<LeaderboardEntry>> = emptyMap(),
  val tutorialSeen: Boolean = false,
  val lastLevelId: String? = null,
) {
  companion object { const val CURRENT_VERSION = 1 }
}
