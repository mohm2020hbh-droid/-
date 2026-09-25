class_name SaveSandbox
extends RefCounted
## Points SaveSystem at a throwaway file for the duration of a test.

const PATH := "user://test_sandbox_save.cfg"

var _original_path: String


func enter() -> void:
	_original_path = SaveSystem.save_path
	SaveSystem.save_path = PATH
	DirAccess.remove_absolute(ProjectSettings.globalize_path(PATH))
	SaveSystem.load_from_disk()


func leave() -> void:
	DirAccess.remove_absolute(ProjectSettings.globalize_path(PATH))
	SaveSystem.save_path = _original_path
	SaveSystem.load_from_disk()
