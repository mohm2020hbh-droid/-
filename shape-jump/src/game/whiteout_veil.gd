class_name WhiteoutVeil
extends Node2D
## Draws World 02's whiteouts (see WhiteoutZone) over the camera view: a
## white flood, then every hazard hitbox and every real ledge redrawn in
## black on top, so the glare drowns only secondary geometry. It sits above
## the level and below the player. Redraws only while a whiteout is on.

var level: Level
var player: Node2D
var clock := 0.0

var _intensity := 0.0
var _blocks: Array[Block] = []
var _hazards: Array[Hazard] = []
var _cached_for: Level


func _ready() -> void:
	z_index = 40
	z_as_relative = false


func _process(_delta: float) -> void:
	var value := 0.0
	if level and player:
		for zone in get_tree().get_nodes_in_group(&"whiteout_zones"):
			value = maxf(value, (zone as WhiteoutZone).intensity(level.clock, player.global_position.x))
	if value > 0.001 or _intensity > 0.001:
		_intensity = value
		queue_redraw()


func get_intensity() -> float:
	return _intensity


func _cache() -> void:
	if _cached_for == level:
		return
	_cached_for = level
	_blocks.clear()
	_hazards.clear()
	for node in level.find_children("*", "Block", true, false):
		_blocks.append(node)
	for node in level.find_children("*", "Hazard", true, false):
		_hazards.append(node)


func _draw() -> void:
	if _intensity <= 0.001 or level == null:
		return
	_cache()
	var view := get_viewport().get_canvas_transform().affine_inverse() * get_viewport_rect()
	var to_local := get_global_transform().affine_inverse()
	draw_set_transform_matrix(to_local)
	draw_rect(view.grow(64.0), Color(0.97, 0.97, 0.97, 0.93 * _intensity))
	var ink := Color(0.0, 0.0, 0.0, clampf(_intensity * 1.4, 0.0, 1.0))
	for block in _blocks:
		if not is_instance_valid(block) or block is PhaseBlock:
			continue
		var r: Rect2 = block.get_rect()
		var a := block.global_position + r.position
		if a.x > view.end.x or a.x + r.size.x < view.position.x:
			continue
		draw_line(a, a + Vector2(r.size.x, 0.0), ink, 4.0)
	for hazard in _hazards:
		if not is_instance_valid(hazard):
			continue
		for child in hazard.get_children(true):
			var shape_node := child as CollisionShape2D
			if shape_node == null or shape_node.disabled:
				continue
			var xf := shape_node.global_transform
			if not view.grow(200.0).has_point(xf.origin):
				continue
			var shape := shape_node.shape
			if shape is RectangleShape2D:
				var h := (shape as RectangleShape2D).size * 0.5
				draw_polyline(PackedVector2Array([xf * Vector2(-h.x, -h.y), xf * Vector2(h.x, -h.y),
					xf * Vector2(h.x, h.y), xf * Vector2(-h.x, h.y), xf * Vector2(-h.x, -h.y)]), ink, 3.0)
			elif shape is CircleShape2D:
				draw_arc(xf.origin, (shape as CircleShape2D).radius, 0.0, TAU, 24, ink, 3.0)
			elif shape is ConvexPolygonShape2D:
				var pts := PackedVector2Array()
				for p in (shape as ConvexPolygonShape2D).points:
					pts.append(xf * p)
				if pts.size() > 1:
					pts.append(pts[0])
					draw_polyline(pts, ink, 3.0)
