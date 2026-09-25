extends TestCase
## The double jump on the real physics server: heights, the two-jump limit,
## coyote/edge interplay, fast taps and restarts.

const T := GameConst.TILE

var arena: PhysicsArena
var player: Player
## Physics frames at which each kind of jump started.
var ground_jumps: Array[int] = []
var air_jumps: Array[int] = []


func before_each() -> void:
	arena = PhysicsArena.new()
	add_child(arena)
	ground_jumps.clear()
	air_jumps.clear()


func after_each() -> void:
	arena.queue_free()
	await get_tree().physics_frame


func _spawn(feet: Vector2, run := true) -> void:
	player = arena.spawn_player(feet, run)
	player.jumped.connect(func() -> void: ground_jumps.append(Engine.get_physics_frames()))
	player.double_jumped.connect(func() -> void: air_jumps.append(Engine.get_physics_frames()))


## Ticks until the player stops rising; returns the height of the feet above [param floor_y].
func _rise_to_apex(floor_y := 0.0) -> float:
	var apex := player.get_feet_position().y
	for i in 120:
		await arena.ticks(1)
		apex = minf(apex, player.get_feet_position().y)
		if player.velocity.y >= 0.0:
			break
	return floor_y - apex


func test_double_jump_at_the_apex_reaches_twice_the_height() -> void:
	arena.block(Vector2(-T * 4, 0), Vector2(T * 80, T * 4))
	_spawn(Vector2(0, 0))
	await arena.ticks(3)
	player.request_jump()
	var first := await _rise_to_apex()
	player.request_jump()
	var total := await _rise_to_apex()
	assert_near(first, player.config.jump_height, 1.5, "first apex")
	assert_near(total, player.config.max_jump_height(), 2.0, "double jump at the apex")
	assert_eq([ground_jumps.size(), air_jumps.size()], [1, 1])
	await arena.ticks(90)
	assert_true(player.is_on_floor() and not player.is_dead(), "landed safely")


func test_double_jump_clears_a_wall_a_single_jump_cannot() -> void:
	# A 3.5-tile wall: 150 px of jump cannot clear it, 300 px can.
	for use_double_jump in [false, true]:
		arena.queue_free()
		await get_tree().physics_frame
		arena = PhysicsArena.new()
		add_child(arena)
		arena.block(Vector2(-T * 4, 0), Vector2(T * 60, T * 4))
		arena.block(Vector2(T * 7, -T * 3.5), Vector2(T * 20, T * 3.5))
		_spawn(Vector2(0, 0))
		await arena.ticks(2)
		player.request_jump()
		if use_double_jump:
			await arena.ticks(20)
			player.request_jump()
		await arena.ticks(90)
		if use_double_jump:
			assert_false(player.is_dead(), "jump + double jump lands on top")
			assert_near(player.get_feet_position().y, -T * 3.5, 0.5, "standing on the wall")
		else:
			assert_true(player.is_dead(), "a single jump hits the wall")


func test_there_is_never_a_third_jump() -> void:
	arena.block(Vector2(-T * 4, 0), Vector2(T * 80, T * 4))
	_spawn(Vector2(0, 0))
	await arena.ticks(2)
	player.request_jump()
	await arena.ticks(6)
	player.request_jump()
	var apex := player.get_feet_position().y
	for i in 40:
		player.request_jump()  # Mash every tick.
		await arena.ticks(1)
		apex = minf(apex, player.get_feet_position().y)
	assert_eq([ground_jumps.size(), air_jumps.size()], [1, 1], "two jumps, no more")
	assert_true(-apex < player.config.max_jump_height(), "never higher than two jumps")


func test_two_taps_before_one_tick_are_a_jump_then_a_double_jump() -> void:
	arena.block(Vector2(-T * 4, 0), Vector2(T * 40, T * 4))
	_spawn(Vector2(0, 0))
	await arena.ticks(3)
	var frame := Engine.get_physics_frames()
	player.request_jump()
	player.request_jump()
	await arena.ticks(4)
	assert_eq(ground_jumps, [frame] as Array[int], "jump on the first tick")
	assert_eq(air_jumps, [frame + 1] as Array[int], "double jump on the next one")


func test_double_jump_comes_back_after_landing() -> void:
	arena.block(Vector2(-T * 4, 0), Vector2(T * 80, T * 4))
	_spawn(Vector2(0, 0))
	await arena.ticks(2)
	player.request_jump()
	await arena.ticks(10)
	player.request_jump()
	await arena.ticks(2)
	assert_false(player.has_double_jump(), "spent in the air")
	await arena.ticks(90)
	assert_true(player.is_on_floor(), "landed")
	assert_true(player.has_double_jump(), "restored by the landing")
	player.request_jump()
	await arena.ticks(10)
	player.request_jump()
	await arena.ticks(2)
	assert_eq([ground_jumps.size(), air_jumps.size()], [2, 2], "the second airtime has both jumps again")


func test_walking_off_an_edge_leaves_only_the_double_jump() -> void:
	arena.block(Vector2(-T * 4, 0), Vector2(T * 6, T * 4))
	arena.block(Vector2(T * 12, T * 2), Vector2(T * 20, T * 4))  # Lower ground far ahead.
	_spawn(Vector2(0, 0))
	while player.global_position.x - player.half_size.x < T * 2:
		await arena.ticks(1)
	await arena.ticks(8)  # 0.13 s: past the coyote window.
	player.request_jump()
	await arena.ticks(2)
	player.request_jump()
	await arena.ticks(2)
	assert_eq(ground_jumps.size(), 0, "no ground jump after the coyote window")
	assert_eq(air_jumps.size(), 1, "the late tap became the double jump")


func test_a_tap_within_coyote_time_keeps_the_double_jump() -> void:
	arena.block(Vector2(-T * 4, 0), Vector2(T * 6, T * 4))
	_spawn(Vector2(0, 0))
	while player.global_position.x - player.half_size.x < T * 2:
		await arena.ticks(1)
	await arena.ticks(2)
	assert_false(player.is_on_floor(), "already off the edge")
	player.request_jump()
	await arena.ticks(15)
	player.request_jump()
	await arena.ticks(2)
	assert_eq([ground_jumps.size(), air_jumps.size()], [1, 1], "coyote jump, then the double jump")


func test_double_jump_just_above_the_ground_while_falling() -> void:
	arena.block(Vector2(-T * 4, 0), Vector2(T * 80, T * 4))
	_spawn(Vector2(0, 0))
	await arena.ticks(2)
	player.request_jump()
	await arena.ticks(10)
	while player.velocity.y < 0.0 or player.get_feet_position().y < -24.0:
		await arena.ticks(1)
	var start := player.get_feet_position().y
	player.request_jump()
	var apex := await _rise_to_apex(start)
	assert_eq(air_jumps.size(), 1, "a double jump, not a landing")
	assert_near(apex, player.config.double_jump_height, 2.0, "full double jump height from low down")


func test_double_jump_into_a_low_ceiling_is_not_fatal() -> void:
	arena.block(Vector2(-T * 4, 0), Vector2(T * 40, T * 4))
	arena.block(Vector2(-T * 2, -T * 5), Vector2(T * 20, T * 2))  # Underside 192 px up.
	_spawn(Vector2(0, 0))
	await arena.ticks(2)
	player.request_jump()
	await arena.ticks(12)
	player.request_jump()
	await arena.ticks(90)
	assert_false(player.is_dead(), "a head bump stops the rise, nothing else")
	assert_true(player.is_on_floor())


func test_respawn_mid_double_jump_restores_everything() -> void:
	arena.block(Vector2(-T * 4, 0), Vector2(T * 40, T * 4))
	_spawn(Vector2(0, 0))
	await arena.ticks(2)
	player.request_jump()
	await arena.ticks(8)
	player.request_jump()
	player.request_jump()  # A third tap left waiting in the buffer.
	await arena.ticks(2)
	player.respawn_at(Vector2(0, 0), true)
	assert_true(player.has_double_jump(), "double jump restored")
	await arena.ticks(30)
	assert_eq([ground_jumps.size(), air_jumps.size()], [1, 1], "no stale tap fired after the respawn")
	assert_true(player.is_on_floor())


func test_landing_a_double_jump_on_a_moving_platform() -> void:
	arena.block(Vector2(-T * 4, 0), Vector2(T * 6, T * 4))
	var lift := arena.block(Vector2(T * 7, -T * 3), Vector2(T * 8, 32), true)
	_spawn(Vector2(0, 0))
	await arena.ticks(2)
	player.request_jump()
	await arena.ticks(20)
	player.request_jump()
	var bounces: Array[int] = [0]
	player.landed.connect(func(_i: float) -> void: bounces[0] += 1)
	for i in 80:
		lift.position.y += 2.0 if (i / 20) % 2 == 0 else -2.0
		await arena.ticks(1)
	assert_false(player.is_dead())
	assert_true(player.is_on_floor(), "riding the platform")
	assert_eq(bounces[0], 1, "one landing, no bouncing on the moving platform")
