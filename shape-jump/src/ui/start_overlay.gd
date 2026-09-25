class_name StartOverlay
extends Control
## Pre-run screen: title, level name, best score and a pulsing "TAP TO START".
## It ignores input; the tap itself is handled by GameSession.

var _time := 0.0

@onready var _level_label: Label = %LevelLabel
@onready var _best_label: Label = %BestLabel
@onready var _tap_label: Label = %TapLabel


func _process(delta: float) -> void:
	_time += delta
	_tap_label.modulate.a = 0.55 + 0.45 * (0.5 + 0.5 * sin(_time * 3.5))


func open(level_name: String, best_score: int) -> void:
	_level_label.text = level_name.to_upper()
	_best_label.text = "BEST  %d" % best_score if best_score > 0 else ""
	_time = 0.0
	show()


func close() -> void:
	hide()
