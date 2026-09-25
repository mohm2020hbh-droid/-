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
	while player.global_position.x + Player.SIZE * 0.5 < T * 2.5:
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
