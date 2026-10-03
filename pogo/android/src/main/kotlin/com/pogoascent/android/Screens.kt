package com.pogoascent.android

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.pogoascent.app.AppCore
import com.pogoascent.customization.ItemCategory
import com.pogoascent.customization.Rarity
import com.pogoascent.customization.UnlockResult
import com.pogoascent.debug.ScenarioKind
import com.pogoascent.levels.LevelLoader
import com.pogoascent.settings.ControlLayout
import com.pogoascent.settings.Quality
import com.pogoascent.settings.VideoSettings
import com.pogoascent.ui.Format
import com.pogoascent.ui.ScreenId

/** What the screens need from the activity. */
interface ScreenHost {
  fun navigate(to: ScreenId)
  fun back()
  fun startLevel(levelId: String)
  fun resume()
  fun restartLevel()
  fun goHome()
  fun runScenario(kind: ScenarioKind)
  fun resetTest()
  fun refresh()
  fun applyDisplaySettings()
  fun exitApp()
}

/** Builds the view for each [ScreenId]. Pure programmatic views; all text comes from [AppCore.str] (English / Arabic). */
class ScreenFactory(private val ctx: Context, private val core: AppCore, private val host: ScreenHost) {
  private var wardrobeCategory = ItemCategory.HAT
  private var leaderboardLevel: String? = null
  private var confirmReset = false

  fun build(id: ScreenId): View = when (id) {
    ScreenId.MAIN_MENU -> mainMenu()
    ScreenId.WORLD_SELECT -> worldSelect()
    ScreenId.LEVEL_SELECT -> levelSelect()
    ScreenId.PAUSE -> pause()
    ScreenId.LEVEL_COMPLETE -> levelComplete()
    ScreenId.SETTINGS -> settingsHub()
    ScreenId.SETTINGS_AUDIO -> settingsAudio()
    ScreenId.SETTINGS_CONTROLS -> settingsControls()
    ScreenId.SETTINGS_GRAPHICS -> settingsGraphics()
    ScreenId.SETTINGS_GAMEPLAY -> settingsGameplay()
    ScreenId.WARDROBE -> wardrobe()
    ScreenId.LEADERBOARD -> leaderboard()
    ScreenId.HOW_TO_PLAY -> howToPlay()
    ScreenId.CREDITS -> credits()
    ScreenId.PHYSICS_TEST -> physicsTest()
    ScreenId.PLAYING -> View(ctx)
  }

  private fun s(key: String) = core.str(key)

  private fun wrap(content: LinearLayout, widthDp: Int = 380): View {
    val maxH = (ctx.resources.displayMetrics.heightPixels * 0.92f).toInt()
    content.setPadding(Ui.dp(ctx, 18), Ui.dp(ctx, 18), Ui.dp(ctx, 18), Ui.dp(ctx, 18))
    val scroll = Ui.scroll(ctx, content, maxH)
    val panel = FrameLayout(ctx).apply {
      background = Ui.rounded(Ui.PANEL, 20f, ctx)
      addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    return FrameLayout(ctx).apply {
      addView(panel, FrameLayout.LayoutParams(Ui.dp(ctx, widthDp), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }
  }

  private fun column(): LinearLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

  private fun click(action: () -> Unit): () -> Unit = { core.uiClick(); action() }
  private fun back(): () -> Unit = { core.uiBack(); host.back() }

  // ---- main menu ------------------------------------------------------------------------------

  private fun mainMenu(): View {
    val col = column()
    col.addView(Ui.title(ctx, s("app_name"), 34f))
    val cont = core.continueLevelId()
    val contName = runCatching { LevelLoader.loadData(cont).name }.getOrDefault(cont)
    col.addView(Ui.button(ctx, "${s(if (core.save.data.levels.isEmpty()) "play" else "continue")}: $contName", accent = true, onClick = click { host.startLevel(cont) }))
    col.addView(Ui.button(ctx, s("worlds"), onClick = click { host.navigate(ScreenId.WORLD_SELECT) }))
    col.addView(Ui.button(ctx, s("wardrobe"), onClick = click { host.navigate(ScreenId.WARDROBE) }))
    col.addView(Ui.button(ctx, s("leaderboard"), onClick = click { host.navigate(ScreenId.LEADERBOARD) }))
    col.addView(Ui.button(ctx, s("settings"), onClick = click { host.navigate(ScreenId.SETTINGS) }))
    col.addView(Ui.button(ctx, s("how_to_play"), onClick = click { host.navigate(ScreenId.HOW_TO_PLAY) }))
    col.addView(Ui.button(ctx, s("credits"), onClick = click { host.navigate(ScreenId.CREDITS) }))
    col.addView(Ui.button(ctx, s("physics_test"), onClick = click { host.navigate(ScreenId.PHYSICS_TEST) }))
    col.addView(Ui.button(ctx, s("quit"), onClick = { host.exitApp() }))
    return wrap(col, 360)
  }

  // ---- worlds / levels ------------------------------------------------------------------------

  private fun worldSelect(): View {
    val col = column()
    col.addView(Ui.title(ctx, s("worlds")))
    for ((i, row) in core.menu.worldRows().withIndex()) {
      val label = "${i + 1}. ${row.world.name}   ${row.completed}/${row.total}" + if (!row.unlocked) "\n${s("locked")}: ${row.requirementText}" else ""
      col.addView(Ui.button(ctx, label, enabled = row.unlocked, onClick = click { core.selectedWorldId = row.world.id; host.navigate(ScreenId.LEVEL_SELECT) }))
      col.addView(Ui.label(ctx, row.world.difficultyFocus, 13f, Ui.TEXT_DIM).apply { setPadding(Ui.dp(ctx, 8), 0, 0, Ui.dp(ctx, 8)) })
    }
    col.addView(Ui.button(ctx, s("back"), onClick = back()))
    return wrap(col, 420)
  }

  private fun levelSelect(): View {
    val col = column()
    val world = core.worlds[core.selectedWorldId]
    col.addView(Ui.title(ctx, world.name))
    for ((i, row) in core.menu.levelRows(world.id).withIndex()) {
      val stars = "★".repeat(row.difficulty.coerceIn(1, 10) / 2 + 1)
      val info = if (row.completed) "${s("best")} ${row.bestTime}  •  ${row.bestJumps} ${s("jumps")}" else if (row.unlocked) "par ${row.parTime}" else s("locked")
      col.addView(Ui.button(ctx, "${i + 1}. ${row.name}   $stars\n$info", enabled = row.unlocked, accent = row.unlocked && !row.completed, onClick = click { host.startLevel(row.levelId) }))
    }
    col.addView(Ui.button(ctx, s("back"), onClick = back()))
    return wrap(col, 420)
  }

  // ---- in-game overlays -----------------------------------------------------------------------

  private fun pause(): View {
    val col = column()
    col.addView(Ui.title(ctx, s("paused")))
    col.addView(Ui.button(ctx, s("resume"), accent = true, onClick = click { host.resume() }))
    col.addView(Ui.button(ctx, s("restart"), onClick = click { host.restartLevel() }))
    col.addView(Ui.button(ctx, s("settings"), onClick = click { host.navigate(ScreenId.SETTINGS) }))
    col.addView(Ui.button(ctx, s("main_menu"), onClick = click { host.goHome() }))
    return wrap(col, 340)
  }

  private fun levelComplete(): View {
    val col = column()
    val info = core.lastCompletion
    col.addView(Ui.title(ctx, s("level_complete"), 30f))
    if (info != null) {
      val r = info.summary.result
      col.addView(Ui.label(ctx, info.levelName, 20f, Ui.TEXT, true).apply { gravity = Gravity.CENTER })
      col.addView(Ui.spacer(ctx, 8))
      col.addView(statLine(s("time"), Format.time(r.timeSec) + if (info.summary.newBestTime) "  ★ ${s("new_best")}" else ""))
      col.addView(statLine(s("jumps"), r.jumps.toString() + if (info.summary.newBestJumps) "  ★" else ""))
      col.addView(statLine(s("boosts"), r.boosts.toString()))
      col.addView(statLine(s("coins"), "+${info.summary.coinsEarned}"))
      if (info.summary.leaderboardRank in 1..10) col.addView(statLine(s("leaderboard"), "#${info.summary.leaderboardRank}"))
      if (info.newItems.isNotEmpty()) col.addView(Ui.label(ctx, "${s("wardrobe")}: " + info.newItems.joinToString { core.items[it].name }, 14f, Ui.GOOD).apply { gravity = Gravity.CENTER })
      col.addView(Ui.spacer(ctx, 10))
      if (info.nextLevelId != null && core.progression.isLevelUnlocked(info.nextLevelId)) {
        val next = info.nextLevelId
        col.addView(Ui.button(ctx, s("next_level"), accent = true, onClick = click { host.startLevel(next) }))
      }
      col.addView(Ui.button(ctx, s("restart"), onClick = click { host.restartLevel() }))
    }
    col.addView(Ui.button(ctx, s("main_menu"), onClick = click { host.goHome() }))
    return wrap(col, 380)
  }

  private fun statLine(name: String, value: String): View = LinearLayout(ctx).apply {
    orientation = LinearLayout.HORIZONTAL
    setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 4))
    addView(Ui.label(ctx, name, 17f, Ui.TEXT_DIM).apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
    addView(Ui.label(ctx, value, 18f, Ui.TEXT, true))
  }

  // ---- settings -------------------------------------------------------------------------------

  private fun settingsHub(): View {
    val col = column()
    col.addView(Ui.title(ctx, s("settings")))
    col.addView(Ui.button(ctx, s("video"), onClick = click { host.navigate(ScreenId.SETTINGS_GRAPHICS) }))
    col.addView(Ui.button(ctx, s("audio"), onClick = click { host.navigate(ScreenId.SETTINGS_AUDIO) }))
    col.addView(Ui.button(ctx, s("controls"), onClick = click { host.navigate(ScreenId.SETTINGS_CONTROLS) }))
    col.addView(Ui.button(ctx, s("gameplay"), onClick = click { host.navigate(ScreenId.SETTINGS_GAMEPLAY) }))
    val langs = listOf("auto", "en", "ar")
    col.addView(Ui.choice(ctx, "Language / اللغة", listOf("Auto", "English", "العربية"), langs.indexOf(core.save.data.settings.gameplay.language).coerceAtLeast(0)) { i ->
      core.updateSettings { it.copy(gameplay = it.gameplay.copy(language = langs[i])) }
      host.refresh()
    })
    col.addView(Ui.button(ctx, if (confirmReset) "${s("reset_progress")}?  (tap again)" else s("reset_progress"), onClick = {
      if (confirmReset) { confirmReset = false; core.resetProgress(); host.refresh() } else { confirmReset = true; host.refresh() }
    }))
    col.addView(Ui.button(ctx, s("back"), onClick = { confirmReset = false; core.uiBack(); host.back() }))
    return wrap(col, 400)
  }

  private fun pct(v: Double) = "${(v * 100).toInt()}%"
  private fun two(v: Double) = "%.2f".format(v)

  private fun settingsAudio(): View {
    val a = core.save.data.settings.audio
    val col = column()
    col.addView(Ui.title(ctx, s("audio")))
    col.addView(Ui.slider(ctx, s("master"), a.master, 0.0, 1.0, ::pct) { v -> core.updateSettings { it.copy(audio = it.audio.copy(master = v)) } })
    col.addView(Ui.slider(ctx, s("music"), a.music, 0.0, 1.0, ::pct) { v -> core.updateSettings { it.copy(audio = it.audio.copy(music = v)) } })
    col.addView(Ui.slider(ctx, s("sfx"), a.sfx, 0.0, 1.0, ::pct) { v -> core.updateSettings { it.copy(audio = it.audio.copy(sfx = v)) }; core.uiClick() })
    col.addView(Ui.slider(ctx, s("ambient"), a.ambient, 0.0, 1.0, ::pct) { v -> core.updateSettings { it.copy(audio = it.audio.copy(ambient = v)) } })
    col.addView(Ui.button(ctx, s("back"), onClick = back()))
    return wrap(col, 420)
  }

  private fun settingsControls(): View {
    val c = core.save.data.settings.controls
    val col = column()
    col.addView(Ui.title(ctx, s("controls")))
    val layouts = ControlLayout.values().toList()
    col.addView(Ui.choice(ctx, s("layout"), layouts.map { it.label }, layouts.indexOf(c.layout)) { i -> core.updateSettings { it.copy(controls = it.controls.copy(layout = layouts[i])) } })
    col.addView(Ui.slider(ctx, s("sensitivity"), c.sensitivity, 0.5, 2.0, ::two) { v -> core.updateSettings { it.copy(controls = it.controls.copy(sensitivity = v)) } })
    col.addView(Ui.slider(ctx, s("deadzone"), c.deadzone, 0.0, 0.4, ::pct) { v -> core.updateSettings { it.copy(controls = it.controls.copy(deadzone = v)) } })
    col.addView(Ui.slider(ctx, s("button_size"), c.buttonSize, 0.7, 1.5, ::two) { v -> core.updateSettings { it.copy(controls = it.controls.copy(buttonSize = v)) } })
    col.addView(Ui.slider(ctx, "Opacity", c.buttonOpacity, 0.2, 1.0, ::pct) { v -> core.updateSettings { it.copy(controls = it.controls.copy(buttonOpacity = v)) } })
    col.addView(Ui.toggle(ctx, s("left_handed"), c.leftHanded) { v -> core.updateSettings { it.copy(controls = it.controls.copy(leftHanded = v)) }; core.uiToggle() })
    col.addView(Ui.toggle(ctx, s("haptics"), c.haptics) { v -> core.updateSettings { it.copy(controls = it.controls.copy(haptics = v)) }; core.uiToggle() })
    col.addView(Ui.slider(ctx, "${s("haptics")} %", c.hapticIntensity, 0.0, 1.0, ::pct) { v -> core.updateSettings { it.copy(controls = it.controls.copy(hapticIntensity = v)) }; core.haptics?.trigger("ui_click") })
    col.addView(Ui.button(ctx, s("back"), onClick = back()))
    return wrap(col, 440)
  }

  private fun settingsGraphics(): View {
    val v = core.save.data.settings.video
    val col = column()
    col.addView(Ui.title(ctx, s("video")))
    val q = Quality.values().toList()
    col.addView(Ui.choice(ctx, s("quality"), q.map { it.label }, q.indexOf(v.quality)) { i ->
      core.updateSettings { it.copy(video = VideoSettings.forQuality(q[i])) }
      host.applyDisplaySettings(); host.refresh()
    })
    col.addView(Ui.slider(ctx, s("resolution"), v.resolutionScale, 0.5, 1.0, ::pct) { x -> core.updateSettings { it.copy(video = it.video.copy(resolutionScale = x)) }; host.applyDisplaySettings() })
    col.addView(Ui.choice(ctx, s("fps_cap"), listOf("30", "60"), if (v.fpsCap <= 30) 0 else 1) { i -> core.updateSettings { it.copy(video = it.video.copy(fpsCap = if (i == 0) 30 else 60)) }; host.applyDisplaySettings() })
    col.addView(Ui.toggle(ctx, s("effects"), v.effects) { x -> core.updateSettings { it.copy(video = it.video.copy(effects = x)) } })
    col.addView(Ui.slider(ctx, s("particles"), v.particleDensity, 0.0, 1.0, ::pct) { x -> core.updateSettings { it.copy(video = it.video.copy(particleDensity = x)) } })
    col.addView(Ui.toggle(ctx, s("shadows"), v.shadows) { x -> core.updateSettings { it.copy(video = it.video.copy(shadows = x)) } })
    col.addView(Ui.button(ctx, s("back"), onClick = back()))
    return wrap(col, 440)
  }

  private fun settingsGameplay(): View {
    val g = core.save.data.settings.gameplay
    val col = column()
    col.addView(Ui.title(ctx, s("gameplay")))
    col.addView(Ui.toggle(ctx, s("show_hud"), g.showHud) { x -> core.updateSettings { it.copy(gameplay = it.gameplay.copy(showHud = x)) } })
    col.addView(Ui.toggle(ctx, s("show_timer"), g.showTimer) { x -> core.updateSettings { it.copy(gameplay = it.gameplay.copy(showTimer = x)) } })
    col.addView(Ui.toggle(ctx, s("show_fps"), g.showFps) { x -> core.updateSettings { it.copy(gameplay = it.gameplay.copy(showFps = x)) } })
    col.addView(Ui.slider(ctx, s("camera_shake"), g.cameraShake, 0.0, 1.0, ::pct) { x -> core.updateSettings { it.copy(gameplay = it.gameplay.copy(cameraShake = x)) } })
    col.addView(Ui.slider(ctx, s("camera_zoom"), g.cameraZoom, 0.65, 1.6, ::two) { x -> core.updateSettings { it.copy(gameplay = it.gameplay.copy(cameraZoom = x)) } })
    col.addView(Ui.toggle(ctx, s("tutorial"), g.tutorialHints) { x -> core.updateSettings { it.copy(gameplay = it.gameplay.copy(tutorialHints = x)) } })
    col.addView(Ui.button(ctx, s("back"), onClick = back()))
    return wrap(col, 440)
  }

  // ---- wardrobe / leaderboard / info ----------------------------------------------------------

  private fun rarityColor(r: Rarity): Int = when (r) {
    Rarity.COMMON -> Color.rgb(0xC9, 0xD1, 0xE0); Rarity.UNCOMMON -> Color.rgb(0x6B, 0xD9, 0x7B)
    Rarity.RARE -> Color.rgb(0x5C, 0xA8, 0xFF); Rarity.EPIC -> Color.rgb(0xC0, 0x7B, 0xFF); Rarity.LEGENDARY -> Color.rgb(0xFF, 0xB7, 0x03)
  }

  private fun wardrobe(): View {
    val col = column()
    col.addView(Ui.title(ctx, s("wardrobe")))
    col.addView(Ui.label(ctx, "${s("coins")}: ${core.save.data.coins}", 18f, Ui.ACCENT, true).apply { gravity = Gravity.CENTER })
    val tabs = ItemCategory.values().toList()
    col.addView(Ui.choice(ctx, "▸", tabs.map { it.label }, tabs.indexOf(wardrobeCategory)) { i -> wardrobeCategory = tabs[i]; host.refresh() })
    val equipped = core.wardrobe.equipped(wardrobeCategory)
    for (item in core.items.inCategory(wardrobeCategory)) {
      val owned = core.wardrobe.isUnlocked(item.id)
      val state = when {
        item.id == equipped.id -> s("equipped")
        owned -> s("equip")
        item.unlock == "coins" -> "${s("buy")} ${item.price}"
        item.unlock.startsWith("level:") -> "${s("locked")}: ${runCatching { LevelLoader.loadData(item.unlock.removePrefix("level:")).name }.getOrDefault("level")}"
        item.unlock.startsWith("world:") -> "${s("locked")}: ${core.worlds.find(item.unlock.removePrefix("world:"))?.name ?: ""}"
        else -> s("locked")
      }
      val b = Ui.button(ctx, "${item.icon}  ${item.name}\n$state", enabled = owned && item.id != equipped.id || item.unlock == "coins" && !owned && core.wardrobe.canAfford(item.id), accent = item.id == equipped.id) {
        core.uiClick()
        if (owned) core.wardrobe.equip(item.id) else if (core.wardrobe.purchase(item.id) == UnlockResult.UNLOCKED) { core.audio?.trigger("ui_unlock"); core.wardrobe.equip(item.id) }
        host.refresh()
      }
      b.setTextColor(rarityColor(item.rarity))
      col.addView(b)
    }
    col.addView(Ui.button(ctx, s("back"), onClick = back()))
    return wrap(col, 420)
  }

  private fun leaderboard(): View {
    val col = column()
    col.addView(Ui.title(ctx, s("leaderboard")))
    val levels = core.worlds.levelOrder.filter { core.progression.isLevelUnlocked(it) }
    if (leaderboardLevel == null || leaderboardLevel !in levels) leaderboardLevel = levels.firstOrNull()
    val names = levels.map { runCatching { LevelLoader.loadData(it).name }.getOrDefault(it) }
    col.addView(Ui.choice(ctx, s("levels"), names, levels.indexOf(leaderboardLevel).coerceAtLeast(0)) { i -> leaderboardLevel = levels[i]; host.refresh() })
    val entries = leaderboardLevel?.let { core.menu.leaderboard(it) } ?: emptyList()
    if (entries.isEmpty()) col.addView(Ui.label(ctx, s("no_scores"), 16f, Ui.TEXT_DIM).apply { setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12)) })
    for ((i, e) in entries.withIndex()) col.addView(statLine("#${i + 1}", "${Format.timeMs(e.timeMs)}   ${e.jumps} ${s("jumps")}"))
    col.addView(Ui.label(ctx, "Local times only – online ranking needs a server.", 12f, Ui.TEXT_DIM).apply { setPadding(0, Ui.dp(ctx, 10), 0, Ui.dp(ctx, 6)) })
    col.addView(Ui.button(ctx, s("back"), onClick = back()))
    return wrap(col, 420)
  }

  private fun howToPlay(): View {
    val col = column()
    col.addView(Ui.title(ctx, s("how_to_play")))
    for (i in 1..6) col.addView(Ui.label(ctx, s("htp_$i"), 16f).apply { setPadding(0, Ui.dp(ctx, 6), 0, Ui.dp(ctx, 10)) })
    col.addView(Ui.button(ctx, s("back"), onClick = back()))
    return wrap(col, 480)
  }

  private fun credits(): View {
    val col = column()
    col.addView(Ui.title(ctx, s("credits")))
    col.addView(Ui.label(ctx, s("credits_text"), 16f).apply { setPadding(0, Ui.dp(ctx, 6), 0, Ui.dp(ctx, 14)) })
    col.addView(Ui.button(ctx, s("back"), onClick = back()))
    return wrap(col, 440)
  }

  // ---- physics test ---------------------------------------------------------------------------

  /** A thin button bar at the bottom; the rest of the screen stays free for the game + debug overlay. */
  private fun physicsTest(): View {
    val bar = LinearLayout(ctx).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER
      background = Ui.rounded(Ui.PANEL_SOFT, 14f, ctx)
      setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 4), Ui.dp(ctx, 8), Ui.dp(ctx, 4))
    }
    fun add(text: String, action: () -> Unit) {
      val b = Ui.button(ctx, text, onClick = click(action))
      b.textSize = 13f
      (b.layoutParams as LinearLayout.LayoutParams).apply { width = ViewGroup.LayoutParams.WRAP_CONTENT; height = ViewGroup.LayoutParams.WRAP_CONTENT; marginStart = Ui.dp(ctx, 4); marginEnd = Ui.dp(ctx, 4) }
      b.minHeight = Ui.dp(ctx, 40); b.minimumHeight = Ui.dp(ctx, 40)
      bar.addView(b)
    }
    add("Reset") { host.resetTest() }
    add("Test Jump") { host.runScenario(ScenarioKind.TEST_JUMP) }
    add("Test Boost") { host.runScenario(ScenarioKind.TEST_BOOST) }
    add("Test Bounce") { host.runScenario(ScenarioKind.TEST_BOUNCE) }
    add("Test Fall") { host.runScenario(ScenarioKind.TEST_FALL) }
    add(s("back")) { host.goHome() }
    val frame = FrameLayout(ctx)
    frame.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = Ui.dp(ctx, 8) })
    return frame
  }

}
