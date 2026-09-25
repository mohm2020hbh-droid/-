@tool
extends Node2D
## The shard's picture, drawn once. Shard animates it cheaply every frame
## (position for the bob, self_modulate for the shimmer) without redrawing.

const HALF_SIZE := Vector2(12, 17)


func _draw() -> void:
	var diamond := PackedVector2Array([
		Vector2(0, -HALF_SIZE.y), Vector2(HALF_SIZE.x, 0), Vector2(0, HALF_SIZE.y), Vector2(-HALF_SIZE.x, 0)])
	Neon.soft_light(self, Vector2.ZERO, 34.0, Color(Palette.SHARD_EDGE, 0.4))
	draw_colored_polygon(diamond, Color(Palette.SHARD_EDGE, 0.85))
	# Facet: a brighter inner diamond reads as a cut gem, not a spike.
	var facet := PackedVector2Array()
	for p in diamond:
		facet.append(p * 0.5 + Vector2(0, -2))
	draw_colored_polygon(facet, Palette.SHARD_CORE)
	Neon.polyline(self, diamond, Palette.SHARD_CORE, 1.5, 0.8, true)
