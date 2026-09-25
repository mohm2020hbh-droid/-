@tool
class_name CollapsingPath
extends StaticBody2D
## COLLAPSING PATH (docs/GDD.md §7): a walkway of cracked tiles that fall
## one after another, starting at level time [member collapse_time] and
## [member interval] seconds apart (negative: from the far end). Each tile
## shakes and its cracks glow for [member warning_time] before it drops.
## Everything is a function of level time: a checkpoint rewind rebuilds it.
## Origin = top-left corner; walkable like a [Block].

const FALL_GRAVITY := 1800.0
const FALL_TIME := 0.6
const SEAM := 3.0

@export_range(1, 64) var tiles := 6:
	set(value):
		tiles = value
		_rebuild()
@export var tile_size := Vector2(64, 32):
	set(value):
		tile_size = value.max(Vector2(16, 8))
		_rebuild()
## Level time at which the first tile drops.
@export var collapse_time := 3.0
## Seconds between two tiles dropping; negative collapses from the far end.
@export_range(-2.0, 2.0, 0.005, "suffix:s") var interval := 0.12
@export_range(0.0, 2.0, 0.05, "suffix:s") var warning_time := 0.35

var _shapes: Array[CollisionShape2D] = []
## Per tile: level time minus its drop time (negative while standing).
var _ages := PackedFloat32Array()
var _animating := true


func _ready() -> void:
	collision_layer = GameConst.LAYER_WORLD
	collision_mask = 0
	_rebuild()
	if not Engine.is_editor_hint():
		Level.join(self)


func _enter_tree() -> void:
	if is_node_ready() and not Engine.is_editor_hint():
		Level.join(self)  # Re-entering after a reparent.


func _exit_tree() -> void:
	if not Engine.is_editor_hint():
		Level.leave(self)


func get_rect() -> Rect2:
	return Rect2(Vector2.ZERO, Vector2(tile_size.x * tiles, tile_size.y))


## Level time at which tile [param index] (0 = nearest) drops.
func drop_time(index: int) -> float:
	var order := index if interval >= 0.0 else tiles - 1 - index
	return collapse_time + order * absf(interval)


func is_tile_solid(index: int) -> bool:
	return not _shapes[index].disabled


func apply_time(t: float) -> void:
	var animating := false
	for i in tiles:
		var age := t - drop_time(i)
		_ages[i] = age
		_shapes[i].disabled = age >= 0.0
		animating = animating or (age >= -warning_time and age < FALL_TIME)
	# Redraw while something shakes or falls, and once more when it settles.
	if animating or _animating:
		queue_redraw()
	_animating = animating


func _rebuild() -> void:
	if not is_inside_tree():
		return
	for shape_node in _shapes:
		shape_node.queue_free()
	_shapes.clear()
	for i in tiles:
		var rect := RectangleShape2D.new()
		rect.size = tile_size
		var shape_node := CollisionShape2D.new()
		shape_node.shape = rect
		shape_node.position = Vector2(tile_size.x * (i + 0.5), tile_size.y * 0.5)
		add_child(shape_node, false, Node.INTERNAL_MODE_FRONT)
		_shapes.append(shape_node)
	_ages.resize(tiles)
	_ages.fill(-INF)
	queue_redraw()


func _draw() -> void:
	for i in tiles:
		var age := _ages[i]
		if age >= FALL_TIME:
			continue
		var offset := Vector2.ZERO
		var alpha := 1.0
		var hot := false
		if age >= 0.0:
			offset.y = 0.5 * FALL_GRAVITY * age * age
			alpha = 1.0 - age / FALL_TIME
		elif age >= -warning_time:
			hot = true
			offset.x = sin(age * 80.0 + i) * 1.5
		var rect := Rect2(Vector2(tile_size.x * i + SEAM * 0.5, 0.0) + offset, Vector2(tile_size.x - SEAM, tile_size.y))
		_draw_tile(rect, i, alpha, hot)


func _draw_tile(rect: Rect2, index: int, alpha: float, hot: bool) -> void:
	draw_rect(rect, Color(Palette.BLOCK_BODY, alpha))
	var lip := Palette.HAZARD_CORE if hot else Palette.NEON
	Neon.line(self, rect.position, Vector2(rect.end.x, rect.position.y), Color(lip, alpha), 3.0, alpha)
	# A crack across every tile: this walkway is not meant to last.
	var x := rect.position.x + rect.size.x * (0.3 + 0.4 * fmod(index * 0.618, 1.0))
	var crack := PackedVector2Array([
		Vector2(x, rect.position.y + 2.0), Vector2(x + 6.0, rect.position.y + rect.size.y * 0.45),
		Vector2(x - 4.0, rect.position.y + rect.size.y * 0.7), Vector2(x + 3.0, rect.end.y)])
	var crack_color := Color(Palette.HAZARD_CORE, alpha) if hot else Color(Palette.NEON_DIM, 0.8 * alpha)
	draw_polyline(crack, crack_color, 2.0, true)
	draw_rect(rect, Color(Palette.NEON_DIM, 0.6 * alpha), false, 1.5)
