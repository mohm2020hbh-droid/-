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

## Lowest world Y the bottom of the view may show (see [method set_kill_line]).
var bottom_limit := INF

var _anchor_y := 0.0
var _trauma := 0.0


func _ready() -> void:
	position_smoothing_enabled = false
	# Physics interpolation requires (and would force) the physics callback.
	process_callback = Camera2D.CAMERA2D_PROCESS_PHYSICS


func _physics_process(delta: float) -> void:
	if target == null:
		return
	_update_anchor()
	var desired := get_desired_position()
	global_position.x = desired.x
	var speed := fall_follow_speed_y if desired.y > global_position.y else follow_speed_y
	global_position.y = lerpf(global_position.y, desired.y, 1.0 - exp(-speed * delta))


func _process(delta: float) -> void:
	if _trauma <= 0.0:
		return
	_trauma = maxf(_trauma - delta / shake_duration, 0.0)
	var amount := _trauma * _trauma * max_shake
	offset = Vector2(randf_range(-1.0, 1.0), randf_range(-1.0, 1.0)) * amount


## Limits how far down the camera follows a fall: just past the level's
## kill line, so the shatter is on screen but the void below is not.
func set_kill_line(kill_y: float) -> void:
	bottom_limit = kill_y + kill_line_margin


## Adds shake trauma (0..1). Squared falloff keeps small hits subtle.
func shake(strength: float = 1.0) -> void:
	_trauma = clampf(_trauma + strength, 0.0, 1.0)


## Jumps straight to the target (after a respawn, hidden by a fade).
func snap_to_target() -> void:
	if target == null:
		return
	_anchor_y = target.global_position.y
	global_position = get_desired_position()
	_trauma = 0.0
	offset = Vector2.ZERO
	reset_physics_interpolation()


## Vertical anchor: the last ground height, pulled down only by real falls.
func _update_anchor() -> void:
	var target_y := target.global_position.y
	if target.is_on_floor():
		_anchor_y = target_y
	elif target_y > _anchor_y + fall_dead_zone:
		_anchor_y = target_y - fall_dead_zone


## Where the camera wants to be for the current anchor (no side effects).
func get_desired_position() -> Vector2:
	var view := get_viewport_rect().size / zoom
	var y := _anchor_y + vertical_offset
	y = minf(y, bottom_limit - view.y * 0.5)
	return Vector2(target.global_position.x + view.x * (0.5 - screen_anchor_x), y)
