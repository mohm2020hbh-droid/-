package com.pogoascent.ui

import com.pogoascent.levels.GameSession
import com.pogoascent.levels.LevelData
import com.pogoascent.levels.Progression
import com.pogoascent.levels.WorldCatalog
import com.pogoascent.levels.WorldDef
import com.pogoascent.save.LeaderboardEntry
import com.pogoascent.save.SaveManager
import com.pogoascent.settings.GameplaySettings

/** What the in-game HUD shows this frame. Hidden elements are empty strings so the view can just set text. */
class HudState(
  val visible: Boolean,
  val progress: Double,
  val progressText: String,
  val timer: String,
  val jumps: String,
  val boosts: String,
  val fps: String,
  val hint: String,
  val charge: Double,
  val boostReady: Boolean,
)

class HudModel(private val settings: () -> GameplaySettings) {
  private var fpsAvg = 60.0
  var hint = ""

  fun onFrame(frameDt: Double) {
    if (frameDt > 0) fpsAvg += (1.0 / frameDt - fpsAvg) * 0.05
  }

  fun state(session: GameSession): HudState {
    val s = settings()
    val p = session.progress.coerceIn(0.0, 1.0)
    return HudState(
      visible = s.showHud,
      progress = maxOf(p, 0.0),
      progressText = Format.percent(session.bestProgress),
      timer = if (s.showTimer) Format.time(session.timeSec) else "",
      jumps = session.player.jumpCount.toString(),
      boosts = session.player.boostCount.toString(),
      fps = if (s.showFps) "%.0f FPS".format(fpsAvg) else "",
      hint = if (s.tutorialHints) hint else "",
      charge = session.player.charge,
      boostReady = session.player.boostArmed,
    )
  }
}

class WorldRow(val world: WorldDef, val unlocked: Boolean, val completed: Int, val total: Int, val requirementText: String)

class LevelRow(
  val levelId: String,
  val name: String,
  val difficulty: Int,
  val unlocked: Boolean,
  val completed: Boolean,
  val bestTime: String,
  val bestJumps: String,
  val parTime: String,
)

/** Builds the list screens from catalog + save state. */
class MenuModel(
  private val worlds: WorldCatalog,
  private val progression: Progression,
  private val saves: SaveManager,
  private val levelLoader: (String) -> LevelData,
) {
  fun worldRows(): List<WorldRow> = worlds.worlds.map { w ->
    val done = w.levels.count { progression.isCompleted(it) }
    val unlocked = progression.isWorldUnlocked(w)
    WorldRow(w, unlocked, done, w.levels.size, if (unlocked) "" else "Complete ${w.unlockLevelsRequired} levels to unlock")
  }

  fun levelRows(worldId: String): List<LevelRow> {
    val w = worlds[worldId]
    return w.levels.map { id ->
      val d = levelLoader(id)
      val prog = saves.data.levels[id]
      LevelRow(
        id, d.name.ifBlank { id }, d.difficulty, progression.isLevelUnlocked(id), prog?.completed == true,
        Format.timeMs(prog?.bestTimeMs ?: 0), if ((prog?.bestJumps ?: 0) > 0) prog!!.bestJumps.toString() else "--", Format.time(d.parTimeSec),
      )
    }
  }

  /** Local top-10 for a level (Leaderboard screen). Online ranking needs a backend and is not implemented (DECISIONS D-010). */
  fun leaderboard(levelId: String): List<LeaderboardEntry> = saves.data.leaderboard[levelId] ?: emptyList()
}
