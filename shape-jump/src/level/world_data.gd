class_name WorldData
extends Resource
## A world: an ordered list of levels played one after another
## (docs/GDD.md §8.5). Only the data; unlocking lives in [Progression].

@export var id: StringName = &"world_01"
@export var number := 1
@export var display_name := "The Red Void"
@export var levels: Array[LevelData] = []
## Shown when the world is completed ("" when no world follows yet).
@export var next_world_name := "World 02"
## The world that must be completed before this one opens (none: open).
@export var requires: WorldData
## Look of the whole world: a [Palette] theme and a background scene.
@export var theme: StringName = &"red"
@export var background: PackedScene


func get_level(index: int) -> LevelData:
	return levels[index] if index >= 0 and index < levels.size() else null


func index_of(level_id: StringName) -> int:
	for i in levels.size():
		if levels[i].id == level_id:
			return i
	return -1
