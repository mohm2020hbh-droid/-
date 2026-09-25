class_name WorldData
extends Resource
## A world: an ordered list of levels played one after another
## (docs/GDD.md §8.5). Only the data; unlocking lives in [Progression].

@export var id: StringName = &"world_01"
@export var number := 1
@export var display_name := "The Red Void"
@export var levels: Array[LevelData] = []
## Shown when the world is completed (the next world is not part of this build).
@export var next_world_name := "World 02"


func get_level(index: int) -> LevelData:
	return levels[index] if index >= 0 and index < levels.size() else null


func index_of(level_id: StringName) -> int:
	for i in levels.size():
		if levels[i].id == level_id:
			return i
	return -1
