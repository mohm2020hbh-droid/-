class_name PauseMenu
extends Control
## Pause overlay: Resume / Restart. Runs while the tree is paused.

signal resume_pressed
signal restart_pressed

@onready var _resume: Button = %ResumeButton
@onready var _restart: Button = %RestartButton


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	_resume.pressed.connect(func() -> void: resume_pressed.emit())
	_restart.pressed.connect(func() -> void: restart_pressed.emit())


func _unhandled_input(event: InputEvent) -> void:
	if visible and event.is_action_pressed(&"pause"):
		get_viewport().set_input_as_handled()
		resume_pressed.emit()


func open() -> void:
	show()


func close() -> void:
	hide()
