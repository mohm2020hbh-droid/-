extends TestCase
## Every World 01 level is finishable by its intended route without dying,
## the simulation is deterministic, and a checkpoint respawn reproduces the
## exact same obstacle timing. test_world_02_playthrough runs the same tests
## on World 02.

const WORLDS: Array[String] = ["res://levels/world_01/world_01.tres", "res://levels/world_02/world_02.tres"]

var WORLD: WorldData

var _harnesses: Array[GameHarness] = []
var _save := SaveSandbox.new()


## Which world these tests play (0-based); overridden per world.
func world_index() -> int:
	return 0


func before_each() -> void:
	WORLD = load(WORLDS[world_index()])
	_save.enter()


func after_each() -> void:
	for harness in _harnesses:
		harness.free_game()
	await get_tree().physics_frame
	get_tree().paused = false
	_save.leave()


func _play(level: int, skip: PackedInt32Array = []) -> GameHarness:
	var harness := GameHarness.new(self, Autoplay.route_for(world_index(), level))
	harness.skip = skip
	_harnesses.append(harness)
	await harness.start(level, world_index())
	harness.game.press_jump()  # Tap to start.
	var stop_on_death := skip.is_empty()
	await harness.run_until(func() -> bool:
		return harness.game.state == GameSession.State.COMPLETE or (stop_on_death and not harness.deaths.is_empty()),
		60 * 200)
	return harness


func test_every_route_finishes_its_level_without_dying() -> void:
	for level in WORLD.levels.size():
		var h := await _play(level)
		var name := WORLD.levels[level].display_name
		assert_eq(h.deaths, [] as Array[Dictionary], "%s: no deaths along the intended route" % name)
		assert_eq(h.game.state, GameSession.State.COMPLETE, "%s: reached the finish gate" % name)
		assert_true(h.game.score.shards > 0, "%s: collected shards on the way" % name)
		assert_true(SaveSystem.get_record(WORLD.levels[level].id).completed, "%s: completion saved" % name)
		h.free_game()
		await get_tree().physics_frame


func test_player_cannot_die_after_the_finish() -> void:
	var h := await _play(0)
	h.game.player.die(&"test")
	assert_false(h.game.player.is_dead(), "invulnerable once the level is complete")


func test_simulation_is_deterministic() -> void:
	var runs: Array[Dictionary] = []
	var last := WORLD.levels.size() - 1
	for i in 2:
		var h := await _play(last)
		runs.append({"deaths": h.deaths.size(), "ticks": h.ticks, "score": h.game.score.get_score(),
			"shards": h.game.score.shards, "position": h.game.player.global_position})
		# Games share one physics space: the next run must not meet this level.
		h.free_game()
		await get_tree().physics_frame
	assert_eq(runs[0].deaths, 0, "first run clean")
	assert_eq(runs[0], runs[1], "two runs are identical (ticks, score, shards, final position)")


func test_death_respawns_at_each_checkpoint_with_same_timing() -> void:
	for level in WORLD.levels.size():
		var route := Autoplay.route_for(world_index(), level)
		var probe := GameHarness.new(self)
		await probe.start(level, world_index())
		var skip := PackedInt32Array()
		for checkpoint in probe.game.level.get_checkpoints():
			# The first tap after each checkpoint: skip it once to die there.
			var x := checkpoint.global_position.x / GameConst.TILE
			for j in route.size():
				if route[j] > x:
					skip.append(j)
					break
		probe.free_game()
		await get_tree().physics_frame
		var forced := skip.size()
		var h := await _play(level, skip)
		var name := WORLD.levels[level].display_name
		assert_eq(h.deaths.size(), forced, "%s: exactly one forced death per checkpoint" % name)
		assert_eq(h.game.state, GameSession.State.COMPLETE,
			"%s: finished after every respawn: timing after a respawn matches the first pass" % name)
		h.free_game()
		await get_tree().physics_frame
