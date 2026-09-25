@tool
class_name Spikes
extends Area2D
## A row of triangular spikes (docs/GDD.md §7).
## Facing UP: origin = bottom-left, place it on a walkable surface.
## Facing DOWN: origin = top-left, hang it under a ceiling.
## Each spike's hitbox is its triangle shrunk toward the centroid, so grazing
## a tip is forgiven.

enum Facing { UP, DOWN }

@export_range(1, 32) var count := 1:
	set(value):
		count = value
		_rebuild()
@export var facing: Facing = Facing.UP:
	set(value):
		facing = value
		_rebuild()
@export var spike_size := Vector2(64, 52):
	set(value):
		spike_size = value
		_rebuild()
@export_range(0.3, 1.0, 0.05) var hitbox_scale := 0.7:
	set(value):
		hitbox_scale = value
		_rebuild()

var _shapes: Array[CollisionShape2D] = []


func _ready() -> void:
	collision_layer = GameConst.LAYER_HAZARD
	collision_mask = 0
	monitoring = false
	_rebuild()


func _process(_delta: float) -> void:
	if not Engine.is_editor_hint():
		HazardPulse.apply(self, position.x * 0.01)


func get_triangle(index: int) -> PackedVector2Array:
	var x0 := index * spike_size.x
	var dir := -1.0 if facing == Facing.UP else 1.0
	return PackedVector2Array([
		Vector2(x0, 0.0),
		Vector2(x0 + spike_size.x * 0.5, dir * spike_size.y),
		Vector2(x0 + spike_size.x, 0.0),
	])


func _rebuild() -> void:
	if not is_inside_tree():
		return
	for shape_node in _shapes:
		shape_node.queue_free()
	_shapes.clear()
	for i in count:
		var tri := get_triangle(i)
		var centroid := (tri[0] + tri[1] + tri[2]) / 3.0
		var shrunk := PackedVector2Array()
		for p in tri:
			shrunk.append(centroid + (p - centroid) * hitbox_scale)
		var poly := ConvexPolygonShape2D.new()
		poly.points = shrunk
		var shape_node := CollisionShape2D.new()
		shape_node.shape = poly
		add_child(shape_node, false, Node.INTERNAL_MODE_FRONT)
		_shapes.append(shape_node)
	queue_redraw()


func _draw() -> void:
	var tip_dir := -1.0 if facing == Facing.UP else 1.0
	for i in count:
		var tri := get_triangle(i)
		# Dark-red body that heats up toward the tip: reads as "danger" even
		# at phone size, and never as a safe (black) block.
		var base_color := Color(Palette.HAZARD.darkened(0.78), 1.0)
		var tip_color := Color(Palette.HAZARD.darkened(0.35), 1.0)
		draw_polygon(tri, PackedColorArray([base_color, tip_color, base_color]))
		Neon.polyline(self, tri, Palette.HAZARD, 3.0, 1.3, true)
		# Hot core line up the spine.
		var base_mid := (tri[0] + tri[2]) * 0.5
		draw_line(base_mid.lerp(tri[1], 0.3), tri[1] - Vector2(0, tip_dir * 3.0), Color(Palette.HAZARD_CORE, 0.85), 2.0, true)
