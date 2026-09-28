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
## World 04 rules ([member tap_mode] = SURFACE_ATTACH): a tap is a HOP, two
## taps are the SURFACE ATTACH. There is never a second jump in the air.
## - Gesture: IDLE --tap on a surface--> TAP_PENDING --tap within
##   attach_window--> ATTACH_REQUEST. The first tap hops at once (next tick):
##   a short hop away from the floor, i.e. toward the surface across, so no
##   tap ever waits to find out whether a second one follows.
## - The request is decided on the next tick, from wherever the hop has got
##   to. With a valid surface across (the player checks it along the real
##   crossing path: [code]attach_ok[/code] of [method begin_tick]) it
##   ATTACHES: the hop becomes the crossing, toward the other surface at
##   [member MovementConfig.attach_speed], speeding up as it goes, with no
##   gravity (a pull, not a fall). Otherwise the attach FAILS: nothing
##   changes, the hop goes on, the gesture is spent.
## - A tap in the air with no gesture open (the hop's window is over, or a
##   fall) is remembered for [member MovementConfig.jump_buffer_time] and
##   plays on landing: one tap is a hop there, two are the whole gesture. At
##   most one action starts per tick.
## - While attaching, taps are swallowed; the crossing ends on touching the
##   surface ([method arrive], the player turns gravity then), so every attach
##   needs a new gesture of its own.

enum Jump { NONE, GROUND, AIR, ATTACH, ATTACH_FAIL, HOP }
## What taps do: jumps and the double jump (Worlds 01-03), or the hop and
## the surface attach gesture (World 04).
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
## World 04: airborne because of a hop (until landing or attaching).
var _hopping := false
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
## World 04: the first tap's hop starts on the next tick.
var _hop_queued := false
## World 04: taps (1 or 2) remembered in the air, played on landing.
var _buffered_taps := 0


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
	_hop_queued = false
	_buffered_taps = 0
	_hopping = false
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


## Jumps the player could still make before landing, as of the last tick
## (World 04: the hop, on a surface only).
func jumps_available() -> int:
	if tap_mode == TapMode.SURFACE_ATTACH:
		return 1 if _can_hop() else 0
	if _grounded:
		return 1 + config.air_jumps
	return (1 if _coyote_left > 0.0 else 0) + air_jumps_left


## World 04: a gesture is complete and will be decided on the next tick
## (the player checks the surface across for it).
func attach_requested() -> bool:
	return gesture == Gesture.ATTACH_REQUEST and not attaching and not _hop_queued


## World 04: a gesture begun now, or the one already open, could still attach.
func can_attach() -> bool:
	return not attaching and (_can_hop() or gesture != Gesture.IDLE)


## World 04: in the air after a hop (not crossing).
func is_hopping() -> bool:
	return _hopping and not attaching and not _grounded


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
	_hopping = false
	_buffered_taps = 0
	velocity.y = 0.0
	_grounded = false
	_coyote_left = config.coyote_time  # A gesture on the landing tick counts as on a surface.
	gesture = Gesture.IDLE


## World 04's tick (see the class notes): run, the hop, the gesture, the crossing.
func _begin_attach_tick(on_floor: bool, delta: float, attach_ok: bool) -> Vector2:
	jump_this_tick = Jump.NONE
	velocity.x = config.run_speed * speed_scale if running else 0.0
	_grounded = on_floor
	if on_floor:
		_coyote_left = config.coyote_time
		_hopping = false
	var can_hop := _can_hop()
	if attaching:
		_attach_tick += 1
		velocity.y = -config.attach_speed_at(_attach_tick, delta)
		if _attach_tick > config.attach_ticks(config.attach_reach, delta) + 6:
			# Nothing met (a surface that moved away): let go; gravity, still
			# the old one, brings the body back.
			attaching = false
	elif _hop_queued:
		# The first tap: the hop, now (its attach window is already open).
		_hop_queued = false
		if can_hop:
			_start_hop()
		else:
			# Off the ledge meanwhile: kept for the landing, as a tap in the air.
			_buffered_taps = 2 if gesture == Gesture.ATTACH_REQUEST else 1
			_buffer_left = config.jump_buffer_time
			gesture = Gesture.IDLE
	elif gesture == Gesture.ATTACH_REQUEST:
		gesture = Gesture.IDLE
		if attach_ok:
			attaching = true
			_hopping = false
			_attach_tick = 0
			_coyote_left = 0.0
			velocity.y = -config.attach_speed_at(0, delta)
			jump_this_tick = Jump.ATTACH
		else:
			jump_this_tick = Jump.ATTACH_FAIL
	elif _buffered_taps > 0 and can_hop:
		# Taps from the air, on landing: a hop, and with two the whole gesture.
		_start_hop()
		gesture = Gesture.ATTACH_REQUEST if _buffered_taps > 1 else Gesture.TAP_PENDING
		_gesture_left = config.attach_window
		_buffered_taps = 0
		_buffer_left = 0.0
	if gesture == Gesture.TAP_PENDING:
		_gesture_left -= delta
		if _gesture_left <= 0.0:
			gesture = Gesture.IDLE  # No second tap: it was just a hop.
	if not on_floor:
		_coyote_left = maxf(_coyote_left - delta, 0.0)
	if _buffered_taps > 0:
		_buffer_left -= delta
		if _buffer_left <= 0.0:
			_buffered_taps = 0
	if not attaching:
		_apply_gravity(delta * 0.5)
	return velocity


func _gesture_tap() -> void:
	if attaching:
		return  # Committed to the crossing: swallowed.
	match gesture:
		Gesture.TAP_PENDING:
			gesture = Gesture.ATTACH_REQUEST
		Gesture.ATTACH_REQUEST:
			pass  # Already asked: a third tap adds nothing.
		_:
			if _can_hop():
				# The hop starts on the next tick; the window for the second tap opens now.
				_hop_queued = true
				gesture = Gesture.TAP_PENDING
				_gesture_left = config.attach_window
			else:
				# In the air: remembered for the landing (never a jump up here).
				_buffered_taps = mini(_buffered_taps + 1, 2)
				_buffer_left = config.jump_buffer_time


func _can_hop() -> bool:
	return not attaching and (_grounded or _coyote_left > 0.0)


func _start_hop() -> void:
	velocity.y = -config.hop_speed()
	_coyote_left = 0.0  # One hop per ledge.
	_hopping = true
	jump_this_tick = Jump.HOP


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
	if tap_mode == TapMode.SURFACE_ATTACH:
		gravity = config.hop_rise_gravity() if velocity.y < 0.0 else config.hop_fall_gravity()
	if external_accel != 0.0:
		gravity += external_accel
	velocity.y = minf(velocity.y + gravity * step, config.max_fall_speed)
