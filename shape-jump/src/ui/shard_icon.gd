@tool
class_name ShardIcon
extends Control
## Small diamond used next to shard counters in the UI.


func _draw() -> void:
	var c := size * 0.5
	var h := Vector2(size.x * 0.32, size.y * 0.46)
	var diamond := PackedVector2Array([
		c + Vector2(0, -h.y), c + Vector2(h.x, 0), c + Vector2(0, h.y), c + Vector2(-h.x, 0)])
	draw_colored_polygon(diamond, Palette.SHARD_EDGE)
	var facet := PackedVector2Array()
	for p in diamond:
		facet.append(c + (p - c) * 0.45)
	draw_colored_polygon(facet, Palette.SHARD_CORE)
