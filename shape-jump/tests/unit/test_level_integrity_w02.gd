extends "res://tests/unit/test_level_integrity.gd"
## The level structure rules, on World 02, plus its own: black and white,
## its own background, and locked until World 01 is complete.


func world_index() -> int:
	return 1


func test_world_02_has_its_own_look_and_follows_world_01() -> void:
	assert_eq(WORLD.number, 2)
	assert_eq(WORLD.theme, &"mono", "black and white")
	assert_true(WORLD.background != null and WORLD.background.resource_path.ends_with("background_mono.tscn"),
		"its own background")
	assert_eq(WORLD.requires.id, &"world_01", "unlocked by finishing World 01")
	assert_eq(WORLD.next_world_name, "World 03", "World 03 follows")


func test_every_level_uses_the_new_systems_not_world_01_obstacles() -> void:
	var old := ["Gate", "CrushBlock", "PrismBeam", "CollapsingPath", "EnergyField", "Spikes", "RotatingArm", "Rotor",
		"PhaseBlock", "WallPanel"]
	var seen := {}
	for i in WORLD.levels.size():
		_load(i)
		for node in level.find_children("*", "", true, false):
			var script := node.get_script() as Script
			var name := script.get_global_name() if script else &""
			assert_false(String(name) in old, "%s: no World 01 obstacle (%s)" % [WORLD.levels[i].display_name, name])
			if name != &"":
				seen[name] = true
			if node is AnimatableBody2D:
				seen[&"FloatingPanel"] = true
	for system in [&"ShadowBlock", &"MirrorWall", &"OrbitRing", &"WhiteoutZone", &"BlackColumn", &"ShadowChaser",
			&"MazePanel", &"BinaryGate", &"FloatingPanel"]:
		assert_true(seen.has(system), "World 02 uses %s" % system)
