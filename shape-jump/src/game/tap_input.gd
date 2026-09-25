class_name TapInput
extends Node
## Turns raw input into game intents: one [signal tapped] per player tap and
## [signal pause_requested]. Keeps device quirks out of the game logic:
##
## - Godot turns only the first finger into mouse clicks (which drive the
##   "jump" action and the GUI). A tap by another finger while one rests on
##   the screen arrives only as a raw touch, so those are handled here.
## - One tap can therefore reach us twice in the same frame (raw touch plus
##   emulated click); only the first counts, or the tap that starts a run
##   would also jump.
## Runs in _unhandled_input, so taps on buttons never count as taps.

signal tapped
signal pause_requested

var _last_tap_frame := -1


func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed(&"pause"):
		get_viewport().set_input_as_handled()
		pause_requested.emit()
	elif event.is_action_pressed(&"jump") or _is_extra_finger_tap(event):
		get_viewport().set_input_as_handled()
		var frame := Engine.get_physics_frames()
		if frame != _last_tap_frame:
			_last_tap_frame = frame
			tapped.emit()


static func _is_extra_finger_tap(event: InputEvent) -> bool:
	var touch := event as InputEventScreenTouch
	return touch != null and touch.pressed and touch.index > 0
