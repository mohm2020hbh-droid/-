class_name DeathBanner
extends Control
## Shown for the short moment between a death and the respawn: the attempt
## number, the score reached and the best, and the level progress before and
## after the death penalty. It sits above the screen fade, so
## it stays readable while the view goes dark, and never blocks input.

@onready var _attempt_label: Label = %AttemptLabel
@onready var _score_label: Label = %ScoreLabel
@onready var _progress_label: Label = %ProgressLabel

var _tween: Tween


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	hide()


func show_death(attempt: int, score: int, best: int, progress_before := 0.0, progress_after := 0.0) -> void:
	_attempt_label.text = "ATTEMPT %d" % attempt
	# ASCII only: the UI font has no arrow glyph.
	_progress_label.text = "PROGRESS %d%% - %d = %d%%" % [floori(progress_before),
		roundi(progress_before - progress_after), floori(progress_after)]
	_score_label.text = "%s     BEST %s" % [UiFormat.thousands(score), UiFormat.thousands(maxi(score, best))]
	_fade(1.0, 0.1)
	show()


func hide_banner() -> void:
	if visible:
		_fade(0.0, 0.15)


func _fade(alpha: float, duration: float) -> void:
	if _tween and _tween.is_valid():
		_tween.kill()
	_tween = create_tween()
	_tween.tween_property(self, ^"modulate:a", alpha, duration)
	if alpha <= 0.0:
		_tween.tween_callback(hide)
