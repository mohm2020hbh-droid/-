package com.pogoascent

import com.pogoascent.levels.LevelResult
import com.pogoascent.levels.Progression
import com.pogoascent.levels.WorldCatalog
import com.pogoascent.save.FileSaveStorage
import com.pogoascent.save.LevelProgress
import com.pogoascent.save.LoadOutcome
import com.pogoascent.save.MemorySaveStorage
import com.pogoascent.save.SaveData
import com.pogoascent.save.SaveManager
import com.pogoascent.settings.AudioSettings
import com.pogoascent.settings.ControlLayout
import com.pogoascent.settings.GameSettings
import com.pogoascent.settings.Quality
import com.pogoascent.settings.VideoSettings
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveTest {
  private fun tmpDir(): File = java.nio.file.Files.createTempDirectory("pogo-save").toFile().also { it.deleteOnExit() }

  @Test fun roundTripKeepsEverythingTheTaskListsForSaving() {
    val storage = MemorySaveStorage()
    val a = SaveManager(storage)
    a.update {
      it.copy(
        levels = mapOf("level_01" to LevelProgress(completed = true, bestTimeMs = 41_234, bestJumps = 19, mostBoosts = 2, bestProgress = 1.0, attempts = 3)),
        coins = 120, unlockedItems = setOf("hat_tophat"), equipped = mapOf("HAT" to "hat_tophat"),
        settings = GameSettings(audio = AudioSettings(master = 0.3), video = VideoSettings.forQuality(Quality.LOW)),
        tutorialSeen = true,
      )
    }
    val b = SaveManager(storage)
    assertEquals(LoadOutcome.LOADED, b.load())
    assertEquals(a.data, b.data)
    assertEquals(41_234L, b.data.levels.getValue("level_01").bestTimeMs)
    assertEquals(0.3, b.data.settings.audio.master, 0.0)
    assertEquals(Quality.LOW, b.data.settings.video.quality)
    assertEquals(ControlLayout.SLIDER, b.data.settings.controls.layout)
  }

  @Test fun freshInstallStartsWithDefaults() {
    val m = SaveManager(MemorySaveStorage())
    assertEquals(LoadOutcome.FRESH, m.load())
    assertEquals(SaveData(), m.data)
  }

  @Test fun corruptMainFileRecoversFromTheBackup() {
    val s = MemorySaveStorage()
    val a = SaveManager(s)
    a.update { it.copy(coins = 5) }
    a.update { it.copy(coins = 9) } // second write rotates the first into the backup
    s.main = "{ this is not json"
    val b = SaveManager(s)
    assertEquals(LoadOutcome.RECOVERED_FROM_BACKUP, b.load())
    assertEquals(5, b.data.coins)
    assertNotNull(b.corruptText)
  }

  @Test fun bothFilesCorruptFallsBackToAFreshSaveWithoutCrashing() {
    val s = MemorySaveStorage(); s.main = "garbage"; s.backup = "{{{"
    val m = SaveManager(s)
    assertEquals(LoadOutcome.CORRUPT_RESET, m.load())
    assertEquals(SaveData(), m.data)
  }

  @Test fun savesWrittenBeforeVersioningAreMigrated() {
    val s = MemorySaveStorage()
    s.main = """{"coins": 77, "levels": {"level_01": {"completed": true}}}"""
    val m = SaveManager(s)
    assertEquals(LoadOutcome.LOADED, m.load())
    assertEquals(77, m.data.coins)
    assertEquals(SaveData.CURRENT_VERSION, m.data.version)
    assertTrue(m.data.levels.getValue("level_01").completed)
  }

  @Test fun unknownFieldsFromNewerVersionsAreIgnored() {
    val s = MemorySaveStorage(); s.main = """{"version":1,"coins":3,"someFutureField":{"x":1}}"""
    val m = SaveManager(s); m.load()
    assertEquals(3, m.data.coins)
  }

  @Test fun outOfRangeSettingsInTheFileAreClamped() {
    val s = MemorySaveStorage(); s.main = """{"version":1,"settings":{"audio":{"master":9.0},"controls":{"sensitivity":-4,"deadzone":3}}}"""
    val m = SaveManager(s); m.load()
    assertEquals(1.0, m.data.settings.audio.master, 0.0)
    assertEquals(0.5, m.data.settings.controls.sensitivity, 0.0)
    assertEquals(0.4, m.data.settings.controls.deadzone, 0.0)
  }

  @Test fun fileStorageSurvivesRestartAndKeepsABackup() {
    val dir = tmpDir()
    val f = File(dir, "save.json")
    val a = SaveManager(FileSaveStorage(f))
    a.update { it.copy(coins = 1) }
    a.update { it.copy(coins = 2) }
    assertTrue(File(dir, "save.json.bak").exists())
    val b = SaveManager(FileSaveStorage(f))
    assertEquals(LoadOutcome.LOADED, b.load())
    assertEquals(2, b.data.coins)
    assertFalse("no temp file left behind", File(dir, "save.json.tmp").exists())
  }

  @Test fun aCrashAfterWritingTheTempFileNeverCorruptsTheSave() {
    val dir = tmpDir()
    val f = File(dir, "save.json")
    val a = SaveManager(FileSaveStorage(f))
    a.update { it.copy(coins = 42) }
    File(dir, "save.json.tmp").writeText("{ half written")
    val b = SaveManager(FileSaveStorage(f))
    assertEquals(LoadOutcome.LOADED, b.load())
    assertEquals(42, b.data.coins)
  }

  @Test fun resetDeletesEverything() {
    val dir = tmpDir()
    val m = SaveManager(FileSaveStorage(File(dir, "save.json")))
    m.update { it.copy(coins = 10) }
    m.reset()
    assertEquals(SaveData(), m.data)
    assertEquals(LoadOutcome.FRESH, SaveManager(FileSaveStorage(File(dir, "save.json"))).load())
  }
}

class ProgressionTest {
  private val worlds = WorldCatalog.load()
  private fun setup(): Pair<Progression, SaveManager> { val s = SaveManager(MemorySaveStorage()); return Progression(worlds, s) to s }
  private fun result(id: String, time: Double = 50.0, jumps: Int = 20, boosts: Int = 0, falls: Int = 0) = LevelResult(id, time, jumps, boosts, falls, 0, 50.0)

  @Test fun onlyTheFirstLevelIsUnlockedAtStart() {
    val (p, _) = setup()
    val order = worlds.levelOrder
    assertTrue(p.isLevelUnlocked(order[0]))
    assertFalse(p.isLevelUnlocked(order[1]))
    assertFalse(p.isWorldUnlocked(worlds.worlds[1]))
    assertEquals(order[0], p.continueLevel())
  }

  @Test fun completingALevelUnlocksTheNextAndRecordsBests() {
    val (p, s) = setup()
    val sum = p.recordCompletion(result("level_01", 61.5, 22), 1, 120.0, 20000)
    assertTrue(sum.firstCompletion && sum.newBestTime && sum.newBestJumps)
    assertEquals("level_02", sum.unlockedLevelId)
    assertTrue(p.isLevelUnlocked("level_02"))
    assertEquals(61_500L, s.data.levels.getValue("level_01").bestTimeMs)
    // a slower, longer second run changes neither best
    val again = p.recordCompletion(result("level_01", 80.0, 30), 1, 120.0, 20001)
    assertFalse(again.newBestTime); assertFalse(again.newBestJumps); assertFalse(again.firstCompletion)
    assertEquals(61_500L, s.data.levels.getValue("level_01").bestTimeMs)
    assertEquals(22, s.data.levels.getValue("level_01").bestJumps)
    assertEquals(2, s.data.levels.getValue("level_01").completions)
    // a faster run improves it
    assertTrue(p.recordCompletion(result("level_01", 40.0, 18, boosts = 2), 1, 120.0, 20002).newBestTime)
    assertEquals(2, s.data.levels.getValue("level_01").mostBoosts)
  }

  @Test fun coinsAreAwardedAndFirstCompletionDoublesThem() {
    val (p, s) = setup()
    val first = p.recordCompletion(result("level_01", 100.0, 20, falls = 0), 2, 150.0, 1)
    // base 20, par bonus +10, no-fall +5 = 35, first completion x2 = 70
    assertEquals(70, first.coinsEarned)
    assertEquals(70, s.data.coins)
    val second = p.recordCompletion(result("level_01", 200.0, 20, falls = 3), 2, 150.0, 2)
    assertEquals(20, second.coinsEarned)
    assertEquals(90, s.data.coins)
  }

  @Test fun leaderboardKeepsTheTenBestTimesSorted() {
    val (p, s) = setup()
    for (i in 0 until 14) p.recordCompletion(result("level_01", 90.0 - i * 2 + (if (i % 3 == 0) 40 else 0)), 1, 120.0, i.toLong())
    val board = s.data.leaderboard.getValue("level_01")
    assertEquals(10, board.size)
    assertEquals(board.sortedBy { it.timeMs }, board)
  }

  @Test fun worldsUnlockWhenEnoughLevelsOfEarlierWorldsAreDone() {
    val (p, _) = setup()
    val w2 = worlds.worlds[1]
    val need = w2.unlockLevelsRequired
    var unlockedWorld: String? = null
    for (id in worlds.levelOrder.take(need)) {
      val sum = p.recordCompletion(result(id), 1, 100.0, 1)
      if (sum.unlockedWorldId != null) unlockedWorld = sum.unlockedWorldId
    }
    assertTrue(p.isWorldUnlocked(w2))
    assertEquals(w2.id, unlockedWorld)
    assertTrue(p.isLevelUnlocked(w2.levels.first()))
  }

  @Test fun abandonedRunsKeepPartialProgressAndStats() {
    val (p, s) = setup()
    p.recordAttempt("level_01", 0.6, 12, 1, 2, 0, 33.0)
    p.recordAttempt("level_01", 0.4, 5, 0, 0, 1, 10.0)
    val lp = s.data.levels.getValue("level_01")
    assertEquals(0.6, lp.bestProgress, 1e-9)
    assertEquals(2, lp.attempts)
    assertFalse(lp.completed)
    assertEquals(17L, s.data.stats.totalJumps)
    assertEquals(43.0, s.data.stats.playTimeSec, 1e-9)
  }
}
