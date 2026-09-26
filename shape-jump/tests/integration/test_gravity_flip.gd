extends TestCase
## Gravity flip (World 03) on the real physics server: the floor becomes the
## ceiling and back, jumps push away from the current floor, and the jump
## rules hold across flips (docs/GDD.md §9C; PlayerMotor.flip).

const T := GameConst.TILE
## The ceiling's underside, 6 tiles above the ground line.
const CEILING_Y := -6.0 * T

var arena: PhysicsArena
var player: Player
var gravity: GravityState
var ground_jumps := 0
var air_jumps := 0
var landings := 0


func before_each() -> void:
	arena = PhysicsArena.new()
	add_child(arena)
	arena.block(Vector2(-20 * T, 0), Vector2(200 * T, T))
	arena.block(Vector2(-20 * T, CEILING_Y - T), Vector2(200 * T, T))
	gravity = GravityState.new()
	ground_jumps = 0
	air_jumps = 0
	landings = 0


func after_each() -> void:
	arena.queue_free()
	await get_tree().physics_frame


func _spawn(on_ceiling := false) -> void:
	gravity.reset(on_ceiling)
	player = PhysicsArena.PLAYER_SCENE.instantiate()
	arena.add_child(player)
	player.kill_y = 10 * T
	player.kill_top = CEILING_Y - 10 * T
	player.gravity = gravity
	player.respawn_at(Vector2(0, CEILING_Y if on_ceiling else 0.0), true)
	player.jumped.connect(func() -> void: ground_jumps += 1)
	player.double_jumped.connect(func() -> void: air_jumps += 1)
	player.landed.connect(func(_speed: float) -> void: landings += 1)


## Flips gravity the way the level does: before the player's next tick.
func _flip(up: bool) -> void:
	gravity.set_up(up)


func _on_ceiling() -> bool:
	return player.is_on_floor() and absf(player.get_feet_position().y - CEILING_Y) < 1.0


func _on_ground() -> bool:
	return player.is_on_floor() and absf(player.get_feet_position().y) < 1.0


func _tick_until(condition: Callable, limit := 120) -> bool:
	for i in limit:
		if condition.call():
			return true
		await arena.ticks(1)
	return condition.call()


func test_flip_while_running_lands_on_the_ceiling_and_back() -> void:
	_spawn()
	await arena.ticks(5)
	assert_true(_on_ground(), "starts on the ground")
	_flip(true)
	await arena.ticks(1)
	assert_false(player.is_on_floor(), "leaves the ground at once")
	assert_true(await _tick_until(_on_ceiling), "falls up and lands on the ceiling")
	assert_eq(player.up_direction, Vector2.DOWN, "the floor is now above")
	assert_near(player.global_position.y, CEILING_Y + player.half_size.y, 1.0, "hangs under the ceiling, not in it")
	var x := player.global_position.x
	await arena.ticks(30)
	assert_true(_on_ceiling(), "keeps running on the ceiling")
	assert_true(player.global_position.x > x + 200.0, "still running forward")
	_flip(false)
	assert_true(await _tick_until(_on_ground), "and back down to the ground")
	assert_eq(landings, 2, "one landing per flip")


func test_a_jump_on_the_ceiling_pushes_down_to_jump_height() -> void:
	_spawn(true)
	await arena.ticks(3)
	assert_true(_on_ceiling(), "spawned standing on the ceiling")
	player.request_jump()
	var lowest := CEILING_Y
	for i in 90:
		await arena.ticks(1)
		lowest = maxf(lowest, player.get_feet_position().y)
		if _on_ceiling():
			break
	assert_eq(ground_jumps, 1, "a ground jump from the ceiling")
	assert_near(lowest - CEILING_Y, player.config.jump_height, 3.0, "same height as on the ground, downward")
	assert_true(_on_ceiling(), "lands back on the ceiling")


func test_double_jump_on_the_ceiling_doubles_the_height_downward() -> void:
	_spawn(true)
	await arena.ticks(3)
	player.request_jump()
	await arena.ticks(18)  # Near the apex.
	player.request_jump()
	var lowest := CEILING_Y
	for i in 120:
		await arena.ticks(1)
		lowest = maxf(lowest, player.get_feet_position().y)
		if _on_ceiling():
			break
	assert_eq(air_jumps, 1, "the double jump works on the ceiling")
	assert_true(lowest - CEILING_Y > player.config.jump_height * 1.8, "and reaches about twice as far")


func test_case_a_jump_then_flip_then_double_jump_lands_on_the_ceiling() -> void:
	_spawn()
	await arena.ticks(3)
	player.request_jump()
	await arena.ticks(8)
	_flip(true)
	await arena.ticks(2)
	assert_true(player.has_double_jump(), "the flip keeps the double jump")
	player.request_jump()
	await arena.ticks(1)
	assert_eq(air_jumps, 1, "a double jump after the flip")
	assert_true(await _tick_until(_on_ceiling), "lands on the ceiling")
	assert_eq(ground_jumps, 1, "only the first jump was a ground jump")


func test_case_b_ceiling_jump_then_flip_then_double_jump_lands_on_the_ground() -> void:
	_spawn(true)
	await arena.ticks(3)
	player.request_jump()
	await arena.ticks(8)
	_flip(false)
	await arena.ticks(2)
	player.request_jump()
	await arena.ticks(1)
	assert_eq(air_jumps, 1, "a double jump after the flip")
	assert_true(await _tick_until(_on_ground), "lands on the ground")


func test_case_c_and_d_both_jumps_spent_then_flip_leaves_no_jump() -> void:
	for start_up in [false, true]:
		_spawn(start_up)
		await arena.ticks(3)
		player.request_jump()
		await arena.ticks(6)
		player.request_jump()
		await arena.ticks(4)
		_flip(not start_up)
		await arena.ticks(2)
		player.request_jump()  # No third jump, flip or not.
		await arena.ticks(2)
		assert_eq(ground_jumps + air_jumps, 2, "no triple jump across a flip (start up=%s)" % start_up)
		assert_true(await _tick_until(_on_ceiling if not start_up else _on_ground), "lands on the new floor")
		assert_true(player.has_double_jump(), "landing restores the double jump")
		player.queue_free()
		await arena.ticks(1)
		ground_jumps = 0
		air_jumps = 0


func test_no_coyote_ground_jump_across_a_flip() -> void:
	_spawn()
	await arena.ticks(3)
	_flip(true)
	player.request_jump()  # The same tick as the flip.
	await arena.ticks(2)
	assert_eq(ground_jumps, 0, "no ground jump: there is no floor under the player any more")
	assert_eq(air_jumps, 1, "the tap is the double jump")
	player.request_jump()
	await arena.ticks(2)
	assert_eq(air_jumps + ground_jumps, 1, "and then nothing until the ceiling")
	assert_true(await _tick_until(_on_ceiling, 200), "still reaches the ceiling")


func test_a_buffered_tap_is_dropped_by_a_flip() -> void:
	_spawn()
	await arena.ticks(3)
	player.request_jump()
	await arena.ticks(6)
	player.request_jump()
	await arena.ticks(20)  # Falling back, no jump left.
	player.request_jump()  # Would fire on landing (jump buffer).
	_flip(true)
	assert_true(await _tick_until(_on_ceiling), "lands on the ceiling")
	await arena.ticks(10)
	assert_eq(ground_jumps + air_jumps, 2, "the buffered tap does not fire on the ceiling")
	assert_true(_on_ceiling(), "still standing there")


func test_two_taps_in_one_frame_at_a_flip_are_two_jumps_at_most() -> void:
	_spawn()
	await arena.ticks(3)
	_flip(true)
	player.request_jump()
	player.request_jump()
	player.request_jump()
	await arena.ticks(6)
	assert_true(ground_jumps + air_jumps <= 1, "one jump left in the air after a flip: one jump")


func test_many_flips_in_a_row_never_break_the_body() -> void:
	_spawn()
	await arena.ticks(3)
	for i in 24:
		_flip(i % 2 == 0)
		await arena.ticks(3 + (i % 5))
		if i % 3 == 0:
			player.request_jump()
	_flip(false)
	assert_true(await _tick_until(_on_ground, 240), "settles on the ground after 24 flips")
	assert_false(player.is_dead(), "never died between two solid surfaces")
	assert_eq(player.up_direction, Vector2.UP)


func test_rising_past_the_top_kill_line_is_a_fall_death() -> void:
	_spawn()
	await arena.ticks(3)
	var hole := arena.get_child(1) as Block  # The ceiling.
	hole.queue_free()
	await arena.ticks(1)
	_flip(true)
	var died: Array[StringName] = []
	player.died.connect(func(cause: StringName) -> void: died.append(cause))
	await _tick_until(func() -> bool: return not died.is_empty(), 240)
	assert_eq(died, [&"fall"] as Array[StringName], "falling up into the void kills like falling down")


func test_respawn_on_the_ceiling_stands_there() -> void:
	_spawn()
	await arena.ticks(3)
	gravity.set_up(true, true)  # A checkpoint on the ceiling.
	player.respawn_at(Vector2(4 * T, CEILING_Y), true)
	assert_true(player.is_on_floor(), "standing on the ceiling at once")
	assert_eq(player.velocity, Vector2.ZERO, "no speed carried over")
	assert_true(player.has_double_jump(), "both jumps back")
	await arena.ticks(10)
	assert_true(_on_ceiling(), "and running there")
	assert_eq(landings, 0, "no fall on the way")


func test_the_drawing_turns_but_the_body_never_does() -> void:
	_spawn()
	await arena.ticks(3)
	_flip(true)
	await arena.ticks(40)
	assert_near(absf(player.visual.rotation), PI, 0.01, "the entity stands on the ceiling (half a turn)")
	assert_eq(player.rotation, 0.0, "the body never turns")
	assert_eq((player.get_node(^"BodyShape") as CollisionShape2D).rotation, 0.0, "nor its collision box")
