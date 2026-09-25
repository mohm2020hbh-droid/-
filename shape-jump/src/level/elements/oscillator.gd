@tool
class_name Oscillator
extends Node2D
## Motion component: moves its parent Node2D back and forth along
## [member travel] as a pure function of level time, so every attempt sees
## exactly the same timing (docs/ARCHITECTURE.md §5).
##
## Add it as a child of any element: a [Block] on an AnimatableBody2D becomes
## a moving platform (MOVING FLOOR SEGMENT), a [Rotor] or [PrismBeam] a
## moving hazard. Waves:
## - SINE: smooth back and forth;
## - LINEAR: constant speed, sharp turns;
## - STEPS: holds at each end, then moves quickly (SHIFTING PLATFORM
##   HEIGHTS, MOVING WALL PANELS). Before each move the parent flashes.
## The track is drawn in the game too (a faint dashed rail), so a moving
## thing always shows where it can be.

enum Wave { SINE, LINEAR, STEPS }

## Offset of the far end of the path, relative to the parent's placed position.
@export var travel := Vector2(0, -128):
	set(value):
		travel = value
		queue_redraw()
@export_range(0.2, 20.0, 0.05, "suffix:s") var period := 2.0
## Starting point within the cycle, 0..1.
@export_range(0.0, 1.0, 0.01) var phase := 0.0
@export var wave: Wave = Wave.SINE
## STEPS: fraction of the cycle spent holding (split between the two ends).
@export_range(0.0, 0.95, 0.01) var hold_ratio := 0.6
## STEPS: how long before each move the parent flashes.
@export_range(0.0, 1.0, 0.05, "suffix:s") var warning_time := 0.3
## Draw the track in the game (always drawn in the editor).
@export var show_track := true:
	set(value):
		show_track = value
		queue_redraw()

const FLASH := Color(2.2, 2.2, 2.2)

var _parent: Node2D
var _origin := Vector2.ZERO


func _ready() -> void:
	_parent = get_parent() as Node2D
	if _parent:
		_origin = _parent.position
	update_configuration_warnings()
	if not Engine.is_editor_hint():
		# The track stays where the path is while the parent moves along it.
		top_level = true
		global_position = _parent.global_position if _parent else global_position
		z_index = -1
		Level.join(self)


func _enter_tree() -> void:
	if is_node_ready() and not Engine.is_editor_hint():
		Level.join(self)  # Re-entering after a reparent.


func _exit_tree() -> void:
	if not Engine.is_editor_hint():
		Level.leave(self)


func _get_configuration_warnings() -> PackedStringArray:
	var parent := get_parent()
	if not parent is Node2D:
		return ["Oscillator moves its parent, which must be a Node2D."]
	if parent is PhysicsBody2D and not parent is AnimatableBody2D:
		return ["A moving solid must be an AnimatableBody2D, or bodies standing on it are not carried."]
	return []


## Fraction of [member travel] covered at level time [param t] (0 = origin).
func progress_at(t: float) -> float:
	var cycle := fposmod(t / period + phase, 1.0)
	match wave:
		Wave.SINE:
			return 0.5 - 0.5 * cos(cycle * TAU)
		Wave.LINEAR:
			return 1.0 - absf(cycle * 2.0 - 1.0)
	# STEPS: hold at 0, move out, hold at 1, move back.
	var hold := hold_ratio * 0.5
	var move := (1.0 - hold_ratio) * 0.5
	if cycle < hold:
		return 0.0
	if cycle < hold + move:
		return smoothstep(0.0, 1.0, (cycle - hold) / move)
	if cycle < hold * 2.0 + move:
		return 1.0
	return 1.0 - smoothstep(0.0, 1.0, (cycle - hold * 2.0 - move) / move)


## STEPS only: true during the warning time before a move starts.
func is_about_to_move(t: float) -> bool:
	if wave != Wave.STEPS or warning_time <= 0.0:
		return false
	var cycle := fposmod(t / period + phase, 1.0) * period
	var hold := hold_ratio * 0.5 * period
	var move := (1.0 - hold_ratio) * 0.5 * period
	var first_move := hold
	var second_move := hold * 2.0 + move
	return (cycle >= first_move - warning_time and cycle < first_move) \
		or (cycle >= second_move - warning_time and cycle < second_move)


func offset_at(t: float) -> Vector2:
	return travel * progress_at(t)


func apply_time(t: float) -> void:
	if _parent == null:
		return
	_parent.position = _origin + offset_at(t)
	if wave == Wave.STEPS:
		var flash := is_about_to_move(t) and fmod(t * 10.0, 1.0) < 0.5
		_parent.self_modulate = FLASH if flash else Color.WHITE


func reset_parent_interpolation() -> void:
	if _parent:
		_parent.reset_physics_interpolation()


func _draw() -> void:
	if not show_track and not Engine.is_editor_hint():
		return
	var color := Color(Palette.UI_MUTED, 0.8) if Engine.is_editor_hint() else Color(Palette.NEON_DIM, 0.55)
	draw_dashed_line(Vector2.ZERO, travel, color, 2.0, 8.0)
	draw_circle(Vector2.ZERO, 4.0, color)
	draw_circle(travel, 4.0, color)
