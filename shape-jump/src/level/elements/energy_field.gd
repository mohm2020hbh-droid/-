@tool
class_name EnergyField
extends Hazard
## ENERGY FIELD (docs/GDD.md §7): a band of energy that switches on and off
## on a fixed cycle. Off, it is a harmless dashed frame. For
## [member warning_time] before switching on it flickers hot (the telegraph);
## on, touching it is deadly. Give it an [Oscillator] child to move it.
## Origin = top-left corner.

enum State { OFF, WARNING, ON }

const SCANLINE_SPACING := 10.0

@export var size := Vector2(64, 192):
	set(value):
		size = value.max(Vector2(16, 16))
		_rebuild()
@export_range(0.4, 20.0, 0.05, "suffix:s") var period := 2.0
## Fraction of the cycle during which the field is on (deadly).
@export_range(0.1, 0.9, 0.01) var on_ratio := 0.5
## Starting point within the cycle, 0..1.
@export_range(0.0, 1.0, 0.01) var phase := 0.0
@export_range(0.0, 1.5, 0.05, "suffix:s") var warning_time := 0.35

var _shape_node: CollisionShape2D
var _state: State = State.ON
var _flicker_on := false


func _ready() -> void:
	super()
	_rebuild()
	apply_time(0.0)


func get_rect() -> Rect2:
	return Rect2(Vector2.ZERO, size)


func state_at(t: float) -> State:
	var u := fposmod(t / period + phase, 1.0) * period
	if u < on_ratio * period:
		return State.ON
	if u >= period - warning_time:
		return State.WARNING
	return State.OFF


func is_on() -> bool:
	return _state == State.ON


func apply_time(t: float) -> void:
	if _shape_node == null:
		return
	var state := state_at(t)
	var flicker := state == State.WARNING and fmod(t * 14.0, 1.0) < 0.5
	_shape_node.disabled = state != State.ON
	if state != _state or flicker != _flicker_on:
		_state = state
		_flicker_on = flicker
		queue_redraw()


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _shape_node == null:
		_shape_node = add_hitbox(RectangleShape2D.new())
	(_shape_node.shape as RectangleShape2D).size = size - Vector2.ONE * HITBOX_INSET * 2.0
	_shape_node.position = size * 0.5
	queue_redraw()


func _draw() -> void:
	var rect := get_rect()
	match _state:
		State.ON:
			draw_rect(rect, Color(Palette.HAZARD, 0.24))
			var y := SCANLINE_SPACING * 0.5
			while y < size.y:
				draw_line(Vector2(0, y), Vector2(size.x, y), Color(Palette.HAZARD_CORE, 0.18), 2.0)
				y += SCANLINE_SPACING
			Neon.rect_outline(self, rect, Palette.HAZARD, 3.0, 1.3)
			for x in [2.0, size.x - 2.0]:
				draw_line(Vector2(x, 0), Vector2(x, size.y), Color(Palette.HAZARD_CORE, 0.8), 2.0)
		State.WARNING when _flicker_on:
			draw_rect(rect, Color(Palette.HAZARD, 0.1))
			Neon.rect_outline(self, rect, Color(Palette.HAZARD_CORE, 0.9), 2.0, 0.8)
		_:
			Neon.dashed_rect(self, rect, Color(Palette.NEON_DIM, 0.7), 2.0, 12.0)
