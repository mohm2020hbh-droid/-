@tool
class_name OrbitalHazard
extends Hazard
## ORBITAL HAZARD (World 03): small dark moons circling a dead star on a
## fixed orbit. The orbit is drawn; the moons move at a constant rate, so
## where they will be is always readable. Origin = the centre of the orbit.

@export var radius := 96.0:
	set(value):
		radius = value
		_rebuild()
@export_range(1, 6) var bodies := 2:
	set(value):
		bodies = value
		_rebuild()
@export var body_radius := 18.0:
	set(value):
		body_radius = value
		_rebuild()
## Radians per second; positive turns clockwise.
@export_range(-8.0, 8.0, 0.05, "suffix:rad/s") var spin := 1.6
@export_range(0.0, 1.0, 0.01) var phase := 0.0

const HITBOX_SCALE := 0.8

var _moons: Array[Node2D] = []
var _shapes: Array[CollisionShape2D] = []


func _ready() -> void:
	super()
	_rebuild()
	apply_time(0.0)


func angle_of(i: int, t: float) -> float:
	return phase * TAU + spin * t + TAU * i / bodies


func apply_time(t: float) -> void:
	for i in _moons.size():
		var at := Vector2.from_angle(angle_of(i, t)) * radius
		_moons[i].position = at
		_moons[i].rotation = t * 1.3
		_shapes[i].position = at


func _rebuild() -> void:
	if not is_inside_tree():
		return
	for node in _moons:
		node.queue_free()
	for node in _shapes:
		node.queue_free()
	_moons.clear()
	_shapes.clear()
	for i in bodies:
		var moon := _Moon.new()
		moon.seed = OrganicArt.seed_of(position, 21 + i)
		moon.radius = body_radius
		add_child(moon, false, Node.INTERNAL_MODE_FRONT)
		_moons.append(moon)
		var circle := CircleShape2D.new()
		circle.radius = body_radius * HITBOX_SCALE
		_shapes.append(add_hitbox(circle))
	queue_redraw()


func _draw() -> void:
	# The orbit and the dead star it circles.
	draw_arc(Vector2.ZERO, radius, 0.0, TAU, 64, Color(GalaxyArt.DANGER, 0.22), 2.0, true)
	var marks := 16
	for i in marks:
		var a := TAU * i / marks
		draw_line(Vector2.from_angle(a) * (radius - 5.0), Vector2.from_angle(a) * (radius + 5.0),
			Color(GalaxyArt.DANGER, 0.3), 1.5)
	Neon.soft_light(self, Vector2.ZERO, 36.0, Color(GalaxyArt.DANGER, 0.25))
	draw_circle(Vector2.ZERO, 9.0, GalaxyArt.DANGER_BODY)
	draw_arc(Vector2.ZERO, 9.0, 0.0, TAU, 16, GalaxyArt.DANGER, 2.0, true)


class _Moon extends Node2D:
	var seed := 0
	var radius := 18.0:
		set(value):
			radius = value
			_rock.clear()
			queue_redraw()
	var _rock := PackedVector2Array()
	var _craters := PackedVector2Array()

	## A ringed moon of pitted rock: an uneven body (never inside the
	## hitbox), craters, a lit crescent and its thin ring.
	func _draw() -> void:
		if _rock.is_empty():
			_rock = OrganicArt.blot(Vector2.ZERO, radius * 0.97, seed, 11)
			_craters.clear()
			for i in 3:
				var c := Vector2.from_angle(OrganicArt.rand(seed, 90 + i) * TAU) * radius * (0.2 + 0.4 * OrganicArt.rand(seed, 95 + i))
				var r := radius * (0.14 + 0.1 * OrganicArt.rand(seed, 99 + i))
				_craters.append_array([c + Vector2(-r, 0), c + Vector2(0, r * 0.7), c + Vector2(0, r * 0.7), c + Vector2(r, 0)])
		Neon.soft_light(self, Vector2.ZERO, radius * 2.4, Color(GalaxyArt.DANGER, 0.3))
		draw_colored_polygon(_rock, GalaxyArt.DANGER_BODY)
		draw_multiline(_craters, Color(GalaxyArt.DANGER, 0.55), 1.5)
		draw_polyline(GalaxyArt.closed(_rock), GalaxyArt.DANGER, 2.5, true)
		draw_arc(Vector2.ZERO, radius * 0.62, -0.6, 1.4, 12, Color(GalaxyArt.DANGER_CORE, 0.7), 2.0, true)
		draw_line(Vector2(-radius * 1.35, radius * 0.3), Vector2(radius * 1.35, -radius * 0.3),
			Color(GalaxyArt.DANGER, 0.55), 1.5)
