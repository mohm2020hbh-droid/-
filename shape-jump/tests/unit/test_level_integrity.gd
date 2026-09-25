extends TestCase
## Structural rules every World 01 level must follow (docs/GDD.md §8).

const WORLD := preload("res://levels/world_01/world_01.tres")
## GDD §8.2: after a checkpoint the player respawns running, so the next
## hazard or gap must be at least this far away.
const MIN_RUNWAY_SECONDS := 1.5

var level: Level


func after_each() -> void:
	if level:
		level.queue_free()
		level = null
		await get_tree().process_frame


func _load(index: int) -> Level:
	if level:
		level.queue_free()
	level = (load(WORLD.levels[index].scene_path) as PackedScene).instantiate() as Level
	add_child(level)
	return level


func test_world_lists_five_levels_of_rising_speed() -> void:
	assert_eq(WORLD.levels.size(), 5, "World 01 has five levels")
	var ids := {}
	var previous_speed := 0.0
	for data in WORLD.levels:
		assert_false(ids.has(data.id), "unique level id %s" % data.id)
		ids[data.id] = true
		assert_true(ResourceLoader.exists(data.scene_path), "%s: scene exists" % data.id)
		assert_true(data.speed_scale >= previous_speed, "%s: never slower than the level before" % data.id)
		previous_speed = data.speed_scale
	for i in WORLD.levels.size():
		assert_true(World01Routes.get_route(i).size() > 0, "level %d has a route" % (i + 1))


func test_levels_have_required_structure() -> void:
	for i in WORLD.levels.size():
		_load(i)
		var name := WORLD.levels[i].display_name
		assert_true(level.data == WORLD.levels[i], "%s: scene uses its LevelData" % name)
		assert_true(level.spawn_point != null, "%s has a SpawnPoint" % name)
		assert_true(level.get_finish() != null, "%s has a FinishGate" % name)
		assert_true(level.get_finish().global_position.x > level.get_spawn_feet_position().x, "finish is ahead of spawn")
		assert_true(level.get_shard_count() > 0, "has shards")
		assert_true(level.kill_y > 0.0, "kill plane below the ground line")


func test_checkpoints_are_ordered_and_have_a_safe_runway() -> void:
	for i in WORLD.levels.size():
		_load(i)
		var name := WORLD.levels[i].display_name
		var runway := MovementConfig.new().run_speed * level.data.speed_scale * MIN_RUNWAY_SECONDS
		var reaches: Array[Vector2] = []
		for node in level.find_children("*", "Area2D", true, false):
			if node is Hazard:
				reaches.append(_x_reach(node as Hazard))
		var previous_x := level.get_spawn_feet_position().x
		for checkpoint in level.get_checkpoints():
			var cx := checkpoint.global_position.x
			var cy := checkpoint.global_position.y
			assert_true(cx > previous_x, "%s: checkpoints are ordered along the run" % name)
			previous_x = cx
			for reach in reaches:
				assert_false(reach.y > cx - GameConst.TILE and reach.x < cx + runway,
					"%s: a hazard reaching x=%.0f..%.0f is inside the runway after the checkpoint at x=%.0f" % [
						name, reach.x, reach.y, cx])
			assert_true(_ground_under(cx, cx + runway, cy), "%s: solid ground for the runway after x=%.0f" % [name, cx])


func test_timed_elements_are_pure_functions_of_level_time() -> void:
	for i in WORLD.levels.size():
		_load(i)
		var timed := level.get_timed_elements()
		assert_true(timed.size() > 0, "level has timed elements")
		level.rewind_to(3.7)
		var first := _snapshot()
		level.rewind_to(11.2)
		level.rewind_to(3.7)
		assert_eq(_snapshot(), first, "%s: same time -> same positions and states" % WORLD.levels[i].display_name)


func _snapshot() -> Array:
	var out := []
	for node in level.get_timed_elements():
		var item := node as Node2D
		out.append([item.position, item.rotation])
		if node is Oscillator:
			out.append((node.get_parent() as Node2D).position)
		for child in node.get_children(true):
			if child is CollisionShape2D:
				out.append([child.position, child.disabled])
	return out


## The x range (px) a hazard can ever cover: its hitboxes, the full reach of
## an arm, and the travel of a crush block or an oscillator.
func _x_reach(hazard: Hazard) -> Vector2:
	var lo := INF
	var hi := -INF
	for child in hazard.get_children(true):
		if child is CollisionShape2D:
			var box: Rect2 = (child as CollisionShape2D).global_transform * (child as CollisionShape2D).shape.get_rect()
			lo = minf(lo, box.position.x)
			hi = maxf(hi, box.end.x)
	var x := hazard.global_position.x
	if hazard is RotatingArm:
		lo = minf(lo, x - (hazard as RotatingArm).length)
		hi = maxf(hi, x + (hazard as RotatingArm).length)
	var travel := 0.0
	if hazard is CrushBlock:
		travel = (hazard as CrushBlock).travel.x
	for child in hazard.get_children():
		if child is Oscillator:
			travel = (child as Oscillator).travel.x
	return Vector2(lo + minf(travel, 0.0), hi + maxf(travel, 0.0))


## True when static blocks cover every point of [x0, x1] at height [param y].
func _ground_under(x0: float, x1: float, y: float) -> bool:
	var x := x0
	while x <= x1:
		var covered := false
		for node in level.find_children("*", "StaticBody2D", true, false):
			if not node is Block or node is PhaseBlock:
				continue
			var rect: Rect2 = (node as Block).get_rect()
			rect.position += (node as Block).global_position
			if x >= rect.position.x and x <= rect.end.x and absf(rect.position.y - y) < 1.0:
				covered = true
				break
		if not covered:
			return false
		x += GameConst.TILE * 0.5
	return true
