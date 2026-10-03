package com.pogoascent

import com.pogoascent.customization.ItemCatalog
import com.pogoascent.customization.ItemCategory
import com.pogoascent.customization.UnlockResult
import com.pogoascent.customization.Wardrobe
import com.pogoascent.levels.GameSession
import com.pogoascent.levels.LevelLoader
import com.pogoascent.levels.LevelResult
import com.pogoascent.levels.Progression
import com.pogoascent.levels.WorldCatalog
import com.pogoascent.physics.PhysicsConfig
import com.pogoascent.player.PlayerInput
import com.pogoascent.render.HatKind
import com.pogoascent.save.MemorySaveStorage
import com.pogoascent.save.SaveManager
import com.pogoascent.settings.ControlLayout
import com.pogoascent.settings.ControlSettings
import com.pogoascent.settings.GameplaySettings
import com.pogoascent.touch.TouchControlMapper
import com.pogoascent.ui.Format
import com.pogoascent.ui.HudModel
import com.pogoascent.ui.MenuModel
import com.pogoascent.ui.Navigator
import com.pogoascent.ui.ScreenId
import com.pogoascent.ui.UiStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchTest {
  private fun mapper(layout: ControlLayout = ControlLayout.SLIDER, sens: Double = 1.0, dz: Double = 0.08, left: Boolean = false): TouchControlMapper {
    val m = TouchControlMapper(ControlSettings(layout = layout, sensitivity = sens, deadzone = dz, leftHanded = left))
    m.resize(2000, 1000)
    return m
  }
  private fun read(m: TouchControlMapper): PlayerInput = PlayerInput().also { m.read(it) }

  @Test fun noTouchMeansNoInput() {
    val i = read(mapper())
    assertEquals(0.0, i.lean, 0.0); assertFalse(i.jumpHeld)
  }

  @Test fun dragRightLeansRightAndReachesFullLean() {
    val m = mapper()
    m.pointerDown(1, 400f, 500f)
    m.pointerMove(1, 400f + 100f, 500f)
    val half = read(m).lean
    assertTrue(half > 0.0 && half < 1.0)
    m.pointerMove(1, 400f + 5000f, 500f)
    assertEquals(1.0, read(m).lean, 1e-9)
    m.pointerMove(1, 400f - 5000f, 500f)
    assertEquals(-1.0, read(m).lean, 1e-9)
    m.pointerUp(1)
    assertEquals(0.0, read(m).lean, 0.0)
  }

  @Test fun deadzoneIgnoresTinyMovementButStaysContinuous() {
    val m = mapper(dz = 0.2)
    m.pointerDown(1, 400f, 500f)
    val travel = 2000f * 0.16f
    m.pointerMove(1, 400f + travel * 0.15f, 500f)
    assertEquals(0.0, read(m).lean, 0.0)
    m.pointerMove(1, 400f + travel * 0.2001f, 500f)
    assertTrue(read(m).lean < 0.01)
    m.pointerMove(1, 400f + travel, 500f)
    assertEquals(1.0, read(m).lean, 1e-6)
  }

  @Test fun sensitivityShortensTheTravel() {
    val low = mapper(sens = 0.5); val high = mapper(sens = 2.0)
    for (m in listOf(low, high)) { m.pointerDown(1, 400f, 500f); m.pointerMove(1, 520f, 500f) }
    assertTrue(read(high).lean > read(low).lean)
  }

  @Test fun jumpButtonIsHeldWhileTouchedAndIndependentOfLean() {
    val m = mapper()
    m.pointerDown(1, 400f, 500f) // lean thumb (left)
    m.pointerDown(2, m.jumpButton.cx, m.jumpButton.cy)
    m.pointerMove(1, 500f, 500f)
    var i = read(m)
    assertTrue(i.jumpHeld && i.lean > 0.0)
    m.pointerUp(2)
    i = read(m)
    assertFalse(i.jumpHeld); assertTrue(i.lean > 0.0)
  }

  @Test fun jumpButtonHasAGenerousHitArea() {
    val m = mapper()
    m.pointerDown(1, m.jumpButton.cx - m.jumpButton.radius * 1.2f, m.jumpButton.cy)
    assertTrue(read(m).jumpHeld)
  }

  @Test fun leftHandedMirrorsTheLayout() {
    val m = mapper(left = true)
    assertTrue(m.jumpButton.cx < 1000f)
    m.pointerDown(1, 1600f, 500f) // lean zone is on the right now
    m.pointerMove(1, 1700f, 500f)
    assertTrue(read(m).lean > 0.0)
  }

  @Test fun swipeLayoutUsesTheRightHalfAsTheJumpAnywhere() {
    val m = mapper(ControlLayout.SWIPE)
    m.pointerDown(1, 1500f, 200f)
    assertTrue(read(m).jumpHeld)
    m.pointerDown(2, 300f, 700f); m.pointerMove(2, 450f, 700f)
    assertTrue(read(m).lean > 0.0)
  }

  @Test fun fullscreenLayoutFirstFingerLeansSecondJumps() {
    val m = mapper(ControlLayout.FULLSCREEN)
    m.pointerDown(1, 1500f, 200f); m.pointerMove(1, 1600f, 200f)
    assertTrue(read(m).lean > 0.0); assertFalse(read(m).jumpHeld)
    m.pointerDown(2, 300f, 700f)
    assertTrue(read(m).jumpHeld)
  }

  @Test fun changingSettingsReleasesAllTouchesAndPressCallbackFiresOncePerPress() {
    val m = mapper()
    var presses = 0
    m.onPress = { presses++ }
    m.pointerDown(1, m.jumpButton.cx, m.jumpButton.cy)
    m.pointerMove(1, m.jumpButton.cx + 5, m.jumpButton.cy)
    assertEquals(1, presses)
    m.apply(ControlSettings(buttonSize = 1.4))
    assertFalse(read(m).jumpHeld)
    assertTrue(m.jumpButton.radius > 0f)
  }
}

class CustomizationTest {
  private val worlds = WorldCatalog.load()
  private fun setup(): Triple<Wardrobe, SaveManager, Progression> {
    val s = SaveManager(MemorySaveStorage())
    val p = Progression(worlds, s)
    return Triple(Wardrobe(ItemCatalog.load(), s, worlds, p), s, p)
  }

  @Test fun everyCategoryHasADefaultAndAllRequiredFieldsAreFilled() {
    val c = ItemCatalog.load()
    for (cat in ItemCategory.values()) assertTrue(c.inCategory(cat).isNotEmpty())
    for (i in c.items) {
      assertTrue(i.id.isNotBlank() && i.prefab.isNotBlank() && i.icon.isNotBlank() && i.colors.isNotEmpty())
      assertTrue("unlock rule of ${i.id}", i.unlock == "default" || i.unlock == "coins" || i.unlock.startsWith("level:") || i.unlock.startsWith("world:"))
      if (i.unlock == "coins") assertTrue(i.price > 0)
    }
    for (i in c.items) if (i.unlock.startsWith("level:")) assertTrue(worlds.levelOrder.contains(i.unlock.removePrefix("level:")))
    for (i in c.items) if (i.unlock.startsWith("world:")) assertTrue(worlds.find(i.unlock.removePrefix("world:")) != null)
  }

  @Test fun purchaseSpendsCoinsOnceAndEquipChangesTheStyle() {
    val (w, s, _) = setup()
    assertEquals(UnlockResult.NOT_ENOUGH_COINS, w.purchase("hat_tophat"))
    s.update { it.copy(coins = 100) }
    assertEquals(UnlockResult.UNLOCKED, w.purchase("hat_tophat"))
    assertEquals(40, s.data.coins)
    assertEquals(UnlockResult.ALREADY_OWNED, w.purchase("hat_tophat"))
    assertEquals(HatKind.CAP, w.style().hat)
    assertTrue(w.equip("hat_tophat"))
    assertEquals(HatKind.TOPHAT, w.style().hat)
    assertFalse("cannot equip what you do not own", w.equip("hat_crown"))
    assertEquals(UnlockResult.LOCKED_BY_PROGRESS, w.purchase("stick_neon"))
    assertEquals(UnlockResult.UNKNOWN_ITEM, w.purchase("zzz"))
  }

  @Test fun progressUnlocksAreGrantedWhenTheConditionIsMet() {
    val (w, _, p) = setup()
    assertFalse(w.isUnlocked("hat_helmet"))
    p.recordCompletion(LevelResult("level_01", 50.0, 20, 0, 0, 0, 50.0), 1, 100.0, 1)
    val granted = w.grantProgressUnlocks()
    assertTrue("hat_helmet" in granted)
    assertTrue(w.isUnlocked("hat_helmet"))
    assertTrue(w.grantProgressUnlocks().isEmpty())
  }

  @Test fun equippedStateSurvivesASaveRoundTrip() {
    val storage = MemorySaveStorage()
    val s = SaveManager(storage); val p = Progression(worlds, s); val w = Wardrobe(ItemCatalog.load(), s, worlds, p)
    s.update { it.copy(coins = 500) }
    w.purchase("clothes_purple"); w.equip("clothes_purple")
    val s2 = SaveManager(storage); s2.load()
    val w2 = Wardrobe(ItemCatalog.load(), s2, worlds, Progression(worlds, s2))
    assertEquals("clothes_purple", w2.equipped(ItemCategory.CLOTHES).id)
  }
}

class UiTest {
  @Test fun backNavigationFollowsTheGameFlow() {
    val n = Navigator()
    assertFalse("back on the main menu leaves the app", n.back())
    n.push(ScreenId.WORLD_SELECT); n.push(ScreenId.LEVEL_SELECT)
    n.startPlaying()
    assertEquals(ScreenId.PLAYING, n.current)
    assertTrue(n.back()); assertEquals(ScreenId.PAUSE, n.current); assertTrue(n.isOverlay)
    assertTrue(n.back()); assertEquals(ScreenId.PLAYING, n.current)
    n.back()
    n.push(ScreenId.SETTINGS)
    assertTrue(n.back()); assertEquals(ScreenId.PAUSE, n.current)
    n.home(); assertEquals(ScreenId.MAIN_MENU, n.current); assertEquals(1, n.depth)
  }

  @Test fun restartingALevelDoesNotGrowTheStack() {
    val n = Navigator()
    n.push(ScreenId.LEVEL_SELECT)
    repeat(5) { n.startPlaying(); n.back() }
    n.startPlaying()
    assertEquals(3, n.depth)
  }

  @Test fun timeFormattingMatchesTheHud() {
    assertEquals("0:00.0", Format.time(0.0))
    assertEquals("1:23.4", Format.time(83.4))
    assertEquals("10:05.9", Format.time(605.99))
    assertEquals("0:00.0", Format.time(Double.NaN))
    assertEquals("--:--", Format.timeMs(0))
    assertEquals("45%", Format.percent(0.456))
  }

  @Test fun hudCanBeHiddenAndRespectsItsToggles() {
    val level = LevelLoader.load("level_01", surfaces)
    val session = GameSession(level, PhysicsConfig())
    var settings = GameplaySettings()
    val hud = HudModel { settings }
    hud.hint = "hint"
    assertTrue(hud.state(session).visible)
    assertEquals("0:00.0", hud.state(session).timer)
    settings = GameplaySettings(showHud = false, showTimer = false, showFps = true, tutorialHints = false)
    val s = hud.state(session)
    assertFalse(s.visible); assertEquals("", s.timer); assertEquals("", s.hint); assertTrue(s.fps.endsWith("FPS"))
  }

  @Test fun englishAndArabicStringsStayInSync() {
    assertEquals(emptySet<String>(), UiStrings.missingArabic())
    assertEquals(emptySet<String>(), UiStrings.unknownArabic())
    assertEquals("Play", UiStrings.get("play", "en")); assertEquals("العب", UiStrings.get("play", "ar"))
    assertEquals("missing_key", UiStrings.get("missing_key", "ar"))
  }

  @Test fun menuModelReflectsLocksAndBestTimes() {
    val worlds = WorldCatalog.load()
    val s = SaveManager(MemorySaveStorage()); val p = Progression(worlds, s)
    val menu = MenuModel(worlds, p, s) { LevelLoader.loadData(it) }
    val rows0 = menu.levelRows("world_1")
    assertTrue(rows0[0].unlocked); assertFalse(rows0[1].unlocked); assertEquals("--:--", rows0[0].bestTime)
    p.recordCompletion(LevelResult("level_01", 61.2, 20, 0, 0, 0, 1.0), 1, 120.0, 5)
    val rows1 = menu.levelRows("world_1")
    assertTrue(rows1[1].unlocked); assertEquals("1:01.2", rows1[0].bestTime); assertTrue(rows1[0].completed)
    assertEquals(1, menu.leaderboard("level_01").size)
    val wr = menu.worldRows()
    assertTrue(wr[0].unlocked); assertFalse(wr[1].unlocked); assertTrue(wr[1].requirementText.isNotEmpty())
  }
}
