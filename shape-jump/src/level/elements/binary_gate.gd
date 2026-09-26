@tool
class_name BinaryGate
extends Hazard
## BINARY GATE (docs/GDD.md §7, World 02): two barriers across the run, one
## WHITE and one BLACK, usually guarding the low and the high route. The gate
## switches state every half [member period]: in the WHITE state the white
## barrier is solid and the black one is only a dashed outline you can pass
## through, and the other way round. The barrier about to become solid
## flickers for [member warning_time] first. Deterministic: learn the
## rhythm, take the open route. Origin = centre line x at floor level.

@export_range(16.0, 256.0, 1.0, "suffix:px") var width := 48.0:
	set(value):
		width = value
		_rebuild()
## Top and bottom y (relative, negative = above the floor) of the white barrier.
@export var white_span := Vector2(-160.0, 0.0):
	set(value):
		white_span = value
		_rebuild()
@export var black_span := Vector2(-600.0, -224.0):
	set(value):
		black_span = value
		_rebuild()
@export_range(0.2, 20.0, 0.05, "suffix:s") var period := 2.0
@export_range(0.0, 1.0, 0.01) var phase := 0.0
@export_range(0.0, 1.0, 0.05, "suffix:s") var warning_time := 0.35

const PARKED := Vector2(0, 100000)

var _white: _Barrier
var _black: _Barrier
var _white_shape: CollisionShape2D
var _black_shape: CollisionShape2D


func _ready() -> void:
	super()
	_rebuild()
	apply_time(0.0)


## 0 = WHITE solid, 1 = BLACK solid.
func state_at(t: float) -> int:
	return int(fposmod(t / period + phase, 1.0) * 2.0) % 2


func apply_time(t: float) -> void:
	if _white == null:
		return
	var state := state_at(t)
	var half := period * 0.5
	var u := fposmod(t / period + phase, 1.0) * period
	var into_half := fmod(u, half)
	var warn := into_half >= half - warning_time and fmod(t * 12.0, 1.0) < 0.5
	_white.set_solid(state == 0, warn and state != 0)
	_black.set_solid(state == 1, warn and state != 1)
	_white_shape.position = _span_center(white_span) if state == 0 else PARKED
	_black_shape.position = _span_center(black_span) if state == 1 else PARKED


func _span_center(span: Vector2) -> Vector2:
	return Vector2(0.0, (span.x + span.y) * 0.5)


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _white == null:
		_white = _Barrier.new()
		_black = _Barrier.new()
		add_child(_white, false, Node.INTERNAL_MODE_FRONT)
		add_child(_black, false, Node.INTERNAL_MODE_FRONT)
		_white_shape = add_hitbox(RectangleShape2D.new())
		_black_shape = add_hitbox(RectangleShape2D.new())
	_white.setup(Rect2(-width * 0.5, white_span.x, width, white_span.y - white_span.x), true)
	_black.setup(Rect2(-width * 0.5, black_span.x, width, black_span.y - black_span.x), false)
	(_white_shape.shape as RectangleShape2D).size = Vector2(width, white_span.y - white_span.x) - Vector2.ONE * HITBOX_INSET * 2.0
	(_black_shape.shape as RectangleShape2D).size = Vector2(width, black_span.y - black_span.x) - Vector2.ONE * HITBOX_INSET * 2.0


## One barrier: a solid picture and a dashed ghost, both drawn once; the
## gate only toggles which one shows.
class _Barrier extends Node2D:
	var rect := Rect2()
	var white := true
	var _solid: Node2D
	var _ghost: Node2D

	func setup(r: Rect2, is_white: bool) -> void:
		rect = r
		white = is_white
		if _solid == null:
			_solid = _Pic.new()
			_ghost = _Pic.new()
			add_child(_solid)
			add_child(_ghost)
		(_solid as _Pic).setup(rect, white, true)
		(_ghost as _Pic).setup(rect, white, false)

	func set_solid(solid: bool, warning: bool) -> void:
		_solid.visible = solid
		_ghost.visible = not solid
		_ghost.modulate = Color(1, 1, 1, 1.0 if warning else 0.45)


class _Pic extends Node2D:
	var rect := Rect2()
	var white := true
	var solid := true

	func setup(r: Rect2, is_white: bool, is_solid: bool) -> void:
		rect = r
		white = is_white
		solid = is_solid
		queue_redraw()

	func _draw() -> void:
		var ink := Color(0.96, 0.96, 0.96) if white else Color(0.0, 0.0, 0.0)
		var line := Color(0.0, 0.0, 0.0) if white else Color(1.0, 1.0, 1.0)
		if solid:
			draw_rect(rect, ink)
			HazardArt.stripes(self, rect.grow(-4.0), 20.0, Color(line, 0.35), 3.0)
			draw_rect(rect, Color(1, 1, 1) if white else Color(1, 1, 1), false, 2.5)
			draw_rect(rect.grow(-4.0), line, false, 1.5)
		else:
			Neon.dashed_rect(self, rect, Color(1, 1, 1, 0.8), 2.0, 12.0)
