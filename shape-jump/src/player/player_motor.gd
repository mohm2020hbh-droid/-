class_name PlayerMotor
extends RefCounted
## Pure movement rules for the player: auto-run, jump, double jump, coyote
## time, jump buffer and gravity. It never touches nodes or the physics
## server; the [Player] feeds it collision results, and unit tests feed it
## synthetic ones.
##
## Each physics tick is split in two halves around the collision move:
##   velocity = begin_tick(on_floor, dt)   # run speed, jump, half gravity
##   <move the body with velocity>
##   velocity = end_tick(resolved_velocity, dt, on_floor)  # other half of gravity
## Applying gravity half before and half after the move integrates the
## parabola exactly, so the real jump heights match the configured ones.
##
## Tap rules (docs/GDD.md §4):
## - Every tap is one jump: on the ground (or within coyote time) it is the
##   ground jump, in the air it is the double jump while one is left.
## - At most one jump starts per tick. Taps that arrive together (a slow frame
##   delivers them in one batch) run on consecutive ticks, so the result does
##   not depend on the frame rate.
## - A tap with no jump left is remembered for [member MovementConfig.jump_buffer_time]
##   and fires as the ground jump on landing. Several such taps are still one
##   jump: mashing before a landing never wastes the double jump.
##
## Frame: y is measured toward the surface the player stands on (+y = falling),
## whatever the world's gravity. The [Player] turns it into world space; in
## every world before World 03 the two are the same.
##
## Gravity flip rules (World 03, [method flip]):
## - The body keeps its world velocity: rising toward the new floor becomes
##   falling toward it.
## - A flip never grants or takes a jump: the double jump left stays, the
##   ground jump needs a surface (no coyote time across a flip), and a tap
##   waiting in the jump buffer is dropped (it was meant for the old floor).
## - Taps already queued still fire, as the double jump if one is left.
##
## Surface latch rules (World 04, [member air_action] = SURFACE_LATCH):
## - The air tap is not a second jump. If the other surface is in reach
##   (the player checks it: [code]latch_reach[/code] of [method begin_tick]),
##   it LATCHES: the frame turns (the other surface is the new floor) and the
##   body sets off toward it at [member MovementConfig.latch_speed].
## - Out of reach it is a MISS: nothing moves, no teleport. The air tap is
##   spent all the same (one decision per airtime), and the tap waits in the
##   jump buffer, so a miss just before landing still jumps on landing.
## - While latching the body is committed: taps are swallowed (no jump on
##   arrival, no second latch), wind does not act, and it ends on touching
##   the new floor, which restores the air tap as any landing does.

enum Jump { NONE, GROUND, AIR, LATCH, LATCH_MISS }
## What a tap in the air does: the double jump (Worlds 01-03) or the surface
## latch (World 04).
enum AirAction { DOUBLE_JUMP, SURFACE_LATCH }

var config: MovementConfig
var velocity := Vector2.ZERO
## When false the body stands still (before the first tap, after finishing).
var running := false
## Level-wide run speed multiplier (difficulty knob).
var speed_scale := 1.0
## The jump that started during this tick, if any.
var jump_this_tick: Jump = Jump.NONE
## Double jumps (or, in World 04, latches) left before the next landing.
var air_jumps_left := 0
var air_action: AirAction = AirAction.DOUBLE_JUMP
## World 04: between a latch and touching the surface it latched to.
var latching := false
## World 04: extra acceleration toward the floor (+) or away from it (-),
## e.g. a gust of wind. Set by the player every tick.
var external_accel := 0.0

## On the floor after the last move.
var _grounded := false
var _coyote_left := 0.0
## Taps that arrived while a jump was available; each starts one jump.
var _queued_taps := 0
## Time left for a tap that arrived while no jump was available.
var _buffer_left := 0.0


func _init(movement_config: MovementConfig) -> void:
	config = movement_config
	reset()


## Back to rest, e.g. on respawn. [param on_floor]: whether the body now stands on the floor.
func reset(on_floor: bool = false) -> void:
	velocity = Vector2.ZERO
	jump_this_tick = Jump.NONE
	air_jumps_left = config.air_jumps
	latching = false
	external_accel = 0.0
	_grounded = on_floor
	_coyote_left = 0.0
	_queued_taps = 0
	_buffer_left = 0.0


## Registers a tap. It starts a jump on the next tick if one is available
## (after any taps already waiting), otherwise it waits in the jump buffer.
func request_jump() -> void:
	if latching:
		return  # Committed to the latch: the tap is swallowed.
	if _queued_taps < jumps_available():
		_queued_taps += 1
	else:
		_buffer_left = config.jump_buffer_time


## Jumps the player could still make before landing, as of the last tick.
func jumps_available() -> int:
	if latching:
		return 0
	if _grounded:
		return 1 + config.air_jumps
	return (1 if _coyote_left > 0.0 else 0) + air_jumps_left


## [param latch_reach] (World 04): the other surface is in reach right now,
## so an air tap this tick latches to it.
func begin_tick(on_floor: bool, delta: float, latch_reach := false) -> Vector2:
	jump_this_tick = Jump.NONE
	velocity.x = config.run_speed * speed_scale if running else 0.0
	_grounded = on_floor
	if on_floor:
		_coyote_left = config.coyote_time
		air_jumps_left = config.air_jumps
		latching = false

	var can_ground_jump := on_floor or _coyote_left > 0.0
	if _queued_taps > 0:
		_queued_taps -= 1
		if can_ground_jump:
			_start_jump(Jump.GROUND)
		elif air_jumps_left > 0 and air_action == AirAction.SURFACE_LATCH:
			_air_latch(latch_reach)
		elif air_jumps_left > 0:
			_start_jump(Jump.AIR)
		else:
			_buffer_left = config.jump_buffer_time
	elif _buffer_left > 0.0 and can_ground_jump:
		_start_jump(Jump.GROUND)
		_buffer_left = 0.0

	if not on_floor:
		_coyote_left = maxf(_coyote_left - delta, 0.0)
	if _buffer_left > 0.0:
		_buffer_left = maxf(_buffer_left - delta, 0.0)

	_apply_gravity(delta * 0.5)
	return velocity


func end_tick(resolved_velocity: Vector2, delta: float, on_floor: bool) -> Vector2:
	velocity = resolved_velocity
	_grounded = on_floor
	if on_floor:
		air_jumps_left = config.air_jumps
		latching = false
	_apply_gravity(delta * 0.5)
	return velocity


## Gravity just reversed (see the class notes).
func flip() -> void:
	velocity.y = -velocity.y
	_grounded = false
	_coyote_left = 0.0
	_buffer_left = 0.0


## World 04's air tap (see the class notes): a latch or a miss.
func _air_latch(in_reach: bool) -> void:
	air_jumps_left -= 1
	if not in_reach:
		jump_this_tick = Jump.LATCH_MISS
		_buffer_left = config.jump_buffer_time
		return
	# The other surface becomes the floor: head for it.
	flip()
	velocity.y = config.latch_speed
	latching = true
	_queued_taps = 0
	jump_this_tick = Jump.LATCH


func _start_jump(kind: Jump) -> void:
	if kind == Jump.GROUND:
		velocity.y = -config.jump_speed()
		# Spend the coyote window: no second ground jump from the same ledge.
		_coyote_left = 0.0
	else:
		velocity.y = -config.double_jump_speed()
		air_jumps_left -= 1
	jump_this_tick = kind


func _apply_gravity(step: float) -> void:
	var gravity := config.rise_gravity() if velocity.y < 0.0 else config.fall_gravity()
	if external_accel != 0.0 and not latching:
		gravity += external_accel
	velocity.y = minf(velocity.y + gravity * step, config.max_fall_speed)
