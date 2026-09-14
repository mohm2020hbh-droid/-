extends TestSuite

## Validates all authored puzzle content against the engine's own rules.
##
## The most valuable check here shapes each "chars" element with the real font
## and confirms that every character-index solution lands on the character the
## author intended. That is the one class of content bug that is invisible in
## review and fatal in play: an off-by-one index silently asks the player for
## the wrong letter.

const VALID_UI := ["ui:hint", "ui:skip", "ui:stage", "ui:timer", "ui:back", "ui:instruction"]
const EXPECTED_STAGES := 150

var _ts: TextServer = TextServerManager.get_primary_interface()
var _font: Font

func suite_name() -> String:
	return "content validation"

func run() -> void:
	_font = GameTheme.bold()
	check(Puzzles.load_errors().is_empty(),
			"puzzle files loaded without error: %s" % str(Puzzles.load_errors()))
	equal(Puzzles.total_stages(), EXPECTED_STAGES, "campaign stage count")
	check(Puzzles.raw_daily().size() >= 7,
			"daily pool has at least a week of puzzles (%d)" % Puzzles.raw_daily().size())

	_check_unique_ids()
	for stage in range(1, Puzzles.total_stages() + 1):
		for loc in Loc.SUPPORTED:
			_validate(Puzzles.get_puzzle(stage, loc), "stage %d [%s]" % [stage, loc])
	for i in range(Puzzles.raw_daily().size()):
		for loc in Loc.SUPPORTED:
			var daily := PuzzleDefinition.from_dict(Puzzles.raw_daily()[i], loc, 0)
			_validate(daily, "daily %d [%s]" % [i, loc])
	_check_chapter_spread()

func _check_unique_ids() -> void:
	var seen := {}
	for raw in Puzzles.raw_campaign() + Puzzles.raw_daily():
		var id: String = raw.get("id", "")
		check(not id.is_empty(), "puzzle has an id")
		check(not seen.has(id), "puzzle id is unique: %s" % id)
		seen[id] = true

func _check_chapter_spread() -> void:
	for chapter in range(1, Puzzles.CHAPTER_COUNT + 1):
		var stages := Puzzles.stages_in_chapter(chapter)
		check(stages.size() >= 5,
				"chapter %d has enough stages (%d)" % [chapter, stages.size()])

func _validate(p: PuzzleDefinition, label: String) -> void:
	if p == null:
		failures.append("%s: missing locale spec" % label)
		assertions += 1
		return

	check(not p.instruction.is_empty(), "%s: has an instruction" % label)
	check(not p.explanation.is_empty(), "%s: has an explanation" % label)
	check(p.solution != null, "%s: has a solution" % label)
	check(not p.elements.is_empty() or p.solution.is_passive(),
			"%s: has elements or is a wait puzzle" % label)

	var ids := {}
	for element in p.elements:
		check(element.id != &"", "%s: element has an id" % label)
		check(not ids.has(element.id),
				"%s: element id is unique: %s" % [label, element.id])
		ids[element.id] = element
		_validate_on_tap(p, element, label)

	for target in p.solution.all_targets():
		_validate_target(p, target, label, ids)
	for target in p.wrong_notes.keys():
		check(_target_exists(p, str(target), ids),
				"%s: wrong-note target exists: %s" % [label, target])
	for ui in p.ui_targets:
		check(VALID_UI.has(ui), "%s: known ui target: %s" % [label, ui])

	_validate_chrome_arming(p, label)
	_validate_expectations(p, label)
	_validate_tuning(p, label)

func _validate_on_tap(p: PuzzleDefinition, element: PuzzleElement, label: String) -> void:
	for key in ["reveal", "hide"]:
		for id in element.on_tap.get(key, []):
			check(p.element_by_id(StringName(id)) != null,
					"%s: on_tap.%s references a real element: %s" % [label, key, id])

func _target_exists(p: PuzzleDefinition, target: String, ids: Dictionary) -> bool:
	if target == "board":
		return true
	if target.begins_with("ui:"):
		return VALID_UI.has(target)
	if target.begins_with("char:"):
		var parts := target.split(":")
		return parts.size() == 3 and ids.has(StringName(parts[1]))
	return ids.has(StringName(target))

func _validate_target(p: PuzzleDefinition, target: String, label: String,
		ids: Dictionary) -> void:
	check(_target_exists(p, target, ids),
			"%s: solution target exists: %s" % [label, target])
	if not target.begins_with("char:"):
		if ids.has(StringName(target)):
			var element: PuzzleElement = ids[StringName(target)]
			check(element.is_tappable(),
					"%s: solution target is tappable: %s" % [label, target])
		return

	var parts := target.split(":")
	if parts.size() != 3 or not ids.has(StringName(parts[1])):
		return
	var element: PuzzleElement = ids[StringName(parts[1])]
	check(element.kind == PuzzleElement.Kind.CHARS,
			"%s: char target points at a chars element: %s" % [label, target])
	var count := _grapheme_count(element.text, p.locale)
	var index := int(parts[2])
	check(index >= 0 and index < count,
			"%s: char index %d is inside '%s' (%d graphemes)"
					% [label, index, element.text, count])

## Every armed chrome control must actually be used by the solution, and every
## chrome control the solution needs must be armed — otherwise the puzzle is
## either unsolvable or silently steals a button the player still needs.
func _validate_chrome_arming(p: PuzzleDefinition, label: String) -> void:
	for target in p.solution.all_targets():
		if target.begins_with("ui:"):
			check(p.ui_targets.has(target),
					"%s: solution needs armed chrome: %s" % [label, target])
	for ui in p.ui_targets:
		var used := false
		for target in p.solution.all_targets():
			if target == ui:
				used = true
		check(used, "%s: armed chrome is used by the solution: %s" % [label, ui])
	if p.ui_targets.has("ui:hint"):
		check(p.hints.is_empty(),
				"%s: a puzzle that arms the hint button offers no hints" % label)

## Confirms the author's stated character against the real shaped text.
func _validate_expectations(p: PuzzleDefinition, label: String) -> void:
	var targets := p.solution.all_targets()
	if p.solution.expect.is_empty():
		for target in targets:
			check(not target.begins_with("char:"),
					"%s: char solution declares its expected character" % label)
		return
	equal(p.solution.expect.size(), targets.size(),
			"%s: expect list matches target count" % label)
	for i in range(mini(p.solution.expect.size(), targets.size())):
		var wanted := p.solution.expect[i]
		if wanted.is_empty():
			continue
		var parts := targets[i].split(":")
		if parts.size() != 3:
			continue
		var element := p.element_by_id(StringName(parts[1]))
		if element == null:
			continue
		var actual := _grapheme_at(element.text, int(parts[2]), p.locale)
		equal(actual, wanted, "%s: target %d lands on the intended character in '%s'"
				% [label, i, element.text])

## Keeps timing and scoring inside ranges that stay playable on a phone.
func _validate_tuning(p: PuzzleDefinition, label: String) -> void:
	check(p.par_time > 0.0, "%s: par time is set" % label)
	check(p.reward > 0, "%s: reward is positive" % label)
	if p.time_limit > 0.0:
		check(p.time_limit >= 5.0,
				"%s: a timed puzzle allows at least 5 seconds" % label)
		if p.solution.not_after > 0.0:
			check(p.solution.not_after <= p.time_limit,
					"%s: the answer window closes before the clock does" % label)
	if p.solution.not_before > 0.0 and p.solution.not_after > 0.0:
		check(p.solution.not_after > p.solution.not_before,
				"%s: answer window is not inverted" % label)
	if p.solution.kind == SolutionRule.Kind.NO_TAP \
			or p.solution.kind == SolutionRule.Kind.HOLD:
		check(p.solution.duration > 0.0, "%s: wait/hold duration is set" % label)
	check(p.hints.size() <= 2, "%s: at most two hints" % label)
	if not p.ui_targets.has("ui:hint"):
		check(p.hints.size() == 2, "%s: offers both hint levels" % label)

# --- Text shaping helpers -----------------------------------------------------

func _shape(text: String, loc: String) -> TextLine:
	var line := TextLine.new()
	line.direction = TextServer.DIRECTION_RTL if loc == "ar" else TextServer.DIRECTION_LTR
	line.add_string(text, _font, 64, loc)
	return line

func _grapheme_count(text: String, loc: String) -> int:
	if text.is_empty():
		return 0
	return _ts.shaped_text_get_character_breaks(_shape(text, loc).get_rid()).size()

func _grapheme_at(text: String, index: int, loc: String) -> String:
	var breaks := _ts.shaped_text_get_character_breaks(_shape(text, loc).get_rid())
	if index < 0 or index >= breaks.size():
		return ""
	var start: int = 0 if index == 0 else breaks[index - 1]
	return text.substr(start, breaks[index] - start)
