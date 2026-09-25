class_name Player
extends CharacterBody2D
## "The Core": the auto-running geometric player.
##
## Owns the gameplay physics (motor + move_and_slide + collision rules) and
## the player state. The collision box never rotates; all rotation, squash
## and particles live in PlayerVisual / PlayerFx, which only listen to the
## signals below. Knows nothing about score, UI or audio.

signal jumped
signal landed(impact_speed: float)
signal died(cause: StringName)
signal respawned
signal state_changed(new_state: State, old_state: State)

enum State { IDLE, RUN, JUMP, FALL, DEAD }

## Walls whose normal points back at us more than this count as a head-on hit.
const WALL_HIT_NORMAL_X := -0.7
const LEDGE_ASSIST_STEP := 2.0
const LEDGE_ASSIST_CLEARANCE := 2.0

@export var config: MovementConfig

## Falling below this world Y kills the player. Set by the game from the level.
var kill_y := INF
## While true nothing can kill the player (e.g. after crossing the finish).
var invulnerable := false
var state: State = State.IDLE
var motor: PlayerMotor
## Half extents of the collision box, read from the BodyShape in the scene
## (the single source of truth for the player's size).
var half_size := Vector2.ZERO

@onready var _hurtbox: Area2D = $Hurtbox


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


## Position of the player's feet (bottom-centre of the collision box).
func get_feet_position() -> Vector2:
	return global_position + Vector2(0.0, half_size.y)


## Current horizontal speed while running (px/s), including the level scale.
func get_run_speed() -> float:
	return config.run_speed * motor.speed_scale


func is_dead() -> bool:
	return state == State.DEAD


func set_running(value: bool) -> void:
	motor.running = value


func set_speed_scale(value: float) -> void:
	motor.speed_scale = value


func request_jump() -> void:
	if state != State.DEAD:
		motor.request_jump()


## Places the player standing on [param feet_position] and revives it.
func respawn_at(feet_position: Vector2, run: bool) -> void:
	global_position = feet_position - Vector2(0.0, half_size.y)
	velocity = Vector2.ZERO
	motor.reset()
	motor.running = run
	invulnerable = false
	# Teleport: do not interpolate from the death position.
	reset_physics_interpolation()
	apply_floor_snap()
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
	var was_on_floor := is_on_floor()
	velocity = motor.begin_tick(was_on_floor, delta)
	if was_on_floor:
		# Platforms may carry the player vertically, but never change its run
		# speed: x(t) stays linear, so every x maps to one fixed level time and
		# obstacle timing is identical on every attempt.
		velocity.x -= get_platform_velocity().x
	_apply_ledge_assist(delta)
	var incoming_fall_speed := velocity.y
	move_and_slide()

	if _hit_wall_head_on():
		die(&"wall")
		return
	if is_on_floor() and is_on_ceiling():
		# Squeezed between a platform and a ceiling: resolving it would push
		# the body through one of them.
		die(&"crush")
		return
	velocity = motor.end_tick(velocity, delta)

	if motor.jumped_this_tick:
		jumped.emit()
	elif is_on_floor() and not was_on_floor:
		landed.emit(maxf(incoming_fall_speed, 0.0))

	if global_position.y > kill_y:
		die(&"fall")
		return
	_update_state()


## If this tick's move would hit the face of a ledge whose top is only a few
## pixels above our feet, lift onto it instead. Covers running into a low
## lip and landing a hair short while falling onto a corner.
func _apply_ledge_assist(delta: float) -> void:
	if config.ledge_assist <= 0.0 or velocity.x <= 0.0:
		return
	var motion := velocity * delta
	var grounded := is_on_floor()
	if grounded:
		motion.y = 0.0  # Otherwise the resting floor contact is the first hit.
	var hit := KinematicCollision2D.new()
	if not test_move(global_transform, motion, hit) or not _is_head_on(hit.get_normal()):
		return
	var lift := LEDGE_ASSIST_STEP
	while lift <= config.ledge_assist:
		# Clear the lip by a little more than the physics safe margin, or the
		# next move still clips its corner.
		var up := Vector2(0.0, -(lift + LEDGE_ASSIST_CLEARANCE))
		if test_move(global_transform, up):
			return  # Ceiling in the way.
		var lifted_hit := KinematicCollision2D.new()
		var blocked := test_move(global_transform.translated(up), motion, lifted_hit)
		if not blocked or not _is_head_on(lifted_hit.get_normal()):
			if grounded:
				# Do this tick's horizontal step here too: left to move_and_slide,
				# floor snapping would first pull us back onto the lower ground.
				global_position += up + Vector2(motion.x, 0.0)
				velocity.x = 0.0
			else:
				global_position += up
			return
		lift += LEDGE_ASSIST_STEP


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
		next = State.JUMP if velocity.y < 0.0 else State.FALL
	_set_state(next)


func _set_state(next: State) -> void:
	if next == state:
		return
	var previous := state
	state = next
	state_changed.emit(next, previous)


func _on_hurtbox_area_entered(_area: Area2D) -> void:
	die(&"hazard")
