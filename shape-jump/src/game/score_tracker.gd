class_name ScoreTracker
extends RefCounted
## Score rules (docs/GDD.md §11): points for furthest progress plus points
## per shard. Snapshots let a checkpoint respawn roll the score back.

signal changed(score: int, shards: int)

const POINTS_PER_TILE := 10
const POINTS_PER_SHARD := 100

var start_x := 0.0
## Furthest progress, in whole tiles from [member start_x].
var tiles := 0
var shards := 0


func reset(start: float) -> void:
	start_x = start
	tiles = 0
	shards = 0
	_emit()


func update_progress(x: float) -> void:
	var reached := int(floor((x - start_x) / GameConst.TILE))
	if reached > tiles:
		tiles = reached
		_emit()


func add_shard() -> void:
	shards += 1
	_emit()


func get_score() -> int:
	return tiles * POINTS_PER_TILE + shards * POINTS_PER_SHARD


func snapshot() -> Dictionary:
	return {"tiles": tiles, "shards": shards}


func restore(state: Dictionary) -> void:
	tiles = state.tiles
	shards = state.shards
	_emit()


func _emit() -> void:
	changed.emit(get_score(), shards)
