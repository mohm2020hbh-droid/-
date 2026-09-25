class_name PlayerMotor
extends RefCounted
## Pure movement rules for the player: auto-run, jump, coyote time, jump
## buffer and gravity. It never touches nodes or the physics server; the
## [Player] feeds it collision results, and unit tests feed it synthetic ones.
##
## Each physics tick is split in two halves around the collision move:
##   velocity = begin_tick(on_floor, dt)   # run speed, jump, half gravity
##   <move the body with velocity>
##   velocity = end_tick(resolved_velocity, dt)  # other half of gravity
## Applying gravity half before and half after the move integrates the
## parabola exactly, so the real jump height matches the configured one.

var config: MovementConfig
var velocity := Vector2.ZERO
## When false the body stands still (before the first tap, after finishing).
var running := false
## Level-wide run speed multiplier (difficulty knob).
var speed_scale := 1.0
## True during the tick in which a jump started.
var jumped_this_tick := false

var _coyote_left := 0.0
var _buffer_left := 0.0
var _jump_requested := false


func _init(movement_config: MovementConfig) -> void:
	config = movement_config


func reset() -> void:
	velocity = Vector2.ZERO
	jumped_this_tick = false
	_coyote_left = 0.0
	_buffer_left = 0.0
	_jump_requested = false


## Registers a tap. It is consumed on the next tick where a jump is allowed,
## as long as that happens within the buffer window.
func request_jump() -> void:
	_jump_requested = true
	_buffer_left = config.jump_buffer_time


func begin_tick(on_floor: bool, delta: float) -> Vector2:
	jumped_this_tick = false
	velocity.x = config.run_speed * speed_scale if running else 0.0

	if on_floor:
		_coyote_left = config.coyote_time
	var can_jump := on_floor or _coyote_left > 0.0
	if _jump_requested and can_jump:
		velocity.y = -config.jump_speed()
		jumped_this_tick = true
		_jump_requested = false
		_buffer_left = 0.0
		# Spend the coyote window: no second jump from the same ledge.
		_coyote_left = 0.0

	if not on_floor:
		_coyote_left = maxf(_coyote_left - delta, 0.0)
	if _jump_requested:
		_buffer_left -= delta
		if _buffer_left < 0.0:
			_jump_requested = false

	_apply_gravity(delta * 0.5)
	return velocity


func end_tick(resolved_velocity: Vector2, delta: float) -> Vector2:
	velocity = resolved_velocity
	_apply_gravity(delta * 0.5)
	return velocity


func _apply_gravity(step: float) -> void:
	var gravity := config.rise_gravity() if velocity.y < 0.0 else config.fall_gravity()
	velocity.y = minf(velocity.y + gravity * step, config.max_fall_speed)
