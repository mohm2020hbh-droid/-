extends TestCase
## Level progress, the 33% / 66% checkpoints, the death penalty and the
## respawn, on the real game scene for every World 01 level.

const WORLD := preload("res://levels/world_01/world_01.tres")

var h: GameHarness


func after_each() -> void:
	if h:
		h.free_game()
		h = null
	await get_tree().physics_frame


func _percent(x_tiles: float) -> float:
	return h.game.progress.percent_at(x_tiles * GameConst.TILE)


## Index of the first route tap at or after [param percent] of the level.
func _tap_after(route: PackedFloat32Array, percent: float) -> int:
	for i in route.size():
		if _percent(route[i]) >= percent:
			return i
	return -1


func test_every_level_has_checkpoints_near_a_third_and_two_thirds() -> void:
	for i in WORLD.levels.size():
		h = GameHarness.new(self)
		await h.start(i)
		var cps := h.game.level.get_checkpoints()
		assert_eq(cps.size(), 2, "level %d: two checkpoints" % (i + 1))
		var a := _percent(cps[0].global_position.x / GameConst.TILE)
		var b := _percent(cps[1].global_position.x / GameConst.TILE)
		assert_true(absf(a - 100.0 / 3.0) <= 4.0, "level %d: first checkpoint near 33%% (%.1f%%)" % [i + 1, a])
		assert_true(absf(b - 200.0 / 3.0) <= 8.0, "level %d: second checkpoint near 66%% (%.1f%%)" % [i + 1, b])
		h.free_game()
		await get_tree().physics_frame


## Per level: run, die after the first checkpoint (~50%) and after the second
## (~80%): each death takes 25 points, the respawn is at the last checkpoint
## with a clean player, and the run still reaches 100% and the finish.
func test_deaths_cost_25_points_and_respawn_at_the_last_checkpoint() -> void:
	for i in WORLD.levels.size():
		await _die_twice_then_finish(i)


func _die_twice_then_finish(index: int) -> void:
	var route := World01Routes.get_route(index)
	h = GameHarness.new(self, route)
	await h.start(index)
	var name := "level %d" % (index + 1)
	var cps := h.game.level.get_checkpoints()
	h.skip = [_tap_after(route, 50.0), _tap_after(route, 80.0)]
	var shown: Array[float] = []
	h.game.progress.changed.connect(func(v: float) -> void: shown.append(v))
	h.game.press_jump()
	for death in 2:
		var last := [0.0]  # Progress on the tick before the death (lambdas copy plain locals).
		assert_true(await h.run_until(func() -> bool:
			if h.deaths.size() > death:
				return true
			last[0] = h.game.progress.percent
			return false), "%s: death %d happened" % [name, death + 1])
		var before: float = last[0]
		await h.run_ticks(1)
		var died_at: float = h.deaths[death].x
		var banner := (h.game.death_banner.get_node(^"%ProgressLabel") as Label).text
		assert_true(banner.ends_with("= %d%%" % floori(h.game.progress.percent)), "%s: banner shows the penalty (%s)" % [name, banner])
		assert_near(h.game.progress.percent, maxf(before - 25.0, 0.0), 0.1, "%s: 25 points off (%.1f%%)" % [name, before])
		var last_cp: Checkpoint = null
		for cp in cps:
			if cp.global_position.x / GameConst.TILE < died_at:
				last_cp = cp
		assert_true(last_cp != null, "%s: a checkpoint was passed before death %d" % [name, death + 1])
		assert_true(await h.run_until(h.is_state(GameSession.State.PLAYING)), "%s: back in play" % name)
		var player := h.game.player
		assert_near(player.global_position.x, last_cp.global_position.x, 12.0, "%s: respawned at the last checkpoint" % name)
		assert_true(player.is_on_floor(), "%s: on the ground" % name)
		assert_eq(player.velocity.y, 0.0, "%s: no vertical speed" % name)
		assert_true(player.has_double_jump(), "%s: double jump restored" % name)
		await h.run_ticks(1)
		assert_near(h.game.progress.percent, maxf(before - 25.0, _percent(player.global_position.x / GameConst.TILE)), 0.6,
			"%s: progress kept the penalty" % name)
	assert_true(await h.run_until(h.is_state(GameSession.State.COMPLETE), 60 * 90), "%s: finished after two deaths" % name)
	assert_eq(h.game.progress.percent, 100.0, "%s: 100%% at the finish" % name)
	for v in shown:
		assert_true(v >= 0.0 and v <= 100.0, "%s: progress stays within 0..100" % name)
	h.free_game()
	await get_tree().physics_frame
	h = null


## The web page restarts the engine when the browser drops the WebGL
## context; the run must continue at the same checkpoint with its progress,
## attempts and score, and still finish.
func test_resume_continues_the_run_at_its_checkpoint() -> void:
	h = GameHarness.new(self, World01Routes.get_route(1))
	await h.start(1)
	var cp := h.game.level.get_checkpoints()[1]
	h.game._resume(1, 55.0, 4, {"tiles": 150, "shards": 10})
	h.seek(h.player_x())
	assert_eq(h.game.state, GameSession.State.PLAYING, "playing at once")
	assert_near(h.game.player.global_position.x, cp.global_position.x, 12.0, "at the checkpoint")
	assert_near(h.game.progress.percent, 55.0, 0.01, "progress kept")
	assert_eq(h.game.attempts, 4, "attempts kept")
	assert_eq(h.game.score.shards, 10, "shards kept")
	assert_true(await h.run_until(h.is_state(GameSession.State.COMPLETE), 60 * 60), "the resumed run finishes")
	assert_true(h.deaths.is_empty(), "with the same timing as the first pass")
