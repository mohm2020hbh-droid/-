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
var _seed := 0
## A crystal star (built once): each point a shard of its own length, the
## edges between them broken, never pulled in past the hitbox.
var _star := PackedVector2Array()


func _ready() -> void:
	super()
	_seed = OrganicArt.seed_of(position, 8)
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
	_star.clear()
	queue_redraw()


## A star of void crystal: the tips where the polygon's corners are, the
## valleys between them cut in only down to the hitbox (a little outside it),
## every blade a little uneven.
func _build_star() -> void:
	var valley := hitbox_scale * radius * cos(PI / points) + 3.0
	for i in points:
		var a := TAU * i / points - PI * 0.5
		var next := a + TAU / points
		var tip := Vector2.from_angle(a) * radius
		_star.append(tip)
		# The blade's edge: a kink on the way down to the valley.
		var kink := a + TAU / points * (0.22 + 0.1 * OrganicArt.rand(_seed, i))
		_star.append(Vector2.from_angle(kink) * lerpf(radius, valley, 0.45 + 0.15 * OrganicArt.rand(_seed, 20 + i)))
		_star.append(Vector2.from_angle((a + next) * 0.5 + (OrganicArt.rand(_seed, 40 + i) - 0.5) * 0.15) * valley)


func _draw() -> void:
	if _star.is_empty():
		_build_star()
	Neon.soft_light(self, Vector2.ZERO, radius * 1.6, Color(Palette.HAZARD, 0.28))
	draw_colored_polygon(_star, Palette.HAZARD_BODY)
	# Facets: every point cut back to the heart.
	var outer := get_polygon()
	for p in outer:
		draw_line(p * 0.94, Vector2.ZERO, Color(Palette.HAZARD, 0.35), 1.5, true)
	for i in range(1, _star.size(), 3):
		draw_line(_star[i], _star[i] * 0.3, Color(Palette.HAZARD_CORE, 0.3), 1.0, true)
	Neon.polyline(self, _star, Palette.HAZARD, 3.0, 1.2, true)
	draw_circle(Vector2.ZERO, radius * 0.14, Palette.HAZARD_CORE)
