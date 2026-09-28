extends "res://tests/integration/test_world_01_playthrough.gd"
## The World 01 playthrough tests, on World 04: every route finishes (through
## every attach), the run is deterministic, and a respawn at each checkpoint
## keeps the timing and the surface of that checkpoint.


func world_index() -> int:
	return 3


## As World 01's, but the death after each checkpoint happens where its first
## tap would be (in World 04 a later tap can stand in for a skipped one): the
## respawn there must replay the first pass exactly, on that checkpoint's
## surface, to the finish.
func test_death_respawns_at_each_checkpoint_with_same_timing() -> void:
	for level in WORLD.levels.size():
		var route := Autoplay.route_for(world_index(), level)
		var harness := GameHarness.new(self, route)
		_harnesses.append(harness)
		await harness.start(level, world_index())
		for checkpoint in harness.game.level.get_checkpoints():
			var x := checkpoint.global_position.x / GameConst.TILE
			for tap in route:
				if tap > x:
					harness.kill_at.append(tap)
					break
		var forced := harness.kill_at.size()
		harness.game.press_jump()  # Tap to start.
		await harness.run_until(harness.is_state(GameSession.State.COMPLETE), 60 * 200)
		var name := WORLD.levels[level].display_name
		assert_eq(forced, 2, "%s: a death after each of the two checkpoints" % name)
		assert_eq(harness.deaths.size(), forced, "%s: exactly one forced death per checkpoint" % name)
		assert_eq(harness.game.state, GameSession.State.COMPLETE,
			"%s: finished after every respawn: timing after a respawn matches the first pass" % name)
		harness.free_game()
		await get_tree().physics_frame
