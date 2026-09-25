class_name LevelCompletePanel
extends Control
## End-of-level results: score, best, shards, attempts, what got unlocked,
## and the way on: NEXT LEVEL or RETRY (the level's start screen, which also
## lists every level). After the last level it is the world-complete screen.

signal next_pressed
signal retry_pressed

## What the panel shows; filled in by GameSession.
class Result:
	var title := "LEVEL COMPLETE"
	var score := 0
	var best := 0
	var is_new_best := false
	var shards := 0
	var total_shards := 0
	var attempts := 1
	var unlocked := ""
	var has_next := true

@onready var _title_label: Label = %TitleLabel
@onready var _score_label: Label = %ScoreLabel
@onready var _best_label: Label = %BestLabel
@onready var _shards_label: Label = %ShardsLabel
@onready var _attempts_label: Label = %AttemptsLabel
@onready var _unlock_label: Label = %UnlockLabel
@onready var _next: Button = %NextButton
@onready var _retry: Button = %RetryButton


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	_next.pressed.connect(func() -> void: next_pressed.emit())
	_retry.pressed.connect(func() -> void: retry_pressed.emit())


func open(result: Result) -> void:
	_title_label.text = result.title
	_score_label.text = UiFormat.thousands(result.score)
	_best_label.text = "NEW BEST!" if result.is_new_best else "BEST  %s" % UiFormat.thousands(result.best)
	_best_label.modulate = Palette.NEON if result.is_new_best else Palette.UI_MUTED
	_shards_label.text = "%d / %d" % [result.shards, result.total_shards]
	_attempts_label.text = "ATTEMPTS  %d" % result.attempts
	_unlock_label.text = result.unlocked
	_unlock_label.visible = not result.unlocked.is_empty()
	_next.visible = result.has_next
	show()
	modulate.a = 0.0
	create_tween().tween_property(self, ^"modulate:a", 1.0, 0.35)


func close() -> void:
	hide()
