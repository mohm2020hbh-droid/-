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
## Surface attach rules (World 04, [member tap_mode] = SURFACE_ATTACH). There
## is no jump at all, and no second jump: two taps are ONE gesture.
## - Gesture: IDLE --tap--> TAP_PENDING --tap within attach_window-->
##   ATTACH_REQUEST. A lone tap is forgotten when the window runs out: it
##   never moves the player.
## - The request is decided on the next tick. On a surface (or within coyote
##   time of leaving one) with a valid surface across (the player checks it
##   along the real crossing path: [code]attach_ok[/code] of
##   [method begin_tick]) it ATTACHES: the body sets off toward the other
##   surface at [member MovementConfig.attach_speed], speeding up as it goes,
##   with no gravity (a pull, not a fall). Otherwise the attach FAILS: nothing
##   moves, nothing else happens, the gesture is spent.
## - While attaching, taps are swallowed; the crossing ends on touching the
##   surface ([method arrive], the player turns gravity then), so every attach
##   needs a new gesture of its own.

enum Jump { NONE, GROUND, AIR, ATTACH, ATTACH_FAIL }
## What taps do: jumps and the double jump (Worlds 01-03), or the surface
## attach gesture (World 04).
enum TapMode { JUMP, SURFACE_ATTACH }
## World 04's two-tap gesture.
enum Gesture { IDLE, TAP_PENDING, ATTACH_REQUEST }

var config: MovementConfig
var velocity := Vector2.ZERO
## When false the body stands still (before the first tap, after finishing).
var running := false
## Level-wide run speed multiplier (difficulty knob).
var speed_scale := 1.0
## The jump that started during this tick, if any.
var jump_this_tick: Jump = Jump.NONE
## Double jumps left before the next landing (Worlds 01-03).
var air_jumps_left := 0
var tap_mode: TapMode = TapMode.JUMP
## World 04: the two-tap gesture so far.
var gesture: Gesture = Gesture.IDLE
## World 04: between the start of an attach and touching the surface.
var attaching := false
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
## World 04: time left for the second tap of the gesture.
var _gesture_left := 0.0
## World 04: ticks since the attach started (its speed follows them).
var _attach_tick := 0


func _init(movement_config: MovementConfig) -> void:
	config = movement_config
	reset()


## Back to rest, e.g. on respawn. [param on_floor]: whether the body now stands on the floor.
func reset(on_floor: bool = false) -> void:
	velocity = Vector2.ZERO
	jump_this_tick = Jump.NONE
	air_jumps_left = config.air_jumps
	attaching = false
	gesture = Gesture.IDLE
	_gesture_left = 0.0
	_attach_tick = 0
	external_accel = 0.0
	_grounded = on_floor
	_coyote_left = 0.0
	_queued_taps = 0
	_buffer_left = 0.0


## Registers a tap. It starts a jump on the next tick if one is available
## (after any taps already waiting), otherwise it waits in the jump buffer.
## World 04: a step of the attach gesture instead (see the class notes).
func request_jump() -> void:
	if tap_mode == TapMode.SURFACE_ATTACH:
		_gesture_tap()
		return
	if _queued_taps < jumps_available():
		_queued_taps += 1
	else:
		_buffer_left = config.jump_buffer_time


## Jumps the player could still make before landing, as of the last tick.
func jumps_available() -> int:
	if tap_mode == TapMode.SURFACE_ATTACH:
		return 0  # World 04 never jumps.
	if _grounded:
		return 1 + config.air_jumps
	return (1 if _coyote_left > 0.0 else 0) + air_jumps_left


## World 04: a gesture is complete and will be decided on the next tick
## (the player checks the surface across for it).
func attach_requested() -> bool:
	return gesture == Gesture.ATTACH_REQUEST and not attaching


## World 04: standing on a surface (or just off one), free to attach.
func can_attach() -> bool:
	return not attaching and (_grounded or _coyote_left > 0.0)


## [param attach_ok] (World 04): an attach requested now would reach a valid
## surface (the player checked the crossing path).
func begin_tick(on_floor: bool, delta: float, attach_ok := false) -> Vector2:
	if tap_mode == TapMode.SURFACE_ATTACH:
		return _begin_attach_tick(on_floor, delta, attach_ok)
	jump_this_tick = Jump.NONE
	velocity.x = config.run_speed * speed_scale if running else 0.0
	_grounded = on_floor
	if on_floor:
		_coyote_left = config.coyote_time
		air_jumps_left = config.air_jumps

	var can_ground_jump := on_floor or _coyote_left > 0.0
	if _queued_taps > 0:
		_queued_taps -= 1
		if can_ground_jump:
			_start_jump(Jump.GROUND)
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
	if not attaching:
		_apply_gravity(delta * 0.5)
	return velocity


## Gravity just reversed (see the class notes).
func flip() -> void:
	velocity.y = -velocity.y
	_grounded = false
	_coyote_left = 0.0
	_buffer_left = 0.0


## World 04: the attach touched the surface across. The player turns
## gravity now (that surface is the floor); the body rests against it.
func arrive() -> void:
	attaching = false
	velocity.y = 0.0
	_grounded = false
	_coyote_left = config.coyote_time  # A gesture on the landing tick counts as on a surface.
	gesture = Gesture.IDLE


## World 04's tick (see the class notes): run, the gesture, the crossing.
func _begin_attach_tick(on_floor: bool, delta: float, attach_ok: bool) -> Vector2:
	jump_this_tick = Jump.NONE
	velocity.x = config.run_speed * speed_scale if running else 0.0
	_grounded = on_floor
	if on_floor:
		_coyote_left = config.coyote_time
	if attaching:
		_attach_tick += 1
		velocity.y = -config.attach_speed_at(_attach_tick, delta)
		if _attach_tick > config.attach_ticks(config.attach_reach, delta) + 6:
			# Nothing met (a surface that moved away): let go; gravity, still
			# the old one, brings the body back.
			attaching = false
	elif gesture == Gesture.ATTACH_REQUEST:
		gesture = Gesture.IDLE
		if attach_ok and (on_floor or _coyote_left > 0.0):
			attaching = true
			_attach_tick = 0
			_coyote_left = 0.0
			velocity.y = -config.attach_speed_at(0, delta)
			jump_this_tick = Jump.ATTACH
		else:
			jump_this_tick = Jump.ATTACH_FAIL
	elif gesture == Gesture.TAP_PENDING:
		_gesture_left -= delta
		if _gesture_left <= 0.0:
			gesture = Gesture.IDLE  # A lone tap: forgotten.
	if not on_floor:
		_coyote_left = maxf(_coyote_left - delta, 0.0)
	if not attaching:
		_apply_gravity(delta * 0.5)
	return velocity


func _gesture_tap() -> void:
	if attaching:
		return  # Committed to the crossing: swallowed.
	match gesture:
		Gesture.IDLE:
			gesture = Gesture.TAP_PENDING
			_gesture_left = config.attach_window
		Gesture.TAP_PENDING:
			gesture = Gesture.ATTACH_REQUEST


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
	if external_accel != 0.0:
		gravity += external_accel
	velocity.y = minf(velocity.y + gravity * step, config.max_fall_speed)
