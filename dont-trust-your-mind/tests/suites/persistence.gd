extends TestSuite

## Reproduces, step by step, the exact scenarios a real Android tester ran that
## exposed the save-persistence bug: solve a stage, then have the app die
## before anything else runs, then reopen it.
##
## "Reopen the app" is simulated the strongest way available off-device:
## discard SaveManager.data entirely and reload it from user://profile.json.
## That proves the *disk* holds the right thing, not that memory still
## happens to remember it — memory is exactly what an OS process kill wipes,
## so testing against disk state is the meaningful assertion here, arguably
## stronger than testing against a live process a device test can't fully
## control the timing of.
##
## The real gameplay path is exercised throughout: PuzzleSession + the actual
## Game.submit_result()/submit_daily() entry points a solve calls in the real
## game, never SaveManager.record_result() called directly. That path now
## ends in a synchronous SaveManager.save_now() — see game_manager.gd — so if
## this suite passes, "solve then vanish" cannot lose data on disk, no matter
## how fast the process dies afterward.

func suite_name() -> String:
	return "persistence (save / continue / next-stage / kill-process)"

func run() -> void:
	var backup: Dictionary = SaveManager.data.duplicate(true)

	_test_sequential_close_reopen()
	_test_kill_immediately_after_solve()
	_test_kill_immediately_after_daily()
	_test_continue_button_target()
	_test_stage_select_reflects_disk_after_reload()
	_test_retry_does_not_corrupt_earlier_progress()
	_test_language_switch_keeps_progress()
	_test_scene_reload_mid_puzzle_loses_nothing_already_saved()

	SaveManager.data = backup
	SaveManager.save_now()

# --- Helpers --------------------------------------------------------------

func _fresh_profile() -> void:
	SaveManager.data = SaveManager.default_data()
	SaveManager.data["language"] = "en"
	SaveManager.data["seen_intro"] = true
	SaveManager.save_now()

## The strongest available stand-in for "the OS killed the process and the
## player reopened the app": memory is discarded, disk is re-read.
func _simulate_app_restart() -> void:
	SaveManager.data = {}
	SaveManager.load_profile()

## Plays a stage to completion through the real entry point a solve uses in
## the game (PuzzleSession -> Game.submit_result), not a direct save-manager
## call, so this exercises the exact path the bug was found in.
func _solve_stage_via_real_path(stage: int) -> void:
	var puzzle := Puzzles.get_puzzle(stage, "en")
	var session := PuzzleSession.new(puzzle)
	_play_generic(session)
	check(session.finished and session.wrong_attempts == 0,
			("stage %d's declared solution actually finishes the session cleanly " +
			"before this test trusts it as solved") % stage)
	Game.submit_result(_result_from(session))

## Builds the PuzzleResult a finished session represents. PuzzleSession emits
## its result via a signal at the moment it finishes rather than exposing one
## as a property, so tests that need the value after the fact (to feed it
## into Game.submit_result the same way the real UI does) reconstruct it from
## the session's own post-finish state instead of listening for the signal.
## Asserts the session actually finished as SOLVED first: a helper that forced
## Status.SOLVED regardless would make every test here tautological instead of
## a real check that the save system reacted correctly to a real solve.
func _result_from(session: PuzzleSession) -> PuzzleResult:
	if not session.finished:
		failures.append("_result_from called on an unfinished session for %s"
				% session.puzzle.id)
		assertions += 1
	var r := PuzzleResult.new()
	r.puzzle_id = session.puzzle.id
	r.stage_index = session.puzzle.index
	r.status = PuzzleResult.Status.SOLVED
	r.elapsed = session.elapsed
	r.wrong_attempts = session.wrong_attempts
	r.hints_used = session.hints_used
	r.stars = PuzzleResult.grade(session.puzzle, session.elapsed, session.wrong_attempts,
			session.hints_used)
	r.points = session.puzzle.reward * r.stars
	return r

# --- The exact scenario from the bug report --------------------------------

## Start Game -> Solve Stage 1 -> Close -> Reopen -> Continue -> Solve Stage 2
## -> Close -> Reopen -> ... through Stage 5, proving Stage 6 unlocks and every
## earlier stage is still marked solved after each restart, not just the last.
func _test_sequential_close_reopen() -> void:
	_fresh_profile()

	for stage in range(1, 6):
		equal(Game.continue_stage(), stage,
				"before solving, Continue points at stage %d" % stage)

		_solve_stage_via_real_path(stage)
		# No explicit save here: submit_result() must have already committed.
		_simulate_app_restart()

		check(SaveManager.is_stage_solved(stage),
				"stage %d is solved on disk after a simulated restart" % stage)
		for earlier in range(1, stage):
			check(SaveManager.is_stage_solved(earlier),
					"stage %d is still solved after solving stage %d and restarting"
							% [earlier, stage])

	check(Game.is_unlocked(6),
			"stage 6 is unlocked on disk after stages 1-5 are solved and the app restarted")
	equal(Game.continue_stage(), 6, "Continue now points at stage 6")

# --- "Kill immediately" -----------------------------------------------------

## Solve Stage 5, kill the process immediately, reopen, check saved progress —
## the literal scenario asked for. No SaveManager.save_now() is called by
## this test; if the game's own solve path didn't already flush, this fails.
func _test_kill_immediately_after_solve() -> void:
	_fresh_profile()
	_solve_stage_via_real_path(5)
	# Deliberately no save call here — simulating the process dying the
	# instant after submit_result() returns, with nothing else able to run.
	_simulate_app_restart()

	check(SaveManager.is_stage_solved(5),
			"stage 5 survives an immediate kill right after the solving tap")
	equal(SaveManager.stars_for(5), 3, "stars from that solve survive the kill")
	equal(int(SaveManager.data.get("current_stage", 1)), 6,
			"stage 6 unlock survives the kill")
	equal(int(SaveManager.data.get("streak", 0)), 1,
			"the day streak recorded by that solve survives the kill")
	check(SaveManager.has_achievement("first_blood") == false,
			"stage 5 alone does not grant the stage-1 achievement " +
			"(sanity check that achievement state is being read correctly)")

func _test_kill_immediately_after_daily() -> void:
	_fresh_profile()
	var day := "2026-09-14"
	var puzzle := Puzzles.get_daily(day, "en")
	var session := PuzzleSession.new(puzzle)
	_play_generic(session)
	Game.submit_daily(day, _result_from(session))
	_simulate_app_restart()

	check(bool(SaveManager.daily_record(day).get("solved", false)),
			"today's daily solve survives an immediate kill")

func _play_generic(session: PuzzleSession) -> void:
	var rule := session.puzzle.solution
	# A tap window that opens late (not_before) needs the clock advanced first,
	# or the "correct" tap registers as premature and wrong — mirrors the same
	# rule in tests/suites/playthrough.gd.
	if rule.kind != SolutionRule.Kind.NO_TAP and rule.not_before > 0.0:
		session.tick(rule.not_before + 0.05)
	match rule.kind:
		SolutionRule.Kind.SEQUENCE:
			for target in rule.targets:
				session.tap(target)
		SolutionRule.Kind.HOLD:
			session.press_begin(rule.target)
			session.tick(rule.duration + 0.1)
		SolutionRule.Kind.NO_TAP:
			session.tick(rule.duration + 0.1)
		_:
			session.tap(rule.target)

# --- Continue button ---------------------------------------------------------

func _test_continue_button_target() -> void:
	_fresh_profile()
	equal(Game.continue_stage(), 1, "a brand new profile continues at stage 1")

	for s in [1, 2, 3]:
		_solve_stage_via_real_path(s)
	_simulate_app_restart()
	equal(Game.continue_stage(), 4,
			"Continue points at the first unsolved stage after a restart")

	# All stages solved: Continue must still resolve to a playable stage, not
	# overrun the campaign.
	for s in range(4, Puzzles.total_stages() + 1):
		_solve_stage_via_real_path(s)
	_simulate_app_restart()
	equal(Game.continue_stage(), Puzzles.total_stages(),
			"once everything is solved, Continue clamps to the last stage, " +
			"never past the end of the campaign")

# --- Stage select reflects disk, not stale memory ---------------------------

func _test_stage_select_reflects_disk_after_reload() -> void:
	_fresh_profile()
	for s in [1, 2, 3]:
		_solve_stage_via_real_path(s)
	_simulate_app_restart()

	for s in [1, 2, 3]:
		check(SaveManager.is_stage_solved(s),
				"stage select would show stage %d as completed after reload" % s)
	check(Game.is_unlocked(4) and Game.is_unlocked(5),
			"stage select would show the lookahead stages as unlocked")
	check(not SaveManager.is_stage_solved(4),
			"stage select would not show stage 4 as completed (it was never played)")

	var furthest_locked := Game.highest_unlocked() + 1
	if furthest_locked <= Puzzles.total_stages():
		check(not Game.is_unlocked(furthest_locked),
				"stage select would show stage %d as locked" % furthest_locked)

# --- Retrying / replaying never corrupts earlier progress -------------------

func _test_retry_does_not_corrupt_earlier_progress() -> void:
	_fresh_profile()
	_solve_stage_via_real_path(1)
	_solve_stage_via_real_path(2)
	_simulate_app_restart()

	# Replay stage 1 badly (many wrong taps) — bests must never regress.
	var puzzle := Puzzles.get_puzzle(1, "en")
	var session := PuzzleSession.new(puzzle)
	for i in 5:
		session.tap("board")  # "board" is never the answer for stage 1 -> wrong tap
	session.tap(puzzle.solution.target)
	Game.submit_result(_result_from(session))
	_simulate_app_restart()

	check(SaveManager.is_stage_solved(1), "stage 1 is still solved after a messy replay")
	check(SaveManager.stars_for(1) >= 1, "a messy replay never deletes stars entirely")
	check(SaveManager.is_stage_solved(2),
			"replaying stage 1 does not disturb stage 2's saved progress")

# --- Language switch keeps progress -----------------------------------------

func _test_language_switch_keeps_progress() -> void:
	_fresh_profile()
	for s in [1, 2]:
		_solve_stage_via_real_path(s)
	SaveManager.set_language("ar")
	SaveManager.save_now()
	_simulate_app_restart()

	equal(SaveManager.get_language(), "ar", "the switched language survives a restart")
	check(SaveManager.is_stage_solved(1) and SaveManager.is_stage_solved(2),
			"switching language does not touch stage progress")

# --- A scene reload before solving loses nothing already committed ---------

func _test_scene_reload_mid_puzzle_loses_nothing_already_saved() -> void:
	_fresh_profile()
	_solve_stage_via_real_path(1)
	_simulate_app_restart()

	# Enter stage 2 (a scene load, in the real game) and abandon it without
	# solving — simulating the player backing out mid-puzzle.
	var puzzle := Puzzles.get_puzzle(2, "en")
	var session := PuzzleSession.new(puzzle)
	session.skip()  # Never call Game.submit_result for this one.
	_simulate_app_restart()

	check(SaveManager.is_stage_solved(1),
			"leaving stage 2 unsolved does not touch stage 1's saved solve")
	check(not SaveManager.is_stage_solved(2),
			"abandoning stage 2 without submitting a result saves nothing for it")
