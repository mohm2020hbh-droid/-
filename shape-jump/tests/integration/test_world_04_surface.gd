extends TestCase
## World 04 on the real game scene (docs/GDD.md §9D): the hop and the TAP
## TAP surface attach with the camera, the colours, the run's bookkeeping,
## and the edge cases of the spec (an attach during death, restart, pause, a
## lost WebGL context; respawns on either surface; the run always going left
## to right; never a double jump). The physics-only cases are in
## test_surface_attach.

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


## Opens World 04 in the sandboxed save (World 03 complete), so that the
## restart of a level (through the level select's unlock rules) is allowed.
func _unlock_world_04() -> void:
	var w3: WorldData = load("res://levels/world_03/world_03.tres")
	for data in w3.levels:
		SaveSystem.record_result(data.id, 100, 1, true)


func _gravity() -> GravityState:
	return h.game.gravity


func _run() -> SurfaceRun:
	return h.game.surface_run


func _backdrop() -> Node:
	return h.game.get_node(^"World/Background")


## Runs until the player is attaching (the first time after [param count] attaches).
func _until_attaching(count := 0) -> bool:
	return await h.run_until(func() -> bool: return _run().attaches > count and h.game.player.is_attaching(), 60 * 40)


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


func test_an_attach_never_turns_the_view_and_the_run_stays_left_to_right() -> void:
	await _start(0)
	var camera := h.game.camera
	assert_true(await _until_attaching(), "reached the first attach")
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
	assert_true(forward_ok, "moving right on every tick, through the attach")
	assert_true(anchor_ok, "at the same place on screen (left), the view looking right")
	assert_true(ahead_ok, "the view's centre is ahead, to the right")
	assert_eq(h.game.hud.transform, Transform2D.IDENTITY, "the HUD never moves")
	assert_true(h.deaths.is_empty())


func test_the_view_keeps_the_corridor_framed_through_an_attach() -> void:
	await _start(0)
	var camera := h.game.camera
	assert_true(await _until_attaching(), "attaching")
	var ys: Array[float] = []
	for i in 40:
		await h.tick()
		ys.append(camera.global_position.y)
		var view := camera.get_view_rect()
		assert_true(view.grow(-40.0).has_point(h.game.player.global_position), "the player stays well inside the view")
	# The view does not follow the arc of the crossing: it only settles on the
	# new surface's height (this first ceiling is lower than the level's
	# corridor), one way, by less than a tile. No bob: never back and forth.
	var lo: float = ys.min()
	var hi: float = ys.max()
	assert_true(hi - lo < T, "the view barely moves while the player crosses (%.1f px)" % (hi - lo))
	var turns := 0
	var heading := 0.0
	for i in range(1, ys.size()):
		var step := ys[i] - ys[i - 1]
		if absf(step) > 0.25:
			if heading != 0.0 and signf(step) != heading:
				turns += 1
			heading = signf(step)
	assert_eq(turns, 0, "no bob: the view settles one way (%s)" % str(ys))


func test_the_world_inverts_its_colours_with_the_surface() -> void:
	await _start(0)
	assert_eq(GardenLook.blend, 0.0, "the ground state")
	assert_true(GardenLook.player_color("body").is_equal_approx(GardenLook.PLAYER_GROUND.body), "a yellow player")
	var pivot: Node2D = _backdrop().get(&"_pivot")
	assert_eq(pivot.scale.y, 1.0, "the landscape upright")
	assert_true(await _until_attaching(), "attaching")
	assert_eq(GardenLook.blend, 0.0, "the colours hold while the player crosses (gravity turns on the touch)")
	assert_true(await h.run_until(func() -> bool: return _gravity().up, 60), "touched the ceiling")
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
	var phases: Array[SurfaceRun.Phase] = [_run().phase]
	var targets: Array[SurfaceRun.Surface] = []
	_run().phase_changed.connect(func(p: SurfaceRun.Phase, _old: SurfaceRun.Phase) -> void:
		if phases.is_empty() or phases[-1] != p:
			phases.append(p)
			if p == SurfaceRun.Phase.ATTACHING:
				targets.append(_run().target_surface))
	var gestures := {}
	for i in 60 * 40:
		await h.tick()
		gestures[_run().gesture] = true
		if _run().attaches >= 2 and h.game.player.is_on_floor() and not _gravity().up:
			break
	var want: Array[SurfaceRun.Phase] = [SurfaceRun.Phase.GROUND_RUN, SurfaceRun.Phase.HOPPING,
		SurfaceRun.Phase.GROUND_RUN, SurfaceRun.Phase.HOPPING, SurfaceRun.Phase.ATTACHING,
		SurfaceRun.Phase.CEILING_RUN, SurfaceRun.Phase.ATTACHING, SurfaceRun.Phase.GROUND_RUN]
	var at := 0
	for p in phases:
		if at < want.size() and p == want[at]:
			at += 1
	assert_eq(at, want.size(),
		"ground run -> hop -> ground run -> hop -> attaching -> ceiling run -> attaching -> ground run (%s)" % [phases])
	assert_false(SurfaceRun.Phase.AIRBORNE in phases, "never falling: every airborne moment is a hop")
	assert_true(_run().hops >= 3, "hops counted (%d)" % _run().hops)
	assert_eq(targets.slice(0, 2), [SurfaceRun.Surface.CEILING, SurfaceRun.Surface.GROUND] as Array[SurfaceRun.Surface],
		"each attach names its target surface")
	assert_true(gestures.has(SurfaceRun.Gesture.TAP_PENDING) and gestures.has(SurfaceRun.Gesture.IDLE),
		"the gesture goes idle -> tap pending (-> request, decided within the tick)")
	assert_eq(_run().surface, SurfaceRun.Surface.GROUND)
	assert_eq(_run().target_surface, SurfaceRun.Surface.NONE)
	assert_eq(_run().action, SurfaceRun.Action.NONE)
	assert_eq(_run().gravity_direction(), Vector2.DOWN)
	assert_eq(_run().surface_normal(), Vector2.UP)
	assert_true(_run().can_attach(), "ready for the next gesture")


func test_a_gesture_with_no_surface_across_is_only_the_hop() -> void:
	# The first checkpoint's runway: flat ground, open sky above it.
	var found: Array = await _find_checkpoint(false)
	await _start(found[0], false)
	var cp := h.game.level.get_checkpoints()[found[1]]
	h.start_run_at(cp.global_position)
	await h.run_ticks(3)
	var fails := [0]
	h.game.player.attach_failed.connect(func() -> void: fails[0] += 1)
	var y := h.game.player.global_position.y
	h.game.press_jump()
	await h.run_ticks(2)
	h.game.press_jump()
	await h.run_ticks(1)
	assert_eq(fails[0], 1, "a failed attach")
	assert_false(_gravity().up, "no turn, no teleport")
	assert_eq(_run().attaches, 0)
	assert_eq(_run().fails, 1)
	var top := 0.0
	for i in 40:
		await h.tick()
		top = maxf(top, y - h.game.player.global_position.y)
	assert_true(h.deaths.is_empty())
	assert_true(h.game.player.is_on_floor(), "the hop came down: back on the ground")
	assert_near(top, h.game.player.config.hop_height, 3.0, "only the hop: no fallback jump")
	assert_near(h.game.player.global_position.y, y, 0.5)


func test_hammering_the_screen_never_double_jumps_and_never_chains_attaches() -> void:
	await _start(0, false)
	var events: Array[String] = []
	h.game.player.double_jumped.connect(func() -> void: events.append("D"))
	h.game.player.attach_started.connect(func(_l: Vector2, _u: bool) -> void: events.append("A"))
	h.game.player.attached.connect(func(_u: bool) -> void: events.append("_"))
	var hop := h.game.player.config.hop_height
	for i in 150:
		h.game.press_jump()
		await h.tick()
		if not h.deaths.is_empty():
			break
		var p := h.game.player
		if not p.is_attaching():
			# Height of the feet off the floor, toward the other surface.
			var feet := p.get_feet_position().y
			var height := feet + h.game.level.corridor_height * T if _gravity().up else -feet
			assert_true(height <= hop + 3.0, "tick %d: never higher than one hop (%.1f px)" % [i, height])
	var s := "".join(events)
	assert_false("D" in s, "never a double jump (%s)" % s)
	assert_false("AA" in s, "never a second attach before touching down (%s)" % s)


func test_dying_while_attaching_respawns_clean_on_the_start_surface() -> void:
	await _start(0)
	assert_true(await _until_attaching(), "attaching")
	h.game.player.die(&"test")
	assert_true(await h.run_until(h.is_state(GameSession.State.PLAYING), 60 * 5), "back in play")
	await h.run_ticks(2)
	var player := h.game.player
	assert_false(player.is_attaching(), "no crossing carried over")
	assert_false(_gravity().up, "the start's surface (no checkpoint passed yet)")
	assert_true(player.is_on_floor(), "standing")
	var feet := player.global_position.y
	await h.run_ticks(3)
	assert_eq(player.global_position.y, feet, "at rest vertically")
	assert_true(player.can_attach(), "ready to attach")
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
	assert_eq(player.up_direction, Vector2.DOWN, "the ceiling is the floor")
	assert_near(player.get_feet_position().y, cp.global_position.y, 1.0, "at the checkpoint's surface")
	assert_eq(h.game.camera.rotation, 0.0, "the view upright: left to right")
	assert_eq(GardenLook.blend, 1.0, "yellow and black")
	assert_true(GardenLook.player_color("body").is_equal_approx(GardenLook.PLAYER_CEILING.body), "a blue player")
	assert_eq(_run().surface, SurfaceRun.Surface.CEILING)
	assert_eq(_run().respawn_surface, SurfaceRun.Surface.CEILING)
	assert_true(player.can_attach(), "ready to attach")
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
	assert_true(await _until_attaching(_run().attaches), "and attached again after it")
	h.game.player.die(&"test")
	assert_true(await h.run_until(h.is_state(GameSession.State.PLAYING), 60 * 5), "back in play")
	await h.run_ticks(2)
	assert_false(_gravity().up, "gravity toward the ground")
	assert_true(h.game.player.is_on_floor(), "standing on the ground")
	assert_eq(GardenLook.blend, 0.0, "blue and white")
	assert_eq(_run().respawn_surface, SurfaceRun.Surface.GROUND)
	assert_true(await h.run_until(h.is_state(GameSession.State.COMPLETE), 60 * 90), "the run finishes from there")


func test_restart_during_an_attach_starts_the_level_clean() -> void:
	_unlock_world_04()
	await _start(0)
	assert_true(await _until_attaching(), "attaching")
	h.game.restart_level()
	await h.run_ticks(3)  # (The level is rebuilt at once, behind a fade.)
	assert_eq(h.game.state, GameSession.State.READY)
	assert_false(_gravity().up, "the start's surface")
	assert_eq(_gravity().phase, GravityState.Phase.NORMAL, "no transition left over")
	assert_eq(GardenLook.blend, 0.0, "the start's colours")
	assert_false(h.game.player.is_attaching())
	assert_true(h.game.level.get_surface_log().is_empty())
	assert_eq(_run().attaches, 0)


func test_pause_during_an_attach_holds_it_and_resume_finishes_it() -> void:
	await _start(0)
	assert_true(await _until_attaching(), "attaching")
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


func test_the_last_level_ends_right_after_its_last_attach_at_100_percent() -> void:
	await _start(4)
	var last_attach := [-1]
	h.game.player.attached.connect(func(_u: bool) -> void: last_attach[0] = h.ticks)
	assert_true(await h.run_until(h.is_state(GameSession.State.COMPLETE), 60 * 120), "finished")
	assert_true(h.deaths.is_empty(), "without dying")
	assert_true(last_attach[0] >= 0 and h.ticks - last_attach[0] < 90, "the finish comes right after an attach")
	assert_eq(h.game.progress.percent, 100.0)
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
	_unlock_world_04()
	await _start(0)
	assert_true(await _until_attaching(), "attaching")
	var nodes := 0
	var inks := 0
	for i in 10:
		h.game.restart_level()
		await h.run_ticks(3)
		assert_eq(h.game.state, GameSession.State.READY, "restarted")
		assert_eq(h.game.surface_run.attaches, 0, "a fresh run")
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
	var moves := [0]
	h.game.player.jumped.connect(func() -> void: moves[0] += 1)
	h.game.player.attach_started.connect(func(_l: Vector2, _u: bool) -> void: moves[0] += 1)
	# Hammer the screen for as long as the death lasts (pause and fade).
	var taps := 0
	while h.game.state == GameSession.State.DYING and taps < 60 * 5:
		h.game.press_jump()
		await h.tick()
		taps += 1
	assert_true(taps > 20, "tapped through the whole death (%d taps)" % taps)
	assert_eq(h.game.state, GameSession.State.PLAYING, "back in play")
	await h.run_ticks(2)
	assert_eq(moves[0], 0, "no jump and no attach from a tap while dead or respawning")
	assert_true(h.game.player.is_on_floor(), "standing, not launched")
