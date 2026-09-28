extends TestCase
## World 03 on the real game scene: gravity, camera, look and the run's
## bookkeeping around flips (docs/GDD.md §9C, the edge cases of the spec).
## The physics-only cases (jumps across a flip, two taps in a tick, flips on a
## platform edge or a moving platform, a ceiling corner) are in
## test_gravity_flip.

const WORLD := 2

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


## World 03 opens once Worlds 01 and 02 are complete (restarting a level
## goes through the unlock rules).
func _unlock_world_03() -> void:
	for path in ["res://levels/world_01/world_01.tres", "res://levels/world_02/world_02.tres"]:
		for data in (load(path) as WorldData).levels:
			SaveSystem.record_result(data.id, 100, 1, true)


func _gravity() -> GravityState:
	return h.game.gravity


func _flipping() -> Callable:
	return func() -> bool: return _gravity().phase == GravityState.Phase.FLIPPING


## First checkpoint of any World 03 level hanging from the ceiling (or
## standing on the ground): [level index, checkpoint index], or [] if none.
func _find_checkpoint(hanging: bool) -> Array:
	for level in 5:
		var probe := GameHarness.new(self)
		await probe.start(level, WORLD)
		var found := -1
		var cps := probe.game.level.get_checkpoints()
		for i in cps.size():
			if (absf(wrapf(cps[i].global_rotation, -PI, PI)) > PI * 0.5) == hanging:
				found = i
				break
		probe.free_game()
		await get_tree().physics_frame
		if found >= 0:
			return [level, found]
	return []


func test_a_gate_flips_gravity_and_the_view_stays_upright_steady_and_ahead() -> void:
	await _start(0)
	var camera := h.game.camera
	var player := h.game.player
	assert_true(await h.run_until(_flipping(), 60 * 20), "reached the first gate")
	assert_true(_gravity().up, "gravity pulls up after it")
	var centre_y := camera.get_screen_center_position().y
	var drift := 0.0
	var ahead := true
	var mirrored := false
	var landed := 0
	for i in 150:
		await h.tick()
		landed = landed + 1 if player.is_on_floor() and _gravity().up else 0
		if landed > 5 and i >= 40:
			break
		assert_eq(camera.rotation, 0.0, "the view never turns")
		drift = maxf(drift, absf(camera.get_screen_center_position().y - centre_y))
		ahead = ahead and camera.get_screen_center_position().x > player.global_position.x
		mirrored = mirrored or player.visual.scale.x < 0.0 or player.visual.rotation != 0.0 \
			or player.fx.scale.x < 0.0 or player.fx.rotation != 0.0
		assert_true(player.velocity.x > 0.0, "the run goes on to the right")
	assert_true(player.is_on_floor(), "standing on the ceiling")
	assert_true(drift < 8.0, "the view holds the corridor through the flip (moved %.1f px)" % drift)
	assert_true(ahead, "the view looks ahead, to the right")
	assert_false(mirrored, "the player is never mirrored left to right")
	assert_true(player.visual.is_upside_down() and player.visual.scale.y < 0.0, "turned over top to bottom only")
	assert_eq(h.game.hud.rotation, 0.0, "the HUD never turns")
	assert_eq(h.game.hud.transform, Transform2D.IDENTITY, "nor moves")
	assert_true(h.deaths.is_empty())


## Edge cases 1 and 18: a gate turns gravity when the player's centre reaches
## its x, whatever the player is doing there (running, jumping, high or low).
func test_a_gate_turns_gravity_at_its_x_whether_running_or_jumping() -> void:
	var at: Array[float] = []
	for jump_before in [false, true]:
		await _start(0)
		var gate_x: float = h.game.level.get_gravity_events()[0].x
		assert_true(await h.run_until(func() -> bool:
			return h.game.player.global_position.x >= gate_x - 1.5 * GameConst.TILE, 60 * 20), "near the gate")
		if jump_before:
			assert_true(h.game.player.is_on_floor(), "running")
			h.game.press_jump()  # Airborne and rising when the gate comes.
			await h.tick()
			assert_false(h.game.player.is_on_floor(), "jumping")
		assert_true(await h.run_until(func() -> bool: return _gravity().up, 60 * 5), "gravity turned")
		at.append(h.game.player.global_position.x)
		assert_true(absf(h.game.player.global_position.x - gate_x) <= h.game.player.get_run_speed() / 60.0 + 0.5,
			"turned on the tick the centre reached the gate")
		h.free_game()
		await get_tree().physics_frame
		h = null
	assert_near(at[0], at[1], 0.01, "the same x with or without a jump")


## Edge case 5: dying during the flip's transition: the respawn is clean.
func test_dying_during_a_flip_respawns_clean() -> void:
	await _start(0)
	assert_true(await h.run_until(_flipping(), 60 * 20), "flipping")
	await h.run_ticks(5)
	h.game.player.die(&"test")
	assert_true(await h.run_until(h.is_state(GameSession.State.PLAYING), 60 * 5), "back in play")
	var player := h.game.player
	assert_false(_gravity().up, "the start's gravity (no checkpoint passed yet)")
	assert_eq(_gravity().phase, GravityState.Phase.NORMAL, "no transition left over")
	assert_eq(h.game.camera.rotation, 0.0, "the view upright")
	assert_true(player.is_on_floor(), "standing")
	assert_eq(player.velocity.y, 0.0, "no vertical speed")
	assert_true(player.has_double_jump(), "both jumps")
	assert_false(player.visual.is_upside_down(), "the entity stands on the ground")
	assert_eq(player.visual.scale, Vector2.ONE, "upright")
	assert_true(await h.run_until(h.is_state(GameSession.State.COMPLETE), 60 * 60), "the run still finishes")
	assert_eq(h.deaths.size(), 1, "with no other death")


## Edge cases 6 and 20: a death just after a checkpoint on the ceiling comes
## back there: gravity up, the body turned over, the ceiling colours; the
## view stays upright (left to right).
func test_death_just_after_a_ceiling_checkpoint_respawns_upside_down() -> void:
	var where := await _find_checkpoint(true)
	assert_false(where.is_empty(), "World 03 has a checkpoint on the ceiling")
	if where.is_empty():
		return
	await _start(where[0])
	var cp: Checkpoint = h.game.level.get_checkpoints()[where[1]]
	assert_true(await h.run_until(func() -> bool: return h.game.get_respawn_point().feet.x >= cp.global_position.x - 32.0,
		60 * 60), "reached the checkpoint")
	await h.run_ticks(1)
	h.game.player.die(&"test")
	assert_true(await h.run_until(h.is_state(GameSession.State.PLAYING), 60 * 5), "back in play")
	var player := h.game.player
	assert_true(_gravity().up, "gravity pulls up")
	assert_near(player.global_position.x, cp.global_position.x, 12.0, "at the checkpoint")
	assert_true(player.is_on_floor(), "standing on the ceiling")
	assert_true(player.global_position.y > cp.global_position.y, "under it")
	assert_eq(h.game.camera.rotation, 0.0, "the view upright: the run still goes left to right")
	assert_true(player.visual.is_upside_down(), "the entity stands on the ceiling")
	assert_eq(player.visual.scale, Vector2(1.0, -1.0), "turned over top to bottom, never mirrored")
	var background := h.game.get_node(^"World/Background")
	assert_near(background._blend, 1.0, 0.001, "the ceiling's colours")
	assert_true(player.has_double_jump(), "both jumps")
	assert_true(await h.run_until(h.is_state(GameSession.State.COMPLETE), 60 * 90), "the run finishes from there")


## Edge case 7: a checkpoint on the ground after the ceiling brings back
## gravity down, the upright body and the ground's colours.
func test_death_after_a_ground_checkpoint_respawns_upright() -> void:
	var where := await _find_checkpoint(false)
	assert_false(where.is_empty(), "World 03 has a checkpoint on the ground")
	if where.is_empty():
		return
	await _start(where[0])
	var cp: Checkpoint = h.game.level.get_checkpoints()[where[1]]
	assert_true(await h.run_until(func() -> bool: return h.game.get_respawn_point().feet.x >= cp.global_position.x - 32.0,
		60 * 60), "reached the checkpoint")
	# Die later, wherever gravity is then.
	await h.run_ticks(90)
	if not h.game.player.is_dead():
		h.game.player.die(&"test")
	assert_true(await h.run_until(h.is_state(GameSession.State.PLAYING), 60 * 5), "back in play")
	assert_false(_gravity().up, "gravity pulls down")
	assert_eq(h.game.camera.rotation, 0.0, "upright view")
	assert_false(h.game.player.visual.is_upside_down(), "upright body")
	assert_true(h.game.player.is_on_floor(), "on the ground")
	var background := h.game.get_node(^"World/Background")
	assert_near(background._blend, 0.0, 0.001, "the ground's colours")


## Edge case 11: restarting while the world is turning starts the level
## over cleanly.
func test_restart_during_a_flip_starts_clean() -> void:
	_unlock_world_03()
	await _start(0)
	assert_true(await h.run_until(_flipping(), 60 * 20), "flipping")
	h.game.restart_level()
	await h.run_ticks(2)
	assert_eq(h.game.state, GameSession.State.READY, "waiting for the first tap")
	assert_false(_gravity().up, "gravity down")
	assert_eq(_gravity().phase, GravityState.Phase.NORMAL, "no transition")
	assert_eq(h.game.camera.rotation, 0.0, "upright")
	assert_false(h.game.player.visual.is_upside_down(), "upright body")
	assert_near(h.game.player.global_position.x, h.game.level.get_spawn_feet_position().x, 1.0, "at the start")


## Edge case 12: many restarts, some in the middle of a flip, leave no
## trace: same node count, and the level is still played through.
func test_repeated_restarts_leave_nothing_behind() -> void:
	_unlock_world_03()
	for data in (load("res://levels/world_03/world_03.tres") as WorldData).levels.slice(0, 1):
		SaveSystem.record_result(data.id, 100, 1, true)  # Level 02 opens after Level 01.
	await _start(1)
	await h.run_ticks(5)
	var nodes := -1
	for i in 10:
		h.game.restart_level()
		await h.run_ticks(3)
		if nodes < 0:
			nodes = Performance.get_monitor(Performance.OBJECT_NODE_COUNT)
		h.game.press_jump()
		h.seek(h.player_x())
		await h.run_ticks(60 + 37 * i)  # Some of these stop in the middle of a flip.
	h.game.restart_level()
	await h.run_ticks(3)
	assert_near(Performance.get_monitor(Performance.OBJECT_NODE_COUNT), nodes, 2.0, "no nodes pile up")
	h.game.press_jump()
	h.seek(h.player_x())
	h.deaths.clear()
	assert_true(await h.run_until(h.is_state(GameSession.State.COMPLETE), 60 * 90), "and it still plays through")
	assert_true(h.deaths.is_empty(), "without a death")


## A pause in the middle of a flip freezes it; resuming carries on.
func test_pause_during_a_flip_freezes_and_resumes_it() -> void:
	await _start(0)
	assert_true(await h.run_until(_flipping(), 60 * 20), "flipping")
	await h.run_ticks(3)
	h.game.pause()
	var progress := _gravity().transition_progress()
	var view := h.game.camera.global_position
	for i in 20:
		await get_tree().physics_frame
	assert_near(_gravity().transition_progress(), progress, 0.0001, "the transition waits")
	assert_eq(h.game.camera.global_position, view, "the view waits")
	h.game.resume()
	assert_true(await h.run_until(h.is_state(GameSession.State.COMPLETE), 60 * 60), "the run goes on to the end")
	assert_true(h.deaths.is_empty(), "without a death")


## Edge case 19: a level that ends on the ceiling right after its last
## flips reaches 100%; progress follows x only, so a flip never moves it back.
func test_progress_runs_through_flips_to_100_on_the_ceiling() -> void:
	await _start(1)
	var shown: Array[float] = []
	h.game.progress.changed.connect(func(v: float) -> void: shown.append(v))
	assert_true(await h.run_until(h.is_state(GameSession.State.COMPLETE), 60 * 90), "finished")
	assert_true(h.deaths.is_empty(), "without a death")
	assert_true(_gravity().up, "on the ceiling at the finish")
	assert_eq(h.game.progress.percent, 100.0, "100%")
	for i in range(1, shown.size()):
		assert_true(shown[i] >= shown[i - 1], "progress never goes back across a flip")


## A resumed run (a fresh engine after a lost WebGL context) at a ceiling
## checkpoint gets the ceiling's gravity, not the level start's.
func test_resume_at_a_ceiling_checkpoint_is_upside_down() -> void:
	var where := await _find_checkpoint(true)
	if where.is_empty():
		return
	h = GameHarness.new(self, Autoplay.route_for(WORLD, where[0]))
	await h.start(where[0], WORLD)
	h.game._resume(where[1], 40.0, 2, {"tiles": 100, "shards": 5})
	h.seek(h.player_x())
	assert_true(_gravity().up, "gravity up")
	assert_true(h.game.player.is_on_floor(), "standing on the ceiling")
	assert_eq(h.game.camera.rotation, 0.0, "the view upright")
	assert_true(h.game.player.visual.is_upside_down(), "the body turned over")
	assert_true(await h.run_until(h.is_state(GameSession.State.COMPLETE), 60 * 90), "the resumed run finishes")
	assert_true(h.deaths.is_empty(), "with the timing of the first pass")


func test_world_02_opens_world_03_and_world_03_opens_world_04() -> void:
	var w2: WorldData = load("res://levels/world_02/world_02.tres")
	var w3: WorldData = load("res://levels/world_03/world_03.tres")
	assert_false(Progression.is_world_unlocked(w3), "locked on a fresh save")
	for data in w2.levels:
		SaveSystem.record_result(data.id, 100, 1, true)
	assert_true(Progression.is_world_unlocked(w3), "open once World 02 is complete")
	h = GameHarness.new(self)
	await h.start(4, 1)
	h.game.press_jump()
	h.game._on_finish_reached()
	await h.run_ticks(80)  # Brake and results delay.
	var panel := h.game.complete_panel
	assert_eq((panel.get_node(^"%TitleLabel") as Label).text, "WORLD 02 COMPLETE")
	assert_eq((panel.get_node(^"%UnlockLabel") as Label).text, "WORLD 03 UNLOCKED")
	panel.next_pressed.emit()
	await h.run_ticks(2)
	assert_eq(h.game.world_index, 2, "NEXT goes on to World 03")
	assert_eq(h.game.level.data.id, &"w03_l01", "at First Flip")
	assert_true(Palette.is_galaxy(), "in the galaxy's colours")
	h.free_game()
	await get_tree().physics_frame
	for data in w3.levels:
		SaveSystem.record_result(data.id, 100, 1, true)
	h = GameHarness.new(self)
	await h.start(4, WORLD)
	h.game.press_jump()
	h.game._on_finish_reached()
	await h.run_ticks(80)
	panel = h.game.complete_panel
	assert_eq((panel.get_node(^"%TitleLabel") as Label).text, "WORLD 03 COMPLETE")
	assert_eq((panel.get_node(^"%UnlockLabel") as Label).text, "THE INVERTED GARDEN UNLOCKED")
	assert_true((panel.get_node(^"%NextButton") as Button).visible, "World 04 follows")
	panel.next_pressed.emit()
	await h.run_ticks(2)
	assert_eq(h.game.world_index, 3, "NEXT goes on to World 04")
	assert_eq(h.game.level.data.id, &"w04_l01", "at its first level")
	assert_true(Palette.is_garden(), "in the garden's colours")


## World 03's player is a white square (never the black entity of World 02),
## and each world keeps its own player; only the drawing changes.
func test_the_world_03_player_is_a_white_square() -> void:
	await _start(0)
	var visual := h.game.player.visual
	assert_true(visual.galaxy_style, "World 03 draws the white square")
	assert_false(visual.void_style, "not World 02's black entity")
	assert_false(visual.garden_style, "not World 04's seed")
	assert_eq(h.game.player.half_size, Vector2(24.0, 24.0), "the same 48 px square hitbox")
	h.game._use_world(1)  # Just the look (World 02 is locked on this save).
	assert_true(h.game.player.visual.void_style and not h.game.player.visual.galaxy_style, "World 02 keeps its entity")
	h.game._use_world(0)
	assert_false(h.game.player.visual.void_style or h.game.player.visual.galaxy_style, "World 01 keeps its core")
