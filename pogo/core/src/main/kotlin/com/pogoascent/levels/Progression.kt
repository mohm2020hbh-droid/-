package com.pogoascent.levels

import com.pogoascent.save.LeaderboardEntry
import com.pogoascent.save.LevelProgress
import com.pogoascent.save.SaveManager
import kotlin.math.roundToInt

/** What the Level Complete screen shows. */
class ResultSummary(
  val result: LevelResult,
  val newBestTime: Boolean,
  val newBestJumps: Boolean,
  val firstCompletion: Boolean,
  val coinsEarned: Int,
  val leaderboardRank: Int,
  val unlockedLevelId: String?,
  val unlockedWorldId: String?,
)

/** Worlds/levels unlock rules, rewards and recording of results into the save file. */
class Progression(private val worlds: WorldCatalog, private val saves: SaveManager) {
  private fun progressOf(levelId: String): LevelProgress = saves.data.levels[levelId] ?: LevelProgress()

  fun isCompleted(levelId: String): Boolean = progressOf(levelId).completed

  fun completedCount(): Int = worlds.levelOrder.count { isCompleted(it) }

  private fun completedBefore(world: WorldDef): Int =
    worlds.worlds.filter { it.order < world.order }.sumOf { w -> w.levels.count { isCompleted(it) } }

  fun isWorldUnlocked(world: WorldDef): Boolean = completedBefore(world) >= world.unlockLevelsRequired

  /** A level is playable when its world is unlocked and the level before it in the same world is completed. */
  fun isLevelUnlocked(levelId: String): Boolean {
    val w = worlds.worldOfLevel(levelId) ?: return false
    if (!isWorldUnlocked(w)) return false
    val i = w.levels.indexOf(levelId)
    return i == 0 || isCompleted(w.levels[i - 1])
  }

  fun nextLevelAfter(levelId: String): String? {
    val order = worlds.levelOrder
    val i = order.indexOf(levelId)
    return if (i >= 0 && i + 1 < order.size) order[i + 1] else null
  }

  /** First level that is unlocked but not completed (the "Continue" button), else the first level. */
  fun continueLevel(): String = worlds.levelOrder.firstOrNull { isLevelUnlocked(it) && !isCompleted(it) } ?: worlds.levelOrder.first()

  fun coinReward(difficulty: Int, parTimeSec: Double, result: LevelResult, first: Boolean): Int {
    var coins = 10 * difficulty
    if (result.timeSec <= parTimeSec) coins += coins / 2
    if (result.falls == 0) coins += 5
    if (first) coins *= 2
    return coins
  }

  /** Records a finished run: best times, leaderboard, coins, stats. Persists immediately. */
  fun recordCompletion(result: LevelResult, difficulty: Int, parTimeSec: Double, epochDay: Long): ResultSummary {
    val before = progressOf(result.levelId)
    val ms = (result.timeSec * 1000.0).roundToInt().toLong()
    val first = !before.completed
    val newBestTime = before.bestTimeMs == 0L || ms < before.bestTimeMs
    val newBestJumps = before.bestJumps == 0 || result.jumps < before.bestJumps
    val coins = coinReward(difficulty, parTimeSec, result, first)

    val wasWorldUnlocked = worlds.worlds.associate { it.id to isWorldUnlocked(it) }
    val nextId = nextLevelAfter(result.levelId)
    val nextWasUnlocked = nextId?.let { isLevelUnlocked(it) } ?: true

    var rank = 0
    saves.update { d ->
      val entries = (d.leaderboard[result.levelId] ?: emptyList()) + LeaderboardEntry(ms, result.jumps, result.boosts, epochDay)
      val sorted = entries.sortedBy { it.timeMs }.take(10)
      rank = sorted.indexOfFirst { it.timeMs == ms && it.epochDay == epochDay && it.jumps == result.jumps } + 1
      d.copy(
        levels = d.levels + (result.levelId to before.copy(
          completed = true,
          bestTimeMs = if (newBestTime) ms else before.bestTimeMs,
          bestJumps = if (newBestJumps) result.jumps else before.bestJumps,
          mostBoosts = maxOf(before.mostBoosts, result.boosts),
          bestProgress = 1.0,
          attempts = before.attempts + 1,
          completions = before.completions + 1,
        )),
        coins = d.coins + coins,
        leaderboard = d.leaderboard + (result.levelId to sorted),
        lastLevelId = result.levelId,
        stats = d.stats.copy(
          totalJumps = d.stats.totalJumps + result.jumps,
          totalBoosts = d.stats.totalBoosts + result.boosts,
          totalFalls = d.stats.totalFalls + result.falls,
          totalResets = d.stats.totalResets + result.resets,
          playTimeSec = d.stats.playTimeSec + result.timeSec,
        ),
      )
    }

    val unlockedWorld = worlds.worlds.firstOrNull { !wasWorldUnlocked.getValue(it.id) && isWorldUnlocked(it) }?.id
    val unlockedLevel = if (nextId != null && !nextWasUnlocked && isLevelUnlocked(nextId)) nextId else null
    return ResultSummary(result, newBestTime, newBestJumps, first, coins, rank, unlockedLevel, unlockedWorld)
  }

  /** Called when a run is abandoned/failed so attempts and partial progress are kept. */
  fun recordAttempt(levelId: String, bestProgress: Double, jumps: Int, boosts: Int, falls: Int, resets: Int, timeSec: Double) {
    val before = progressOf(levelId)
    saves.update { d ->
      d.copy(
        levels = d.levels + (levelId to before.copy(attempts = before.attempts + 1, bestProgress = maxOf(before.bestProgress, bestProgress))),
        lastLevelId = levelId,
        stats = d.stats.copy(
          totalJumps = d.stats.totalJumps + jumps, totalBoosts = d.stats.totalBoosts + boosts,
          totalFalls = d.stats.totalFalls + falls, totalResets = d.stats.totalResets + resets,
          playTimeSec = d.stats.playTimeSec + timeSec,
        ),
      )
    }
  }
}
