class_name StarRow
extends HBoxContainer

## Three stars, drawn as polygons so there is no icon asset to ship.

const STAR_SIZE := 42.0

func setup(earned: int, total: int = 3, star_size: float = STAR_SIZE) -> void:
	for child in get_children():
		child.queue_free()
	alignment = BoxContainer.ALIGNMENT_CENTER
	add_theme_constant_override("separation", 10)
	for i in range(total):
		add_child(_star(i < earned, star_size))

func _star(filled: bool, star_size: float) -> Control:
	var star := _Star.new()
	star.filled = filled
	star.custom_minimum_size = Vector2(star_size, star_size)
	star.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return star

class _Star extends Control:
	var filled := false

	func _draw() -> void:
		var points := PackedVector2Array()
		var centre := size * 0.5
		var outer := minf(size.x, size.y) * 0.5
		var inner := outer * 0.45
		for i in range(10):
			var angle := -PI * 0.5 + PI * float(i) / 5.0
			var radius := outer if i % 2 == 0 else inner
			points.append(centre + Vector2(cos(angle), sin(angle)) * radius)
		if filled:
			draw_colored_polygon(points, Palette.GOLD)
		else:
			draw_polyline(points + PackedVector2Array([points[0]]),
					Palette.TEXT_FAINT, 2.5, true)
