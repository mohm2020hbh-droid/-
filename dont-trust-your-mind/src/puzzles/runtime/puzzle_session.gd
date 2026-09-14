class_name PuzzleSession
extends RefCounted

## Runs one puzzle. Deliberately free of nodes, scenes and rendering so the whole
## rule system can be exercised headlessly by the test suite.
##
## The board feeds it taps and frame deltas; it answers with signals.

signal solved(result: PuzzleResult)
signal failed(result: PuzzleResult)
signal wrong_tap(target: String, note: String)
signal neutral_tap(target: String)
signal sequence_advanced(progress: int, total: int)
signal sequence_reset()
signal element_revealed(id: StringName)
signal element_hidden(id: StringName)
signal element_changed(id: StringName, changes: Dictionary)
signal countdown_changed(seconds_left: float)
signal hint_revealed(level: int, text: String)

const EPSILON := 0.0001

var puzzle: PuzzleDefinition
var elapsed: float = 0.0
var wrong_attempts: int = 0
var hints_used: int = 0
var finished: bool = false

var _seq_progress: int = 0
var _hold_target: String = ""
var _hold_time: float = 0.0
var _no_tap_since: float = 0.0
var _visible: Dictionary = {}       ## StringName -> bool
var _pending_reveal: Array = []     ## [{id, at}]
var _pending_hide: Array = []
var _result: PuzzleResult

func _init(p: PuzzleDefinition) -> void:
	puzzle = p
	_result = PuzzleResult.new()
	_result.puzzle_id = p.id
	_result.stage_index = p.index
	for e in p.elements:
		_visible[e.id] = not e.starts_hidden
		if e.reveal_after >= 0.0:
			_pending_reveal.append({"id": e.id, "at": e.reveal_after})
		if e.hide_after >= 0.0:
			_pending_hide.append({"id": e.id, "at": e.hide_after})

func is_visible(eid: StringName) -> bool:
	return _visible.get(eid, true)

func sequence_progress() -> int:
	return _seq_progress

func seconds_left() -> float:
	if puzzle.time_limit <= 0.0:
		return -1.0
	return maxf(0.0, puzzle.time_limit - elapsed)

## Seconds still to survive for a NO_TAP puzzle; -1 when the rule is not NO_TAP.
func no_tap_left() -> float:
	if puzzle.solution.kind != SolutionRule.Kind.NO_TAP:
		return -1.0
	return maxf(0.0, puzzle.solution.duration - (elapsed - _no_tap_since))

func tick(delta: float) -> void:
	if finished:
		return
	elapsed += delta
	_process_schedules()

	if puzzle.solution.kind == SolutionRule.Kind.NO_TAP:
		countdown_changed.emit(no_tap_left())
		if elapsed - _no_tap_since >= puzzle.solution.duration - EPSILON:
			_finish_solved()
			return
	elif puzzle.time_limit > 0.0:
		countdown_changed.emit(seconds_left())
		if elapsed >= puzzle.time_limit:
			_finish_failed(PuzzleResult.Status.FAILED_OUT_OF_TIME)
			return

	if puzzle.solution.kind == SolutionRule.Kind.HOLD and _hold_target != "":
		_hold_time += delta
		if _hold_time >= puzzle.solution.duration - EPSILON:
			_finish_solved()

func _process_schedules() -> void:
	for i in range(_pending_reveal.size() - 1, -1, -1):
		if elapsed >= _pending_reveal[i]["at"]:
			var id: StringName = _pending_reveal[i]["id"]
			_visible[id] = true
			element_revealed.emit(id)
			_pending_reveal.remove_at(i)
	for i in range(_pending_hide.size() - 1, -1, -1):
		if elapsed >= _pending_hide[i]["at"]:
			var id: StringName = _pending_hide[i]["id"]
			_visible[id] = false
			element_hidden.emit(id)
			_pending_hide.remove_at(i)

## Main entry point from the board. [param target] uses SolutionRule addressing.
func tap(target: String) -> void:
	if finished:
		return
	var rule := puzzle.solution
	_apply_tap_effects(target)

	match rule.kind:
		SolutionRule.Kind.NO_TAP:
			_register_wrong(target)
			_no_tap_since = elapsed
			sequence_reset.emit()
		SolutionRule.Kind.TAP:
			if rule.accepts(target) and _within_window():
				_finish_solved()
			else:
				_register_wrong(target)
		SolutionRule.Kind.SEQUENCE:
			_handle_sequence_tap(target)
		SolutionRule.Kind.HOLD:
			# A tap that never became a hold is simply too short.
			if target != rule.target:
				_register_wrong(target)

func press_begin(target: String) -> void:
	if finished or puzzle.solution.kind != SolutionRule.Kind.HOLD:
		return
	if target == puzzle.solution.target:
		_hold_target = target
		_hold_time = 0.0

func press_end(target: String) -> void:
	if finished or puzzle.solution.kind != SolutionRule.Kind.HOLD:
		return
	if _hold_target == target:
		_hold_target = ""
		_hold_time = 0.0

func use_hint() -> String:
	if not puzzle.allow_hints or hints_used >= puzzle.hints.size():
		return ""
	var text := puzzle.hints[hints_used]
	hints_used += 1
	hint_revealed.emit(hints_used, text)
	return text

func skip() -> void:
	if finished:
		return
	_result.status = PuzzleResult.Status.SKIPPED
	_result.elapsed = elapsed
	_result.wrong_attempts = wrong_attempts
	_result.hints_used = hints_used
	_result.stars = 0
	_result.points = 0
	finished = true
	failed.emit(_result)

func _handle_sequence_tap(target: String) -> void:
	var rule := puzzle.solution
	if _seq_progress < rule.targets.size() and target == rule.targets[_seq_progress]:
		_seq_progress += 1
		sequence_advanced.emit(_seq_progress, rule.targets.size())
		if _seq_progress >= rule.targets.size():
			if _within_window():
				_finish_solved()
			else:
				_seq_progress = 0
				_register_wrong(target)
				sequence_reset.emit()
		return
	_register_wrong(target)
	if rule.reset_on_wrong and _seq_progress > 0:
		_seq_progress = 0
		sequence_reset.emit()

func _within_window() -> bool:
	var rule := puzzle.solution
	if elapsed < rule.not_before:
		return false
	if rule.not_after > 0.0 and elapsed > rule.not_after:
		return false
	return true

func _apply_tap_effects(target: String) -> void:
	var eid := StringName(_element_id_of(target))
	var e := puzzle.element_by_id(eid)
	if e == null or e.on_tap.is_empty():
		return
	for id in e.on_tap.get("reveal", []):
		_visible[StringName(id)] = true
		element_revealed.emit(StringName(id))
	for id in e.on_tap.get("hide", []):
		_visible[StringName(id)] = false
		element_hidden.emit(StringName(id))
	var changes: Dictionary = e.on_tap.get("set", {})
	for id in changes:
		element_changed.emit(StringName(id), changes[id])

## "char:word:2" addresses a character inside element "word".
static func _element_id_of(target: String) -> String:
	if target.begins_with("char:"):
		var parts := target.split(":")
		return parts[1] if parts.size() > 2 else ""
	if target.begins_with("ui:"):
		return ""
	return target

func _register_wrong(target: String) -> void:
	wrong_attempts += 1
	wrong_tap.emit(target, str(puzzle.wrong_notes.get(target, "")))
	if puzzle.max_attempts > 0 and wrong_attempts >= puzzle.max_attempts:
		_finish_failed(PuzzleResult.Status.FAILED_OUT_OF_ATTEMPTS)

func _finish_solved() -> void:
	finished = true
	_result.status = PuzzleResult.Status.SOLVED
	_result.elapsed = elapsed
	_result.wrong_attempts = wrong_attempts
	_result.hints_used = hints_used
	_result.stars = PuzzleResult.grade(puzzle, elapsed, wrong_attempts, hints_used)
	_result.points = puzzle.reward * _result.stars
	solved.emit(_result)

func _finish_failed(status: PuzzleResult.Status) -> void:
	finished = true
	_result.status = status
	_result.elapsed = elapsed
	_result.wrong_attempts = wrong_attempts
	_result.hints_used = hints_used
	_result.stars = 0
	_result.points = 0
	failed.emit(_result)
