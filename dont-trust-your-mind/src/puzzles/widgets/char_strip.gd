class_name CharStrip
extends Control

## Text whose individual characters are independently tappable, in Arabic and in
## English alike.
##
## Splitting a string into one Label per letter would destroy Arabic, which is
## cursive: letters change shape depending on their neighbours. So the string is
## shaped once as a single run through TextServer (HarfBuzz), and character
## targets are recovered afterwards from the shaped result:
##
##   * grapheme boundaries come from [method TextServer.shaped_text_get_character_breaks],
##     so a base letter plus its diacritics counts as one target;
##   * the visual box of a grapheme comes from
##     [method TextServer.shaped_text_get_selection], which is BiDi-aware, so
##     "character 0" lands on the rightmost glyph in Arabic and the leftmost in
##     English without any manual flipping.
##
## That difference is not an implementation detail here, it is gameplay: puzzles
## about "the first letter" are meant to resolve differently per language.

signal character_tapped(grapheme_index: int)
signal strip_tapped()

const MIN_TAP_WIDTH := 56.0   ## Auto-fit aims to keep every target this wide.
const SLOT_HEIGHT := 3.0
const SLOT_GAP := 6.0

@export var text: String = "":
	set(value):
		text = value
		_dirty = true
		queue_redraw()

@export var max_font_size: int = 96:
	set(value):
		max_font_size = value
		_dirty = true
		queue_redraw()

@export var min_font_size: int = 28
@export var font_color: Color = Color.WHITE
@export var show_slots: bool = false   ## Draw a tap guide under each character.
@export var slot_color: Color = Color(1, 1, 1, 0.18)
@export var per_character: bool = true ## False = the whole strip is one target.

var _font: Font
var _line := TextLine.new()
var _ts: TextServer = TextServerManager.get_primary_interface()
var _fitted_size: int = 0
var _breaks: PackedInt32Array = PackedInt32Array()
var _boxes: Array[Rect2] = []
var _dirty := true
var _highlights: Dictionary = {}       ## grapheme index -> Color

func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_STOP
	clip_contents = false
	resized.connect(func() -> void:
		_dirty = true
		queue_redraw())

func set_font(font: Font) -> void:
	_font = font
	_dirty = true
	queue_redraw()

func grapheme_count() -> int:
	_ensure_shaped()
	return maxi(_breaks.size(), 0)

## Visual rectangle of one grapheme, in this control's local space.
func box_for(index: int) -> Rect2:
	_ensure_shaped()
	if index < 0 or index >= _boxes.size():
		return Rect2()
	return _boxes[index]

func highlight(index: int, color: Color) -> void:
	_highlights[index] = color
	queue_redraw()

func clear_highlights() -> void:
	_highlights.clear()
	queue_redraw()

func _ensure_shaped() -> void:
	if not _dirty:
		return
	_dirty = false
	_boxes.clear()
	if _font == null or text.is_empty():
		_breaks = PackedInt32Array()
		return

	_fitted_size = _fit_font_size()
	_shape(_fitted_size)
	_breaks = _ts.shaped_text_get_character_breaks(_line.get_rid())
	_measure_boxes()

func _shape(font_size: int) -> void:
	_line.clear()
	_line.direction = Loc.text_direction()
	_line.orientation = TextServer.ORIENTATION_HORIZONTAL
	_line.width = -1.0
	_line.add_string(text, _font, font_size, Loc.locale)

## Largest size that fits the control's width, preferring big, finger-sized
## targets over decorative typography.
func _fit_font_size() -> int:
	var available := size.x
	if available <= 1.0:
		return max_font_size
	var low := min_font_size
	var high := max_font_size
	var best := min_font_size
	while low <= high:
		var mid := (low + high) / 2
		_shape(mid)
		if _line.get_size().x <= available:
			best = mid
			low = mid + 1
		else:
			high = mid - 1
	return best

func _measure_boxes() -> void:
	var rid := _line.get_rid()
	var line_size := _line.get_size()
	var origin := _text_origin(line_size)
	var count := _breaks.size()
	var start := 0
	for i in range(count):
		var end: int = _breaks[i]
		var ranges := _ts.shaped_text_get_selection(rid, start, end)
		var box := Rect2(origin.x, origin.y, 0.0, line_size.y)
		if not ranges.is_empty():
			var x0: float = ranges[0].x
			var x1: float = ranges[0].y
			for r in ranges:
				x0 = minf(x0, minf(r.x, r.y))
				x1 = maxf(x1, maxf(r.x, r.y))
			box = Rect2(origin.x + x0, origin.y, x1 - x0, line_size.y)
		_boxes.append(box)
		start = end

func _text_origin(line_size: Vector2) -> Vector2:
	var block_height := line_size.y
	if show_slots and per_character:
		block_height += SLOT_GAP + SLOT_HEIGHT
	return Vector2((size.x - line_size.x) * 0.5, maxf((size.y - block_height) * 0.5, 0.0))

func _draw() -> void:
	_ensure_shaped()
	if _font == null or text.is_empty():
		return
	var line_size := _line.get_size()
	var origin := _text_origin(line_size)

	for index in _highlights:
		if index >= 0 and index < _boxes.size():
			var box: Rect2 = _boxes[index]
			draw_rect(box.grow(6.0), _highlights[index], true)

	if show_slots and per_character:
		var y := origin.y + line_size.y + SLOT_GAP
		for box in _boxes:
			var w: float = maxf(box.size.x - 4.0, 8.0)
			draw_rect(Rect2(box.position.x + 2.0, y, w, SLOT_HEIGHT), slot_color, true)

	# TextLine.draw takes the top-left of the line box, not the baseline.
	_line.draw(get_canvas_item(), origin, font_color)

## Reserves height for the largest size the strip could be drawn at, plus the
## slot guides. Measuring at [member max_font_size] rather than at the fitted
## size keeps this independent of the control's own width, so it cannot feed
## back into the layout pass that decides that width.
func _get_minimum_size() -> Vector2:
	if _font == null or text.is_empty():
		return Vector2(0, float(min_font_size) * 1.4)
	var probe := TextLine.new()
	probe.direction = Loc.text_direction()
	probe.add_string(text, _font, max_font_size, Loc.locale)
	var height := probe.get_size().y
	if show_slots and per_character:
		height += SLOT_GAP + SLOT_HEIGHT
	return Vector2(0, height)

func _gui_input(event: InputEvent) -> void:
	if not (event is InputEventMouseButton and event.pressed \
			and event.button_index == MOUSE_BUTTON_LEFT):
		return
	accept_event()
	if not per_character:
		strip_tapped.emit()
		return
	var index := grapheme_at(event.position)
	if index >= 0:
		character_tapped.emit(index)
	else:
		strip_tapped.emit()

## Resolves a local point to a grapheme, snapping to the nearest one horizontally
## so a slightly-off finger still hits the letter the player aimed at.
func grapheme_at(local_point: Vector2) -> int:
	_ensure_shaped()
	if _boxes.is_empty():
		return -1
	for i in range(_boxes.size()):
		if _boxes[i].has_point(Vector2(local_point.x, _boxes[i].position.y + 1.0)):
			return i
	var best := -1
	var best_distance := INF
	for i in range(_boxes.size()):
		var center: float = _boxes[i].position.x + _boxes[i].size.x * 0.5
		var d: float = absf(center - local_point.x)
		if d < best_distance:
			best_distance = d
			best = i
	# Beyond half a target width past the end of the word, treat it as a miss.
	if best >= 0 and best_distance > maxf(_boxes[best].size.x, MIN_TAP_WIDTH):
		return -1
	return best
