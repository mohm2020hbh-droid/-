extends "res://tests/integration/test_world_01_playthrough.gd"
## The World 01 playthrough tests, on World 03: every route finishes (through
## every gravity flip), the run is deterministic, respawns keep the timing and
## the gravity of their checkpoint.


func world_index() -> int:
	return 2
