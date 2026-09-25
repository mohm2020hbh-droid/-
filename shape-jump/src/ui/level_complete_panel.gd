class_name LevelCompletePanel
extends Control
## End-of-level results: score, best score, shards found, play again.

signal play_again_pressed

@onready var _score_label: Label = %ScoreLabel
@onready var _best_label: Label = %BestLabel
@onready var _shards_label: Label = %ShardsLabel
@onready var _again: Button = %PlayAgainButton


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	_again.pressed.connect(func() -> void: play_again_pressed.emit())


func open(score: int, best_score: int, is_new_best: bool, shards: int, total_shards: int) -> void:
	_score_label.text = Hud.format_int(score)
	_best_label.text = "NEW BEST!" if is_new_best else "BEST  %s" % Hud.format_int(best_score)
	_best_label.modulate = Palette.NEON if is_new_best else Palette.UI_MUTED
	_shards_label.text = "%d / %d" % [shards, total_shards]
	show()
	modulate.a = 0.0
	create_tween().tween_property(self, ^"modulate:a", 1.0, 0.35)


func close() -> void:
	hide()
