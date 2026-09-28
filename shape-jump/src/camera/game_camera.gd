class_name GameCamera
extends Camera2D
## Side-scrolling follow camera (docs/GDD.md §10). One camera, one framing
## rule for every world and level: nothing here is tuned per world.
##
## - Scale: zoom is always 1 (a world pixel is a view pixel), so the player,
##   the obstacles and the distance seen ahead are the same size in every
##   world; the project's stretch (canvas_items, expand) scales the whole
##   view to the screen the same way for all of them.
## - X is locked to the target plus a look-ahead: the player stays at
##   [member screen_anchor_x] of the view from the left and sees the rest of
##   the screen ahead, to the right.
## - Y frames the PLAY BAND: the space between the floor and the surface
##   across from it is centred on screen ([method frame_band]). A level with
##   a ceiling corridor (Worlds 03 and 04) frames that corridor; an open-sky
##   level frames the standard band, [constant STANDARD_BAND]. So the floor
##   line sits at the same height of the screen in every world, and a flip
##   or a latch to the other surface never moves the view.
## - Y follows the last floor height (not every jump), so jumps never bob
##   the view; it only chases falls past a dead zone.
## - Direction: the view NEVER rotates or mirrors. The run always goes left
##   to right on screen and the way ahead is always on the right, whichever
##   surface is the floor; gravity only chooses which side of the band the
##   floor is on (every "down" here follows gravity: falls, the kill line).
## - Smoothing is exponential and frame-rate independent. Moved in
##   _physics_process, so physics interpolation keeps it smooth at any Hz.
## - Shake is trauma-based, tiny, and only used for important events.
## - A latch (World 04) re-anchors the view on the surface the player is
##   crossing to ([method anchor_to]) before it gets there.

## The play band of a level without a ceiling (px): the height of the space
## above the floor that the view centres, the same as the corridors of the
## worlds that have one (5.2 to 5.5 tiles), so every world is framed alike.
const STANDARD_BAND := 5.4 * GameConst.TILE

@export var target: CharacterBody2D
## Where the target sits horizontally, as a fraction of the view from the left.
@export_range(0.1, 0.5, 0.01) var screen_anchor_x := 0.28
## Camera centre relative to the floor anchor (negative = toward the other
## surface). Derived from the play band by [method frame_band].
var vertical_offset := -(STANDARD_BAND * 0.5 - GameConst.TILE * 0.375)
## Height (px) of the play band being framed.
var band_height := STANDARD_BAND
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
## The run's gravity (Worlds 03 and 04): which side of the band is the
## floor. null: always down.
var gravity: GravityState:
	set(value):
		if gravity and gravity.flipped.is_connected(_on_gravity_flipped):
			gravity.flipped.disconnect(_on_gravity_flipped)
		gravity = value
		if gravity:
			gravity.flipped.connect(_on_gravity_flipped)

## Runs after the player every physics tick (it follows where the player is
## now), whatever the scene tree order.
const PHYSICS_PRIORITY := 10

## Lowest world Y the bottom of the view may show (see [method set_kill_line]).
var bottom_limit := INF
## Highest world Y the view may show while gravity pulls up.
var top_limit := -INF

var _anchor_y := 0.0
var _trauma := 0.0
## Half the target's height (its centre's distance from the floor).
var _target_half := GameConst.TILE * 0.375


func _ready() -> void:
	process_physics_priority = PHYSICS_PRIORITY
	position_smoothing_enabled = false
	# Never turned: the run is left to right on screen in every world.
	rotation = 0.0
	ignore_rotation = true
	zoom = Vector2.ONE
	frame_band(band_height)
	# Physics interpolation requires (and would force) the physics callback.
	process_callback = Camera2D.CAMERA2D_PROCESS_PHYSICS


func _physics_process(delta: float) -> void:
	if target == null:
		return
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
	_trauma = 0.0
	offset = Vector2.ZERO
	reset_physics_interpolation()


## Frames a play band [param height] px tall (0: the standard band): the
## view centres the space between the floor and [param height] above it.
func frame_band(height: float) -> void:
	band_height = height if height > 0.0 else STANDARD_BAND
	if target and target.get(&"half_size") is Vector2 and (target.get(&"half_size") as Vector2).y > 0.0:
		_target_half = (target.get(&"half_size") as Vector2).y
	vertical_offset = -(band_height * 0.5 - _target_half)


## Anchors the view on a player centre height of [param y] now (World 04: the
## surface a latch is heading for), as if the player already stood there.
func anchor_to(y: float) -> void:
	_anchor_y = y


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


## What the player sees right now, measured (QA: the same framing in every
## world). Screen values are in view pixels from the top-left.
func framing_report() -> Dictionary:
	# From the camera's own position this tick (the viewport's cached screen
	# centre can lag a tick behind the physics).
	var size := get_viewport_rect().size / zoom
	var view := Rect2(global_position + offset - size * 0.5, size)
	var feet: Vector2 = target.get_feet_position() if target.has_method(&"get_feet_position") else target.global_position
	var on_screen := target.global_position - view.position
	var feet_y := feet.y - view.position.y
	var down := _down()
	return {
		"zoom": zoom,
		"viewport": get_viewport_rect().size,
		"view": view.size,
		"rotation": rotation,
		"camera": global_position,
		"anchor_x": on_screen.x / view.size.x,
		"look_ahead_px": view.size.x - on_screen.x,
		"player_px": (target.get(&"half_size") as Vector2) * 2.0 * zoom if target.get(&"half_size") is Vector2 else Vector2.ZERO,
		"floor_y": feet_y,
		"floor_frac": feet_y / view.size.y,
		# Visible space from the floor line toward the other surface, and beyond it (below the floor).
		"toward_other_px": feet_y if down > 0.0 else view.size.y - feet_y,
		"behind_floor_px": view.size.y - feet_y if down > 0.0 else feet_y,
		"band_px": band_height,
	}


## Where the camera wants to be for the current anchor (no side effects).
func get_desired_position() -> Vector2:
	var view := get_viewport_rect().size / zoom
	var y := _anchor_y + vertical_offset * _down()
	if _down() > 0.0:
		y = minf(y, bottom_limit - view.y * 0.5)
	else:
		y = maxf(y, top_limit + view.y * 0.5)
	return Vector2(target.global_position.x + view.x * (0.5 - screen_anchor_x), y)


func _down() -> float:
	return gravity.down_sign() if gravity else 1.0


## Gravity turned mid-run (a World 03 gate or field, a World 04 latch): the
## floor is now the other side of the band, so the anchor moves there too.
## The band's centre, and so the view, stays exactly where it was: a flip
## never swings the view (a latch then refines the anchor with its real
## landing, [method anchor_to]). A restore (instant) waits for the
## respawn's [method snap_to_target].
func _on_gravity_flipped(_up: bool, instant: bool) -> void:
	if instant:
		return
	_anchor_y += (band_height - 2.0 * _target_half) * _down()
