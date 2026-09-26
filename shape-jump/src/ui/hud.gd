class_name Hud
extends CanvasLayer
## In-game HUD (docs/GDD.md §12): score top-left, shards + pause top-right,
## a thin level progress bar top-centre. Nothing else, so the playfield stays
## clear. Respects the mobile safe area.

signal pause_pressed

const EDGE_MARGIN := 24
## How often the safe area is re-read on mobile: flipping a landscape phone
## 180° moves the notch to the other side without changing the screen size.
const SAFE_AREA_POLL := 0.5

var _shards := 0
var _pop := 0.0
var _safe_area := Rect2i()
var _safe_area_timer := 0.0

@onready var _root: MarginContainer = $Root
@onready var _score_label: Label = %ScoreLabel
@onready var _shard_label: Label = %ShardLabel
@onready var _shard_icon: Control = %ShardIcon
@onready var _pause_button: Button = %PauseButton
@onready var _progress: LevelProgressBar = %Bar


func _ready() -> void:
	_pause_button.pressed.connect(func() -> void: pause_pressed.emit())
	get_viewport().size_changed.connect(_apply_safe_area)
	_apply_safe_area()


func _process(delta: float) -> void:
	if OS.has_feature("mobile"):
		_safe_area_timer -= delta
		if _safe_area_timer <= 0.0:
			_safe_area_timer = SAFE_AREA_POLL
			if DisplayServer.get_display_safe_area() != _safe_area:
				_apply_safe_area()
	if _pop > 0.0:
		_pop = maxf(_pop - delta * 4.0, 0.0)
		var s := 1.0 + 0.35 * _pop
		_shard_icon.scale = Vector2(s, s)


func set_score(score: int, shards: int) -> void:
	_score_label.text = UiFormat.thousands(score)
	if shards > _shards:
		_pop = 1.0  # Little bounce on the shard icon for each pickup.
	_shards = shards
	_shard_label.text = str(shards)


## Level progress in percent; [param snap] skips the easing (a new level).
func set_progress(percent: float, snap := false) -> void:
	_progress.set_value(percent, snap)


func set_progress_marks(marks: Array[float]) -> void:
	_progress.set_marks(marks)


func mark_checkpoint(index: int) -> void:
	_progress.mark_reached(index)


## Redraws what paints itself from the [Palette] (after a world change).
func refresh_look() -> void:
	_progress.queue_redraw()
	_shard_icon.queue_redraw()
	_pause_button.queue_redraw()


func set_pause_enabled(enabled: bool) -> void:
	_pause_button.visible = enabled


func _apply_safe_area() -> void:
	var margins := Vector4(EDGE_MARGIN, EDGE_MARGIN, EDGE_MARGIN, EDGE_MARGIN)
	if OS.has_feature("mobile"):
		# Notches / rounded corners: convert the screen safe area into
		# viewport units and push the HUD inside it.
		_safe_area = DisplayServer.get_display_safe_area()
		var window := Vector2(DisplayServer.window_get_size())
		var safe := Rect2(_safe_area)
		var to_view := get_viewport().get_visible_rect().size / window
		margins.x += maxf(safe.position.x * to_view.x, 0.0)
		margins.y += maxf(safe.position.y * to_view.y, 0.0)
		margins.z += maxf((window.x - safe.end.x) * to_view.x, 0.0)
	_root.add_theme_constant_override(&"margin_left", int(margins.x))
	_root.add_theme_constant_override(&"margin_top", int(margins.y))
	%Progress.offset_top = margins.y - 2.0
	%Progress.offset_bottom = margins.y + 46.0
	_root.add_theme_constant_override(&"margin_right", int(margins.z))

