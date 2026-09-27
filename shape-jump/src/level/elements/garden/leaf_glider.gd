@tool
class_name LeafGlider
extends GardenHazard
## FLOATING LEAVES, the dangerous kind (World 04): a big leaf with an inked,
## razor edge gliding on a figure-of-eight loop through the corridor, tilting
## with its flight. Most leaves in the garden are only scenery (pale, soft,
## behind everything: [GardenWeather]); these are dark and outlined, always
## drawn in front of the scenery leaves, and kill on touch.
## Origin = the loop's centre. (Mirrored in tools/levelgen/levelgen.py:
## LeafGlider.)

@export var loop := Vector2(120.0, 60.0)
@export_range(0.5, 10.0, 0.05, "suffix:s") var period := 2.6
@export_range(0.0, 1.0, 0.01) var phase := 0.0
@export var size := Vector2(84.0, 40.0):
	set(value):
		size = value
		_rebuild()

## The hitbox: this share of the leaf's length and width (inside the lens).
const CORE := Vector2(0.7, 0.6)

var _leaf: _Leaf
var _shape: CollisionShape2D


func _ready() -> void:
	super()
	_rebuild()
	apply_time(0.0)


func offset_at(t: float) -> Vector2:
	var a := TAU * (t / period + phase)
	return Vector2(loop.x * sin(a), loop.y * sin(a * 2.0))


## The leaf's tilt at [param t]: along its flight.
func tilt_at(t: float) -> float:
	var a := TAU * (t / period + phase)
	return atan2(2.0 * loop.y * cos(a * 2.0), loop.x * cos(a) + 0.001) * 0.5


## The world x range (px) it can ever cover (its whole motion).
func x_reach() -> Vector2:
	var x := global_position.x
	return Vector2(x - loop.x - size.x * 0.5, x + loop.x + size.x * 0.5)


func apply_time(t: float) -> void:
	if _leaf == null:
		return
	advance_to(t)
	var p := offset_at(t)
	var tilt := tilt_at(t)
	_leaf.position = p
	_leaf.rotation = tilt
	_shape.position = p
	_shape.rotation = tilt


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _leaf == null:
		_leaf = _Leaf.new()
		add_child(_leaf, false, Node.INTERNAL_MODE_FRONT)
		ink(_leaf)
		_shape = add_hitbox(RectangleShape2D.new())
	(_shape.shape as RectangleShape2D).size = size * CORE - Vector2.ONE * HITBOX_INSET
	(_leaf as _Leaf).size = size
	_leaf.queue_redraw()
	queue_redraw()


func _draw() -> void:
	var points := PackedVector2Array()
	for i in 33:
		var a := TAU * i / 32.0
		points.append(Vector2(loop.x * sin(a), loop.y * sin(a * 2.0)))
	draw_polyline(points, Color(GardenLook.INK_BODY, 0.18), 2.0, true)


class _Leaf extends Node2D:
	var size := Vector2(84.0, 40.0)

	func _draw() -> void:
		var shape := GardenArt.leaf(Vector2.ZERO, size.x, size.y, 0.0, 18)
		draw_colored_polygon(shape, GardenLook.INK_BODY)
		draw_polyline(GardenArt.closed(shape), GardenLook.INK_RIM, 3.0, true)
		var veins := PackedVector2Array([Vector2(-size.x * 0.45, 0.0), Vector2(size.x * 0.45, 0.0)])
		for i in 4:
			var x := -size.x * 0.3 + size.x * 0.18 * i
			veins.append_array([Vector2(x, 0.0), Vector2(x + 10.0, -size.y * 0.3)])
			veins.append_array([Vector2(x, 0.0), Vector2(x + 10.0, size.y * 0.3)])
		draw_multiline(veins, Color(GardenLook.INK_RIM, 0.55), 1.5, true)
