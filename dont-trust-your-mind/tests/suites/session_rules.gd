extends TestSuite

## Exercises every solution rule against the real puzzle engine, plus the
## scoring and attempt bookkeeping that hangs off it.

## GDScript lambdas capture locals by value, so signal results are collected
## through a field rather than a local.
var _outcome: PuzzleResult

func suite_name() -> String:
	return "session rules"

func _capture(session: PuzzleSession) -> void:
	_outcome = null
	session.failed.connect(func(r: PuzzleResult) -> void: _outcome = r)

func run() -> void:
	_test_tap()
	_test_alternatives()
	_test_sequence()
	_test_no_tap()
	_test_hold()
	_test_windows()
	_test_reveal_schedule()
	_test_on_tap_effects()
	_test_attempt_limit()
	_test_time_limit()
	_test_stars()
	_test_hints()
	_test_skip()

func _puzzle(spec: Dictionary, loc := "en") -> PuzzleDefinition:
	var raw := {"id": "t", "chapter": 1, "par_time": 20.0, "locales": {loc: spec}}
	for key in ["time_limit", "max_attempts", "par_time", "reward"]:
		if spec.has(key):
			raw[key] = spec[key]
	return PuzzleDefinition.from_dict(raw, loc, 1)

func _simple(solution: Dictionary, elements: Array = []) -> PuzzleSession:
	var els = elements if not elements.is_empty() else [
		{"id": "a", "kind": "shape"}, {"id": "b", "kind": "shape"},
	]
	return PuzzleSession.new(_puzzle({
		"instruction": "i", "explanation": "e", "elements": els, "solution": solution,
	}))

func _test_tap() -> void:
	var s := _simple({"kind": "tap", "target": "a"})
	s.tap("b")
	check(not s.finished, "wrong tap does not finish the puzzle")
	equal(s.wrong_attempts, 1, "wrong tap is counted")
	s.tap("a")
	check(s.finished, "correct tap finishes the puzzle")

func _test_alternatives() -> void:
	var s := _simple({"kind": "tap", "target": "a", "alternatives": ["b"]})
	s.tap("b")
	check(s.finished, "an alternative answer is accepted")
	equal(s.wrong_attempts, 0, "an alternative answer costs no attempt")

func _test_sequence() -> void:
	var s := _simple({"kind": "sequence", "targets": ["a", "b", "a"]},
			[{"id": "a", "kind": "shape"}, {"id": "b", "kind": "shape"},
			 {"id": "c", "kind": "shape"}])
	s.tap("a")
	s.tap("b")
	equal(s.sequence_progress(), 2, "sequence advances on correct taps")
	s.tap("c")
	equal(s.sequence_progress(), 0, "a wrong tap resets the sequence")
	s.tap("a")
	s.tap("b")
	s.tap("a")
	check(s.finished, "completing the sequence solves the puzzle")

	var strict := _simple({"kind": "sequence", "targets": ["a", "b"], "reset_on_wrong": false},
			[{"id": "a", "kind": "shape"}, {"id": "b", "kind": "shape"},
			 {"id": "c", "kind": "shape"}])
	strict.tap("a")
	strict.tap("c")
	equal(strict.sequence_progress(), 1, "reset_on_wrong false keeps progress")

func _test_no_tap() -> void:
	var s := _simple({"kind": "no_tap", "duration": 3.0})
	s.tick(1.0)
	check(not s.finished, "waiting is not instantly complete")
	s.tap("a")
	equal(s.wrong_attempts, 1, "tapping during a wait puzzle is wrong")
	s.tick(2.5)
	check(not s.finished, "the wait restarts after a tap")
	s.tick(0.6)
	check(s.finished, "surviving the full duration solves it")

func _test_hold() -> void:
	var s := _simple({"kind": "hold", "target": "a", "duration": 2.0})
	s.press_begin("a")
	s.tick(1.0)
	s.press_end("a")
	s.tick(1.5)
	check(not s.finished, "releasing early does not solve a hold")
	s.press_begin("a")
	s.tick(2.1)
	check(s.finished, "holding long enough solves it")

	var wrong := _simple({"kind": "hold", "target": "a", "duration": 1.0})
	wrong.press_begin("b")
	wrong.tick(1.5)
	check(not wrong.finished, "holding the wrong target does nothing")

func _test_windows() -> void:
	var early := _simple({"kind": "tap", "target": "a", "not_before": 2.0})
	early.tick(1.0)
	early.tap("a")
	check(not early.finished, "tapping before the window opens is wrong")
	equal(early.wrong_attempts, 1, "an early tap is counted")
	early.tick(1.5)
	early.tap("a")
	check(early.finished, "tapping inside the window solves it")

	var late := _simple({"kind": "tap", "target": "a", "not_after": 2.0})
	late.tick(3.0)
	late.tap("a")
	check(not late.finished, "tapping after the window closes is wrong")

func _test_reveal_schedule() -> void:
	var s := _simple({"kind": "tap", "target": "late"},
			[{"id": "late", "kind": "shape", "reveal_after": 2.0},
			 {"id": "early", "kind": "shape", "hide_after": 1.0}])
	check(not s.is_visible(&"late"), "a scheduled element starts hidden")
	check(s.is_visible(&"early"), "an unscheduled element starts visible")
	s.tick(1.1)
	check(not s.is_visible(&"early"), "hide_after hides the element")
	s.tick(1.0)
	check(s.is_visible(&"late"), "reveal_after shows the element")

func _test_on_tap_effects() -> void:
	var s := _simple({"kind": "sequence", "targets": ["a", "hidden"]},
			[{"id": "a", "kind": "shape", "on_tap": {"reveal": ["hidden"]}},
			 {"id": "hidden", "kind": "shape", "starts_hidden": true}])
	check(not s.is_visible(&"hidden"), "the second step starts hidden")
	s.tap("a")
	check(s.is_visible(&"hidden"), "tapping the first step reveals the second")
	s.tap("hidden")
	check(s.finished, "the revealed element completes the sequence")

func _test_attempt_limit() -> void:
	var s := PuzzleSession.new(_puzzle({
		"instruction": "i", "explanation": "e", "max_attempts": 2,
		"elements": [{"id": "a", "kind": "shape"}, {"id": "b", "kind": "shape"}],
		"solution": {"kind": "tap", "target": "a"},
	}))
	_capture(s)
	s.tap("b")
	check(not s.finished, "one wrong tap is survivable with two allowed")
	s.tap("b")
	check(s.finished, "running out of attempts ends the puzzle")
	check(_outcome != null and _outcome.status == PuzzleResult.Status.FAILED_OUT_OF_ATTEMPTS,
			"the failure reason is attempts")

func _test_time_limit() -> void:
	var s := PuzzleSession.new(_puzzle({
		"instruction": "i", "explanation": "e", "time_limit": 3.0,
		"elements": [{"id": "a", "kind": "shape"}],
		"solution": {"kind": "tap", "target": "a"},
	}))
	_capture(s)
	s.tick(2.0)
	near(s.seconds_left(), 1.0, "the clock counts down")
	s.tick(1.2)
	check(_outcome != null and _outcome.status == PuzzleResult.Status.FAILED_OUT_OF_TIME,
			"running out of time ends the puzzle")

func _test_stars() -> void:
	var p := _puzzle({"instruction": "i", "explanation": "e", "par_time": 10.0,
			"elements": [{"id": "a", "kind": "shape"}],
			"solution": {"kind": "tap", "target": "a"}})
	equal(PuzzleResult.grade(p, 5.0, 0, 0), 3, "clean and fast earns three stars")
	equal(PuzzleResult.grade(p, 20.0, 0, 0), 2, "slow but clean earns two")
	equal(PuzzleResult.grade(p, 5.0, 1, 0), 2, "one wrong tap earns two")
	equal(PuzzleResult.grade(p, 5.0, 0, 1), 2, "one hint earns two")
	equal(PuzzleResult.grade(p, 5.0, 3, 0), 1, "several wrong taps earn one")
	equal(PuzzleResult.grade(p, 5.0, 0, 2), 1, "both hints earn one")

func _test_hints() -> void:
	var s := PuzzleSession.new(_puzzle({
		"instruction": "i", "explanation": "e", "hints": ["first", "second"],
		"elements": [{"id": "a", "kind": "shape"}],
		"solution": {"kind": "tap", "target": "a"},
	}))
	equal(s.use_hint(), "first", "the first hint comes first")
	equal(s.use_hint(), "second", "the second hint follows")
	equal(s.use_hint(), "", "there is no third hint")
	equal(s.hints_used, 2, "hint usage is counted")

func _test_skip() -> void:
	var s := _simple({"kind": "tap", "target": "a"})
	_capture(s)
	s.skip()
	check(_outcome != null and _outcome.status == PuzzleResult.Status.SKIPPED,
			"skipping reports a skip")
	if _outcome != null:
		equal(_outcome.stars, 0, "skipping earns no stars")
		s.tap("a")
		check(_outcome.status == PuzzleResult.Status.SKIPPED,
				"a finished session ignores further taps")
