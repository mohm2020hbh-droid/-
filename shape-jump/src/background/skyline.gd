@tool
class_name Skyline
extends Node2D
## Procedural silhouette of geometric towers and monoliths for one parallax
## layer. Deterministic from [member seed_value]; drawn once (cached).
## Spans x in [0, width) so a Parallax2D can repeat it seamlessly.

@export var seed_value := 1:
	set(value):
		seed_value = value
		queue_redraw()
@export var width := 2048.0:
	set(value):
		width = value
		queue_redraw()
## Y of the tower bases (drawn down to [member base_y] + 600 to hide gaps).
@export var base_y := 200.0
@export var min_height := 180.0
@export var max_height := 560.0
@export var min_width := 50.0
@export var max_width := 150.0
@export var color := Palette.SILHOUETTE_FAR
## Chance per tower of a thin neon accent line.
@export_range(0.0, 1.0, 0.05) var accent_chance := 0.0
@export var accent_color := Color(Palette.NEON, 0.35)


func _draw() -> void:
	var rng := RandomNumberGenerator.new()
	rng.seed = seed_value
	var x := 0.0
	while x < width:
		var w := rng.randf_range(min_width, max_width)
		w = minf(w, width - x)
		var h := rng.randf_range(min_height, max_height)
		_draw_tower(rng, x, w, h)
		x += w + rng.randf_range(0.0, 40.0)


func _draw_tower(rng: RandomNumberGenerator, x: float, w: float, h: float) -> void:
	var top := base_y - h
	draw_rect(Rect2(x, top, w, h + 600.0), color)
	match rng.randi_range(0, 3):
		0:  # Spire.
			draw_colored_polygon(PackedVector2Array([
				Vector2(x, top), Vector2(x + w * 0.5, top - w * rng.randf_range(0.6, 1.4)), Vector2(x + w, top)]), color)
		1:  # Stepped crown.
			draw_rect(Rect2(x + w * 0.2, top - w * 0.35, w * 0.6, w * 0.35), color)
			draw_rect(Rect2(x + w * 0.4, top - w * 0.7, w * 0.2, w * 0.35), color)
		2:  # Antenna.
			draw_rect(Rect2(x + w * 0.48, top - h * 0.25, 3.0, h * 0.25), color)
		_:
			pass
	if rng.randf() < accent_chance:
		var ax := x + rng.randf_range(0.15, 0.85) * w
		draw_line(Vector2(ax, top + 10.0), Vector2(ax, top + h * rng.randf_range(0.3, 0.8)), accent_color, 2.0)
