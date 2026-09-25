class_name SoundLibrary
extends Resource
## Maps sound ids to streams. Adding a sound = add a file + one entry here.
## Missing ids are allowed and simply stay silent.

@export var sounds: Dictionary[StringName, AudioStream] = {}


func get_stream(id: StringName) -> AudioStream:
	return sounds.get(id)
