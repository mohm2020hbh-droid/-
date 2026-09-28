extends TestCase
## World 04's two moves on the real physics server (docs/GDD.md §9D): a TAP
## is a short HOP; TAP TAP is ONE gesture, the SURFACE ATTACH (GROUND -> TAP
## TAP -> CEILING, CEILING -> TAP TAP -> GROUND): the first tap hops, the
## second turns the hop into the crossing. Never a second jump in the air: a
## gesture that finds no surface it can reach leaves the hop as it was; a tap
## in the air waits for the landing. Gravity turns only when the crossing
## touches the surface. Every test also watches, tick by tick, that the run
## goes on to the right and that the player is never mirrored.

const T := GameConst.TILE
## The standard World 04 corridor: the ceiling's underside 5 tiles up.
const CEILING_Y := -5.0 * T

var arena: PhysicsArena
var player: Player
var gravity: GravityState
var jumps := 0
var armed := 0
var started := 0
var arrivals := 0
var fails := 0
var faults: Array[String] = []
var _last_x := -INF


func before_each() -> void:
	arena = PhysicsArena.new()
	add_child(arena)
	gravity = GravityState.new()
	jumps = 0
	armed = 0
	started = 0
	arrivals = 0
	fails = 0
	faults.clear()
	_last_x = -INF


func after_each() -> void:
	arena.queue_free()
	await get_tree().physics_frame


func _corridor(ceiling_y := CEILING_Y, from := -20.0, to := 400.0) -> void:
	arena.block(Vector2(from * T, 0), Vector2((to - from) * T, T))
	arena.block(Vector2(from * T, ceiling_y - T), Vector2((to - from) * T, T))


func _spawn(on_ceiling := false, ceiling_y := CEILING_Y, x := 0.0) -> void:
	gravity.reset(on_ceiling)
	player = PhysicsArena.PLAYER_SCENE.instantiate()
	arena.add_child(player)
	player.kill_y = 10 * T
	player.kill_top = ceiling_y - 10 * T
	player.gravity = gravity
	player.set_surface_attach(true)
	player.visual.garden_style = true
	player.respawn_at(Vector2(x, ceiling_y if on_ceiling else 0.0), true)
	for s in [player.jumped, player.double_jumped]:
		s.connect(func() -> void: jumps += 1)
	player.attach_armed.connect(func() -> void: armed += 1)
	player.attach_started.connect(func(_at: Vector2, _up: bool) -> void: started += 1)
	player.attached.connect(func(_up: bool) -> void: arrivals += 1)
	player.attach_failed.connect(func() -> void: fails += 1)
	await arena.ticks(3)
	_last_x = player.global_position.x


## One tick, then the direction rules (the run goes right, nothing mirrored).
func _step() -> void:
	await arena.ticks(1)
	if player.is_dead():
		return
	var x := player.global_position.x
	if x <= _last_x:
		faults.append("x went from %.2f to %.2f" % [_last_x, x])
	_last_x = x
	if player.velocity.x <= 0.0:
		faults.append("velocity.x %.1f" % player.velocity.x)
	if player.visual.scale.x != 1.0 or player.visual.rotation != 0.0 or player.fx.scale.x != 1.0:
		faults.append("mirrored or turned")


func _steps(n: int) -> void:
	for i in n:
		await _step()


func _step_until(condition: Callable, limit := 120) -> bool:
	for i in limit:
		if condition.call():
			return true
		await _step()
	return condition.call()


func _on_ceiling(ceiling_y := CEILING_Y) -> bool:
	return player.is_on_floor() and gravity.up and absf(player.get_feet_position().y - ceiling_y) < 1.0


func _on_ground() -> bool:
	return player.is_on_floor() and not gravity.up and absf(player.get_feet_position().y) < 1.0


## TAP TAP: two taps [param gap] ticks apart (0: in the same frame).
func _tap_tap(gap := 4) -> void:
	player.request_jump()
	if gap > 0:
		await _steps(gap)
	player.request_jump()


## [param hops] hops (each attach's gesture begins with one), and never a double jump.
func _hops(hops: int) -> void:
	assert_eq(jumps, hops, "%d hop(s), never a double jump" % hops)


## Feet height above the floor the player runs on (px).
func _feet_height(floor_y := 0.0) -> float:
	return absf(player.get_feet_position().y - floor_y)


# 1. A lone tap is a hop: up about hop_height, back down on the same surface.
func test_one_tap_on_the_ground_hops() -> void:
	_corridor()
	await _spawn()
	player.request_jump()
	assert_eq(armed, 1, "the gesture has begun (its hop)")
	var top := 0.0
	var ticks := 0
	await _step()
	assert_false(player.is_on_floor(), "off the ground at once: the tap never waits for a second one")
	while not player.is_on_floor() and ticks < 60:
		top = maxf(top, _feet_height())
		await _step()
		ticks += 1
	assert_true(_on_ground(), "back on the ground")
	assert_near(top, player.config.hop_height, 3.0, "a short hop (%.1f px)" % top)
	assert_true(ticks >= 20 and ticks <= 28, "about %.2f s in the air (%d ticks)" % [player.config.hop_airtime(), ticks])
	assert_eq(started + fails, 0, "no attach: one tap is only the hop")
	assert_false(gravity.up)
	_hops(1)
	await _steps(20)
	assert_eq(player.motor.gesture, PlayerMotor.Gesture.IDLE, "the gesture window ran out")
	assert_eq(faults, [] as Array[String])


# 2. TAP TAP on the ground: the hop becomes one crossing to the ceiling.
func test_tap_tap_on_the_ground_attaches_to_the_ceiling() -> void:
	_corridor()
	await _spawn()
	await _tap_tap(4)
	var crossing: Array[float] = []
	var gravity_before_touch := true
	for i in 40:
		await _step()
		if player.is_attaching():
			crossing.append(player.global_position.y)
			gravity_before_touch = gravity_before_touch and not gravity.up
		if _on_ceiling():
			break
	assert_true(_on_ceiling(), "running on the ceiling")
	assert_eq(started, 1, "one attach")
	assert_eq(arrivals, 1, "it touched the ceiling")
	assert_true(gravity_before_touch, "gravity turned only on the touch, never before")
	assert_true(gravity.up, "gravity up now")
	assert_eq(player.up_direction, Vector2.DOWN, "the ceiling is the floor")
	_hops(1)
	# A crossing, not a teleport: a few ticks, each a steady step toward the ceiling.
	assert_true(crossing.size() >= 5 and crossing.size() <= 16, "a fast crossing: %d ticks" % crossing.size())
	for i in range(1, crossing.size()):
		var step := crossing[i - 1] - crossing[i]
		assert_true(step > 0.0 and step <= player.config.attach_max_speed / 60.0 + 0.5, "step %d: %.1f px up" % [i, step])
	assert_eq(faults, [] as Array[String], "left to right, never mirrored")


# 3. A very fast TAP TAP (both taps in one frame) is still one hop and one attach.
func test_a_very_fast_tap_tap_is_one_attach() -> void:
	_corridor()
	await _spawn()
	await _tap_tap(0)
	assert_true(await _step_until(_on_ceiling, 30), "on the ceiling")
	assert_eq(started, 1)
	_hops(1)


# 4. On the ceiling: a tap hops toward the ground and back; TAP TAP goes down.
func test_tap_and_tap_tap_on_the_ceiling() -> void:
	_corridor()
	await _spawn(true)
	assert_true(_on_ceiling(), "starts on the ceiling")
	player.request_jump()
	await _step()
	assert_false(player.is_on_floor(), "hopping away from the ceiling")
	assert_true(player.global_position.y > CEILING_Y + player.half_size.y, "toward the ground")
	assert_true(await _step_until(_on_ceiling, 40), "a hop: back on the ceiling")
	assert_true(gravity.up)
	await _steps(20)
	await _tap_tap(3)
	var gravity_kept := true
	for i in 40:
		await _step()
		if player.is_attaching():
			gravity_kept = gravity_kept and gravity.up
		if _on_ground():
			break
	assert_true(_on_ground(), "running on the ground")
	assert_true(gravity_kept, "gravity stayed up until the touch")
	assert_false(gravity.up)
	assert_eq(player.up_direction, Vector2.UP)
	assert_eq(started, 1)
	_hops(2)
	assert_eq(faults, [] as Array[String])


# 5. Taps during the crossing are swallowed: no second attach, nothing on arrival.
func test_taps_during_the_crossing_are_swallowed() -> void:
	_corridor()
	await _spawn()
	await _tap_tap(2)
	await _step()
	assert_true(player.is_attaching(), "crossing")
	for i in 3:
		player.request_jump()
		await _step()
	assert_true(await _step_until(_on_ceiling, 30), "arrived")
	await _steps(20)
	assert_true(_on_ceiling(), "stayed on the ceiling: the taps did nothing")
	assert_eq(started, 1)
	assert_eq(player.motor.gesture, PlayerMotor.Gesture.IDLE, "no gesture carried over")
	_hops(1)


# 6. A tap on the landing tick starts the next gesture: TAP TAP goes back.
func test_tap_tap_right_on_arrival_goes_straight_back() -> void:
	_corridor()
	await _spawn()
	await _tap_tap(2)
	assert_true(await _step_until(func() -> bool: return arrivals == 1, 30), "arrived")
	await _tap_tap(2)
	assert_true(await _step_until(_on_ground, 40), "back on the ground")
	assert_eq(started, 2, "two attaches, each from its own gesture")
	_hops(2)
	assert_eq(faults, [] as Array[String])


# 7-8. Taps during the death and the respawn do nothing; after it, both moves work.
func test_taps_around_a_death_and_a_respawn() -> void:
	_corridor()
	await _spawn()
	await _tap_tap(2)
	await _steps(3)
	assert_true(player.is_attaching())
	player.die(&"test")
	for i in 3:
		player.request_jump()
		await arena.ticks(1)
	gravity.set_up(false, true)
	player.respawn_at(Vector2(player.global_position.x, 0.0), true)
	_last_x = player.global_position.x
	assert_false(player.is_attaching(), "no crossing carried over")
	player.request_jump()  # A tap on the respawn itself: a hop.
	await _steps(40)
	assert_true(_on_ground(), "the hop landed; nothing else happened")
	assert_eq(started, 1)
	await _tap_tap(3)
	assert_true(await _step_until(_on_ceiling, 30), "TAP TAP works as the first time")
	assert_eq(started, 2)
	_hops(3)


# 9. Many TAP TAP in a row: ground, ceiling, ground... one transition each.
func test_surface_switching_many_times_in_a_row() -> void:
	_corridor(CEILING_Y, -20.0, 1200.0)
	await _spawn()
	for i in 12:
		var want_up := i % 2 == 0
		await _tap_tap(3)
		assert_true(await _step_until(func() -> bool: return _on_ceiling() if want_up else _on_ground(), 40),
			"attach %d reached its surface" % (i + 1))
		await _steps(6)
	assert_eq(started, 12, "every gesture attached once")
	assert_eq(arrivals, 12)
	assert_eq(fails, 0)
	assert_false(gravity.up, "an even number: back on the ground")
	_hops(12)
	assert_eq(faults, [] as Array[String])


# Rapid repeated taps (mashing): never higher than one hop off a surface
# (no second jump), never an attach without two taps after the last
# arrival, never more than one crossing at a time.
func test_mashing_never_chains_attaches_and_never_double_jumps() -> void:
	_corridor(CEILING_Y, -20.0, 1200.0)
	await _spawn()
	var taps_since_arrival := 0
	var last_arrivals := 0
	for i in 300:
		player.request_jump()
		if not player.is_attaching():
			taps_since_arrival += 1
		await _step()
		if arrivals != last_arrivals:
			last_arrivals = arrivals
			taps_since_arrival = 0
		if player.motor.jump_this_tick == PlayerMotor.Jump.ATTACH:
			assert_true(taps_since_arrival >= 2, "tick %d: an attach needs its own two taps" % i)
			taps_since_arrival = 0
		if not player.is_attaching():
			var floor_y := CEILING_Y if gravity.up else 0.0
			assert_true(_feet_height(floor_y) <= player.config.hop_height + 3.0,
				"tick %d: no higher than one hop (%.1f px)" % [i, _feet_height(floor_y)])
	assert_true(started >= 4, "the surfaces kept switching (%d)" % started)
	assert_true(started - arrivals <= 1, "one crossing at a time")
	assert_eq(faults, [] as Array[String])


# A tap in the air, after the hop's window: never a second jump; it waits
# for the landing and hops then. Two such taps are the whole gesture.
func test_taps_in_the_air_wait_for_the_landing() -> void:
	_corridor()
	await _spawn()
	player.request_jump()
	await _steps(int(player.config.attach_window * 60.0) + 2)
	assert_false(player.is_on_floor(), "still in the air, the window over")
	var vy := player.velocity.y
	player.request_jump()  # Near the landing.
	await _step()
	assert_true(player.velocity.y >= vy, "no jump in the air: still coming down")
	assert_true(await _step_until(func() -> bool: return jumps == 2, 20), "the waiting tap hops on the landing")
	_hops(2)
	assert_eq(started + fails, 0, "one tap in the air: a hop, no attach")
	# Two taps in the air before landing: the gesture, played on the landing.
	assert_true(await _step_until(func() -> bool: return player.motor.gesture == PlayerMotor.Gesture.IDLE \
		and not player.is_on_floor() and player.velocity.y > 0.0, 40), "coming down again")
	await _step_until(func() -> bool: return _feet_height() < 30.0, 30)
	player.request_jump()
	player.request_jump()
	assert_true(await _step_until(_on_ceiling, 40), "TAP TAP just before the landing: attached")
	assert_eq(started, 1)
	assert_eq(faults, [] as Array[String])


# 10. TAP TAP with no surface across: a failed attach; the hop goes on as it was.
func test_no_surface_across_is_a_failed_attach_and_only_the_hop() -> void:
	arena.block(Vector2(-20 * T, 0), Vector2(420 * T, T))  # Ground only: open sky.
	await _spawn()
	await _tap_tap(3)
	await _step()
	assert_eq(fails, 1, "the gesture failed")
	assert_eq(started, 0)
	var top := 0.0
	while not player.is_on_floor():
		top = maxf(top, _feet_height())
		await _step()
	assert_near(top, player.config.hop_height, 3.0, "just the hop: no fallback jump, no extra height")
	assert_true(_on_ground())
	assert_false(gravity.up)
	_hops(1)


# 11. Something solid between the surfaces is what the attach meets: never
# through it. A slab across the way: the attach lands on its underside.
func test_a_slab_between_the_surfaces_is_met_first() -> void:
	_corridor()
	arena.block(Vector2(-2 * T, -3.0 * T - 16.0), Vector2(40 * T, 16.0))
	await _spawn()
	await _tap_tap(2)
	assert_true(await _step_until(func() -> bool: return player.is_on_floor() and gravity.up, 30), "attached")
	assert_near(player.get_feet_position().y, -3.0 * T, 1.0, "on the slab's underside, never through it")
	assert_eq(faults, [] as Array[String])


# 11b. A wall in the crossing's path blocks it: a failed attach.
func test_a_wall_in_the_path_blocks_the_attach() -> void:
	_corridor()
	# A pillar standing on the ground just ahead, 3 tiles tall: the diagonal
	# crossing would hit its side before it cleared its top.
	arena.block(Vector2(1.3 * T, -3.0 * T), Vector2(0.5 * T, 3.0 * T))
	await _spawn()
	await _tap_tap(2)
	await _step()
	assert_eq(fails, 1, "blocked: the attach fails")
	assert_eq(started, 0)
	assert_false(gravity.up)


# 11c. A surface that cannot be held (slick stone): a failed attach.
func test_a_slick_surface_cannot_be_attached_to() -> void:
	arena.block(Vector2(-20 * T, 0), Vector2(420 * T, T))
	var roof := arena.block(Vector2(-20 * T, CEILING_Y - T), Vector2(420 * T, T))
	var slick := GDScript.new()
	slick.source_code = "extends Block\nvar latchable := false\n"
	slick.reload()
	roof.set_script(slick)
	roof.size = Vector2(420 * T, T)
	await _spawn()
	await _tap_tap(2)
	await _step()
	assert_eq(fails, 1)
	assert_eq(started, 0)


# 12. Near the end of the target surface: it must still be there where the
# crossing arrives, or the attach fails.
func test_a_ceiling_ending_before_the_arrival_fails() -> void:
	arena.block(Vector2(-20 * T, 0), Vector2(420 * T, T))
	# The ceiling ends 1 tile ahead: the crossing (~1.8 tiles forward) would miss it.
	arena.block(Vector2(-20 * T, CEILING_Y - T), Vector2(21 * T, T))
	await _spawn()
	player.set_speed_scale(1.0)
	await _tap_tap(0)
	await _steps(2)
	assert_eq(fails, 1, "the ceiling would be gone on arrival")
	assert_false(gravity.up)
	assert_true(await _step_until(_on_ground, 40), "the hop comes down")


# Long and short distances: beyond attach_reach fails; a near surface is quick.
func test_reach_long_fails_short_is_quick() -> void:
	var high := -8.0 * T  # 512 - 48 = 464 px of free space; minus the hop: still beyond the reach.
	_corridor(high)
	await _spawn(false, high)
	await _tap_tap(2)
	await _step()
	assert_eq(fails, 1, "out of reach")
	await _step_until(_on_ground, 40)
	arena.block(Vector2(player.global_position.x + T, -2.0 * T - 16.0), Vector2(60 * T, 16.0))  # A low slab ahead.
	await _steps(4)
	await _tap_tap(2)
	var ticks := 0
	while not (player.is_on_floor() and gravity.up) and ticks < 30:
		await _step()
		ticks += 1
	assert_true(gravity.up, "attached to the low slab")
	assert_true(ticks <= 6, "a short crossing is quick (%d ticks)" % ticks)


# The second tap too late (after the window, back on the ground) is a new first tap: another hop.
func test_a_second_tap_after_the_window_starts_a_new_gesture() -> void:
	_corridor()
	await _spawn()
	player.request_jump()
	assert_true(await _step_until(func() -> bool: return player.is_on_floor() and jumps == 1, 40), "the hop landed")
	player.request_jump()
	await _steps(40)
	assert_eq(started, 0, "no attach")
	assert_eq(armed, 2, "two separate beginnings")
	_hops(2)
	assert_true(_on_ground())


# A moving ceiling: the attach meets it where it is.
func test_a_moving_ceiling_is_attached_to_where_it_is() -> void:
	arena.block(Vector2(-20 * T, 0), Vector2(420 * T, T))
	var panel := arena.block(Vector2(-20 * T, -8.5 * T - 32.0), Vector2(200 * T, 32.0), true)
	await _spawn()
	await _tap_tap(2)
	await _step()
	assert_eq(fails, 1, "risen out of reach")
	await _step_until(_on_ground, 40)
	for i in 30:
		panel.position.y += 3.5 * T / 30.0  # Coming down over 30 ticks.
		await _step()
	await _tap_tap(2)
	assert_true(await _step_until(func() -> bool: return player.is_on_floor() and gravity.up, 40), "on the panel")
	assert_near(player.get_feet_position().y, -5.0 * T, 1.0, "on its underside")


# Just off a ledge (coyote time): a tap still hops, TAP TAP still attaches.
func test_attach_at_a_ledge_edge_uses_coyote_time() -> void:
	arena.block(Vector2(-20 * T, 0), Vector2(21 * T, T))  # Ground ends at x = 1 tile.
	arena.block(Vector2(-20 * T, CEILING_Y - T), Vector2(420 * T, T))
	await _spawn()
	player.set_speed_scale(1.0)
	assert_true(await _step_until(func() -> bool: return not player.is_on_floor(), 30), "ran off the edge")
	await _tap_tap(0)
	assert_true(await _step_until(_on_ceiling, 30), "just off the edge: still a hop and an attach")
	_hops(1)
