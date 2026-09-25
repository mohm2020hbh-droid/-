class_name LevelCard
extends Button
## One level on the level select: its number and name, a lock while it is
## locked, a small diamond once completed, and a bright frame when selected.
## Drawn procedurally in the game's neon style (no textures).

var number := 1:
	set(value):
		number = value
		queue_redraw()
var title := "":
	set(value):
		title = value
		queue_redraw()
var locked := false:
	set(value):
		locked = value
		queue_redraw()
var completed := false:
	set(value):
		completed = value
		queue_redraw()
var selected := false:
	set(value):
		selected = value
		queue_redraw()


func _ready() -> void:
	focus_mode = Control.FOCUS_NONE
	flat = true
	custom_minimum_size = Vector2(150, 118)


func _draw() -> void:
	var rect := Rect2(Vector2.ZERO, size).grow(-3.0)
	var edge := Palette.NEON if selected else (Palette.NEON_DIM if not locked else Color(Palette.UI_MUTED, 0.35))
	draw_rect(rect, Color(0.05, 0.03, 0.045, 0.92 if not locked else 0.6))
	if selected:
		Neon.rect_outline(self, rect, edge, 3.0, 1.2)
	else:
		draw_rect(rect, edge, false, 2.0)
	var font := get_theme_default_font()
	var text_color := Palette.UI_TEXT if not locked else Color(Palette.UI_MUTED, 0.5)
	draw_string(font, Vector2(0, 52), "%02d" % number, HORIZONTAL_ALIGNMENT_CENTER, size.x, 40, text_color)
	draw_string(font, Vector2(0, size.y - 22), title.to_upper(), HORIZONTAL_ALIGNMENT_CENTER, size.x, 16,
		Color(text_color, 0.85))
	if locked:
		_draw_lock(Vector2(size.x - 24, 24), Color(Palette.UI_MUTED, 0.7))
	elif completed:
		var c := Vector2(size.x - 22, 22)
		draw_colored_polygon(PackedVector2Array([c + Vector2(0, -8), c + Vector2(6, 0), c + Vector2(0, 8), c + Vector2(-6, 0)]),
			Palette.SHARD_EDGE)


func _draw_lock(c: Vector2, color: Color) -> void:
	draw_rect(Rect2(c + Vector2(-8, -2), Vector2(16, 12)), color)
	draw_arc(c + Vector2(0, -2), 6.0, PI, TAU, 12, color, 3.0, true)
