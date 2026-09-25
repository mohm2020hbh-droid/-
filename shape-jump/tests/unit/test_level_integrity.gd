extends TestCase
## Structural rules every level must follow (docs/GDD.md §8). Add new
## levels to LEVELS to have them checked too.

const LEVELS: Array[String] = ["res://levels/level_01.tscn"]
## GDD §8.2: after a checkpoint the player respawns running, so the next
## hazard or gap must be at least this far away.
const MIN_RUNWAY_SECONDS := 1.5

var level: Level


func after_each() -> void:
	if level:
		level.queue_free()
		await get_tree().process_frame


func _load(path: String) -> Level:
	level = (load(path) as PackedScene).instantiate() as Level
	add_child(level)
	return level


func test_levels_have_required_structure() -> void:
	for path in LEVELS:
		_load(path)
		assert_true(level.data != null and not String(level.data.id).is_empty(), "%s has LevelData with an id" % path)
		assert_true(level.spawn_point != null, "%s has a SpawnPoint" % path)
		assert_true(level.get_finish() != null, "%s has a FinishGate" % path)
		assert_true(level.get_finish().global_position.x > level.get_spawn_feet_position().x, "finish is ahead of spawn")
		assert_true(level.get_shard_count() > 0, "has shards")
		assert_true(level.kill_y > 0.0, "kill plane below the ground line")
		level.queue_free()
		level = null


func test_checkpoints_are_ordered_and_have_a_safe_runway() -> void:
	for path in LEVELS:
		_load(path)
		var speed := MovementConfig.new().run_speed * level.data.speed_scale
		var runway := speed * MIN_RUNWAY_SECONDS
		var hazards: Array[float] = []
		for node in level.find_children("*", "Area2D"):
			if node is Spikes or node is Saw:
				hazards.append(node.global_position.x)
		var previous_x := level.get_spawn_feet_position().x
		for checkpoint in level.get_checkpoints():
			var cx := checkpoint.global_position.x
			assert_true(cx > previous_x, "checkpoints are ordered along the run")
			previous_x = cx
			for hx in hazards:
				assert_false(hx > cx and hx < cx + runway,
					"%s: hazard at x=%.0f is inside the runway after the checkpoint at x=%.0f" % [path, hx, cx])
			assert_true(_ground_under(cx, cx + runway), "%s: solid ground for the runway after x=%.0f" % [path, cx])
		level.queue_free()
		level = null


func test_timed_elements_are_pure_functions_of_level_time() -> void:
	_load(LEVELS[0])
	var timed := level.get_timed_elements()
	assert_true(timed.size() > 0, "level has timed elements")
	level.rewind_to(3.7)
	var first := _snapshot()
	level.rewind_to(11.2)
	level.rewind_to(3.7)
	assert_eq(_snapshot(), first, "same time -> same positions and states")


func _snapshot() -> Array:
	var out := []
	for node in level.get_timed_elements():
		if node is Oscillator:
			out.append((node.get_parent() as Node2D).position)
		elif node is PhaseBlock:
			out.append(node.is_solid())
	return out


## True when blocks cover every point of [x0, x1] at the height of x0's ground.
func _ground_under(x0: float, x1: float) -> bool:
	var x := x0
	while x <= x1:
		var covered := false
		for node in level.find_children("*", "StaticBody2D"):
			if node is Block and not node is PhaseBlock:
				var rect: Rect2 = (node as Block).get_rect()
				rect.position += (node as Block).global_position
				if x >= rect.position.x and x <= rect.end.x and rect.position.y <= 0.0:
					covered = true
					break
		if not covered:
			return false
		x += GameConst.TILE * 0.5
	return true
