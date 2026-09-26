extends "res://tests/unit/test_level_integrity.gd"
## The level structure rules, on World 03, plus its own: the galaxy look,
## locked until World 02 is complete, the last world; its obstacle language;
## and gravity that is consistent everywhere it is read (checkpoints, the
## finish, the schedule).


func world_index() -> int:
	return 2


func test_world_03_has_its_own_look_and_follows_world_02() -> void:
	assert_eq(WORLD.number, 3)
	assert_eq(WORLD.theme, &"galaxy", "the galaxy palette")
	assert_true(WORLD.background != null and WORLD.background.resource_path.ends_with("background_galaxy.tscn"),
		"its own background")
	assert_eq(WORLD.requires.id, &"world_02", "unlocked by finishing World 02")
	assert_eq(WORLD.next_world_name, "", "the last world: no World 04")
	assert_eq(WORLD.progress_milestones, [50.0, 75.0] as Array[float], "50% and 75% marked on the bar")


func test_every_level_speaks_the_galaxy_language_not_older_obstacles() -> void:
	var old := ["Gate", "CrushBlock", "PrismBeam", "CollapsingPath", "EnergyField", "Spikes", "RotatingArm", "Rotor",
		"PhaseBlock", "WallPanel", "ShadowBlock", "MirrorWall", "OrbitRing", "WhiteoutZone", "BlackColumn",
		"ShadowChaser", "MazePanel", "BinaryGate"]
	var seen := {}
	for i in WORLD.levels.size():
		_load(i)
		var name := WORLD.levels[i].display_name
		var here := {}
		for node in level.find_children("*", "", true, false):
			var script := node.get_script() as Script
			var kind := script.get_global_name() if script else &""
			assert_false(String(kind) in old, "%s: no older obstacle (%s)" % [name, kind])
			if kind != &"":
				here[kind] = true
				seen[kind] = true
			if node is AnimatableBody2D:
				seen[&"FloatingPanel"] = true
		assert_true(here.has(&"GravityGate") or here.has(&"FlipField"), "%s: gravity turns" % name)
		assert_true(here.has(&"GravityMine"), "%s: gravity mines" % name)
	for system in [&"GravityGate", &"FlipField", &"GravityMine", &"FallingAsteroid", &"CeilingTrap", &"OrbitalHazard",
			&"DualHazard", &"GravityEcho", &"GravityLens", &"GalaxyBlock", &"FloatingPanel"]:
		assert_true(seen.has(system), "World 03 uses %s" % system)


## Each level turns gravity more often than the one before it, and every
## change in the schedule really changes it (no gate that does nothing).
func test_gravity_schedules_alternate_and_grow() -> void:
	var previous := 0
	for i in WORLD.levels.size():
		_load(i)
		var name := WORLD.levels[i].display_name
		var events := level.get_gravity_events()
		assert_true(events.size() >= 4, "%s: at least four gravity changes (%d)" % [name, events.size()])
		var up := level.start_gravity_up
		for event in events:
			var next := event.y > 0.5
			assert_true(next != up, "%s: the change at x=%.0f turns gravity" % [name, event.x])
			up = next
		if i < 4:
			assert_true(events.size() >= previous, "%s: no fewer flips than the level before" % name)
		previous = events.size()


## A checkpoint stands on the surface that is the floor when you reach it
## (hanging from the ceiling while gravity pulls up), and so does the finish;
## a respawn there gets that gravity from the schedule.
func test_checkpoints_and_finish_sit_on_the_floor_of_their_moment() -> void:
	for i in WORLD.levels.size():
		_load(i)
		var name := WORLD.levels[i].display_name
		var spawn := level.get_spawn_feet_position().x
		var speed := MovementConfig.new().run_speed * level.data.speed_scale
		level.set_run_line(spawn, speed)
		var markers: Array[Node2D] = []
		for cp in level.get_checkpoints():
			markers.append(cp)
		markers.append(level.get_finish())
		for marker in markers:
			var t := (marker.global_position.x - spawn) / speed
			var up := level.gravity_up_at(t)
			var hanging := absf(wrapf(marker.global_rotation, -PI, PI)) > PI * 0.5
			assert_eq(hanging, up, "%s: %s at x=%.0f faces the floor of its moment" % [name, marker.name,
				marker.global_position.x])


func test_kill_lines_bound_both_surfaces() -> void:
	for i in WORLD.levels.size():
		_load(i)
		var name := WORLD.levels[i].display_name
		assert_true(level.kill_y > 0.0, "%s: kill line under the ground" % name)
		assert_true(level.kill_top < -6.0 * GameConst.TILE, "%s: kill line above the ceiling" % name)
		assert_true(level.camera_offset < -90.0, "%s: the view centres the corridor" % name)
