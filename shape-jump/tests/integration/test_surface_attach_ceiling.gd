extends TestCase
## World 04's surface attach, from the CEILING, on the real physics server:
## CEILING RUN -> tap 1 (release, away from the ceiling) -> AIRBORNE -> tap 2
## -> the ground is validated -> LATCHING -> GROUND RUN. Never a double jump,
## never an instant teleport, never the wrong gravity, and the run never
## goes right to left: every test here also watches, every tick, that the
## player keeps moving right and is never mirrored.

const T := GameConst.TILE
## The standard World 04 corridor: the ceiling's underside 5 tiles up.
const CEILING_Y := -5.0 * T

enum Phase { CEILING_RUN, AIRBORNE, LATCHING, GROUND_RUN }

var arena: PhysicsArena
var player: Player
var gravity: GravityState
var jumps := 0
var double_jumps := 0
var latches := 0
var misses := 0
## Direction faults seen by [method _step] (empty: none).
var faults: Array[String] = []
var _last_x := -INF


func before_each() -> void:
	arena = PhysicsArena.new()
	add_child(arena)
	gravity = GravityState.new()
	jumps = 0
	double_jumps = 0
	latches = 0
	misses = 0
	faults.clear()
	_last_x = -INF


func after_each() -> void:
	arena.queue_free()
	await get_tree().physics_frame


func _corridor(ceiling_y := CEILING_Y, from := -20.0, to := 200.0) -> void:
	arena.block(Vector2(from * T, 0), Vector2((to - from) * T, T))
	arena.block(Vector2(from * T, ceiling_y - T), Vector2((to - from) * T, T))


## A running player on the ceiling (or the ground), as World 04 sets it up.
func _spawn(on_ceiling := true, x := 0.0) -> void:
	gravity.reset(on_ceiling)
	player = PhysicsArena.PLAYER_SCENE.instantiate()
	arena.add_child(player)
	player.kill_y = 10 * T
	player.kill_top = CEILING_Y - 10 * T
	player.gravity = gravity
	player.set_surface_latch(true)
	player.visual.garden_style = true
	player.respawn_at(Vector2(x, CEILING_Y if on_ceiling else 0.0), true)
	player.jumped.connect(func() -> void: jumps += 1)
	player.double_jumped.connect(func() -> void: double_jumps += 1)
	player.latched.connect(func(_at: Vector2) -> void: latches += 1)
	player.latch_missed.connect(func() -> void: misses += 1)
	await arena.ticks(3)
	_last_x = player.global_position.x


## One physics tick, then the direction rules: the run goes right, the body
## is never mirrored or turned round, its particles neither.
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
	if player.visual.scale.x != 1.0 or player.visual.rotation != 0.0:
		faults.append("visual mirrored or turned (scale %s, rotation %.2f)" % [player.visual.scale, player.visual.rotation])
	if player.fx.scale.x != 1.0 or player.fx.rotation != 0.0:
		faults.append("fx mirrored or turned")
	if player.rotation != 0.0:
		faults.append("body turned")


func _steps(n: int) -> void:
	for i in n:
		await _step()


func _phase() -> Phase:
	if player.is_latching():
		return Phase.LATCHING
	if player.is_on_floor():
		return Phase.CEILING_RUN if gravity.up else Phase.GROUND_RUN
	return Phase.AIRBORNE


func _on_ceiling() -> bool:
	return player.is_on_floor() and gravity.up and absf(player.get_feet_position().y - CEILING_Y) < 1.0


func _on_ground() -> bool:
	return player.is_on_floor() and not gravity.up and absf(player.get_feet_position().y) < 1.0


func _step_until(condition: Callable, limit := 120) -> bool:
	for i in limit:
		if condition.call():
			return true
		await _step()
	return condition.call()


## Free space (px) from the body's far side to the ground, straight down.
func _gap_to_ground() -> float:
	var hit := KinematicCollision2D.new()
	if player.test_move(player.global_transform, Vector2(0.0, 1000.0), hit):
		return hit.get_travel().length()
	return INF


func test_ceiling_tap_tap_is_release_airborne_attach_ground_run() -> void:
	_corridor()
	await _spawn()
	assert_true(_on_ceiling(), "starts running on the ceiling")
	var phases: Array[Phase] = [_phase()]
	var crossing: Array[float] = []
	player.request_jump()  # Tap 1: release, away from the ceiling.
	for i in 18:
		await _step()
		if phases[-1] != _phase():
			phases.append(_phase())
	assert_true(gravity.up, "tap 1 alone keeps the ceiling's gravity")
	assert_true(player.velocity.y * gravity.down_sign() != 0.0 and not player.is_on_floor(), "airborne")
	player.request_jump()  # Tap 2: attach to the ground.
	for i in 30:
		await _step()
		if phases[-1] != _phase():
			phases.append(_phase())
		if player.is_latching() or (crossing.size() > 0 and crossing.size() < 20):
			crossing.append(player.global_position.y)
		if _on_ground():
			break
	await _steps(10)
	if phases[-1] != _phase():
		phases.append(_phase())
	assert_eq(phases, [Phase.CEILING_RUN, Phase.AIRBORNE, Phase.LATCHING, Phase.GROUND_RUN] as Array[Phase],
		"ceiling run -> airborne -> latching -> ground run")
	assert_eq(jumps, 1, "one jump: the release")
	assert_eq(double_jumps, 0, "never a double jump")
	assert_eq(latches, 1, "one attach")
	assert_eq(misses, 0)
	assert_false(gravity.up, "the ground's gravity")
	assert_eq(player.up_direction, Vector2.UP, "the floor is the ground")
	assert_true(_on_ground(), "running on the ground")
	assert_eq(player.visual.scale, Vector2.ONE, "the body upright again")
	# A crossing, not a teleport: the body sets off at the latch speed and
	# falls from there (never faster than the fall cap), toward the ground.
	for i in range(1, crossing.size()):
		var step := crossing[i] - crossing[i - 1]
		assert_true(step >= -0.01 and step <= player.config.max_fall_speed / 60.0 + 1.0,
			"crossing step %d: %.1f px toward the ground" % [i, step])
	assert_true(crossing.size() >= 2, "the crossing takes more than one tick (%d)" % crossing.size())
	assert_eq(faults, [] as Array[String], "left to right the whole way, never mirrored")


## Tap 2 at every delay after tap 1, from 1 tick (a very fast double tap) to
## the fall back onto the ceiling: it attaches exactly when the ground is in
## reach, never otherwise; nothing else ever happens in the air.
func test_ceiling_tap_two_at_every_delay_attaches_only_in_reach() -> void:
	_corridor(CEILING_Y, -20.0, 400.0)
	var attached_at: Array[int] = []
	for wait in range(1, 44):
		if player:
			player.queue_free()
			await arena.ticks(1)
		jumps = 0
		double_jumps = 0
		latches = 0
		misses = 0
		faults.clear()
		await _spawn(true, 0.0)
		player.request_jump()
		await _steps(wait)
		var airborne := not player.is_on_floor()
		var reach := airborne and _gap_to_ground() <= player.config.latch_reach
		player.request_jump()
		await _step()
		assert_eq(double_jumps, 0, "wait %d: never a double jump" % wait)
		if not airborne:
			# Back on the ceiling already: tap 2 is simply the next jump.
			assert_eq(latches + misses, 0, "wait %d: on a surface, no air action" % wait)
			continue
		assert_eq(latches, 1 if reach else 0, "wait %d: attaches iff the ground is in reach" % wait)
		assert_eq(misses, 0 if reach else 1, "wait %d: otherwise a miss" % wait)
		if reach:
			attached_at.append(wait)
			assert_true(await _step_until(_on_ground, 20), "wait %d: reaches the ground" % wait)
			assert_false(gravity.up, "wait %d: ground gravity" % wait)
		else:
			assert_true(gravity.up, "wait %d: a miss keeps the ceiling's gravity" % wait)
			assert_true(await _step_until(_on_ceiling, 90), "wait %d: natural gravity takes it back" % wait)
			assert_eq(latches, 0, "wait %d: no late attach" % wait)
		assert_eq(faults, [] as Array[String], "wait %d: left to right, never mirrored" % wait)
	assert_true(attached_at.size() >= 10, "a wide window around the apex attaches (%s)" % [attached_at])
	assert_true(not 1 in attached_at and not 2 in attached_at, "a double tap right off the ceiling is out of reach")


## Mashing from the ceiling: one release and one air action per airtime,
## never a third jump, never a second attach.
func test_mashing_on_the_ceiling_is_never_a_triple_jump_or_a_second_attach() -> void:
	_corridor(CEILING_Y, -20.0, 400.0)
	await _spawn()
	var airtimes := 0
	var was_floor := true
	var base := 0
	for i in 240:
		player.request_jump()
		await _step()
		if not player.is_on_floor() and was_floor:
			airtimes += 1
			base = latches + misses
		if not player.is_on_floor():
			assert_true(latches + misses - base <= 1, "tick %d: one air action per airtime" % i)
		was_floor = player.is_on_floor()
	assert_eq(double_jumps, 0, "never a double jump")
	assert_true(latches + misses <= airtimes, "at most one air action per airtime")
	assert_true(jumps <= airtimes + 1, "one jump per surface contact (%d jumps, %d airtimes)" % [jumps, airtimes])
	assert_eq(faults, [] as Array[String], "left to right, never mirrored")


## Just after entering the ceiling (a ground -> ceiling attach), tap tap
## takes the player straight back down: every attach needs its own airtime.
func test_just_after_reaching_the_ceiling_tap_tap_comes_back_to_the_ground() -> void:
	_corridor(CEILING_Y, -20.0, 400.0)
	await _spawn(false)
	player.request_jump()
	await _steps(18)
	player.request_jump()
	assert_true(await _step_until(_on_ceiling, 30), "attached to the ceiling")
	assert_eq(latches, 1)
	# The arrival tick: the next tap is the release, never an attach.
	player.request_jump()
	await _step()
	assert_eq(latches, 1, "no attach without an airtime first")
	assert_eq(jumps, 2, "the tap on arrival is the release from the ceiling")
	assert_false(player.is_on_floor(), "left the ceiling")
	await _steps(17)
	player.request_jump()
	assert_true(await _step_until(_on_ground, 30), "and tap 2 attaches to the ground")
	assert_eq(latches, 2)
	assert_eq(double_jumps, 0)
	assert_false(gravity.up)
	assert_eq(faults, [] as Array[String], "left to right, never mirrored")


## A tap in the same tick as the landing on the ceiling (falling back after
## a lone release) is the next release, never an attach.
func test_a_tap_on_the_landing_tick_is_a_release_not_an_attach() -> void:
	_corridor()
	await _spawn()
	player.request_jump()
	await _step()
	assert_true(await _step_until(func() -> bool: return player.is_on_floor(), 90), "back on the ceiling")
	player.request_jump()
	await _step()
	assert_eq(jumps, 2, "released again")
	assert_eq(latches + misses, 0, "no air action")
	assert_true(gravity.up)
	assert_eq(faults, [] as Array[String])


## Taps during the ceiling -> ground crossing are swallowed; the first tap
## after arriving is a jump off the ground.
func test_taps_while_crossing_down_are_swallowed() -> void:
	_corridor()
	await _spawn()
	player.request_jump()
	await _steps(18)
	player.request_jump()
	await _step()
	assert_true(player.is_latching(), "crossing to the ground")
	for i in 3:
		player.request_jump()
		await _step()
	assert_true(await _step_until(_on_ground, 30), "arrived")
	await _steps(6)
	assert_true(_on_ground(), "no jump fired on arrival")
	assert_eq(jumps, 1)
	assert_eq(latches, 1, "no second attach")
	player.request_jump()
	await _steps(2)
	assert_eq(jumps, 2, "a fresh tap after arriving jumps off the ground")
	assert_false(gravity.up, "upward, from the ground")
	assert_eq(faults, [] as Array[String])


## The ground ends just ahead of where the attach would land: a miss, and
## the player comes back to the ceiling by itself.
func test_ground_ending_before_the_arrival_is_a_miss_from_the_ceiling() -> void:
	var jump_x := 0.0
	var ends := jump_x + (18.0 + 3.0) * 520.0 / 60.0
	arena.block(Vector2(-20 * T, 0), Vector2(ends + 20 * T, T))
	arena.block(Vector2(-20 * T, CEILING_Y - T), Vector2(220 * T, T))
	await _spawn()
	player.set_speed_scale(1.0)
	player.request_jump()
	await _steps(18)
	player.request_jump()
	await _step()
	assert_eq(misses, 1, "the ground would be gone on arrival")
	assert_eq(latches, 0)
	assert_true(gravity.up, "the ceiling's gravity")
	assert_true(await _step_until(_on_ceiling, 90), "natural gravity: back on the ceiling")
	assert_eq(faults, [] as Array[String])


## No ground under the ceiling at all (a pit): tap 2 does nothing but spend
## itself; no teleport, no fall into the pit.
func test_no_ground_under_the_ceiling_is_a_miss_and_no_teleport() -> void:
	arena.block(Vector2(-20 * T, CEILING_Y - T), Vector2(220 * T, T))
	await _spawn()
	player.request_jump()
	await _steps(18)
	var y := player.global_position.y
	player.request_jump()
	await _step()
	assert_eq(misses, 1)
	assert_true(absf(player.global_position.y - y) < 12.0, "no teleport: one tick of normal motion")
	assert_true(await _step_until(_on_ceiling, 90), "back on the ceiling")
	assert_eq(faults, [] as Array[String])


## Something solid between the ceiling and the ground is the surface the
## attach meets: never passed through.
func test_a_slab_between_ceiling_and_ground_is_the_target_never_passed_through() -> void:
	_corridor()
	arena.block(Vector2(-2 * T, -1.6 * T), Vector2(30 * T, 16.0))
	await _spawn()
	player.request_jump()
	await _steps(6)
	assert_true(_gap_to_ground() <= player.config.latch_reach, "the slab is in reach")
	player.request_jump()
	await _step()
	assert_eq(latches, 1)
	assert_true(await _step_until(func() -> bool: return player.is_on_floor() and not gravity.up, 30), "landed")
	assert_near(player.get_feet_position().y, -1.6 * T, 1.0, "on the slab's top, never through it")
	assert_eq(faults, [] as Array[String])


## A tap while dead does nothing; after a respawn on the ceiling, tap tap
## works as the first time (no stale tap, no stale crossing).
func test_taps_around_a_death_and_a_ceiling_respawn() -> void:
	_corridor()
	await _spawn()
	player.request_jump()
	await _steps(18)
	player.request_jump()
	await _step()
	assert_true(player.is_latching())
	player.die(&"test")
	for i in 3:
		player.request_jump()  # Tapping on the death screen.
		await arena.ticks(1)
	gravity.set_up(true, true)
	player.respawn_at(Vector2(player.global_position.x, CEILING_Y), true)
	_last_x = player.global_position.x
	await _steps(4)
	assert_true(_on_ceiling(), "on the ceiling, standing")
	assert_false(player.is_latching(), "no crossing carried over")
	assert_eq(jumps, 1, "no stale tap fired on the respawn")
	player.request_jump()
	await _steps(18)
	player.request_jump()
	assert_true(await _step_until(_on_ground, 30), "tap tap reaches the ground")
	assert_eq(latches, 2)
	assert_eq(double_jumps, 0)
	assert_eq(faults, [] as Array[String])
