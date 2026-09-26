extends TestCase
## Unlocking, the level select, the results panel and the death banner on
## the real game scene.

const WORLD := preload("res://levels/world_01/world_01.tres")

var _save := SaveSandbox.new()
var h: GameHarness


func before_each() -> void:
	_save.enter()


func after_each() -> void:
	if h:
		h.free_game()
	await get_tree().physics_frame
	get_tree().paused = false
	_save.leave()


func _complete(level: int) -> void:
	SaveSystem.record_result(WORLD.levels[level].id, 100, 1, true)


func _finish(level: int) -> GameHarness:
	h = GameHarness.new(self, World01Routes.get_route(level))
	await h.start(level)
	h.game.press_jump()
	await h.run_until(h.is_state(GameSession.State.COMPLETE), 60 * 90)
	await h.run_ticks(80)  # Brake and results delay.
	return h


func test_unlock_rules_follow_completions() -> void:
	assert_true(Progression.is_unlocked(WORLD, 0), "the first level is always open")
	assert_false(Progression.is_unlocked(WORLD, 1), "the second waits for the first")
	assert_eq(Progression.furthest_unlocked(WORLD), 0)
	_complete(0)
	assert_true(Progression.is_unlocked(WORLD, 1), "completing a level opens the next")
	assert_false(Progression.is_unlocked(WORLD, 2))
	assert_eq(Progression.furthest_unlocked(WORLD), 1, "returning players start on the first open level")
	assert_false(Progression.is_world_completed(WORLD))
	for i in WORLD.levels.size():
		_complete(i)
	assert_true(Progression.is_world_completed(WORLD), "all five: world complete, next world unlocked")
	assert_eq(Progression.furthest_unlocked(WORLD), WORLD.levels.size() - 1)
	assert_false(Progression.is_unlocked(WORLD, WORLD.levels.size()), "no level past the end")


func test_unlock_all_opens_every_level_for_playtests() -> void:
	Progression.unlock_all = true
	for i in WORLD.levels.size():
		assert_true(Progression.is_unlocked(WORLD, i), "level %d open in test mode" % (i + 1))
	assert_false(Progression.is_unlocked(WORLD, WORLD.levels.size()), "still no level past the end")
	assert_false(Progression.is_world_completed(WORLD), "opening levels completes nothing")
	Progression.unlock_all = false
	assert_false(Progression.is_unlocked(WORLD, 1), "back to the normal rules")


func test_level_select_ignores_locked_levels() -> void:
	h = GameHarness.new(self)
	await h.start(0)
	h.game.start_overlay.level_chosen.emit(3)
	await h.run_ticks(3)
	assert_eq(h.game.level_index, 0, "a locked level cannot be opened")
	_complete(0)
	h.game.start_overlay.level_chosen.emit(1)
	await h.run_ticks(3)
	assert_eq(h.game.level_index, 1, "an unlocked level opens")
	assert_eq(h.game.state, GameSession.State.READY, "waiting for the first tap")
	assert_eq(h.game.level.data, WORLD.levels[1])


func test_finishing_a_level_offers_the_next_one() -> void:
	await _finish(0)
	var panel := h.game.complete_panel
	assert_true(panel.visible, "results shown")
	assert_true((panel.get_node(^"%NextButton") as Button).visible, "NEXT LEVEL offered")
	assert_eq((panel.get_node(^"%TitleLabel") as Label).text, "LEVEL COMPLETE")
	assert_true(Progression.is_unlocked(WORLD, 1), "level 2 unlocked by the save")
	(panel.get_node(^"%NextButton") as Button).pressed.emit()
	await h.run_ticks(3)
	assert_eq(h.game.level_index, 1, "NEXT LEVEL loads level 2")
	assert_eq(h.game.state, GameSession.State.READY)
	assert_false(panel.visible)


func test_finishing_the_last_level_completes_the_world() -> void:
	for i in WORLD.levels.size() - 1:
		_complete(i)
	var last := WORLD.levels.size() - 1
	await _finish(last)
	var panel := h.game.complete_panel
	assert_eq((panel.get_node(^"%TitleLabel") as Label).text, "WORLD 01 COMPLETE")
	assert_eq((panel.get_node(^"%UnlockLabel") as Label).text, "WORLD 02 UNLOCKED")
	assert_true((panel.get_node(^"%NextButton") as Button).visible, "World 02 follows the last level")
	assert_true(Progression.is_world_completed(WORLD))
	assert_true(Progression.is_world_unlocked(h.game.worlds[1]), "completing World 01 opens World 02")
	panel.next_pressed.emit()
	await h.run_ticks(2)
	assert_eq(h.game.world_index, 1, "NEXT goes on to World 02")
	assert_eq(h.game.level_index, 0, "at its first level")
	assert_true(Palette.is_mono(), "dressed in World 02's black and white")
	assert_true(h.game.player.visual.void_style, "the player is the World 02 entity")


func test_death_banner_counts_attempts_and_clears_on_respawn() -> void:
	h = GameHarness.new(self, World01Routes.get_route(0))
	h.skip = [0]
	await h.start(0)
	h.game.press_jump()
	await h.run_until(h.is_state(GameSession.State.DYING))
	await h.run_ticks(2)
	var banner := h.game.death_banner
	assert_true(banner.visible, "shown while dying")
	assert_eq((banner.get_node(^"%AttemptLabel") as Label).text, "ATTEMPT 1")
	await h.run_until(h.is_state(GameSession.State.PLAYING))
	await h.run_ticks(30)
	assert_false(banner.visible, "gone once the run is back")
	assert_eq(h.game.attempts, 2, "the next try is attempt 2")
	h.game.restart_level()
	await h.run_ticks(2)
	assert_eq(h.game.attempts, 1, "a restart starts counting again")


func test_world_tabs_open_world_02_once_world_01_is_done() -> void:
	h = GameHarness.new(self)
	await h.start(0)
	var overlay := h.game.start_overlay
	var tabs: Array[Button] = overlay._tabs
	assert_eq(tabs.size(), 2, "one tab per world")
	assert_true(tabs[1].disabled, "World 02 is locked on a fresh save")
	overlay.world_chosen.emit(1)
	await h.run_ticks(2)
	assert_eq(h.game.world_index, 0, "a locked world cannot be chosen")
	assert_false(Palette.is_mono(), "still World 01's reds")
	for i in WORLD.levels.size():
		_complete(i)
	h.game.load_level(0)  # Back on the start screen: the tabs are rebuilt.
	assert_false(tabs[1].disabled, "World 02 opens once World 01 is complete")
	overlay.world_chosen.emit(1)
	await h.run_ticks(2)
	assert_eq(h.game.world_index, 1, "the World 02 tab switches worlds")
	assert_eq(h.game.level.data.id, &"w02_l01", "at its first level")
	assert_true(Palette.is_mono(), "black and white")
	overlay.world_chosen.emit(0)
	await h.run_ticks(2)
	assert_eq(h.game.world_index, 0, "and back to World 01")
	assert_false(Palette.is_mono(), "in red again")
	assert_false(h.game.player.visual.void_style, "with the World 01 core")
