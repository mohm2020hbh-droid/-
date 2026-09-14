extends TestSuite

## Plays every puzzle, in both languages, using only its own declared solution.
##
## This is the test that answers "is the game actually completable". It also
## enforces the fairness rule that matters most: at the moment the player is
## required to tap a target, that target must be visible on screen. A puzzle
## whose answer is hidden when it must be pressed would pass every static check
## and be impossible to play.

const STEP := 0.05
const MAX_SECONDS := 30.0

func suite_name() -> String:
	return "playthrough (every puzzle, both languages)"

func run() -> void:
	var original := Loc.locale
	for loc in Loc.SUPPORTED:
		Loc.set_locale(loc)
		for stage in range(1, Puzzles.total_stages() + 1):
			_play(Puzzles.get_puzzle(stage, loc), "stage %d [%s]" % [stage, loc])
		for i in range(Puzzles.raw_daily().size()):
			_play(PuzzleDefinition.from_dict(Puzzles.raw_daily()[i], loc, 0),
					"daily %d [%s]" % [i, loc])
	Loc.set_locale(original)

func _play(puzzle: PuzzleDefinition, label: String) -> void:
	if puzzle == null:
		failures.append("%s: missing puzzle" % label)
		assertions += 1
		return
	var session := PuzzleSession.new(puzzle)
	var rule := puzzle.solution

	match rule.kind:
		SolutionRule.Kind.NO_TAP:
			_advance(session, rule.duration + STEP * 2.0)
		SolutionRule.Kind.HOLD:
			_wait_until_visible(session, rule.target, label)
			session.press_begin(rule.target)
			_advance(session, rule.duration + STEP * 2.0)
		SolutionRule.Kind.TAP:
			if rule.not_before > 0.0:
				_advance(session, rule.not_before + STEP)
			_wait_until_visible(session, rule.target, label)
			session.tap(rule.target)
		SolutionRule.Kind.SEQUENCE:
			if rule.not_before > 0.0:
				_advance(session, rule.not_before + STEP)
			for target in rule.targets:
				_wait_until_visible(session, target, label)
				session.tap(target)

	check(session.finished, "%s: the declared solution finishes the puzzle" % label)
	equal(session.wrong_attempts, 0,
			"%s: playing the solution costs no wrong attempts" % label)
	check(session.elapsed <= MAX_SECONDS,
			"%s: the solution is reachable within %d seconds" % [label, MAX_SECONDS])

	if session.finished and rule.kind != SolutionRule.Kind.NO_TAP:
		_check_star_reachable(puzzle, session, label)

## Advances the clock in small steps so scheduled reveals and hides fire in order.
func _advance(session: PuzzleSession, seconds: float) -> void:
	var remaining := seconds
	while remaining > 0.0 and not session.finished:
		session.tick(minf(STEP, remaining))
		remaining -= STEP

## Waits for a target to become visible, and fails if it never does.
func _wait_until_visible(session: PuzzleSession, target: String, label: String) -> void:
	var id := _element_of(target)
	if id == &"" or session.puzzle.element_by_id(id) == null:
		return  # Chrome and board taps are always available.
	var waited := 0.0
	while not session.is_visible(id) and waited < MAX_SECONDS and not session.finished:
		session.tick(STEP)
		waited += STEP
	check(session.is_visible(id),
			"%s: target is on screen when it must be tapped: %s" % [label, target])

## A puzzle nobody can three-star is a scoring bug, not a difficulty choice.
func _check_star_reachable(puzzle: PuzzleDefinition, session: PuzzleSession,
		label: String) -> void:
	var stars := PuzzleResult.grade(puzzle, session.elapsed, 0, 0)
	equal(stars, 3, "%s: a clean run earns three stars (par %.0fs, took %.1fs)"
			% [label, puzzle.par_time, session.elapsed])

static func _element_of(target: String) -> StringName:
	if target.begins_with("char:"):
		var parts := target.split(":")
		return StringName(parts[1]) if parts.size() > 2 else &""
	if target.begins_with("ui:") or target == "board":
		return &""
	return StringName(target)
