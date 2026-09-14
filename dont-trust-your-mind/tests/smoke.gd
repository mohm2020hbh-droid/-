extends Node

## End-to-end smoke test: walks a real player's path from a cold first run
## through solving a stage, and fails loudly if any step does not land.
##
## Screens are instantiated as children rather than swapped in with
## change_scene_to_file, because that would free this node mid-test.

var _failures := 0

func _ready() -> void:
	await get_tree().process_frame
	_reset_profile()

	_expect(SaveManager.get_language() == "", "a cold start has no language yet")
	SaveManager.set_language("ar")
	Loc.set_locale("ar")
	_expect(Loc.is_rtl(), "choosing Arabic switches the interface to RTL")

	for name in ["menu", "stages", "daily", "settings", "achievements"]:
		var screen := await _open(Game.SCENES[name])
		_expect(screen != null and screen.get_child_count() > 0, "%s screen opens" % name)
		if screen != null:
			screen.queue_free()
		await get_tree().process_frame

	await _play_stage_one()
	_check_share()

	print("\n%s  smoke test (%d failure(s))"
			% ["PASS" if _failures == 0 else "FAIL", _failures])
	get_tree().quit(1 if _failures > 0 else 0)

func _reset_profile() -> void:
	var dir := DirAccess.open("user://")
	dir.remove("profile.json")
	dir.remove("profile.backup.json")
	SaveManager.data = SaveManager.default_data()

func _open(path: String) -> Node:
	var packed := load(path) as PackedScene
	if packed == null:
		return null
	var instance := packed.instantiate()
	add_child(instance)
	await get_tree().process_frame
	await get_tree().process_frame
	return instance

func _play_stage_one() -> void:
	Game.pending_daily = false
	Game.pending_stage = 1
	var screen := await _open(Game.SCENES["puzzle"])
	_expect(screen != null and screen.session != null, "the puzzle session starts")
	if screen == null or screen.session == null:
		return

	_expect(screen.puzzle.instruction == "اضغط على الأحمر",
			"stage 1 shows its Arabic instruction")
	screen.session.tap("c_blue")
	_expect(screen.session.wrong_attempts == 1, "a wrong tap is registered")
	_expect(not screen.session.finished, "a wrong tap does not end the stage")
	screen.session.tap("w_red")
	_expect(screen.session.finished, "the correct tap solves stage 1")
	await get_tree().process_frame

	_expect(SaveManager.is_stage_solved(1), "the solve is written to the profile")
	_expect(int(SaveManager.data["current_stage"]) == 2, "stage 2 unlocks")
	_expect(int(SaveManager.data["streak"]) == 1, "the day streak starts")
	_expect(SaveManager.has_achievement("first_blood"), "the first-solve achievement is granted")
	_expect(SaveManager.stars_for(1) == 2, "one wrong tap scores two stars")

	_expect(SaveManager.save_now(), "the profile writes to disk")
	SaveManager.data = {}
	SaveManager.load_profile()
	_expect(SaveManager.is_stage_solved(1), "progress survives a reload")

	screen.queue_free()
	await get_tree().process_frame

func _check_share() -> void:
	var share := Game.share_text_for_stage(1, 12.3)
	_expect(share.contains("12.3"), "share text reports the time")
	_expect(not share.contains("w_red") and not share.contains("الأحمر"),
			"share text does not leak the answer")

func _expect(ok: bool, what: String) -> void:
	print(("   PASS  " if ok else "   FAIL  ") + what)
	if not ok:
		_failures += 1
