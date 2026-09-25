extends TestCase
## Start, input, death, respawn, restart and pause on the real game scene.

var h: GameHarness
var _save := SaveSandbox.new()


func before_each() -> void:
	_save.enter()
	h = GameHarness.new(self, Level01Route.TAPS)
	await h.start()


func after_each() -> void:
	get_tree().paused = false
	h.free_game()
	await get_tree().physics_frame
	_save.leave()


func _count_jumps() -> Array[int]:
	var jumps: Array[int] = [0]
	h.game.player.jumped.connect(func() -> void: jumps[0] += 1)
	return jumps


func _touch(index: int) -> void:
	var touch := InputEventScreenTouch.new()
	touch.index = index
	touch.pressed = true
	touch.position = Vector2(640, 400)
	h.game.get_viewport().push_input(touch)


func test_first_tap_starts_the_run_without_jumping() -> void:
	var jumps := _count_jumps()
	assert_eq(h.game.state, GameSession.State.READY)
	h.game.press_jump()
	await h.run_ticks(30)
	assert_eq(h.game.state, GameSession.State.PLAYING)
	assert_eq(jumps[0], 0, "the start tap is not a jump")
	assert_true(h.game.player.velocity.x > 0.0, "running")


func test_a_tap_delivered_twice_in_one_frame_counts_once() -> void:
	var jumps := _count_jumps()
	# The same tap as a raw extra-finger touch and as Godot's emulated click.
	_touch(2)
	var click := InputEventMouseButton.new()
	click.device = InputEvent.DEVICE_ID_EMULATION
	click.button_index = MOUSE_BUTTON_LEFT
	click.pressed = true
	click.position = Vector2(640, 400)
	h.game.get_viewport().push_input(click)
	await h.run_ticks(30)
	assert_eq(h.game.state, GameSession.State.PLAYING)
	assert_eq(jumps[0], 0, "starting tap did not also jump")


func test_extra_finger_tap_jumps() -> void:
	var jumps := _count_jumps()
	h.game.press_jump()
	await h.run_ticks(10)
	_touch(1)  # A second finger while the first rests on the screen.
	await h.run_ticks(2)
	assert_eq(jumps[0], 1, "second finger tap jumped")
	await h.run_ticks(60)
	_touch(0)  # Raw first-finger touches are left to the emulated mouse click.
	await h.run_ticks(2)
	assert_eq(jumps[0], 1, "raw first-finger touch is not counted twice")


func test_death_before_any_checkpoint_resets_to_spawn() -> void:
	h.skip = [0]
	h.game.press_jump()
	await h.run_until(func() -> bool: return not h.deaths.is_empty())
	assert_true(h.game.score.shards > 0, "had collected runway shards")
	await h.run_until(h.is_state(GameSession.State.PLAYING))
	var game := h.game
	assert_near(game.player.get_feet_position().x, game.level.get_spawn_feet_position().x, 0.5, "back at spawn")
	assert_near(game.level.clock, 0.0, 0.001, "level time rewound to 0")
	assert_eq(game.score.shards, 0, "shards rolled back")
	assert_eq(game.score.get_score(), 0, "score rolled back")
	assert_false(game.player.is_dead())


func test_death_after_a_checkpoint_keeps_progress_before_it() -> void:
	h.skip = [7]  # First tap after checkpoint A.
	h.game.press_jump()
	await h.run_until(func() -> bool: return not h.deaths.is_empty())
	var point := h.game.get_respawn_point()
	assert_true(point.feet.x > h.game.level.get_spawn_feet_position().x, "a checkpoint was recorded")
	await h.run_until(h.is_state(GameSession.State.PLAYING))
	var game := h.game
	assert_near(game.player.get_feet_position().x, point.feet.x, 0.5, "back at the checkpoint")
	assert_near(game.level.clock, point.level_time, 0.001, "level time rewound to the checkpoint")
	assert_eq(game.score.shards, point.score.shards, "shards from before the checkpoint are kept")
	assert_true(game.score.shards > 0)


func test_restart_from_pause_resets_everything() -> void:
	h.game.press_jump()
	await h.run_until(func() -> bool: return h.player_x() >= 60.0)
	var old_level := h.game.level
	h.game.pause()
	assert_eq(h.game.state, GameSession.State.PAUSED)
	assert_true(get_tree().paused)
	h.game.restart_level()
	await h.run_ticks(2)
	var game := h.game
	assert_false(get_tree().paused, "unpaused")
	assert_eq(game.state, GameSession.State.READY, "waiting for the first tap")
	assert_true(game.level != old_level, "fresh level instance")
	assert_near(game.player.get_feet_position().x, game.level.get_spawn_feet_position().x, 0.5, "at spawn")
	assert_eq(game.player.velocity.x, 0.0, "not running yet")
	assert_eq(game.score.get_score(), 0)
	assert_eq(game.level.clock, 0.0)


func test_restart_while_dying_cancels_the_pending_respawn() -> void:
	h.skip = [0]
	h.game.press_jump()
	await h.run_until(h.is_state(GameSession.State.DYING))
	h.game.restart_level()
	await h.run_ticks(120)
	assert_eq(h.game.state, GameSession.State.READY, "the old death sequence did not fire")
	assert_false(h.game.player.is_dead())
	assert_eq(h.game.player.velocity.x, 0.0)


func test_pause_during_death_then_resume_still_respawns() -> void:
	h.skip = [0]
	h.game.press_jump()
	await h.run_until(h.is_state(GameSession.State.DYING))
	h.game.pause()
	await h.run_ticks(120)
	assert_eq(h.game.state, GameSession.State.PAUSED, "death sequence frozen while paused")
	assert_true(h.game.player.is_dead())
	h.game.resume()
	var respawned := await h.run_until(h.is_state(GameSession.State.PLAYING), 120)
	assert_true(respawned, "respawn continued after resume")
	assert_false(h.game.player.is_dead())
