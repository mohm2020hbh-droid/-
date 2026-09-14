extends TestSuite

## Save durability: round trips, corruption recovery, progression bookkeeping
## and the day-streak arithmetic.

const PROBE_PATH := "user://profile.json"

func suite_name() -> String:
	return "save system"

func run() -> void:
	var backup: Dictionary = SaveManager.data.duplicate(true)
	_test_round_trip()
	_test_corruption_recovery()
	_test_progress()
	_test_streak()
	_test_daily()
	_test_language_survives_reset()
	SaveManager.data = backup
	SaveManager.save_now()

func _fresh() -> void:
	SaveManager.data = SaveManager.default_data()

func _test_round_trip() -> void:
	_fresh()
	SaveManager.data["total_points"] = 1234
	SaveManager.set_language("ar")
	check(SaveManager.save_now(), "profile saves")
	SaveManager.data = {}
	SaveManager.load_profile()
	equal(int(SaveManager.data.get("total_points", 0)), 1234, "points survive a round trip")
	equal(SaveManager.get_language(), "ar", "language survives a round trip")

func _test_corruption_recovery() -> void:
	_fresh()
	SaveManager.data["total_points"] = 999
	SaveManager.save_now()          # Writes profile.json, and backs up the previous one.
	SaveManager.data["total_points"] = 111
	SaveManager.save_now()          # Now backup holds 999, primary holds 111.

	var f := FileAccess.open(PROBE_PATH, FileAccess.WRITE)
	f.store_string("{\"sum\":\"deadbeef\",\"payload\":{\"total_points\":7}}")
	f.close()
	SaveManager.load_profile()
	equal(int(SaveManager.data.get("total_points", 0)), 999,
			"a tampered save falls back to the backup instead of loading bad data")

	f = FileAccess.open(PROBE_PATH, FileAccess.WRITE)
	f.store_string("not json at all {{{")
	f.close()
	DirAccess.open("user://").remove("profile.backup.json")
	SaveManager.load_profile()
	equal(int(SaveManager.data.get("total_points", 0)), 0,
			"with no readable save the profile resets to defaults rather than crashing")

func _result(stage: int, stars: int, elapsed: float, wrong: int) -> PuzzleResult:
	var r := PuzzleResult.new()
	r.stage_index = stage
	r.puzzle_id = &"t"
	r.status = PuzzleResult.Status.SOLVED
	r.stars = stars
	r.elapsed = elapsed
	r.wrong_attempts = wrong
	r.points = stars * 10
	return r

func _test_progress() -> void:
	_fresh()
	SaveManager.record_result(_result(1, 2, 12.0, 3))
	check(SaveManager.is_stage_solved(1), "a solved stage is recorded")
	equal(SaveManager.stars_for(1), 2, "stars are recorded")
	equal(int(SaveManager.data["current_stage"]), 2, "the next stage unlocks")

	SaveManager.record_result(_result(1, 3, 5.0, 0))
	equal(SaveManager.stars_for(1), 3, "a better run raises the star count")
	near(float(SaveManager.stage_record(1)["best_time"]), 5.0, "best time improves")

	SaveManager.record_result(_result(1, 1, 40.0, 9))
	equal(SaveManager.stars_for(1), 3, "a worse run never lowers stars")
	near(float(SaveManager.stage_record(1)["best_time"]), 5.0, "a worse run never worsens best time")
	equal(int(SaveManager.data["total_points"]), 20, "points are only awarded on the first solve")

	SaveManager.record_result(_result(3, 3, 4.0, 0))
	equal(SaveManager.solved_count(), 2, "solved stages are counted")
	equal(SaveManager.total_stars(), 6, "stars sum across stages")

func _test_streak() -> void:
	_fresh()
	SaveManager.touch_streak("2026-09-10")
	equal(int(SaveManager.data["streak"]), 1, "the first day starts a streak")
	SaveManager.touch_streak("2026-09-10")
	equal(int(SaveManager.data["streak"]), 1, "playing twice in one day does not double count")
	SaveManager.touch_streak("2026-09-11")
	equal(int(SaveManager.data["streak"]), 2, "a consecutive day extends the streak")
	SaveManager.touch_streak("2026-09-13")
	equal(int(SaveManager.data["streak"]), 1, "a missed day resets the streak")
	equal(int(SaveManager.data["best_streak"]), 2, "the best streak is remembered")
	SaveManager.touch_streak("2026-10-01")
	SaveManager.touch_streak("2026-10-02")
	equal(int(SaveManager.data["streak"]), 2, "streaks work across a month boundary")

func _test_daily() -> void:
	_fresh()
	var r := _result(0, 3, 9.0, 0)
	SaveManager.record_daily("2026-09-14", r)
	check(bool(SaveManager.daily_record("2026-09-14")["solved"]), "the daily result is stored")
	var worse := _result(0, 1, 60.0, 5)
	SaveManager.record_daily("2026-09-14", worse)
	equal(int(SaveManager.daily_record("2026-09-14")["stars"]), 3,
			"a solved daily cannot be overwritten by a later worse attempt")
	equal(int(SaveManager.data["total_points"]), 30, "the daily pays out once")

func _test_language_survives_reset() -> void:
	_fresh()
	SaveManager.set_language("ar")
	SaveManager.set_setting("sfx", false)
	SaveManager.record_result(_result(5, 3, 3.0, 0))
	SaveManager.reset_progress()
	equal(SaveManager.get_language(), "ar", "resetting progress keeps the chosen language")
	equal(bool(SaveManager.get_setting("sfx", true)), false, "resetting progress keeps settings")
	equal(SaveManager.solved_count(), 0, "resetting progress clears solved stages")
