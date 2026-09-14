class_name PuzzleResult
extends RefCounted

## Outcome of one attempt at a puzzle.

enum Status { SOLVED, FAILED_OUT_OF_TIME, FAILED_OUT_OF_ATTEMPTS, SKIPPED, ABANDONED }

var puzzle_id: StringName
var stage_index: int = 0
var status: Status = Status.ABANDONED
var elapsed: float = 0.0
var wrong_attempts: int = 0
var hints_used: int = 0
var stars: int = 0
var points: int = 0

func solved() -> bool:
	return status == Status.SOLVED

## Stars reward clean, fast solving without punishing experimentation too hard:
## 3 = first try, no hints, inside par. 2 = a stumble or one hint. 1 = got there.
static func grade(p: PuzzleDefinition, elapsed_s: float, wrong: int, hints: int) -> int:
	if wrong == 0 and hints == 0 and elapsed_s <= p.par_time:
		return 3
	if wrong <= 2 and hints <= 1:
		return 2
	return 1
