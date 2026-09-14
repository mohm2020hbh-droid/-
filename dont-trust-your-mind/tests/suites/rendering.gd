extends TestSuite

## Integration coverage: every puzzle is actually built into live controls in
## both languages, every screen is instantiated, and the daily selector is
## checked for determinism. This is what catches a puzzle whose data is valid
## but whose board cannot be rendered or whose answer has no widget to tap.

const VIEWPORT := Vector2(720, 1280)

var _host: SubViewport

func suite_name() -> String:
	return "rendering and integration"

func run() -> void:
	var original := Loc.locale
	_host = SubViewport.new()
	_host.size = VIEWPORT
	_host.disable_3d = true
	add_child(_host)

	for loc in Loc.SUPPORTED:
		Loc.set_locale(loc)
		await _build_all_boards(loc)
	await _test_screens()
	_test_daily_selection()
	_test_widget_sizes()

	Loc.set_locale(original)
	_host.queue_free()

func _build_all_boards(loc: String) -> void:
	for stage in range(1, Puzzles.total_stages() + 1):
		await _build_board(Puzzles.get_puzzle(stage, loc), "stage %d [%s]" % [stage, loc])
	for i in range(Puzzles.raw_daily().size()):
		var daily := PuzzleDefinition.from_dict(Puzzles.raw_daily()[i], loc, 0)
		await _build_board(daily, "daily %d [%s]" % [i, loc])

func _build_board(puzzle: PuzzleDefinition, label: String) -> void:
	if puzzle == null:
		failures.append("%s: no puzzle" % label)
		assertions += 1
		return
	var session := PuzzleSession.new(puzzle)
	var board := PuzzleBoard.new()
	board.size = VIEWPORT
	_host.add_child(board)
	board.build(puzzle, session)
	await get_tree().process_frame
	check(board.usable_width() >= VIEWPORT.x - PuzzleBoard.SIDE_PADDING * 2.0,
			"%s: the board measured a full-width layout area (%.0f)"
					% [label, board.usable_width()])

	for target in puzzle.solution.all_targets():
		if target.begins_with("ui:") or target == "board":
			continue
		check(board.widget_for(target) != null,
				"%s: solution target has a widget: %s" % [label, target])

	# Every element the player is told they can tap must exist on screen.
	for element in puzzle.elements:
		if element.is_tappable():
			check(board.widget_for(str(element.id)) != null,
					"%s: tappable element has a widget: %s" % [label, element.id])

	_check_chars_targets(board, puzzle, label)
	board.reveal_solution()
	board.queue_free()
	await get_tree().process_frame

## Confirms that a character target resolves to a real on-screen box, which is
## what makes the tap reachable with a finger.
func _check_chars_targets(board: PuzzleBoard, puzzle: PuzzleDefinition,
		label: String) -> void:
	for target in puzzle.solution.all_targets():
		if not target.begins_with("char:"):
			continue
		var parts := target.split(":")
		if parts.size() != 3:
			continue
		var strip := board.widget_for(target) as CharStrip
		if strip == null:
			continue
		var index := int(parts[2])
		check(index < strip.grapheme_count(),
				"%s: char index is inside the rendered strip: %s" % [label, target])
		var box := strip.box_for(index)
		check(box.size.x > 0.0,
				"%s: char target has a visible box: %s" % [label, target])

func _test_screens() -> void:
	for name in ["language", "intro", "menu", "stages", "settings",
			"daily", "achievements"]:
		var path: String = Game.SCENES[name]
		var packed := load(path) as PackedScene
		check(packed != null, "screen loads: %s" % name)
		if packed == null:
			continue
		var instance := packed.instantiate()
		_host.add_child(instance)
		await get_tree().process_frame
		check(instance.get_child_count() > 0, "screen builds content: %s" % name)
		instance.queue_free()
		await get_tree().process_frame

func _test_daily_selection() -> void:
	var first := Puzzles.daily_index_for("2026-09-14")
	equal(Puzzles.daily_index_for("2026-09-14"), first,
			"the same date always selects the same daily puzzle")
	check(first >= 0 and first < Puzzles.raw_daily().size(),
			"the daily index is inside the pool")

	var seen := {}
	for day in range(1, 29):
		seen[Puzzles.daily_index_for("2026-09-%02d" % day)] = true
	check(seen.size() >= 5,
			"a month of dates reaches at least five different daily puzzles (got %d)"
					% seen.size())
	check(Puzzles.get_daily("2026-09-14", "ar") != null, "the daily resolves in Arabic")
	check(Puzzles.get_daily("2026-09-14", "en") != null, "the daily resolves in English")

## Touch targets must stay finger-sized on the smallest screen the game
## supports, and this guards the enlarged minimums specifically — a future
## edit that quietly shrinks them back toward the old, player-reported-as-too-
## small values should fail here, not just clear a generic lower bound.
func _test_widget_sizes() -> void:
	check(PuzzleBoard.MIN_TOUCH >= 100.0,
			"the minimum touch target is at least 100 units on a 720-wide viewport")
	check(CharStrip.MIN_TAP_WIDTH >= 60.0,
			"character tap snapping covers at least 60 units")
	check(GameTheme.TOUCH_STANDARD >= 80.0,
			"standard chrome buttons (Hint, Skip, Retry, toggles) are at least 80 units tall")
	check(GameTheme.TOUCH_PRIMARY >= 96.0,
			"the primary action on a screen is at least 96 units tall")
