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

## Side length of the square collision body, in pixels.
const SIZE := 48.0
## Walls whose normal points back at us more than this count as a head-on hit.
const WALL_HIT_NORMAL_X := -0.7
const LEDGE_ASSIST_STEP := 2.0
const LEDGE_ASSIST_CLEARANCE := 2.0

@export var config: MovementConfig

## Falling below this world Y kills the player. Set by the game from the level.
var kill_y := INF
var state: State = State.IDLE
var motor: PlayerMotor

@onready var _body_shape: CollisionShape2D = $BodyShape
@onready var _hurtbox: Area2D = $Hurtbox


func _ready() -> void:
	if config == null:
		config = MovementConfig.new()
	motor = PlayerMotor.new(config)
	_hurtbox.area_entered.connect(_on_hurtbox_area_entered)


## Position of the player's feet (bottom-centre of the collision box).
func get_feet_position() -> Vector2:
	return global_position + Vector2(0.0, SIZE * 0.5)


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
	global_position = feet_position - Vector2(0.0, SIZE * 0.5)
	velocity = Vector2.ZERO
	motor.reset()
	motor.running = run
	_body_shape.disabled = false
	_hurtbox.monitoring = true
	# Teleport: do not interpolate from the death position.
	reset_physics_interpolation()
	apply_floor_snap()
	_set_state(State.RUN if run and is_on_floor() else State.IDLE)
	respawned.emit()


func die(cause: StringName) -> void:
	if state == State.DEAD:
		return
	motor.running = false
	velocity = Vector2.ZERO
	# May be called from a physics callback, where shapes cannot change directly.
	_body_shape.set_deferred(&"disabled", true)
	_hurtbox.set_deferred(&"monitoring", false)
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
	velocity = motor.end_tick(velocity, delta)

	if motor.jumped_this_tick:
		jumped.emit()
	elif is_on_floor() and not was_on_floor:
		landed.emit(maxf(incoming_fall_speed, 0.0))

	if global_position.y > kill_y:
		die(&"fall")
		return
	_update_state()


## If the next horizontal move would hit a ledge that is only a few pixels
## above our feet, step up onto it. Makes near-miss landings feel fair.
func _apply_ledge_assist(delta: float) -> void:
	if config.ledge_assist <= 0.0 or velocity.x <= 0.0:
		return
	var motion := Vector2(velocity.x * delta, 0.0)
	var hit := KinematicCollision2D.new()
	if not test_move(global_transform, motion, hit):
		return
	if hit.get_normal().x > WALL_HIT_NORMAL_X:
		return
	var lift := LEDGE_ASSIST_STEP
	while lift <= config.ledge_assist:
		# Clear the lip by a little more than the physics safe margin, or the
		# next move still clips its corner.
		var up := Vector2(0.0, -(lift + LEDGE_ASSIST_CLEARANCE))
		if test_move(global_transform, up):
			return  # Ceiling in the way.
		if not test_move(global_transform.translated(up), motion):
			# Do this tick's horizontal step here too: left to move_and_slide,
			# floor snapping would first pull us back onto the lower ground.
			global_position += up + motion
			velocity.x = 0.0
			return
		lift += LEDGE_ASSIST_STEP


## Auto-running into a wall would pin the player forever (a softlock), so a
## head-on wall hit is a death. Grazing a surface from above or below is not.
func _hit_wall_head_on() -> bool:
	for i in get_slide_collision_count():
		if get_slide_collision(i).get_normal().x <= WALL_HIT_NORMAL_X:
			return true
	return false


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
