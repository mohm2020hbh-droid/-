extends TestCase
## Player behaviour on the real physics server, in small arenas built in code.

const PLAYER_SCENE := preload("res://src/player/player.tscn")
const SHARD_SCENE := preload("res://src/level/elements/shard.tscn")
const T := GameConst.TILE
const TICK_DISTANCE := 520.0 / 60.0

var arena: Node2D
var player: Player


func before_each() -> void:
	arena = Node2D.new()
	add_child(arena)


func after_each() -> void:
	arena.queue_free()
	await get_tree().physics_frame


func _block(pos: Vector2, size: Vector2, moving := false) -> Block:
	var block: Block = Block.new() if not moving else _animatable_block()
	block.position = pos
	block.size = size
	arena.add_child(block)
	return block


func _animatable_block() -> Block:
	var body: Object = AnimatableBody2D.new()
	body.set_script(Block)
	return body as Block


func _spawn_player(feet: Vector2, run := true) -> void:
	player = PLAYER_SCENE.instantiate()
	arena.add_child(player)
	player.kill_y = 400.0
	player.respawn_at(feet, run)


func _ticks(n: int) -> void:
	for i in n:
		await get_tree().physics_frame


func test_stands_on_ground_and_runs_at_exact_speed() -> void:
	_block(Vector2(-T * 4, 0), Vector2(T * 40, T * 4))
	_spawn_player(Vector2(0, 0))
	await _ticks(5)
	var x0 := player.global_position.x
	await _ticks(30)
	assert_true(player.is_on_floor(), "grounded")
	assert_near(player.get_feet_position().y, 0.0, 0.5, "feet on the surface")
	assert_near(player.global_position.x - x0, 30 * TICK_DISTANCE, 0.5, "x advances at run speed")
	assert_eq(player.state, Player.State.RUN)


func test_real_jump_reaches_configured_height() -> void:
	_block(Vector2(-T * 4, 0), Vector2(T * 60, T * 4))
	_spawn_player(Vector2(0, 0))
	await _ticks(3)
	player.request_jump()
	var apex := 0.0
	for i in 60:
		await _ticks(1)
		apex = minf(apex, player.get_feet_position().y)
	assert_near(-apex, player.config.jump_height, 1.5, "apex on real physics")
	assert_true(player.is_on_floor(), "landed again")


func test_head_on_wall_kills() -> void:
	_block(Vector2(-T * 4, 0), Vector2(T * 20, T * 4))
	_block(Vector2(T * 3, -T * 2), Vector2(T, T * 2))
	_spawn_player(Vector2(0, 0))
	await _ticks(40)
	assert_true(player.is_dead(), "wall hit is fatal (no softlock)")


func test_ledge_assist_steps_onto_low_lip() -> void:
	_block(Vector2(-T * 4, 0), Vector2(T * 8, T * 4))
	_block(Vector2(T * 4, -8), Vector2(T * 10, T * 4))
	_spawn_player(Vector2(0, 0))
	await _ticks(40)
	assert_false(player.is_dead(), "an 8 px lip is forgiven")
	assert_near(player.get_feet_position().y, -8.0, 0.5, "standing on the lip")


func test_lip_above_assist_limit_kills() -> void:
	_block(Vector2(-T * 4, 0), Vector2(T * 8, T * 4))
	_block(Vector2(T * 4, -20), Vector2(T * 10, T * 4))
	_spawn_player(Vector2(0, 0))
	await _ticks(40)
	assert_true(player.is_dead(), "a 20 px lip is a wall")


func test_spikes_kill_and_jump_clears_them() -> void:
	_block(Vector2(-T * 4, 0), Vector2(T * 30, T * 4))
	var spikes := Spikes.new()
	spikes.position = Vector2(T * 4, 0)
	spikes.count = 2
	arena.add_child(spikes)
	_spawn_player(Vector2(0, 0))
	await _ticks(60)
	assert_true(player.is_dead(), "running into spikes")

	player.queue_free()
	await _ticks(1)
	_spawn_player(Vector2(0, 0))
	# Jump with the front edge ~1.5 tiles before the first spike.
	while player.global_position.x + player.half_size.x < T * 2.5:
		await _ticks(1)
	player.request_jump()
	await _ticks(70)
	assert_false(player.is_dead(), "a timed jump clears two spikes")


func test_falling_into_a_pit_kills() -> void:
	_block(Vector2(-T * 4, 0), Vector2(T * 6, T * 4))
	_spawn_player(Vector2(0, 0))
	await _ticks(90)
	assert_true(player.is_dead(), "fell below kill_y")


func test_collects_shard_on_contact() -> void:
	_block(Vector2(-T * 4, 0), Vector2(T * 20, T * 4))
	var shard: Shard = SHARD_SCENE.instantiate()
	shard.position = Vector2(T * 3, -32)
	arena.add_child(shard)
	var collected := [false]
	shard.collected.connect(func(_s: Shard) -> void: collected[0] = true)
	_spawn_player(Vector2(0, 0))
	await _ticks(40)
	assert_true(collected[0], "shard collected")
	assert_true(shard.is_collected)


func test_moving_platform_carries_vertically_but_not_horizontally() -> void:
	_block(Vector2(-T * 4, 0), Vector2(T * 4, T * 4))
	var mover := _block(Vector2(0, 0), Vector2(T * 30, 32), true)
	_spawn_player(Vector2(T, 0))
	var step := Vector2(6.0, -2.0)
	# Platform drifts right and up at a steady speed (compensation reads the
	# platform velocity of the previous tick, so give it one tick to settle).
	for i in 3:
		mover.position += step
		await _ticks(1)
	var x0 := player.global_position.x
	var y0 := player.global_position.y
	for i in 30:
		mover.position += step
		await _ticks(1)
	assert_false(player.is_dead())
	assert_near(player.global_position.x - x0, 30 * TICK_DISTANCE, 1.0, "run speed unchanged")
	assert_near(y0 - player.global_position.y, 60.0, 3.0, "lifted by the platform")


func test_phase_block_does_not_solidify_inside_player() -> void:
	_block(Vector2(-T * 4, 0), Vector2(T * 40, T * 4))
	var phase := PhaseBlock.new()
	phase.position = Vector2(T * 2, -T)
	phase.size = Vector2(T * 2, T)
	phase.period = 1.0
	phase.solid_ratio = 0.5
	arena.add_child(phase)
	phase.apply_time(0.6)  # Absent.
	assert_false(phase.is_solid())
	_spawn_player(Vector2(T * 3, 0), false)  # Standing inside its area.
	await _ticks(2)
	phase.apply_time(1.1)  # Would be solid now, but the player is inside.
	assert_false(phase.is_solid(), "waits for the player to leave")
	player.global_position.x += T * 4
	await _ticks(2)
	phase.apply_time(1.1)
	assert_true(phase.is_solid(), "solid once clear")


# --- Edge cases -------------------------------------------------------------

func _reset_arena() -> void:
	arena.queue_free()
	await _ticks(1)
	arena = Node2D.new()
	add_child(arena)


func _track_deaths() -> Array[StringName]:
	var causes: Array[StringName] = []
	player.died.connect(func(cause: StringName) -> void: causes.append(cause))
	return causes


func test_jump_starts_on_the_very_next_physics_tick() -> void:
	_block(Vector2(-T * 4, 0), Vector2(T * 40, T * 4))
	_spawn_player(Vector2(0, 0))
	await _ticks(3)
	var jump_frames: Array[int] = []
	player.jumped.connect(func() -> void: jump_frames.append(Engine.get_physics_frames()))
	var tap_frame := Engine.get_physics_frames()
	player.request_jump()
	await _ticks(3)
	assert_eq(jump_frames.size(), 1, "exactly one jump")
	# The tap lands before this physics step runs; the jump must happen in
	# that same step (no extra tick of delay).
	assert_eq(jump_frames[0], tap_frame, "jump starts in the first physics step after the tap")


func test_gaps_up_to_one_tile_are_run_over_without_jumping() -> void:
	for gap in [16.0, 40.0, 64.0]:
		await _reset_arena()
		_block(Vector2(-T * 4, 0), Vector2(T * 8, T * 4))
		_block(Vector2(T * 4 + gap, 0), Vector2(T * 10, T * 4))
		_spawn_player(Vector2(0, 0))
		await _ticks(50)
		assert_false(player.is_dead(), "a %d px gap does not need a jump" % gap)
		assert_near(player.get_feet_position().y, 0.0, 0.5, "back on the surface after a %d px gap" % gap)


func test_two_tile_gap_without_jump_is_fatal() -> void:
	_block(Vector2(-T * 4, 0), Vector2(T * 8, T * 4))
	_block(Vector2(T * 6, 0), Vector2(T * 10, T * 4))
	_spawn_player(Vector2(0, 0))
	await _ticks(90)
	assert_true(player.is_dead(), "falls between the platforms")


func test_three_pixel_overlap_is_enough_to_land_and_stand() -> void:
	_block(Vector2(0, 0), Vector2(T * 2, T))
	_spawn_player(Vector2(-player_half() + 3.0, -80.0), false)
	await _ticks(60)
	assert_false(player.is_dead())
	assert_true(player.is_on_floor(), "stands on a 3 px overlap")
	assert_near(player.get_feet_position().y, 0.0, 0.5)


func test_no_jitter_after_landing() -> void:
	_block(Vector2(-T * 4, 0), Vector2(T * 60, T * 4))
	_spawn_player(Vector2(0, 0))
	await _ticks(2)
	player.request_jump()
	await _ticks(60)
	var lowest := -INF
	var highest := INF
	for i in 60:
		await _ticks(1)
		lowest = maxf(lowest, player.global_position.y)
		highest = minf(highest, player.global_position.y)
	assert_near(lowest - highest, 0.0, 0.01, "no vertical jitter while running")


func test_head_bump_under_a_low_ceiling_is_not_fatal() -> void:
	_block(Vector2(-T * 4, 0), Vector2(T * 40, T * 4))
	_block(Vector2(-T * 2, -T * 3), Vector2(T * 20, T * 1.5))  # Bottom at -96: 48 px clearance.
	_spawn_player(Vector2(0, 0))
	await _ticks(2)
	player.request_jump()
	await _ticks(60)
	assert_false(player.is_dead(), "bumping a ceiling only stops the rise")
	assert_true(player.is_on_floor(), "back on the ground")


func test_near_miss_while_falling_onto_a_ledge_corner_is_forgiven() -> void:
	_block(Vector2(0, 0), Vector2(T * 10, T * 4))
	# Right edge 3 px short of the ledge face, feet 5 px above its top, falling
	# fast: this tick's move would clip the face just below the corner.
	_spawn_player(Vector2(-player_half() - 3.0, -5.0))
	player.motor.velocity = Vector2(0.0, 1000.0)
	var causes := _track_deaths()
	await _ticks(10)
	assert_eq(causes, [] as Array[StringName], "landed instead of hitting the wall")
	assert_near(player.get_feet_position().y, 0.0, 0.5, "standing on the ledge")


func test_elevator_crushing_into_ceiling_kills() -> void:
	_block(Vector2(-T * 4, 0), Vector2(T * 4, T * 4))
	var lift := _block(Vector2(-T, 0), Vector2(T * 6, 32), true)
	_block(Vector2(-T * 2, -T * 2), Vector2(T * 10, T))  # Ceiling 80 px above the feet.
	_spawn_player(Vector2(T, 0), false)
	var causes := _track_deaths()
	for i in 40:
		lift.position.y -= 4.0
		await _ticks(1)
	assert_eq(causes, [&"crush"] as Array[StringName], "crushed, not pushed through")


func test_die_and_respawn_in_one_frame_keeps_collision() -> void:
	_block(Vector2(-T * 4, 0), Vector2(T * 40, T * 4))
	_spawn_player(Vector2(0, 0))
	await _ticks(3)
	player.die(&"test")
	player.respawn_at(Vector2(0, 0), true)
	await _ticks(30)
	assert_false(player.is_dead(), "did not fall through the floor")
	assert_near(player.get_feet_position().y, 0.0, 0.5)


func test_max_speed_fall_does_not_tunnel_through_a_thin_platform() -> void:
	_block(Vector2(-T * 4, 0), Vector2(T * 40, 8))
	player = PLAYER_SCENE.instantiate()
	arena.add_child(player)
	player.kill_y = 400.0
	player.respawn_at(Vector2(0, -3000), false)
	await _ticks(200)  # ~2.4 s of fall, most of it at max speed.
	assert_false(player.is_dead(), "caught by an 8 px platform at max fall speed")
	assert_near(player.get_feet_position().y, 0.0, 0.5)


func test_buffered_tap_jumps_on_the_first_grounded_tick() -> void:
	_block(Vector2(-T * 4, 0), Vector2(T * 60, T * 4))
	_spawn_player(Vector2(0, 0))
	await _ticks(2)
	player.request_jump()
	await _ticks(10)
	while player.get_feet_position().y < -30.0 or player.velocity.y < 0.0:
		await _ticks(1)
	var events: Array[String] = []
	player.landed.connect(func(_i: float) -> void: events.append("land@%d" % Engine.get_physics_frames()))
	player.jumped.connect(func() -> void: events.append("jump@%d" % Engine.get_physics_frames()))
	player.request_jump()  # ~50 ms before touching down.
	await _ticks(20)
	assert_eq(events.size(), 2, "landed then jumped: %s" % [events])
	if events.size() == 2:
		var land := int(events[0].get_slice("@", 1))
		var jump := int(events[1].get_slice("@", 1))
		assert_eq(jump - land, 1, "jump fires on the first grounded tick")


func test_coyote_jump_after_running_off_an_edge() -> void:
	_block(Vector2(-T * 4, 0), Vector2(T * 6, T * 4))
	_block(Vector2(T * 7, 0), Vector2(T * 10, T * 4))
	_spawn_player(Vector2(0, 0))
	# Wait until the back edge has left the platform, then 2 more ticks.
	while player.global_position.x - player_half() < T * 2:
		await _ticks(1)
	await _ticks(2)
	assert_false(player.is_on_floor(), "already off the edge")
	player.request_jump()
	await _ticks(60)
	assert_false(player.is_dead(), "coyote time rescued a late tap")


func test_jumping_into_ceiling_spikes_kills() -> void:
	_block(Vector2(-T * 4, 0), Vector2(T * 40, T * 4))
	var spikes := Spikes.new()
	spikes.position = Vector2(T * 2, -T * 2)
	spikes.count = 20
	spikes.facing = Spikes.Facing.DOWN
	arena.add_child(spikes)
	_spawn_player(Vector2(0, 0))
	await _ticks(60)
	assert_false(player.is_dead(), "running under ceiling spikes is safe")
	player.request_jump()
	await _ticks(30)
	assert_true(player.is_dead(), "jumping into them is not")


## Half width of the player's body, read from the scene (before any spawn).
func player_half() -> float:
	var probe := PLAYER_SCENE.instantiate()
	var half: float = ((probe.get_node("BodyShape") as CollisionShape2D).shape as RectangleShape2D).size.x * 0.5
	probe.free()
	return half


func test_riding_an_elevator_up_and_down_stays_attached() -> void:
	var lift := _block(Vector2(-T * 2, 0), Vector2(T * 4, 32), true)
	_spawn_player(Vector2(0, 0), false)
	var landings: Array[int] = [0]
	player.landed.connect(func(_i: float) -> void: landings[0] += 1)
	for i in 60:  # Down at 180 px/s, then up at 240 px/s.
		lift.position.y += 3.0
		await _ticks(1)
		assert_true(player.is_on_floor(), "attached while descending (tick %d)" % i)
	for i in 60:
		lift.position.y -= 4.0
		await _ticks(1)
		assert_true(player.is_on_floor(), "attached while rising (tick %d)" % i)
	assert_eq(landings[0], 0, "no repeated landing events while riding")
	await _ticks(2)  # Let the last platform move reach the physics server.
	assert_near(player.get_feet_position().y, lift.position.y, 1.0, "feet on the platform")


func test_player_falls_when_a_phase_block_vanishes_under_it() -> void:
	var phase := PhaseBlock.new()
	phase.position = Vector2(-T * 2, 0)
	phase.size = Vector2(T * 4, 32)
	phase.period = 1.0
	phase.solid_ratio = 0.5
	arena.add_child(phase)
	phase.apply_time(0.1)
	_spawn_player(Vector2(0, 0), false)
	await _ticks(5)
	assert_true(player.is_on_floor(), "standing while solid")
	phase.apply_time(0.6)
	await _ticks(10)
	assert_false(player.is_on_floor(), "falls once it vanishes")
	assert_true(player.get_feet_position().y > 20.0, "dropped through where it was")


func test_max_speed_fall_onto_a_rising_thin_platform() -> void:
	var lift := _block(Vector2(-T * 2, 0), Vector2(T * 4, 16), true)
	player = PLAYER_SCENE.instantiate()
	arena.add_child(player)
	player.kill_y = 800.0
	player.respawn_at(Vector2(0, -2500), false)
	for i in 200:
		lift.position.y -= 5.0  # Rising at 300 px/s toward a player at max fall speed.
		await _ticks(1)
		if player.is_on_floor():
			break
	assert_true(player.is_on_floor(), "caught by a 16 px platform rising into a max-speed fall")


func test_grazing_an_overhang_corner_while_falling() -> void:
	_block(Vector2(-T * 4, 0), Vector2(T * 20, T * 4))
	# Overhang starting 3 px ahead whose underside is 3 px below our head
	# (head at y = -198, underside at -195), while we drop slowly past it.
	var half := player_half()
	_block(Vector2(3.0, -400.0), Vector2(T * 4, 400.0 - 195.0))
	_spawn_player(Vector2(-half, -150.0))
	player.motor.velocity = Vector2(0.0, 200.0)
	var causes := _track_deaths()
	await _ticks(40)
	assert_eq(causes, [] as Array[StringName], "a 3 px corner graze is not a head-on wall hit")


func test_a_real_overhang_hit_in_the_air_still_kills() -> void:
	_block(Vector2(-T * 4, 0), Vector2(T * 20, T * 4))
	var half := player_half()
	_block(Vector2(3.0, -400.0), Vector2(T * 4, 400.0 - 178.0))  # 20 px into the head.
	_spawn_player(Vector2(-half, -150.0))
	player.motor.velocity = Vector2(0.0, 200.0)
	var causes := _track_deaths()
	await _ticks(20)
	assert_eq(causes, [&"wall"] as Array[StringName], "beyond the assist limit it is a wall hit")
