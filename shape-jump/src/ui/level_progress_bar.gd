class_name LevelProgressBar
extends Control
## Thin level progress bar for the HUD (docs/GDD.md §12): a dark track, a
## neon fill, a tick for each checkpoint (lit once reached, with a short
## flash), and the percentage in white on the right. The shown value eases
## toward the real one, so a penalty or a respawn never snaps.

const TRACK_HEIGHT := 4.0
const LABEL_WIDTH := 64.0

var _target := 0.0
var _shown := 0.0
var _marks: Array[float] = []
var _reached: Array[bool] = []
var _flash := 0.0
var _flash_index := -1

@onready var _label: Label = %ProgressLabel


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE


## Checkpoint positions (percent) for this level; none reached yet.
func set_marks(marks: Array[float]) -> void:
	_marks = marks.duplicate()
	_reached.clear()
	_reached.resize(_marks.size())
	_reached.fill(false)
	queue_redraw()


func mark_reached(index: int) -> void:
	if index >= 0 and index < _reached.size() and not _reached[index]:
		_reached[index] = true
		_flash = 1.0
		_flash_index = index


## [param snap] shows the value at once (a new level), otherwise it eases in.
func set_value(percent: float, snap := false) -> void:
	_target = clampf(percent, 0.0, 100.0)
	if snap:
		_shown = _target
		_label.text = "%d%%" % floori(_shown)
		queue_redraw()


func get_shown_value() -> float:
	return _shown


func _process(delta: float) -> void:
	if not is_equal_approx(_shown, _target):
		_shown = move_toward(_shown, _target, maxf(absf(_target - _shown) * 8.0, 20.0) * delta)
		_label.text = "%d%%" % floori(_shown)
		queue_redraw()
	if _flash > 0.0:
		_flash = maxf(_flash - delta * 1.6, 0.0)
		queue_redraw()


func _draw() -> void:
	var width := size.x - LABEL_WIDTH
	var y := size.y - TRACK_HEIGHT * 0.5 - 6.0
	draw_line(Vector2(0, y), Vector2(width, y), Color(Palette.UI_MUTED, 0.22), TRACK_HEIGHT)
	var fill := width * _shown / 100.0
	if fill > 0.5:
		Neon.line(self, Vector2(0, y), Vector2(fill, y), Palette.NEON, TRACK_HEIGHT, 0.8)
	for i in _marks.size():
		var x := width * _marks[i] / 100.0
		var lit := _reached[i]
		var color := Palette.UI_TEXT if lit else Color(Palette.UI_MUTED, 0.7)
		draw_line(Vector2(x, y - 7.0), Vector2(x, y + 7.0), color, 2.0)
		if i == _flash_index and _flash > 0.0:
			Neon.soft_light(self, Vector2(x, y), 22.0, Color(Palette.NEON, _flash * 0.8))
