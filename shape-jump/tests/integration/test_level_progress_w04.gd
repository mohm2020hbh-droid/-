extends "res://tests/integration/test_level_progress.gd"
## Progress, checkpoints, the death penalty, respawn and resume on World 04,
## where a checkpoint can hang from the ceiling and a respawn there puts the
## player back on the ceiling.


func world_index() -> int:
	return 3
