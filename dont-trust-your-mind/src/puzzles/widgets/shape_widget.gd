class_name ShapeWidget
extends Control

## A single geometric primitive drawn with the 2D canvas API. Shapes are drawn
## rather than imported so the game ships no sprite atlas and stays crisp at any
## screen density.

signal tapped()
signal press_started()
signal press_ended()

@export var form: PuzzleElement.ShapeForm = PuzzleElement.ShapeForm.CIRCLE:
	set(value):
		form = value
		queue_redraw()

@export var fill: Color = Palette.BLUE:
	set(value):
		fill = value
		queue_redraw()

@export var outline: Color = Color(0, 0, 0, 0):
	set(value):
		outline = value
		queue_redraw()

@export var outline_width: float = 0.0
@export var label_text: String = "":
	set(value):
		label_text = value
		queue_redraw()

@export var label_color: Color = Palette.WHITE
@export var label_font_size: int = 30

var _font: Font

func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_STOP
	_font = GameTheme.bold()

func _draw() -> void:
	var r := Rect2(Vector2.ZERO, size)
	match form:
		PuzzleElement.ShapeForm.CIRCLE:
			var c := r.get_center()
			var radius := minf(size.x, size.y) * 0.5
			draw_circle(c, radius, fill)
			if outline_width > 0.0:
				draw_arc(c, radius - outline_width * 0.5, 0.0, TAU, 48, outline,
						outline_width, true)
		PuzzleElement.ShapeForm.SQUARE:
			draw_rect(r, fill, true)
			if outline_width > 0.0:
				draw_rect(r.grow(-outline_width * 0.5), outline, false, outline_width)
		PuzzleElement.ShapeForm.TRIANGLE:
			var pts := PackedVector2Array([
				Vector2(size.x * 0.5, 0.0), Vector2(size.x, size.y), Vector2(0.0, size.y),
			])
			draw_colored_polygon(pts, fill)
			if outline_width > 0.0:
				draw_polyline(pts + PackedVector2Array([pts[0]]), outline, outline_width, true)
		PuzzleElement.ShapeForm.DIAMOND:
			var d := PackedVector2Array([
				Vector2(size.x * 0.5, 0.0), Vector2(size.x, size.y * 0.5),
				Vector2(size.x * 0.5, size.y), Vector2(0.0, size.y * 0.5),
			])
			draw_colored_polygon(d, fill)
			if outline_width > 0.0:
				draw_polyline(d + PackedVector2Array([d[0]]), outline, outline_width, true)

	if not label_text.is_empty() and _font != null:
		var text_size := _font.get_string_size(label_text, HORIZONTAL_ALIGNMENT_CENTER,
				-1.0, label_font_size)
		var pos := Vector2((size.x - text_size.x) * 0.5,
				(size.y + _font.get_ascent(label_font_size) - _font.get_descent(label_font_size)) * 0.5)
		draw_string(_font, pos, label_text, HORIZONTAL_ALIGNMENT_LEFT, -1.0,
				label_font_size, label_color)

func _gui_input(event: InputEvent) -> void:
	if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT:
		accept_event()
		if event.pressed:
			press_started.emit()
			tapped.emit()
		else:
			press_ended.emit()
