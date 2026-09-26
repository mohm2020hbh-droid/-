@tool
class_name GravityLens
extends Node2D
## GRAVITY LENS (World 03): a knot of bent space hanging in the background,
## rings squeezed toward where gravity will point next. It turns slowly and
## its pointer shows the next flip (up, down, or none left). Decoration that
## tells the truth; no collision. Origin = the centre.

@export var radius := 90.0:
	set(value):
		radius = value
		_refresh()

var _rings: _Rings
var _pointer: _Pointer


func _ready() -> void:
	z_index = -3
	_rings = _Rings.new()
	_pointer = _Pointer.new()
	add_child(_rings, false, Node.INTERNAL_MODE_FRONT)
	add_child(_pointer, false, Node.INTERNAL_MODE_FRONT)
	_refresh()
	if not Engine.is_editor_hint():
		Level.join(self)


func _enter_tree() -> void:
	if is_node_ready() and not Engine.is_editor_hint():
		Level.join(self)


func _exit_tree() -> void:
	if not Engine.is_editor_hint():
		Level.leave(self)


func apply_time(t: float) -> void:
	var level := Level.of(self)
	var next := level.next_gravity_change(t) if level else -1
	_rings.rotation = t * 0.35
	_pointer.visible = next >= 0
	if next >= 0:
		_pointer.rotation = PI if next == 0 else 0.0
		_pointer.modulate = GalaxyArt.state_color(next == 1)


func _refresh() -> void:
	if _rings:
		_rings.radius = radius
		_pointer.radius = radius
		_rings.queue_redraw()
		_pointer.queue_redraw()


class _Rings extends Node2D:
	var radius := 90.0

	func _draw() -> void:
		Neon.soft_light(self, Vector2.ZERO, radius * 1.8, Color(1.0, 1.0, 1.0, 0.08))
		for i in 5:
			var r := radius * (0.3 + 0.16 * i)
			# Squeezed ellipses: bent space.
			var points := PackedVector2Array()
			for k in 33:
				var a := TAU * k / 32.0
				points.append(Vector2(cos(a) * r, sin(a) * r * (0.55 + 0.08 * i)))
			draw_polyline(points, Color(0.85, 0.85, 1.0, 0.22 - 0.03 * i), 1.5, true)
		draw_circle(Vector2.ZERO, radius * 0.16, Color(0.0, 0.0, 0.0, 0.9))
		draw_arc(Vector2.ZERO, radius * 0.16, 0.0, TAU, 20, Color(1.0, 1.0, 1.0, 0.5), 1.5, true)


## Points up (toward the ceiling) at rotation 0; the lens turns it.
class _Pointer extends Node2D:
	var radius := 90.0

	func _draw() -> void:
		var tip := Vector2(0.0, -radius * 0.95)
		draw_polyline(PackedVector2Array([tip + Vector2(-14.0, 14.0), tip, tip + Vector2(14.0, 14.0)]),
			Color.WHITE, 3.0, true)
		draw_line(Vector2(0.0, -radius * 0.25), tip, Color(1.0, 1.0, 1.0, 0.5), 1.5)
