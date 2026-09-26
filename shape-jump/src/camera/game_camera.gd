class_name GameCamera
extends Camera2D
## Side-scrolling follow camera (docs/GDD.md §10).
##
## - X is locked to the target plus a look-ahead, so the player stays at a
##   fixed spot on screen and sees most of the screen ahead.
## - Y follows the last ground height (not every jump), so jumps never bob
##   the view; it only chases falls past a dead zone.
## - Smoothing is exponential and frame-rate independent. Moved in
##   _physics_process, so physics interpolation keeps it smooth at any Hz.
## - Shake is trauma-based, tiny, and only used for important events.
## - Gravity (World 03): the view turns 180° with gravity, so the floor the
##   player runs on is always at the bottom of the screen. The x framing is
##   unchanged in world space, so after the turn the player sits on the right
##   and still sees the way ahead (to the left); every "down" above (falls,
##   the kill line, the sky offset) follows gravity.

@export var target: CharacterBody2D
## Where the target sits horizontally, as a fraction of the view from the left.
@export_range(0.1, 0.5, 0.01) var screen_anchor_x := 0.28
## Camera centre relative to the ground anchor (negative = show more sky).
@export var vertical_offset := -90.0
@export_range(0.5, 20.0, 0.1) var follow_speed_y := 4.5
## Faster follow when the view must move down (falls), so a fall death is
## never below the screen.
@export_range(0.5, 30.0, 0.1) var fall_follow_speed_y := 16.0
## How far the target may drop below its last ground height before we follow.
## Jump arcs only go up from the anchor, so anything below it is a real drop:
## keep this small, or a pit fall ends below the screen.
@export var fall_dead_zone := 24.0
## How far below the kill line the view may reach, so a fall death is seen.
@export var kill_line_margin := 200.0
@export var max_shake := 7.0
@export_range(0.05, 2.0, 0.05, "suffix:s") var shake_duration := 0.35
## The run's gravity (World 03); null: always down, never turns.
var gravity: GravityState:
	set(value):
		if gravity and gravity.flipped.is_connected(_on_gravity_flipped):
			gravity.flipped.disconnect(_on_gravity_flipped)
		gravity = value
		if gravity:
			gravity.flipped.connect(_on_gravity_flipped)
			_on_gravity_flipped(gravity.up, true)

## Runs after the player every physics tick (it follows where the player is
## now), whatever the scene tree order.
const PHYSICS_PRIORITY := 10

## Lowest world Y the bottom of the view may show (see [method set_kill_line]).
var bottom_limit := INF
## Highest world Y the view may show while gravity pulls up.
var top_limit := -INF

var _anchor_y := 0.0
var _trauma := 0.0
var _turn_from := 0.0
var _turn_to := 0.0


func _ready() -> void:
	process_physics_priority = PHYSICS_PRIORITY
	position_smoothing_enabled = false
	# The view turns with gravity (World 03); at rotation 0 this changes nothing.
	ignore_rotation = false
	# Physics interpolation requires (and would force) the physics callback.
	process_callback = Camera2D.CAMERA2D_PROCESS_PHYSICS


func _physics_process(delta: float) -> void:
	if target == null:
		return
	_update_turn()
	_update_anchor()
	var desired := get_desired_position()
	global_position.x = desired.x
	var speed := fall_follow_speed_y if (desired.y - global_position.y) * _down() > 0.0 else follow_speed_y
	global_position.y = lerpf(global_position.y, desired.y, 1.0 - exp(-speed * delta))


func _process(delta: float) -> void:
	if _trauma <= 0.0:
		return
	_trauma = maxf(_trauma - delta / shake_duration, 0.0)
	var amount := _trauma * _trauma * max_shake
	offset = Vector2(randf_range(-1.0, 1.0), randf_range(-1.0, 1.0)) * amount


## Limits how far down the camera follows a fall: just past the level's
## kill line, so the shatter is on screen but the void below is not.
func set_kill_line(kill_y: float, kill_top := -INF) -> void:
	bottom_limit = kill_y + kill_line_margin
	top_limit = kill_top - kill_line_margin


## Adds shake trauma (0..1). Squared falloff keeps small hits subtle.
func shake(strength: float = 1.0) -> void:
	_trauma = clampf(_trauma + strength, 0.0, 1.0)


## Jumps straight to the target (after a respawn, hidden by a fade).
func snap_to_target() -> void:
	if target == null:
		return
	_anchor_y = target.global_position.y
	global_position = get_desired_position()
	_turn_from = _turn_to
	rotation = _turn_to
	_trauma = 0.0
	offset = Vector2.ZERO
	reset_physics_interpolation()


## Vertical anchor: the last ground height, pulled down only by real falls.
func _update_anchor() -> void:
	var target_y := target.global_position.y
	if target.is_on_floor():
		_anchor_y = target_y
	elif (target_y - _anchor_y) * _down() > fall_dead_zone:
		_anchor_y = target_y - fall_dead_zone * _down()


## The part of the world on screen right now (shake ignored).
func get_view_rect() -> Rect2:
	var view := get_viewport_rect().size / zoom
	return Rect2(get_screen_center_position() - view * 0.5, view)


## Where the camera wants to be for the current anchor (no side effects).
func get_desired_position() -> Vector2:
	var view := get_viewport_rect().size / zoom
	var y := _anchor_y + vertical_offset * _down()
	if _down() > 0.0:
		y = minf(y, bottom_limit - view.y * 0.5)
	else:
		y = maxf(y, top_limit + view.y * 0.5)
	return Vector2(target.global_position.x + view.x * (0.5 - screen_anchor_x), y)


## True while the view is turning after a flip.
func is_turning() -> bool:
	return not is_equal_approx(rotation, _turn_to)


func _down() -> float:
	return gravity.down_sign() if gravity else 1.0


## Gravity turned: the view turns half a circle, always the same way round
## (a smooth ease over the flip's transition), or snaps on a restore.
func _on_gravity_flipped(up: bool, instant: bool) -> void:
	var goal := PI if up else 0.0
	if instant:
		_turn_from = goal
		_turn_to = goal
		rotation = goal
		return
	_turn_from = rotation
	_turn_to = _turn_from + wrapf(goal - _turn_from, 0.0, TAU)
	if is_zero_approx(_turn_to - _turn_from):
		_turn_to = _turn_from + TAU  # Two flips within one turn: go all the way.


func _update_turn() -> void:
	if gravity == null or is_equal_approx(rotation, _turn_to):
		return
	var k := smoothstep(0.0, 1.0, gravity.transition_progress())
	rotation = lerpf(_turn_from, _turn_to, k)
	if gravity.phase == GravityState.Phase.NORMAL:
		rotation = wrapf(_turn_to, 0.0, TAU)
		_turn_from = rotation
		_turn_to = rotation
