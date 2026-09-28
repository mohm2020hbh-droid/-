extends "res://tests/unit/test_level_integrity.gd"
## The level structure rules, on World 04, plus its own (docs/GDD.md §9D):
## the garden look, locked until World 03 is complete, the last world; its
## organic obstacle language (no geometric obstacle of an older world); every
## checkpoint standing where only its own surface exists; the surface attach
## set up on every level, and routes that mix both moves: hops and TAP TAP
## surface attaches, up and down.

const T := GameConst.TILE


func world_index() -> int:
	return 3


func test_world_04_has_its_own_look_and_follows_world_03() -> void:
	assert_eq(WORLD.number, 4)
	assert_eq(WORLD.display_name, "The Inverted Garden")
	assert_eq(WORLD.theme, &"garden", "the garden palette")
	assert_true(WORLD.background != null and WORLD.background.resource_path.ends_with("background_garden.tscn"),
		"its own background")
	assert_eq(WORLD.requires.id, &"world_03", "unlocked by finishing World 03")
	assert_eq(WORLD.next_world_name, "", "the last world")
	assert_true(WORLD.surface_latch, "TAP TAP attaches (no jump)")
	assert_eq(WORLD.progress_milestones, [50.0, 75.0] as Array[float], "50% and 75% marked on the bar")


func test_every_level_is_a_surface_attach_level() -> void:
	for i in WORLD.levels.size():
		_load(i)
		var name := WORLD.levels[i].display_name
		assert_true(level.surface_latch, "%s: the player owns gravity" % name)
		assert_false(level.start_gravity_up, "%s: starts on the ground" % name)
		assert_true(level.kill_y > 0.0, "%s: kill line under the ground" % name)
		assert_true(level.kill_top < -6.0 * T, "%s: kill line above the ceiling" % name)
		assert_true(level.corridor_height >= 5.0 and level.corridor_height <= 5.5,
			"%s: declares its corridor (%.2f tiles; the camera frames it)" % [name, level.corridor_height])


func test_every_level_speaks_the_garden_language_not_older_obstacles() -> void:
	var old := ["Gate", "CrushBlock", "PrismBeam", "CollapsingPath", "EnergyField", "Spikes", "RotatingArm", "Rotor",
		"PhaseBlock", "WallPanel", "ShadowBlock", "MirrorWall", "OrbitRing", "WhiteoutZone", "BlackColumn",
		"ShadowChaser", "MazePanel", "BinaryGate", "GravityGate", "FlipField", "GravityMine", "FallingAsteroid",
		"CeilingTrap", "OrbitalHazard", "DualHazard", "GravityEcho", "GravityLens", "GalaxyBlock"]
	var seen := {}
	for i in WORLD.levels.size():
		_load(i)
		var name := WORLD.levels[i].display_name
		var hazards := 0
		for node in level.find_children("*", "", true, false):
			var script := node.get_script() as Script
			var kind := script.get_global_name() if script else &""
			assert_false(String(kind) in old, "%s: no older obstacle (%s)" % [name, kind])
			if kind != &"":
				seen[kind] = true
			if node is GardenHazard:
				hazards += 1
			if node is StaticBody2D or node is AnimatableBody2D:
				# Terrain is garden islands only: no bare geometric block.
				assert_true(node is GardenBlock, "%s: terrain is garden (%s)" % [name, node.name])
				if node is AnimatableBody2D:
					seen[&"MovingCeiling"] = true
				if node is GardenBlock and not (node as GardenBlock).latchable:
					seen[&"SlickStone"] = true
			if node is FallingRock and (node as FallingRock).ice:
				seen[&"IceFall"] = true
			if node is GardenWeather:
				seen[&"Snow" if (node as GardenWeather).kind == GardenWeather.Kind.SNOW else &"LeafStorm"] = true
		assert_true(hazards >= 12, "%s: a full garden (%d obstacles)" % [name, hazards])
	# (No WindBurst: with no jump, a gust could only lift a runner off its
	# floor; the wind is the leaf storm and the leaves riding it.)
	for system in [&"RisingRoots", &"SweepingBranch", &"ClosingFlower", &"WaterWave", &"Waterfall",
			&"FallingRock", &"HangingVines", &"LeafGlider", &"InkFlow", &"CanvasCurtain", &"HangingBoulder",
			&"BirdFlock", &"IceFall", &"Snow", &"LeafStorm", &"GardenBlock", &"SlickStone", &"MovingCeiling"]:
		assert_true(seen.has(system), "World 04 uses %s" % system)


## A checkpoint stands where the surface it is on is the only one (the
## other side opens onto the sky), so it can only be reached, and respawned
## on, on that surface: nothing across the corridor within 2 tiles.
func test_checkpoints_and_the_finish_stand_where_only_their_surface_exists() -> void:
	for i in WORLD.levels.size():
		_load(i)
		var name := WORLD.levels[i].display_name
		for cp in level.get_checkpoints():
			var x := cp.global_position.x
			var hanging := GameSession.checkpoint_hangs(cp)
			for node in level.find_children("*", "StaticBody2D", true, false) + \
					level.find_children("*", "AnimatableBody2D", true, false):
				var block := node as Block
				var rect := block.get_rect()
				rect.position += block.global_position
				if rect.end.x < x - 2.0 * T or rect.position.x > x + 2.0 * T:
					continue
				var across := rect.position.y > cp.global_position.y + 1.0 if hanging \
					else rect.end.y < cp.global_position.y - 1.0
				assert_false(across, "%s: nothing across from the checkpoint at x=%.0f (%s)" % [name, x, block.name])


## Every level asks for both moves, and more of them than the one before:
## single taps (hops, never within an attach window of another tap, or they
## would be one gesture) and TAP TAP gestures (two taps a few ticks apart)
## that turn the world over, up and down alike.
func test_levels_mix_hops_and_attaches_and_ask_for_more_as_they_go() -> void:
	var previous := 0
	for i in WORLD.levels.size():
		var route := Autoplay.route_for(world_index(), i)
		var tick := 520.0 * WORLD.levels[i].speed_scale / 60.0 / T
		var window := 0.3 * 60.0
		var hops := 0
		var attaches := 0
		var k := 0
		while k < route.size():
			if k + 1 < route.size() and (route[k + 1] - route[k]) / tick < window:
				var gap := (route[k + 1] - route[k]) / tick
				assert_true(gap >= 2.0, "level %d: taps %d-%d are one TAP TAP (%.1f ticks apart)" % [i + 1, k + 1, k + 2, gap])
				if k + 2 < route.size():
					# (A tap during the crossing would be swallowed: the next
					# move comes once it has touched down.)
					assert_true((route[k + 2] - route[k + 1]) / tick > 8.0,
						"level %d: the next move after the TAP TAP at %d comes after its crossing" % [i + 1, k + 2])
				attaches += 1
				k += 2
			else:
				hops += 1
				k += 1
		var name := WORLD.levels[i].display_name
		assert_true(hops >= 10, "%s: the hop is a real move (%d hops)" % [name, hops])
		assert_true(attaches >= 10, "%s: TAP TAP attaches (%d)" % [name, attaches])
		assert_true(hops + attaches >= previous, "%s: no fewer moves than the level before (%d)" % [name, hops + attaches])
		previous = hops + attaches
