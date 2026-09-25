extends TestCase
## Level 01 is finishable without dying, deterministic, and a checkpoint
## respawn reproduces the exact same obstacle timing.

## Intended route: player-centre x (tiles) at which the finger taps.
const ROUTE: PackedFloat32Array = [
	19.3, 26.6, 34.3, 40.5, 52.1, 59.6, 66.6, 100.5, 105.4, 110.3, 118.3, 124.9,
	140.6, 147.4, 155.6, 162.0, 182.6, 188.8, 194.8, 200.8, 208.1, 223.4, 229.6,
	247.6, 263.6, 269.9, 275.9, 282.5, 288.5,
]
const TEST_SAVE := "user://test_playthrough_save.cfg"

var _runners: Array[RouteRunner] = []
var _original_save_path: String


func before_each() -> void:
	_original_save_path = SaveSystem.save_path
	SaveSystem.save_path = TEST_SAVE
	SaveSystem.load_from_disk()


func after_each() -> void:
	for runner in _runners:
		runner.free_game()
	await get_tree().physics_frame
	get_tree().paused = false
	DirAccess.remove_absolute(ProjectSettings.globalize_path(TEST_SAVE))
	SaveSystem.save_path = _original_save_path
	SaveSystem.load_from_disk()


func _runner(route: PackedFloat32Array) -> RouteRunner:
	var runner := RouteRunner.new(self, route)
	_runners.append(runner)
	return runner


func test_route_finishes_level_without_dying() -> void:
	var result: Dictionary = await _runner(ROUTE).run()
	assert_eq(result.deaths, [], "no deaths along the intended route")
	assert_true(result.completed, "reached the finish gate")
	assert_true(result.shards > 0, "collected shards on the way")
	assert_true(SaveSystem.get_record(&"level_01").completed, "completion saved")


func test_simulation_is_deterministic() -> void:
	var first := _runner(ROUTE)
	var a: Dictionary = await first.run()
	first.free_game()
	var b: Dictionary = await _runner(ROUTE).run()
	assert_eq(a.ticks, b.ticks, "same duration")
	assert_eq(a.score, b.score, "same score")
	assert_eq(a.shards, b.shards, "same shards")
	assert_eq(a.x, b.x, "same final position")


func test_death_respawns_at_checkpoint_with_same_timing() -> void:
	# Skip the first jump after checkpoint A (the staircase) to die there.
	var runner := _runner(ROUTE)
	runner.skip = [7]
	runner.stop_on_death = false
	var result: Dictionary = await runner.run()
	assert_eq(result.deaths.size(), 1, "exactly one (forced) death")
	assert_true(result.completed, "finished after respawning; timing after a respawn matches the first pass")
