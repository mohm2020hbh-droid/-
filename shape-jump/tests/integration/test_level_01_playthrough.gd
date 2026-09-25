extends TestCase
## Level 01 is finishable without dying, deterministic, and a checkpoint
## respawn reproduces the exact same obstacle timing.

var _harnesses: Array[GameHarness] = []
var _save := SaveSandbox.new()


func before_each() -> void:
	_save.enter()


func after_each() -> void:
	for harness in _harnesses:
		harness.free_game()
	await get_tree().physics_frame
	get_tree().paused = false
	_save.leave()


func _play(skip: PackedInt32Array = []) -> GameHarness:
	var harness := GameHarness.new(self, Level01Route.TAPS)
	harness.skip = skip
	_harnesses.append(harness)
	await harness.start()
	harness.game.press_jump()  # Tap to start.
	var stop_on_death := skip.is_empty()
	await harness.run_until(func() -> bool:
		return harness.game.state == GameSession.State.COMPLETE or (stop_on_death and not harness.deaths.is_empty()))
	return harness


func test_route_finishes_level_without_dying() -> void:
	var h := await _play()
	assert_eq(h.deaths, [] as Array[Dictionary], "no deaths along the intended route")
	assert_eq(h.game.state, GameSession.State.COMPLETE, "reached the finish gate")
	assert_true(h.game.score.shards > 0, "collected shards on the way")
	assert_true(SaveSystem.get_record(&"level_01").completed, "completion saved")


func test_player_cannot_die_after_the_finish() -> void:
	var h := await _play()
	h.game.player.die(&"test")
	assert_false(h.game.player.is_dead(), "invulnerable once the level is complete")


func test_simulation_is_deterministic() -> void:
	var runs: Array[Dictionary] = []
	for i in 2:
		var h := await _play()
		runs.append({"deaths": h.deaths.size(), "ticks": h.ticks, "score": h.game.score.get_score(),
			"shards": h.game.score.shards, "position": h.game.player.global_position})
		# Games share one physics space: the next run must not meet this level.
		h.free_game()
		await get_tree().physics_frame
	assert_eq(runs[0].deaths, 0, "first run clean")
	assert_eq(runs[0], runs[1], "two runs are identical (ticks, score, shards, final position)")


func test_death_respawns_at_checkpoint_with_same_timing() -> void:
	# Skip the first tap after checkpoint A (the staircase) to die there once.
	var h := await _play([7])
	assert_eq(h.deaths.size(), 1, "exactly one (forced) death")
	assert_eq(h.game.state, GameSession.State.COMPLETE,
		"finished after respawning; timing after a respawn matches the first pass")
