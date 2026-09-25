@tool
class_name Rotor
extends Hazard
## ROTATING GEOMETRIC HAZARD (docs/GDD.md §7): a spinning polygon blade
## (triangle, square, hexagon...). The spin is a pure function of level time
## and the hitbox is the polygon itself, slightly smaller, turning with it:
## what you see is what hits. Add an [Oscillator] child to make it travel.
## Origin = centre.

@export_range(16.0, 256.0, 1.0, "suffix:px") var radius := 40.0:
	set(value):
		radius = value
		_rebuild()
@export_range(3, 8) var points := 3:
	set(value):
		points = value
		_rebuild()
## Radians per second; positive turns clockwise.
@export_range(-20.0, 20.0, 0.1, "suffix:rad/s") var spin := 3.0
## Starting angle, in turns (0..1).
@export_range(0.0, 1.0, 0.01) var phase := 0.0
@export_range(0.5, 1.0, 0.05) var hitbox_scale := 0.8:
	set(value):
		hitbox_scale = value
		_rebuild()

var _shape_node: CollisionShape2D


func _ready() -> void:
	super()
	_rebuild()
	apply_time(0.0)


func apply_time(t: float) -> void:
	rotation = fposmod(phase * TAU + spin * t, TAU)


func get_polygon(scale_factor := 1.0) -> PackedVector2Array:
	var polygon := PackedVector2Array()
	for i in points:
		polygon.append(Vector2.from_angle(TAU * i / points - PI * 0.5) * radius * scale_factor)
	return polygon


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _shape_node == null:
		_shape_node = add_hitbox(ConvexPolygonShape2D.new())
	(_shape_node.shape as ConvexPolygonShape2D).points = get_polygon(hitbox_scale)
	queue_redraw()


func _draw() -> void:
	Neon.soft_light(self, Vector2.ZERO, radius * 1.6, Color(Palette.HAZARD, 0.28))
	var outer := get_polygon()
	draw_colored_polygon(outer, HazardArt.BODY)
	Neon.polyline(self, outer, Palette.HAZARD, 3.0, 1.2, true)
	# A counter-set inner polygon and a hot core make the spin readable.
	var inner := PackedVector2Array()
	for p in outer:
		inner.append(p.rotated(PI / points) * 0.45)
	Neon.polyline(self, inner, Palette.HAZARD_CORE, 2.0, 0.7, true)
	for p in outer:
		draw_line(p * 0.5, p * 0.92, Color(Palette.HAZARD_CORE, 0.55), 2.0, true)
	draw_circle(Vector2.ZERO, radius * 0.12, Palette.HAZARD_CORE)
