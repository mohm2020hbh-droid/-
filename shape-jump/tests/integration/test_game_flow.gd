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


## [ground jumps, double jumps], counted live.
func _count_jumps() -> Array[int]:
	var jumps: Array[int] = [0, 0]
	h.game.player.jumped.connect(func() -> void: jumps[0] += 1)
	h.game.player.double_jumped.connect(func() -> void: jumps[1] += 1)
	return jumps


func _touch(index: int, position := Vector2(640, 400)) -> void:
	var touch := InputEventScreenTouch.new()
	touch.index = index
	touch.pressed = true
	touch.position = position
	h.game.get_viewport().push_input(touch)


func _emulated_click(position := Vector2(640, 400)) -> void:
	var click := InputEventMouseButton.new()
	click.device = InputEvent.DEVICE_ID_EMULATION
	click.button_index = MOUSE_BUTTON_LEFT
	click.pressed = true
	click.position = position
	h.game.get_viewport().push_input(click)


func test_first_tap_starts_the_run_without_jumping() -> void:
	var jumps := _count_jumps()
	assert_eq(h.game.state, GameSession.State.READY)
	h.game.press_jump()
	await h.run_ticks(30)
	assert_eq(h.game.state, GameSession.State.PLAYING)
	assert_eq(jumps[0], 0, "the start tap is not a jump")
	assert_true(h.game.player.velocity.x > 0.0, "running")


func test_a_touch_and_its_emulated_click_count_once() -> void:
	var jumps := _count_jumps()
	# Godot turns a touch into a raw touch event plus an emulated mouse click.
	_touch(0)
	_emulated_click()
	await h.run_ticks(30)
	assert_eq(h.game.state, GameSession.State.PLAYING)
	assert_eq(jumps[0], 0, "the start tap did not also jump")
	_touch(0)
	_emulated_click()
	await h.run_ticks(4)
	assert_eq(jumps, [1, 0] as Array[int], "one tap, one jump: the duplicate did not double jump")


func test_every_finger_is_a_tap() -> void:
	var jumps := _count_jumps()
	h.game.press_jump()
	await h.run_ticks(10)
	_touch(1)  # A second finger while the first rests on the screen.
	await h.run_ticks(2)
	assert_eq(jumps[0], 1, "second finger tap jumped")
	await h.run_ticks(60)
	_touch(0)
	await h.run_ticks(2)
	assert_eq(jumps[0], 2, "first finger tap jumped")


func test_tapping_the_pause_button_pauses_without_jumping() -> void:
	var jumps := _count_jumps()
	h.game.press_jump()
	await h.run_ticks(10)
	var button: Control = h.game.hud.get_node(^"%PauseButton")
	var center := button.get_global_rect().get_center()
	for pressed in [true, false]:
		# Like a device: the raw touch plus the click Godot emulates from it.
		var touch := InputEventScreenTouch.new()
		touch.position = center
		touch.pressed = pressed
		Input.parse_input_event(touch)
		await h.run_ticks(2)
	assert_eq(h.game.state, GameSession.State.PAUSED, "the button paused the game")
	assert_eq(jumps[0], 0, "the tap on the button was not a jump")


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


func test_android_back_pauses_and_resumes_instead_of_quitting() -> void:
	h.game.press_jump()
	await h.run_ticks(20)
	h.game.notification(Node.NOTIFICATION_WM_GO_BACK_REQUEST)
	assert_eq(h.game.state, GameSession.State.PAUSED, "back mid-run pauses")
	await h.run_ticks(5)
	h.game.notification(Node.NOTIFICATION_WM_GO_BACK_REQUEST)
	assert_eq(h.game.state, GameSession.State.PLAYING, "back on the pause menu resumes")
	assert_false(ProjectSettings.get_setting("application/config/quit_on_go_back"), "engine does not quit on its own")


func test_player_cannot_die_outside_playing() -> void:
	h.game.player.die(&"test")
	assert_false(h.game.player.is_dead(), "not on the start screen")
	h.game.press_jump()
	await h.run_ticks(5)
	h.game.pause()
	h.game.player.die(&"test")
	assert_false(h.game.player.is_dead(), "not while paused")
	h.game.resume()
	h.game.player.die(&"test")
	assert_true(h.game.player.is_dead(), "but yes while playing")


func test_update_order_does_not_depend_on_scene_order() -> void:
	var game := h.game
	assert_true(game.level.process_physics_priority < game.player.process_physics_priority, "level moves before the player")
	assert_true(game.player.process_physics_priority < game.camera.process_physics_priority, "camera follows after the player")
