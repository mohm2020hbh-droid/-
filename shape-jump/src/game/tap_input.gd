class_name TapInput
extends Node
## Turns raw input into game intents: one [signal tapped] per player tap and
## [signal pause_requested]. Keeps device quirks out of the game logic:
##
## - Every finger that touches the screen is one tap, whichever finger it is
##   and however many already rest on the screen. The raw touch is the only
##   thing counted: the mouse click Godot emulates from a touch is ignored,
##   so a tap can never count twice (the double jump depends on that).
## - Keyboard and a real mouse count through the "jump" action.
## Runs in _unhandled_input: the GUI consumes touches and clicks on buttons
## (pause, menus) first, so pressing a button is never a jump.

signal tapped
signal pause_requested


func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed(&"pause"):
		get_viewport().set_input_as_handled()
		pause_requested.emit()
	elif is_tap(event):
		get_viewport().set_input_as_handled()
		tapped.emit()


static func is_tap(event: InputEvent) -> bool:
	var touch := event as InputEventScreenTouch
	if touch:
		return touch.pressed
	if event is InputEventMouseButton and event.device == InputEvent.DEVICE_ID_EMULATION:
		return false  # A touch already counted above.
	return event.is_action_pressed(&"jump")
