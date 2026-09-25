class_name Hud
extends CanvasLayer
## In-game HUD (docs/GDD.md §12): score top-left, shards + pause top-right.
## Nothing else, so the playfield stays clear. Respects the mobile safe area.

signal pause_pressed

const EDGE_MARGIN := 24

var _shards := 0
var _pop := 0.0

@onready var _root: MarginContainer = $Root
@onready var _score_label: Label = %ScoreLabel
@onready var _shard_label: Label = %ShardLabel
@onready var _shard_icon: Control = %ShardIcon
@onready var _pause_button: Button = %PauseButton


func _ready() -> void:
	_pause_button.pressed.connect(func() -> void: pause_pressed.emit())
	get_viewport().size_changed.connect(_apply_safe_area)
	_apply_safe_area()


func _process(delta: float) -> void:
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


func set_pause_enabled(enabled: bool) -> void:
	_pause_button.visible = enabled


func _apply_safe_area() -> void:
	var margins := Vector4(EDGE_MARGIN, EDGE_MARGIN, EDGE_MARGIN, EDGE_MARGIN)
	if OS.has_feature("mobile"):
		# Notches / rounded corners: convert the screen safe area into
		# viewport units and push the HUD inside it.
		var window := Vector2(DisplayServer.window_get_size())
		var safe := Rect2(DisplayServer.get_display_safe_area())
		var to_view := get_viewport().get_visible_rect().size / window
		margins.x += maxf(safe.position.x * to_view.x, 0.0)
		margins.y += maxf(safe.position.y * to_view.y, 0.0)
		margins.z += maxf((window.x - safe.end.x) * to_view.x, 0.0)
	_root.add_theme_constant_override(&"margin_left", int(margins.x))
	_root.add_theme_constant_override(&"margin_top", int(margins.y))
	_root.add_theme_constant_override(&"margin_right", int(margins.z))

