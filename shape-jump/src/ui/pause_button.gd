@tool
class_name PauseButton
extends Button
## Round-cornered square button with a drawn pause glyph (no font needed).


func _draw() -> void:
	var c := size * 0.5
	var bar := Vector2(size.x * 0.1, size.y * 0.36)
	var gap := size.x * 0.08
	var color := Palette.UI_TEXT if not is_pressed() else Palette.NEON
	draw_rect(Rect2(c + Vector2(-gap - bar.x, -bar.y * 0.5), bar), color)
	draw_rect(Rect2(c + Vector2(gap, -bar.y * 0.5), bar), color)
