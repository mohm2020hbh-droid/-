extends Node
## Persists per-level progress in a ConfigFile under user://.
##
## Layout (see docs/ARCHITECTURE.md §8):
##   [meta] version
##   [<level_id>] best_score, best_shards, completed

const SAVE_VERSION := 1
const DEFAULT_PATH := "user://save.cfg"

var save_path: String = DEFAULT_PATH
var _cfg := ConfigFile.new()


func _ready() -> void:
	load_from_disk()


func load_from_disk() -> void:
	_cfg = ConfigFile.new()
	var err := _cfg.load(save_path)
	if err != OK and err != ERR_FILE_NOT_FOUND:
		push_warning("SaveSystem: could not read %s (error %d); starting fresh." % [save_path, err])
		_cfg = ConfigFile.new()


func get_record(level_id: StringName) -> Dictionary:
	var section := String(level_id)
	return {
		"best_score": int(_cfg.get_value(section, "best_score", 0)),
		"best_shards": int(_cfg.get_value(section, "best_shards", 0)),
		"completed": bool(_cfg.get_value(section, "completed", false)),
	}


## Merges a finished run into the stored record and writes it to disk.
## Returns true when [param score] beats the previous best.
func record_result(level_id: StringName, score: int, shards: int, completed: bool) -> bool:
	var section := String(level_id)
	var previous := get_record(level_id)
	var is_new_best: bool = score > previous.best_score
	_cfg.set_value("meta", "version", SAVE_VERSION)
	_cfg.set_value(section, "best_score", maxi(score, previous.best_score))
	_cfg.set_value(section, "best_shards", maxi(shards, previous.best_shards))
	_cfg.set_value(section, "completed", completed or previous.completed)
	var err := _cfg.save(save_path)
	if err != OK:
		push_error("SaveSystem: could not write %s (error %d)." % [save_path, err])
	return is_new_best
