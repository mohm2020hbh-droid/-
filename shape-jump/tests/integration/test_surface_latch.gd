extends TestCase
## World 04's second tap on the real physics server (docs/GDD.md §9D): the
## surface latch. A tap in the air latches to the other surface when it is in
## reach, and is spent (a miss) when it is not; the body is committed while
## it crosses; the rules hold from the ground and from the ceiling alike.

const T := GameConst.TILE
## The standard World 04 corridor: the ceiling's underside 5 tiles up.
const CEILING_Y := -5.0 * T

var arena: PhysicsArena
var player: Player
var gravity: GravityState
var jumps := 0
var latches := 0
var misses := 0
var landings := 0


func before_each() -> void:
	arena = PhysicsArena.new()
	add_child(arena)
	gravity = GravityState.new()
	jumps = 0
	latches = 0
	misses = 0
	landings = 0


func after_each() -> void:
	arena.queue_free()
	await get_tree().physics_frame


func _corridor(ceiling_y := CEILING_Y, from := -20.0, to := 200.0) -> void:
	arena.block(Vector2(from * T, 0), Vector2((to - from) * T, T))
	arena.block(Vector2(from * T, ceiling_y - T), Vector2((to - from) * T, T))


func _spawn(on_ceiling := false, ceiling_y := CEILING_Y, x := 0.0) -> void:
	gravity.reset(on_ceiling)
	player = PhysicsArena.PLAYER_SCENE.instantiate()
	arena.add_child(player)
	player.kill_y = 10 * T
	player.kill_top = ceiling_y - 10 * T
	player.gravity = gravity
	player.set_surface_latch(true)
	player.respawn_at(Vector2(x, ceiling_y if on_ceiling else 0.0), true)
	player.jumped.connect(func() -> void: jumps += 1)
	player.latched.connect(func(_at: Vector2) -> void: latches += 1)
	player.latch_missed.connect(func() -> void: misses += 1)
	player.landed.connect(func(_speed: float) -> void: landings += 1)


func _on_ceiling(ceiling_y := CEILING_Y) -> bool:
	return player.is_on_floor() and gravity.up and absf(player.get_feet_position().y - ceiling_y) < 1.0


func _on_ground() -> bool:
	return player.is_on_floor() and not gravity.up and absf(player.get_feet_position().y) < 1.0


func _tick_until(condition: Callable, limit := 120) -> bool:
	for i in limit:
		if condition.call():
			return true
		await arena.ticks(1)
	return condition.call()


## Jumps, waits [param wait] ticks, taps again.
func _jump_then_tap(wait: int) -> void:
	player.request_jump()
	await arena.ticks(wait)
	player.request_jump()
	await arena.ticks(1)


func test_second_tap_near_the_apex_latches_to_the_ceiling() -> void:
	_corridor()
	_spawn()
	await arena.ticks(3)
	assert_true(_on_ground(), "starts on the ground")
	await _jump_then_tap(18)
	assert_eq(latches, 1, "the second tap latched")
	assert_true(gravity.up, "the ceiling is the floor now")
	assert_true(player.is_latching(), "crossing, committed")
	assert_true(await _tick_until(_on_ceiling, 30), "locked onto the ceiling")
	assert_false(player.is_latching(), "the crossing ended on touching it")
	assert_eq(player.up_direction, Vector2.DOWN, "jumps now push toward the ground")
	assert_near(player.global_position.y, CEILING_Y + player.half_size.y, 1.0, "hangs under it, not in it")
	var x := player.global_position.x
	await arena.ticks(30)
	assert_true(_on_ceiling(), "runs on the ceiling")
	assert_true(player.global_position.x > x + 200.0, "still running forward: left to right")


func test_the_crossing_is_quick_and_keeps_the_run_speed() -> void:
	_corridor()
	_spawn()
	await arena.ticks(3)
	player.request_jump()
	await arena.ticks(20)
	var start_x := player.global_position.x
	player.request_jump()
	var ticks := 0
	var steady := true
	var last_x := start_x
	while not _on_ceiling() and ticks < 40:
		await arena.ticks(1)
		ticks += 1
		steady = steady and absf(player.global_position.x - last_x - player.get_run_speed() / 60.0) < 0.01
		last_x = player.global_position.x
	assert_true(_on_ceiling(), "arrived")
	assert_true(ticks <= 10, "within ten ticks (%d): a latch, not a slow fall" % ticks)
	assert_true(steady, "the run speed never changed while crossing (x stays a straight line in time)")


func test_an_early_second_tap_misses_and_is_spent() -> void:
	_corridor()
	_spawn()
	await arena.ticks(3)
	await _jump_then_tap(3)
	assert_eq(misses, 1, "too far from the ceiling: a miss")
	assert_eq(latches, 0)
	assert_false(gravity.up, "no teleport, no flip")
	assert_false(player.has_double_jump(), "the second tap is spent")
	# A third tap before landing can never latch (and it is no third jump).
	await arena.ticks(12)
	player.request_jump()
	await arena.ticks(1)
	assert_eq(latches, 0, "no second try in the same airtime")
	assert_true(await _tick_until(_on_ground, 60), "falls back to the ground")
	assert_eq(jumps, 1, "one jump only: no triple jump")


func test_two_taps_in_one_frame_are_a_jump_and_a_miss() -> void:
	_corridor()
	_spawn()
	await arena.ticks(3)
	player.request_jump()
	player.request_jump()
	await arena.ticks(2)
	assert_eq(jumps, 1)
	assert_eq(misses, 1, "the second tap, one tick later, is far from the ceiling")
	assert_eq(latches, 0)
	assert_true(await _tick_until(_on_ground, 80), "lands where it jumped from")


func test_from_the_ceiling_a_jump_and_a_second_tap_latch_to_the_ground() -> void:
	_corridor()
	_spawn(true)
	await arena.ticks(3)
	assert_true(_on_ceiling(), "starts on the ceiling")
	await _jump_then_tap(18)
	assert_eq(latches, 1, "latched to the ground")
	assert_false(gravity.up, "the ground is the floor again")
	assert_true(await _tick_until(_on_ground, 30), "locked onto the ground")
	assert_eq(player.up_direction, Vector2.UP)


func test_a_jump_from_the_ceiling_alone_comes_back_to_the_ceiling() -> void:
	_corridor()
	_spawn(true)
	await arena.ticks(3)
	player.request_jump()
	await arena.ticks(2)
	assert_false(player.is_on_floor(), "let go of the ceiling")
	assert_true(await _tick_until(_on_ceiling, 90), "gravity still pulls up: back on the ceiling")
	assert_eq(latches, 0)


func test_ground_ceiling_ground_many_times_in_a_row() -> void:
	_corridor(CEILING_Y, -20.0, 600.0)
	_spawn()
	await arena.ticks(3)
	for i in 12:
		var want_up := i % 2 == 0
		await _jump_then_tap(18)
		assert_true(await _tick_until(func() -> bool: return _on_ceiling() if want_up else _on_ground(), 40),
			"latch %d reached its surface" % (i + 1))
		await arena.ticks(4)
	assert_eq(latches, 12, "every second tap latched")
	assert_eq(misses, 0)
	assert_false(gravity.up, "an even number of latches: back on the ground")
	assert_near(player.global_position.y, -player.half_size.y, 1.0, "standing on the ground, not in it")


func test_a_ceiling_too_high_for_the_jump_cannot_be_reached() -> void:
	var high := -6.5 * T
	_corridor(high)
	_spawn(false, high)
	await arena.ticks(3)
	await _jump_then_tap(21)  # Right at the apex.
	assert_eq(misses, 1, "out of reach even from the top of the jump")
	assert_false(gravity.up)
	assert_true(await _tick_until(_on_ground, 60), "back on the ground")


func test_taps_while_crossing_are_swallowed() -> void:
	_corridor()
	_spawn()
	await arena.ticks(3)
	await _jump_then_tap(18)
	assert_true(player.is_latching())
	for i in 3:
		player.request_jump()
		await arena.ticks(1)
	assert_true(await _tick_until(_on_ceiling, 30), "arrived")
	await arena.ticks(6)
	assert_true(_on_ceiling(), "no jump fired on arrival")
	assert_eq(jumps, 1, "the taps during the crossing did nothing")
	assert_eq(latches, 1)
	# After arriving, the next tap is a normal jump off the ceiling.
	player.request_jump()
	await arena.ticks(2)
	assert_eq(jumps, 2, "a fresh tap after arriving jumps")
	assert_false(player.is_on_floor())


func test_a_latch_needs_the_surface_to_be_there_on_arrival() -> void:
	# The ceiling ends just ahead of where a latch would land.
	arena.block(Vector2(-20 * T, 0), Vector2(220 * T, T))
	var jump_x := 0.0
	# Where the player is 18 ticks after a jump at x=0, and a little beyond.
	var ends := jump_x + (18.0 + 3.0) * 520.0 / 60.0
	arena.block(Vector2(-20 * T, CEILING_Y - T), Vector2(ends + 20 * T, T))
	_spawn()
	player.set_speed_scale(1.0)
	await arena.ticks(1)
	player.request_jump()
	await arena.ticks(18)
	player.request_jump()
	await arena.ticks(1)
	assert_eq(misses, 1, "the ceiling would be gone when the body got there")
	assert_false(gravity.up)


func test_a_solid_slab_in_the_way_is_the_surface_latched_to() -> void:
	_corridor()
	# A floating slab 3.6 tiles up, under the ceiling: the latch meets it first.
	arena.block(Vector2(-2 * T, -3.6 * T - 16.0), Vector2(30 * T, 16.0))
	_spawn()
	await arena.ticks(3)
	await _jump_then_tap(8)
	assert_eq(latches, 1)
	assert_true(await _tick_until(func() -> bool: return player.is_on_floor() and gravity.up, 30), "landed")
	assert_near(player.get_feet_position().y, -3.6 * T, 1.0, "on the slab's underside, never through it")


func test_a_surface_marked_not_latchable_is_a_miss() -> void:
	arena.block(Vector2(-20 * T, 0), Vector2(220 * T, T))
	var roof := arena.block(Vector2(-20 * T, CEILING_Y - T), Vector2(220 * T, T))
	roof.set_meta(&"latchable", false)
	# Blocks say it with a property; a plain body with the property works too.
	var slick := GDScript.new()
	slick.source_code = "extends Block\nvar latchable := false\n"
	slick.reload()
	roof.set_script(slick)
	roof.size = Vector2(220 * T, T)
	_spawn()
	await arena.ticks(3)
	await _jump_then_tap(18)
	assert_eq(misses, 1, "a surface that says it cannot be held is never latched to")
	assert_false(gravity.up)


func test_a_miss_just_before_landing_still_jumps_on_landing() -> void:
	_corridor()
	_spawn()
	await arena.ticks(3)
	player.request_jump()
	# Falling back, a few ticks above the ground.
	assert_true(await _tick_until(func() -> bool:
		return not player.is_on_floor() and player.velocity.y > 0.0 and player.get_feet_position().y > -40.0, 90),
		"about to land")
	player.request_jump()
	await arena.ticks(1)
	assert_eq(misses, 1, "the ceiling is far: a miss")
	assert_true(await _tick_until(func() -> bool: return jumps == 2, 12), "and the tap jumps on landing (buffer)")


func test_a_moving_ceiling_is_latched_when_it_comes_in_reach() -> void:
	arena.block(Vector2(-20 * T, 0), Vector2(220 * T, T))
	# A ceiling panel on a moving body (a level's clock would drive it; here
	# the test moves it): risen out of reach, then back down within reach.
	var panel := arena.block(Vector2(-20 * T, -7.0 * T - 32.0), Vector2(120 * T, 32.0), true)
	_spawn()
	await arena.ticks(3)
	await _jump_then_tap(18)
	assert_eq(misses, 1, "risen out of reach")
	assert_true(await _tick_until(_on_ground, 60))
	for i in 20:
		panel.position.y += 2.0 * T / 20.0  # Coming down over 20 ticks.
		await arena.ticks(1)
	await _jump_then_tap(18)
	assert_eq(latches, 1, "down in reach: latched")
	assert_true(await _tick_until(func() -> bool: return player.is_on_floor() and gravity.up, 40), "on the moving ceiling")
	assert_near(player.get_feet_position().y, -5.0 * T, 1.0, "on its underside")


func test_wind_lifts_the_jump_and_never_the_latch() -> void:
	_corridor(-6.5 * T)
	_spawn(false, -6.5 * T)
	await arena.ticks(3)
	var calm := await _apex()
	await _tick_until(_on_ground, 90)
	# An updraft: 900 px/s² away from the floor.
	player.wind_source = func(_x: float) -> float: return -900.0
	var lifted := await _apex()
	assert_true(lifted > calm + 60.0, "the updraft carries the jump higher (%.0f vs %.0f px)" % [lifted, calm])
	player.wind_source = Callable()


## Jumps and returns the highest the feet got above the ground (px).
func _apex() -> float:
	player.request_jump()
	var best := 0.0
	for i in 90:
		await arena.ticks(1)
		best = maxf(best, -player.get_feet_position().y)
		if player.is_on_floor() and i > 5:
			break
	return best


func test_dying_while_crossing_then_respawning_is_clean() -> void:
	_corridor()
	_spawn()
	await arena.ticks(3)
	await _jump_then_tap(18)
	assert_true(player.is_latching())
	player.die(&"test")
	assert_true(player.is_dead())
	gravity.set_up(false, true)
	player.respawn_at(Vector2(player.global_position.x, 0.0), true)
	await arena.ticks(2)
	assert_false(player.is_latching(), "no crossing carried over")
	assert_true(player.has_double_jump(), "the second tap is back")
	assert_true(_on_ground(), "standing on the respawn surface")
	await _jump_then_tap(18)
	assert_eq(latches, 2, "and it latches again normally")
