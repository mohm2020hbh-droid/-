extends TestCase
## World 04 on the real game scene (docs/GDD.md §9D): the surface latch with
## the camera, the colours, the run's bookkeeping, and the edge cases of the
## spec (a latch during death, restart, pause, a lost WebGL context; respawns
## on either surface; the run always going left to right). The physics-only
## cases are in test_surface_latch.

const WORLD := 3
const T := GameConst.TILE

var h: GameHarness
var _save := SaveSandbox.new()


func before_each() -> void:
	_save.enter()


func after_each() -> void:
	if h:
		h.free_game()
		h = null
	await get_tree().physics_frame
	get_tree().paused = false
	_save.leave()


func _start(level: int, with_route := true) -> void:
	h = GameHarness.new(self, Autoplay.route_for(WORLD, level) if with_route else PackedFloat32Array())
	await h.start(level, WORLD)
	h.game.press_jump()  # Tap to start.


func _gravity() -> GravityState:
	return h.game.gravity


func _run() -> SurfaceRun:
	return h.game.surface_run


func _backdrop() -> Node:
	return h.game.get_node(^"World/Background")


## Runs until the player is latching (the first time after [param count] latches).
func _until_latching(count := 0) -> bool:
	return await h.run_until(func() -> bool: return _run().latches > count and h.game.player.is_latching(), 60 * 40)


## Until the player stands on the surface [param up], transition over.
func _until_standing(up: bool) -> bool:
	return await h.run_until(func() -> bool:
		return h.game.player.is_on_floor() and _gravity().up == up and _gravity().phase == GravityState.Phase.NORMAL,
		60 * 40)


## First checkpoint of any World 04 level that hangs (or stands): [level, index].
func _find_checkpoint(hanging: bool) -> Array:
	for level in 5:
		var probe := GameHarness.new(self)
		await probe.start(level, WORLD)
		var found := -1
		var cps := probe.game.level.get_checkpoints()
		for i in cps.size():
			if GameSession.checkpoint_hangs(cps[i]) == hanging:
				found = i
				break
		probe.free_game()
		await get_tree().physics_frame
		if found >= 0:
			return [level, found]
	return []


func test_a_latch_never_turns_the_view_and_the_run_stays_left_to_right() -> void:
	await _start(0)
	var camera := h.game.camera
	assert_true(await _until_latching(), "reached the first latch")
	var last_x := h.game.player.global_position.x
	var anchor_ok := true
	var ahead_ok := true
	var forward_ok := true
	for i in 90:
		await h.tick()
		assert_eq(camera.rotation, 0.0, "the view never turns")
		var view := camera.get_view_rect()
		var px := h.game.player.global_position.x
		forward_ok = forward_ok and px > last_x
		last_x = px
		# The player keeps its place on the left of the screen: the way
		# ahead is always on the right.
		anchor_ok = anchor_ok and absf((px - view.position.x) / view.size.x - camera.screen_anchor_x) < 0.02
		ahead_ok = ahead_ok and camera.get_screen_center_position().x > px
	assert_true(_gravity().up, "on the ceiling now")
	assert_true(forward_ok, "moving right on every tick, through the latch")
	assert_true(anchor_ok, "at the same place on screen (left), the view looking right")
	assert_true(ahead_ok, "the view's centre is ahead, to the right")
	assert_eq(h.game.hud.transform, Transform2D.IDENTITY, "the HUD never moves")
	assert_true(h.deaths.is_empty())


func test_the_view_keeps_the_corridor_framed_through_a_latch() -> void:
	await _start(0)
	var camera := h.game.camera
	assert_true(await _until_latching(), "latching")
	var ys: Array[float] = []
	for i in 40:
		await h.tick()
		ys.append(camera.global_position.y)
	var lo: float = ys.min()
	var hi: float = ys.max()
	assert_true(hi - lo < 24.0, "no bob: the view barely moves while the player crosses (%.1f px)" % (hi - lo))
	var view := camera.get_view_rect()
	var player := h.game.player.global_position
	assert_true(view.grow(-40.0).has_point(player), "the player is well inside the view")


func test_the_world_inverts_its_colours_with_the_surface() -> void:
	await _start(0)
	assert_eq(GardenLook.blend, 0.0, "the ground state")
	assert_true(GardenLook.player_color("body").is_equal_approx(GardenLook.PLAYER_GROUND.body), "a yellow player")
	var pivot: Node2D = _backdrop().get(&"_pivot")
	assert_eq(pivot.scale.y, 1.0, "the landscape upright")
	assert_true(await _until_latching(), "latching")
	await h.run_ticks(2)
	var mid := GardenLook.blend
	assert_true(mid > 0.0 and mid < 1.0, "the colours cross over the transition (%.2f), no hard cut" % mid)
	assert_true(await _until_standing(true), "on the ceiling")
	await h.run_ticks(2)
	assert_eq(GardenLook.blend, 1.0, "the ceiling state")
	assert_true(GardenLook.player_color("body").is_equal_approx(GardenLook.PLAYER_CEILING.body), "a blue player")
	assert_near(pivot.scale.y, -1.0, 0.001, "the landscape hangs upside down")
	var block: GardenBlock = h.game.level.find_children("*", "GardenBlock", true, false)[0]
	assert_true(block.self_modulate.is_equal_approx(GardenLook.role_color(&"garden_soil", 1.0)), "terrain in its ceiling colour")
	var hazard: GardenHazard = h.game.level.find_children("*", "", true, false).filter(
		func(n: Node) -> bool: return n is GardenHazard)[0]
	assert_true(hazard.self_modulate.is_equal_approx(GardenLook.role_color(&"garden_ink", 1.0)), "ink in gold and black")
	assert_true(await _until_standing(false), "back on the ground")
	await h.run_ticks(2)
	assert_eq(GardenLook.blend, 0.0, "back to blue and white")
	assert_eq(pivot.scale.y, 1.0, "the landscape upright again")


func test_the_run_state_names_every_phase_of_a_crossing() -> void:
	await _start(0)
	var phases: Array[SurfaceRun.Phase] = []
	_run().phase_changed.connect(func(p: SurfaceRun.Phase, _old: SurfaceRun.Phase) -> void:
		if phases.is_empty() or phases[-1] != p:
			phases.append(p))
	assert_true(await _until_standing(true), "up")
	assert_true(await _until_standing(false), "and down")
	var want: Array[SurfaceRun.Phase] = [SurfaceRun.Phase.AIRBORNE, SurfaceRun.Phase.LATCHING,
		SurfaceRun.Phase.CEILING_RUN, SurfaceRun.Phase.RELEASE, SurfaceRun.Phase.LATCHING, SurfaceRun.Phase.GROUND_RUN]
	var at := 0
	for p in phases:
		if at < want.size() and p == want[at]:
			at += 1
	assert_eq(at, want.size(), "ground -> airborne -> latching -> ceiling -> release -> latching -> ground (%s)" % [phases])
	assert_eq(_run().surface, SurfaceRun.Surface.GROUND)
	assert_eq(_run().gravity_direction(), Vector2.DOWN)
	assert_eq(_run().surface_normal(), Vector2.UP)
	assert_true(_run().has_second_tap(), "the second tap is back on the ground")


func test_a_second_tap_out_of_reach_is_spent_without_moving_anything() -> void:
	await _start(0, false)
	await h.run_ticks(10)
	var misses := [0]
	h.game.player.latch_missed.connect(func() -> void: misses[0] += 1)
	# Under the open sky at the start: nothing to latch to.
	h.game.press_jump()
	await h.run_ticks(12)
	h.game.press_jump()
	await h.run_ticks(1)
	assert_eq(misses[0], 1, "a miss")
	assert_false(_gravity().up, "no flip, no teleport")
	assert_eq(_run().latches, 0)
	assert_false(_run().has_second_tap(), "spent until landing")
	assert_true(await h.run_until(func() -> bool: return h.game.player.is_on_floor(), 60), "lands where it would have")
	assert_true(_run().has_second_tap(), "restored on landing")


func test_hammering_the_screen_never_gives_a_third_action_or_a_latch_without_a_surface() -> void:
	await _start(0, false)
	var events: Array[String] = []
	h.game.player.jumped.connect(func() -> void: events.append("J"))
	h.game.player.latched.connect(func(_l: Vector2) -> void: events.append("L"))
	h.game.player.latch_missed.connect(func() -> void: events.append("m"))
	h.game.player.landed.connect(func(_s: float) -> void: events.append("_"))
	for i in 150:
		h.game.press_jump()
		await h.tick()
		if not h.deaths.is_empty():
			break
	var air := 0
	for e in events:
		if e == "_" or e == "J":
			air = 0 if e == "_" else 1
		else:
			air += 1
			assert_true(air <= 2, "at most one air tap per airtime (%s)" % "".join(events))
	assert_true(events.count("L") == 0 or events.find("L") > events.find("J"), "a latch only after a jump")


func test_dying_while_latching_respawns_clean_on_the_start_surface() -> void:
	await _start(0)
	assert_true(await _until_latching(), "latching")
	h.game.player.die(&"test")
	assert_true(await h.run_until(h.is_state(GameSession.State.PLAYING), 60 * 5), "back in play")
	await h.run_ticks(2)
	var player := h.game.player
	assert_false(player.is_latching(), "no crossing carried over")
	assert_false(_gravity().up, "the start's surface (no checkpoint passed yet)")
	assert_true(player.is_on_floor(), "standing")
	assert_eq(player.velocity.y, 0.0, "at rest vertically")
	assert_true(player.has_double_jump(), "the second tap is back")
	assert_eq(h.game.camera.rotation, 0.0)
	assert_eq(GardenLook.blend, 0.0, "blue and white again")
	assert_true(h.game.level.get_surface_log().is_empty(), "a fresh surface log")
	assert_true(await h.run_until(h.is_state(GameSession.State.COMPLETE), 60 * 90), "and the run still finishes")


func test_a_respawn_on_a_ceiling_checkpoint_puts_everything_on_the_ceiling() -> void:
	var found := await _find_checkpoint(true)
	assert_false(found.is_empty(), "World 04 has a checkpoint hanging from a ceiling")
	if found.is_empty():
		return
	await _start(found[0])
	var cp: Checkpoint = h.game.level.get_checkpoints()[found[1]]
	assert_true(await h.run_until(func() -> bool: return h.game.player.global_position.x > cp.global_position.x + T,
		60 * 90), "passed it")
	assert_true(_gravity().up, "on the ceiling there")
	h.game.player.die(&"test")
	assert_true(await h.run_until(h.is_state(GameSession.State.PLAYING), 60 * 5), "back in play")
	await h.run_ticks(2)
	var player := h.game.player
	assert_true(_gravity().up, "gravity toward the ceiling")
	assert_true(player.is_on_floor(), "standing on the ceiling")
	assert_eq(player.up_direction, Vector2.DOWN, "jumps push toward the ground")
	assert_near(player.get_feet_position().y, cp.global_position.y, 1.0, "at the checkpoint's surface")
	assert_eq(h.game.camera.rotation, 0.0, "the view upright: left to right")
	assert_eq(GardenLook.blend, 1.0, "yellow and black")
	assert_true(GardenLook.player_color("body").is_equal_approx(GardenLook.PLAYER_CEILING.body), "a blue player")
	assert_eq(_run().surface, SurfaceRun.Surface.CEILING)
	assert_eq(_run().respawn_surface, SurfaceRun.Surface.CEILING)
	assert_true(player.has_double_jump(), "the second tap ready")
	assert_true(await h.run_until(h.is_state(GameSession.State.COMPLETE), 60 * 90), "the run finishes from there")


func test_a_respawn_on_a_ground_checkpoint_puts_everything_on_the_ground() -> void:
	var found := await _find_checkpoint(false)
	assert_false(found.is_empty(), "World 04 has a checkpoint on the ground")
	if found.is_empty():
		return
	await _start(found[0])
	var cp: Checkpoint = h.game.level.get_checkpoints()[found[1]]
	assert_true(await h.run_until(func() -> bool: return h.game.player.global_position.x > cp.global_position.x + T,
		60 * 90), "passed it")
	# Die later, wherever the run is (maybe on the ceiling).
	assert_true(await _until_latching(_run().latches), "and latched again after it")
	h.game.player.die(&"test")
	assert_true(await h.run_until(h.is_state(GameSession.State.PLAYING), 60 * 5), "back in play")
	await h.run_ticks(2)
	assert_false(_gravity().up, "gravity toward the ground")
	assert_true(h.game.player.is_on_floor(), "standing on the ground")
	assert_eq(GardenLook.blend, 0.0, "blue and white")
	assert_eq(_run().respawn_surface, SurfaceRun.Surface.GROUND)
	assert_true(await h.run_until(h.is_state(GameSession.State.COMPLETE), 60 * 90), "the run finishes from there")


func test_restart_during_a_latch_starts_the_level_clean() -> void:
	await _start(0)
	assert_true(await _until_latching(), "latching")
	h.game.restart_level()
	await h.run_ticks(3)
	assert_eq(h.game.state, GameSession.State.READY)
	assert_false(_gravity().up, "the start's surface")
	assert_eq(_gravity().phase, GravityState.Phase.NORMAL, "no transition left over")
	assert_eq(GardenLook.blend, 0.0, "the start's colours")
	assert_false(h.game.player.is_latching())
	assert_true(h.game.level.get_surface_log().is_empty())
	assert_eq(_run().latches, 0)


func test_pause_during_a_latch_holds_it_and_resume_finishes_it() -> void:
	await _start(0)
	assert_true(await _until_latching(), "latching")
	var at := h.game.player.global_position
	h.game.pause()
	await h.run_ticks(20)
	assert_eq(h.game.state, GameSession.State.PAUSED)
	assert_eq(h.game.player.global_position, at, "frozen mid-crossing")
	h.game.resume()
	assert_true(await _until_standing(true), "the crossing completes after resuming")
	assert_true(h.deaths.is_empty())


func test_a_resumed_run_after_a_lost_webgl_context_is_back_on_its_ceiling() -> void:
	var found := await _find_checkpoint(true)
	if found.is_empty():
		fail("no hanging checkpoint in World 04")
		return
	h = GameHarness.new(self, Autoplay.route_for(WORLD, found[0]))
	await h.start(found[0], WORLD)
	h.game._resume(found[1], 55.0, 3, {"tiles": 100, "shards": 5})
	h.seek(h.player_x())
	await h.run_ticks(2)
	assert_eq(h.game.state, GameSession.State.PLAYING)
	assert_true(_gravity().up, "the checkpoint's surface")
	assert_true(h.game.player.is_on_floor(), "standing on the ceiling")
	assert_eq(GardenLook.blend, 1.0, "yellow and black")
	assert_eq(h.game.camera.rotation, 0.0)
	assert_true(await h.run_until(h.is_state(GameSession.State.COMPLETE), 60 * 90), "the resumed run finishes")
	assert_true(h.deaths.is_empty(), "with the same timing as the first pass")


func test_the_last_level_ends_right_after_its_last_latch_at_100_percent() -> void:
	await _start(4)
	var last_latch := [-1]
	h.game.player.latched.connect(func(_l: Vector2) -> void: last_latch[0] = h.ticks)
	assert_true(await h.run_until(h.is_state(GameSession.State.COMPLETE), 60 * 120), "finished")
	assert_true(h.deaths.is_empty(), "without dying")
	assert_true(last_latch[0] >= 0 and h.ticks - last_latch[0] < 60, "the finish comes within a second of a latch")
	assert_eq(h.game.progress.percent, 100.0)
	assert_true(_gravity().up, "finishing on the ceiling")
	assert_true(h.game.player.invulnerable, "safe once finished")


func test_world_03_opens_world_04_and_world_04_is_the_last() -> void:
	var w3: WorldData = load("res://levels/world_03/world_03.tres")
	var w4: WorldData = load("res://levels/world_04/world_04.tres")
	assert_false(Progression.is_world_unlocked(w4), "locked on a fresh save")
	for data in w3.levels:
		SaveSystem.record_result(data.id, 100, 1, true)
	assert_true(Progression.is_world_unlocked(w4), "open once World 03 is complete")
	for data in w4.levels:
		SaveSystem.record_result(data.id, 100, 1, true)
	h = GameHarness.new(self)
	await h.start(4, WORLD)
	h.game.press_jump()
	h.game._on_finish_reached()
	await h.run_ticks(80)
	var panel := h.game.complete_panel
	assert_eq((panel.get_node(^"%TitleLabel") as Label).text, "WORLD 04 COMPLETE")
	assert_false((panel.get_node(^"%NextButton") as Button).visible, "no World 05")


func test_ten_restarts_in_a_row_leave_nothing_behind() -> void:
	await _start(0)
	assert_true(await _until_latching(), "latching")
	var nodes := 0
	var inks := 0
	for i in 10:
		h.game.restart_level()
		await h.run_ticks(3)
		h.game.press_jump()
		await h.run_ticks(40)
		if i == 1:
			nodes = get_tree().get_node_count()
			inks = get_tree().get_nodes_in_group(&"garden_ink").size()
	assert_eq(get_tree().get_node_count(), nodes, "the same number of nodes after ten restarts")
	assert_eq(get_tree().get_nodes_in_group(&"garden_ink").size(), inks, "no stale obstacle in the colour roles")


func test_taps_during_the_death_and_the_respawn_do_nothing() -> void:
	await _start(0)
	await h.run_ticks(20)
	h.game.player.die(&"test")
	var jumps := [0]
	h.game.player.jumped.connect(func() -> void: jumps[0] += 1)
	for i in 40:
		h.game.press_jump()
		await h.tick()
	assert_true(await h.run_until(h.is_state(GameSession.State.PLAYING), 60 * 5), "back in play")
	await h.run_ticks(2)
	assert_eq(jumps[0], 0, "no jump from a tap while dead or respawning")
	assert_true(h.game.player.is_on_floor(), "standing, not launched")
