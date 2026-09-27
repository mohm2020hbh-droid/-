extends "res://tests/integration/test_world_01_playthrough.gd"
## The World 01 playthrough tests, on World 04: every route finishes (through
## every latch), the run is deterministic, and a respawn at each checkpoint
## keeps the timing and the surface of that checkpoint.


func world_index() -> int:
	return 3
