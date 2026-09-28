class_name Player
extends CharacterBody2D
## "The Core": the auto-running geometric player.
##
## Owns the gameplay physics (motor + move_and_slide + collision rules) and
## the player state. The collision box never rotates; all rotation, squash
## and particles live in PlayerVisual / PlayerFx, which only listen to the
## signals below. Knows nothing about score, UI or audio.

signal jumped
signal double_jumped
signal landed(impact_speed: float)
signal died(cause: StringName)
signal respawned
signal state_changed(new_state: State, old_state: State)
## World 04: the first tap of the attach gesture (waiting for the second).
signal attach_armed
## World 04: the gesture took hold; the player crosses to the surface across
## and will stand with its centre at [param landing], on the ceiling if
## [param to_up].
signal attach_started(landing: Vector2, to_up: bool)
## World 04: the crossing touched the surface; gravity now points at it.
signal attached(up: bool)
## World 04: the gesture found no surface it could reach (nothing moves).
signal attach_failed

enum State { IDLE, RUN, JUMP, FALL, DEAD }

## Walls whose normal points back at us more than this count as a head-on hit.
const WALL_HIT_NORMAL_X := -0.7
const LEDGE_ASSIST_STEP := 2.0
const LEDGE_ASSIST_CLEARANCE := 2.0

@export var config: MovementConfig

## Falling below this world Y kills the player. Set by the game from the level.
var kill_y := INF
## Rising above this world Y kills the player (World 03, gravity up).
var kill_top := -INF
## The run's gravity (World 03). null: gravity always pulls down.
var gravity: GravityState:
	set(value):
		if gravity and gravity.flipped.is_connected(_on_gravity_flipped):
			gravity.flipped.disconnect(_on_gravity_flipped)
		gravity = value
		if gravity:
			gravity.flipped.connect(_on_gravity_flipped)
			_on_gravity_flipped(gravity.up, true)
## While true nothing can kill the player. The game session sets it for
## every state except PLAYING (start screen, pause, finish).
var invulnerable := false
## World 04: where the wind blows, as a callable (x: float) -> float giving
## the extra acceleration toward the floor (negative: away from it). Unset:
## no wind.
var wind_source := Callable()
## World 04: the attach check (null in other worlds).
var attach: SurfaceAttach
var state: State = State.IDLE
var motor: PlayerMotor
## Half extents of the collision box, read from the BodyShape in the scene
## (the single source of truth for the player's size).
var half_size := Vector2.ZERO

## Gravity turned since the last tick: the floor contact is stale.
var _flipped := false
## An attach is turning gravity itself (the motor rests against the new floor).
var _attach_turning := false

@onready var _hurtbox: Area2D = $Hurtbox
@onready var visual: PlayerVisual = $Visual
@onready var fx: PlayerFx = $Fx


func _enter_tree() -> void:
	# Runs before the children's _ready, so PlayerVisual/PlayerFx can rely on
	# config, motor and size being set.
	if config == null:
		config = MovementConfig.new()
	if motor == null:
		motor = PlayerMotor.new(config)
	var rect := ($BodyShape as CollisionShape2D).shape as RectangleShape2D
	assert(rect != null, "Player BodyShape must be a RectangleShape2D")
	half_size = rect.size * 0.5


func _ready() -> void:
	_hurtbox.area_entered.connect(_on_hurtbox_area_entered)


## Position of the player's feet: the centre of the side facing the floor
## (the bottom, or the top while gravity pulls up).
func get_feet_position() -> Vector2:
	return global_position + Vector2(0.0, half_size.y * gravity_sign())


## +1 while gravity pulls down, -1 while it pulls up.
func gravity_sign() -> float:
	return gravity.down_sign() if gravity else 1.0


## Current horizontal speed while running (px/s), including the level scale.
func get_run_speed() -> float:
	return config.run_speed * motor.speed_scale


func is_dead() -> bool:
	return state == State.DEAD


## True while the double jump is still available (always on the ground).
## World 04 has none: its equivalent is [method can_attach].
func has_double_jump() -> bool:
	return motor.tap_mode == PlayerMotor.TapMode.JUMP and motor.air_jumps_left > 0


## World 04: taps are the surface attach gesture (no jump at all).
func set_surface_attach(enabled: bool) -> void:
	motor.tap_mode = PlayerMotor.TapMode.SURFACE_ATTACH if enabled else PlayerMotor.TapMode.JUMP
	attach = SurfaceAttach.new(self) if enabled else null
	if not enabled:
		wind_source = Callable()


func uses_surface_attach() -> bool:
	return motor.tap_mode == PlayerMotor.TapMode.SURFACE_ATTACH


## World 04: crossing to the other surface.
func is_attaching() -> bool:
	return motor.attaching


## World 04: on a surface and free to attach (the gesture would be decided).
func can_attach() -> bool:
	return uses_surface_attach() and motor.can_attach()


## World 04: the first tap is in, waiting for the second.
func is_attach_pending() -> bool:
	return motor.gesture == PlayerMotor.Gesture.TAP_PENDING


func set_running(value: bool) -> void:
	motor.running = value


func set_speed_scale(value: float) -> void:
	motor.speed_scale = value


func request_jump() -> void:
	if state == State.DEAD:
		return
	var was_idle := motor.gesture == PlayerMotor.Gesture.IDLE and not motor.attaching
	motor.request_jump()
	if uses_surface_attach() and was_idle and motor.gesture == PlayerMotor.Gesture.TAP_PENDING:
		attach_armed.emit()


## Places the player standing on [param feet_position] and revives it.
func respawn_at(feet_position: Vector2, run: bool) -> void:
	_flipped = false
	global_position = feet_position - Vector2(0.0, half_size.y * gravity_sign())
	velocity = Vector2.ZERO
	# Teleport: do not interpolate from the death position.
	reset_physics_interpolation()
	apply_floor_snap()
	motor.reset(is_on_floor())
	motor.running = run
	_set_state(State.RUN if run and is_on_floor() else State.IDLE)
	respawned.emit()


func die(cause: StringName) -> void:
	if state == State.DEAD or invulnerable:
		return
	# The body is simply frozen (DEAD skips all physics). Collision shapes are
	# deliberately left alone: toggling them with deferred calls here and
	# directly in respawn_at could land in the wrong order within one frame
	# and respawn the player without collision.
	motor.running = false
	velocity = Vector2.ZERO
	_set_state(State.DEAD)
	died.emit(cause)


func _physics_process(delta: float) -> void:
	if state == State.DEAD:
		return
	# The motor works toward the floor (+y = falling); world y is that times
	# the gravity sign (1 in every world before World 03).
	var g := gravity_sign()
	var was_on_floor := is_on_floor() and not _flipped
	_flipped = false
	var reach := false
	if attach:
		motor.external_accel = wind_source.call(global_position.x) if wind_source.is_valid() else 0.0
		if motor.attach_requested():
			reach = attach.probe(true)
	var local := motor.begin_tick(was_on_floor, delta, reach)
	velocity = Vector2(local.x, local.y * g)
	if was_on_floor:
		# Platforms may carry the player vertically, but never change its run
		# speed: x(t) stays linear, so every x maps to one fixed level time and
		# obstacle timing is identical on every attempt.
		velocity.x -= _floor_velocity().x
	_apply_ledge_assist(delta)
	var incoming_fall_speed := velocity.y * g
	move_and_slide()

	if _hit_wall_head_on():
		die(&"wall")
		return
	if is_on_floor() and is_on_ceiling():
		# Squeezed between a platform and a ceiling: resolving it would push
		# the body through one of them.
		die(&"crush")
		return
	var arrived := motor.attaching and is_on_ceiling()
	if arrived:
		# The crossing touched the surface across: it is the floor now. Settle
		# on it within this tick (a touch, not a fall onto it).
		motor.arrive()
		_turn_for_attach()
		g = gravity_sign()
		var run := velocity.x
		velocity = -up_direction * 30.0
		move_and_slide()
		velocity.x = run
		_flipped = false
	local = motor.end_tick(Vector2(velocity.x, velocity.y * g), delta, is_on_floor())
	velocity = Vector2(local.x, local.y * g)

	match motor.jump_this_tick:
		PlayerMotor.Jump.GROUND:
			jumped.emit()
		PlayerMotor.Jump.AIR:
			double_jumped.emit()
		PlayerMotor.Jump.ATTACH:
			attach_started.emit(attach.landing, not gravity.up if gravity else true)
		PlayerMotor.Jump.ATTACH_FAIL:
			attach_failed.emit()
		_:
			if not arrived and is_on_floor() and not was_on_floor:
				landed.emit(maxf(incoming_fall_speed, 0.0))
	if arrived:
		attached.emit(gravity.up)  # (After attach_started when a short crossing ends on its first tick.)

	if global_position.y > kill_y or global_position.y < kill_top:
		die(&"fall")
		return
	_update_state()


## The velocity move_and_slide is about to carry us by: that of the floor
## body under us, read now, the way the engine reads it at the start of the
## move. (get_platform_velocity() is last tick's value; while a platform
## accelerates, cancelling that one would let x drift off its exact line.)
func _floor_velocity() -> Vector2:
	for i in get_slide_collision_count():
		var collision := get_slide_collision(i)
		if collision.get_normal().dot(up_direction) < 0.7:
			continue
		var state := PhysicsServer2D.body_get_direct_state(collision.get_collider_rid())
		if state == null:
			return Vector2.ZERO
		return state.get_velocity_at_local_position(global_position - state.transform.origin)
	return get_platform_velocity()


## Forgives near misses on corners. If this tick's move would hit a face
## head-on but only by a few pixels, shift past the corner instead:
## - feet just below a ledge top -> step up (running or falling onto it);
## - head just above an overhang's underside -> duck under (airborne only).
func _apply_ledge_assist(delta: float) -> void:
	if velocity.x <= 0.0:
		return
	var motion := velocity * delta
	var grounded := is_on_floor()
	if grounded:
		motion.y = 0.0  # Otherwise the resting floor contact is the first hit.
	var hit := KinematicCollision2D.new()
	if not test_move(global_transform, motion, hit) or not _is_head_on(hit.get_normal()):
		return
	if _nudge_past_corner(up_direction, config.ledge_assist, motion, grounded):
		return
	if not grounded:
		_nudge_past_corner(-up_direction, config.head_clip_assist, motion, false)


## Tries shifts of growing size along [param direction], up to [param limit]
## pixels, until [param motion] no longer hits a face head-on. Returns true
## if the player was moved.
func _nudge_past_corner(direction: Vector2, limit: float, motion: Vector2, grounded: bool) -> bool:
	var amount := LEDGE_ASSIST_STEP
	while amount <= limit:
		# Clear the corner by a little more than the physics safe margin, or
		# the next move still clips it.
		var shift := direction * (amount + LEDGE_ASSIST_CLEARANCE)
		if test_move(global_transform, shift):
			return false  # Blocked on that side.
		var shifted_hit := KinematicCollision2D.new()
		var blocked := test_move(global_transform.translated(shift), motion, shifted_hit)
		if not blocked or not _is_head_on(shifted_hit.get_normal()):
			if grounded:
				# Do this tick's horizontal step here too: left to move_and_slide,
				# floor snapping would first pull us back onto the lower ground.
				global_position += shift + Vector2(motion.x, 0.0)
				velocity.x = 0.0
			else:
				global_position += shift
			return true
		amount += LEDGE_ASSIST_STEP
	return false


## Auto-running into a wall would pin the player forever (a softlock), so a
## head-on wall hit is a death. Grazing a surface from above or below is not.
func _hit_wall_head_on() -> bool:
	for i in get_slide_collision_count():
		if _is_head_on(get_slide_collision(i).get_normal()):
			return true
	return false


static func _is_head_on(normal: Vector2) -> bool:
	return normal.x <= WALL_HIT_NORMAL_X


func _update_state() -> void:
	var next: State
	if is_on_floor():
		next = State.RUN if motor.running else State.IDLE
	else:
		next = State.JUMP if velocity.y * gravity_sign() < 0.0 else State.FALL
	_set_state(next)


func _set_state(next: State) -> void:
	if next == state:
		return
	var previous := state
	state = next
	state_changed.emit(next, previous)


func _on_hurtbox_area_entered(_area: Area2D) -> void:
	die(&"hazard")


## World 04: the attach turns the run's gravity (the player owns it there)
## once it touches the surface across; the body already rests against it.
func _turn_for_attach() -> void:
	if gravity == null:
		gravity = GravityState.new()
	_attach_turning = true
	gravity.set_up(not gravity.up)
	_attach_turning = false


## Gravity turned (see [GravityState]). The body keeps its world velocity;
## the motor re-reads it toward the new floor (see [method PlayerMotor.flip]).
func _on_gravity_flipped(up: bool, instant: bool) -> void:
	up_direction = Vector2.DOWN if up else Vector2.UP
	if not instant and state != State.DEAD:
		if not _attach_turning:
			motor.flip()
		_flipped = true
	visual.face_gravity(up, instant)
	fx.face_gravity(up, instant)
