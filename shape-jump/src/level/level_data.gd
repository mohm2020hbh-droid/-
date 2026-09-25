class_name LevelData
extends Resource
## Per-level settings that are not geometry (docs/GDD.md §8.4).

@export var id: StringName = &"w01_l01"
@export var display_name := "Awakening"
## One line under the name on the level card, e.g. "Hard tutorial".
@export var tagline := ""
@export_file("*.tscn") var scene_path := ""
## Run speed multiplier for this level (difficulty knob).
@export_range(0.5, 2.0, 0.005) var speed_scale := 1.0
