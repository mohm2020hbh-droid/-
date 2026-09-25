extends TestCase
## Camera positioning on the real game scene (1280x720 view, see runner).

var h: GameHarness
var _save := SaveSandbox.new()


func before_each() -> void:
	_save.enter()
	h = GameHarness.new(self, World01Routes.get_route(0))
	await h.start()


func after_each() -> void:
	h.free_game()
	await get_tree().physics_frame
	_save.leave()


func _view() -> Vector2:
	return h.game.camera.get_viewport_rect().size / h.game.camera.zoom


## Where a world point appears on screen, in pixels from the top-left.
func _on_screen(world: Vector2) -> Vector2:
	var camera := h.game.camera
	return world - (camera.global_position + camera.offset - _view() * 0.5)


func test_player_holds_the_look_ahead_anchor_while_running() -> void:
	h.game.press_jump()
	await h.run_ticks(60)
	var anchor := h.game.camera.screen_anchor_x
	for i in 20:
		await h.tick()
		var x_frac := _on_screen(h.game.player.global_position).x / _view().x
		assert_near(x_frac, anchor, 0.002, "player stays at the look-ahead anchor")
	assert_true(_view().x * (1.0 - anchor) > _view().x * 0.7, "over 70% of the view is ahead of the player")


func test_camera_does_not_bob_during_a_flat_jump() -> void:
	h.game.press_jump()
	await h.run_until(func() -> bool: return h.player_x() >= 23.0)
	var top := INF
	var bottom := -INF
	while h.player_x() < 31.0:  # Jump over the first gap and land on the same height.
		await h.tick()
		top = minf(top, h.game.camera.global_position.y)
		bottom = maxf(bottom, h.game.camera.global_position.y)
	assert_near(bottom - top, 0.0, 0.5, "vertical camera ignores jump arcs")


func test_camera_follows_a_step_up_smoothly() -> void:
	h.game.press_jump()
	await h.run_until(func() -> bool: return h.player_x() >= 92.0)
	var start_y := h.game.camera.global_position.y
	var previous := start_y
	var biggest_step := 0.0
	while h.player_x() < 104.0:  # Off the elevator onto the higher floor.
		await h.tick()
		var y := h.game.camera.global_position.y
		biggest_step = maxf(biggest_step, absf(y - previous))
		previous = y
	assert_true(previous < start_y - 40.0, "camera rose with the step")
	assert_true(biggest_step < 12.0, "no sudden camera jumps (biggest per-tick step %.1f px)" % biggest_step)


func test_fall_death_happens_on_screen_and_within_the_camera_limit() -> void:
	h.skip = [5]  # Run off into the elevator pit (a real fall, not a wall hit).
	h.game.press_jump()
	await h.run_until(func() -> bool: return not h.deaths.is_empty())
	var camera := h.game.camera
	assert_eq(h.deaths[0].cause, &"fall")
	assert_true(camera.global_position.y + _view().y * 0.5 <= camera.bottom_limit + 0.01, "respects the bottom limit")
	var on_screen := _on_screen(h.game.player.global_position)
	assert_true(on_screen.y < _view().y - h.game.player.half_size.y, "the shatter is visible (y=%.0f)" % on_screen.y)


func test_camera_snaps_exactly_on_respawn() -> void:
	h.skip = [0]
	h.game.press_jump()
	await h.run_until(func() -> bool: return not h.deaths.is_empty())
	await h.run_until(h.is_state(GameSession.State.PLAYING))
	var camera := h.game.camera
	assert_true(camera.global_position.distance_to(camera.get_desired_position()) < 0.01, "no pan back from the death spot")
	var x_frac := _on_screen(h.game.player.global_position).x / _view().x
	assert_near(x_frac, camera.screen_anchor_x, 0.002, "player back at the anchor")
