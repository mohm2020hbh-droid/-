extends "res://tests/integration/test_level_progress.gd"
## Progress, checkpoints, the death penalty, respawn and resume on World 04,
## where a checkpoint can hang from the ceiling and a respawn there puts the
## player back on the ceiling.


func world_index() -> int:
	return 3


## In World 04 a later tap can stand in for a skipped one (a latch one tap
## late, where the level allows it), so skipping a tap does not always kill:
## the deaths happen where that tap would have been.
func _force_deaths(route: PackedFloat32Array, percents: Array[float]) -> void:
	h.kill_at = []
	for p in percents:
		h.kill_at.append(route[_tap_after(route, p)])
