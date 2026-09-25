class_name LevelData
extends Resource
## Per-level settings that are not geometry (docs/GDD.md §8.4).

@export var id: StringName = &"level_01"
@export var display_name := "Awakening"
## Run speed multiplier for this level (difficulty knob).
@export_range(0.5, 2.0, 0.05) var speed_scale := 1.0
