@tool
extends Node2D
## Horizontal haze band that sits between the far layers and the playfield.
## Lives in a Parallax2D with scroll_scale.x = 0, so it never ends.

@export var half_width := 2400.0
@export var top := -260.0
@export var bottom := 700.0
@export var peak := 120.0
@export var color := Color(Palette.FOG, 0.55)


func _draw() -> void:
	var points := PackedVector2Array([
		Vector2(-half_width, top), Vector2(half_width, top),
		Vector2(half_width, peak), Vector2(-half_width, peak)])
	var clear := Color(color, 0.0)
	draw_polygon(points, PackedColorArray([clear, clear, color, color]))
	draw_rect(Rect2(-half_width, peak, half_width * 2.0, bottom - peak), color)
