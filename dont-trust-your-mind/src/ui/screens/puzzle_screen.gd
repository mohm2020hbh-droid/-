extends ScreenBase

## The gameplay screen. Owns the HUD, the board and the result overlay, and is
## the one place where chrome can itself become part of a puzzle.

const REVEAL_DELAY := 0.85

var puzzle: PuzzleDefinition
var session: PuzzleSession
var board: PuzzleBoard
var is_daily := false
var day_key := ""

var _stage_label: Label
var _counter_label: Label
var _instruction: Label
var _sub_instruction: Label
var _hint_label: Label
var _hint_button: Button
var _skip_button: Button
var _back_button: Button
var _progress_row: HBoxContainer
var _progress_started := false
var _overlay: ResultOverlay
var _armed: Dictionary = {}   ## "ui:x" -> true when this puzzle claims that chrome.
var _last_tick_second: int = -1

func on_back() -> void:
	_leave()

func build_content() -> void:
	is_daily = Game.pending_daily
	day_key = Puzzles.today_key()
	puzzle = Puzzles.get_daily(day_key) if is_daily else Puzzles.get_puzzle(Game.pending_stage)
	if puzzle == null:
		content.add_child(make_title(Loc.t("end.title")))
		content.add_child(make_body(Loc.t("end.body")))
		content.add_child(make_button(Loc.t("hud.back"), _leave, true))
		return

	for target in puzzle.ui_targets:
		_armed[target] = true

	session = PuzzleSession.new(puzzle)
	_connect_session()

	content.add_child(_top_bar())
	content.add_child(_instruction_block())
	_board_area()
	content.add_child(_hint_area())
	content.add_child(_bottom_bar())
	_refresh_counter()
	set_process(true)

func _process(delta: float) -> void:
	if session != null and not session.finished:
		session.tick(delta)

# --- HUD ----------------------------------------------------------------------

func _top_bar() -> Control:
	var row := HBoxContainer.new()
	row.layout_direction = Loc.layout_direction()
	row.add_theme_constant_override("separation", 12)

	_back_button = Button.new()
	_back_button.text = "‹"
	_back_button.custom_minimum_size = Vector2(GameTheme.TOUCH_COMPACT, GameTheme.TOUCH_COMPACT)
	_back_button.add_theme_font_size_override("font_size", 30)
	_back_button.add_theme_stylebox_override("normal", StyleBoxEmpty.new())
	_back_button.add_theme_stylebox_override("hover", StyleBoxEmpty.new())
	_back_button.add_theme_stylebox_override("pressed", StyleBoxEmpty.new())
	_back_button.add_theme_color_override("font_color", Palette.TEXT_DIM)
	_back_button.pressed.connect(func() -> void: _chrome_pressed("ui:back", _leave))
	row.add_child(_back_button)

	_stage_label = Label.new()
	_stage_label.text = Loc.t("daily.title") if is_daily else Loc.format_stage(puzzle.index)
	_stage_label.add_theme_font_size_override("font_size", GameTheme.SIZE_SMALL)
	_stage_label.add_theme_color_override("font_color", Palette.TEXT_DIM)
	_stage_label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	_stage_label.mouse_filter = Control.MOUSE_FILTER_STOP
	_stage_label.gui_input.connect(_chrome_input.bind("ui:stage", Callable()))
	row.add_child(_stage_label)

	row.add_child(make_spacer())

	_counter_label = Label.new()
	_counter_label.add_theme_font_size_override("font_size", GameTheme.SIZE_SMALL)
	_counter_label.add_theme_color_override("font_color", Palette.TEXT_DIM)
	_counter_label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	_counter_label.mouse_filter = Control.MOUSE_FILTER_STOP
	_counter_label.gui_input.connect(_chrome_input.bind("ui:timer", Callable()))
	row.add_child(_counter_label)
	return row

func _instruction_block() -> Control:
	var box := VBoxContainer.new()
	box.add_theme_constant_override("separation", 8)

	_instruction = make_title(puzzle.instruction, GameTheme.SIZE_INSTRUCTION)
	_instruction.custom_minimum_size = Vector2(0, 72)
	if _armed.has("ui:instruction"):
		_instruction.mouse_filter = Control.MOUSE_FILTER_STOP
		_instruction.gui_input.connect(_chrome_input.bind("ui:instruction", Callable()))
	box.add_child(_instruction)

	if not puzzle.sub_instruction.is_empty():
		_sub_instruction = make_body(puzzle.sub_instruction, Palette.TEXT_FAINT,
				GameTheme.SIZE_SMALL)
		box.add_child(_sub_instruction)
	return box

## Returns the board already parented, because it sizes its rows from the width
## it can actually see and must therefore be in the tree before it builds.
func _board_area() -> Control:
	board = PuzzleBoard.new()
	board.size_flags_vertical = Control.SIZE_EXPAND_FILL
	board.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	board.mouse_filter = Control.MOUSE_FILTER_PASS
	board.target_tapped.connect(_on_board_tap)
	board.press_started.connect(func(t: String) -> void: session.press_begin(t))
	board.press_ended.connect(func(t: String) -> void: session.press_end(t))
	content.add_child(board)
	board.build(puzzle, session)
	return board

func _hint_area() -> Control:
	_hint_label = make_body("", Palette.YELLOW, GameTheme.SIZE_SMALL)
	_hint_label.custom_minimum_size = Vector2(0, 58)
	_hint_label.visible = false
	return _hint_label

func _bottom_bar() -> Control:
	var box := VBoxContainer.new()
	box.add_theme_constant_override("separation", 8)

	_progress_row = HBoxContainer.new()
	_progress_row.layout_direction = Loc.layout_direction()
	_progress_row.alignment = BoxContainer.ALIGNMENT_CENTER
	_progress_row.add_theme_constant_override("separation", 6)
	_progress_row.custom_minimum_size = Vector2(0, 24)
	_progress_row.visible = false
	box.add_child(_progress_row)

	var row := HBoxContainer.new()
	row.layout_direction = Loc.layout_direction()
	row.add_theme_constant_override("separation", 10)

	_hint_button = Button.new()
	_hint_button.text = Loc.t("hud.hint")
	_hint_button.custom_minimum_size = Vector2(0, GameTheme.TOUCH_STANDARD)
	_hint_button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_hint_button.layout_direction = Loc.layout_direction()
	_hint_button.pressed.connect(func() -> void: _chrome_pressed("ui:hint", _use_hint))
	row.add_child(_hint_button)

	_skip_button = Button.new()
	_skip_button.text = Loc.t("hud.skip")
	_skip_button.custom_minimum_size = Vector2(0, GameTheme.TOUCH_STANDARD)
	_skip_button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_skip_button.layout_direction = Loc.layout_direction()
	_skip_button.disabled = not puzzle.allow_skip
	_skip_button.pressed.connect(func() -> void: _chrome_pressed("ui:skip", _skip))
	row.add_child(_skip_button)

	box.add_child(row)
	return box

# --- Chrome that a puzzle may claim -------------------------------------------

## A puzzle can "arm" a HUD control, which suppresses that control's normal job
## and routes the tap into the solution rule instead. Un-armed chrome never feeds
## the session, so a player can always use Hint or Back without risking a strike.
func _chrome_pressed(target: String, normal_action: Callable) -> void:
	Audio.play(Audio.Sfx.BUTTON)
	if _armed.has(target) and session != null and not session.finished:
		session.tap(target)
		return
	if normal_action.is_valid():
		normal_action.call()

func _chrome_input(event: InputEvent, target: String, normal_action: Callable) -> void:
	if event is InputEventMouseButton and event.pressed \
			and event.button_index == MOUSE_BUTTON_LEFT:
		if _armed.has(target):
			get_viewport().set_input_as_handled()
			_chrome_pressed(target, normal_action)

func _chrome_widget(target: String) -> Control:
	match target:
		"ui:hint": return _hint_button
		"ui:skip": return _skip_button
		"ui:stage": return _stage_label
		"ui:timer": return _counter_label
		"ui:back": return _back_button
		"ui:instruction": return _instruction
	return null

# --- Session wiring -----------------------------------------------------------

func _connect_session() -> void:
	session.solved.connect(_on_solved)
	session.failed.connect(_on_failed)
	session.wrong_tap.connect(_on_wrong)
	session.countdown_changed.connect(_on_countdown)
	session.sequence_advanced.connect(_on_sequence)
	session.sequence_reset.connect(_on_sequence_reset)

func _on_board_tap(_target: String) -> void:
	Audio.play(Audio.Sfx.TAP)

func _on_wrong(target: String, note: String) -> void:
	Audio.play(Audio.Sfx.WRONG)
	Audio.buzz(18)
	var widget := _chrome_widget(target)
	if widget != null:
		FeedbackFx.shake(widget)
		FeedbackFx.flash(widget, Palette.RED)
	_refresh_counter()
	if not note.is_empty():
		_show_note(note, Palette.ORANGE)

func _on_countdown(seconds_left: float) -> void:
	_refresh_counter(seconds_left)

func _on_sequence(progress: int, total: int) -> void:
	Audio.play(Audio.Sfx.CORRECT, 0.85 + 0.1 * progress)
	_progress_started = true
	_set_progress(progress, total)

func _on_sequence_reset() -> void:
	_set_progress(0, puzzle.solution.targets.size())

## Rebuilds the "X of Y" progress row from separate Label nodes rather than
## one templated string — see make_ratio_line()'s docstring in
## screen_base.gd for why: two numbers in one Arabic string can render in
## the wrong visual order, which a plain Label.text assignment cannot avoid.
func _set_progress(current: int, total: int) -> void:
	_progress_row.visible = _progress_started
	for child in _progress_row.get_children():
		child.queue_free()
	for text in [str(current), Loc.t("fmt.of_separator"), str(total)]:
		var label := Label.new()
		label.text = text
		label.add_theme_font_size_override("font_size", 19)
		label.add_theme_color_override("font_color", Palette.TEXT_FAINT)
		_progress_row.add_child(label)

func _on_solved(result: PuzzleResult) -> void:
	set_process(false)
	Audio.play(Audio.Sfx.STAGE_COMPLETE)
	Audio.buzz(35)
	board.reveal_solution()
	_lock_chrome()
	if is_daily:
		Game.submit_daily(day_key, result)
	else:
		Game.submit_result(result)
	_open_overlay(result, REVEAL_DELAY)

func _on_failed(result: PuzzleResult) -> void:
	set_process(false)
	if result.status != PuzzleResult.Status.SKIPPED:
		Audio.play(Audio.Sfx.WRONG)
	board.reveal_solution()
	_lock_chrome()
	if is_daily:
		Game.submit_daily(day_key, result)
	else:
		Game.submit_result(result)
	_open_overlay(result, REVEAL_DELAY)

func _lock_chrome() -> void:
	_hint_button.disabled = true
	_skip_button.disabled = true

func _open_overlay(result: PuzzleResult, delay: float) -> void:
	await get_tree().create_timer(delay).timeout
	if not is_inside_tree():
		return
	_overlay = ResultOverlay.new()
	add_child(_overlay)
	_overlay.show_result(puzzle, result, is_daily, day_key)
	_overlay.next_requested.connect(_next)
	_overlay.retry_requested.connect(_retry)
	_overlay.menu_requested.connect(_leave)

# --- HUD state ----------------------------------------------------------------

const TICK_WARNING_SECONDS := 3

func _refresh_counter(seconds_left: float = -1.0) -> void:
	var parts: PackedStringArray = PackedStringArray()
	var tickable_left := -1.0
	if session.puzzle.solution.kind == SolutionRule.Kind.NO_TAP:
		tickable_left = maxf(session.no_tap_left(), 0.0)
		parts.append(Loc.format_seconds(tickable_left))
	elif puzzle.show_timer and puzzle.time_limit > 0.0:
		var left := seconds_left if seconds_left >= 0.0 else session.seconds_left()
		tickable_left = left
		parts.append(Loc.format_seconds(left))
		if left <= float(TICK_WARNING_SECONDS):
			_counter_label.add_theme_color_override("font_color", Palette.RED)
	if puzzle.show_attempts and puzzle.max_attempts > 0:
		parts.append("%s %d/%d" % [Loc.t("hud.attempts"),
				session.wrong_attempts, puzzle.max_attempts])
	_counter_label.text = "   ".join(parts)
	_maybe_tick(tickable_left)

## A short, dry tick once per whole second while a countdown is inside its
## last few seconds — real audible pressure for a timed puzzle, without
## firing every frame (countdown_changed fires every tick(), i.e. every
## frame, so this gates on the integer second actually changing).
func _maybe_tick(seconds_left: float) -> void:
	if seconds_left < 0.0 or seconds_left > float(TICK_WARNING_SECONDS):
		_last_tick_second = -1
		return
	var whole := int(ceil(seconds_left - 0.001))
	if whole != _last_tick_second and whole >= 0:
		_last_tick_second = whole
		Audio.play(Audio.Sfx.TICK)

func _show_note(text: String, color: Color) -> void:
	_hint_label.visible = true
	_hint_label.text = text
	_hint_label.add_theme_color_override("font_color", color)

func _use_hint() -> void:
	if not puzzle.allow_hints:
		_show_note(Loc.t("hint.locked"), Palette.TEXT_DIM)
		return
	var text := session.use_hint()
	if text.is_empty():
		_show_note(Loc.t("hint.used_all"), Palette.TEXT_DIM)
		return
	Audio.play(Audio.Sfx.HINT)
	_show_note(text, Palette.YELLOW)
	if session.hints_used >= puzzle.hints.size():
		_hint_button.disabled = true

func _skip() -> void:
	session.skip()

# --- Navigation ---------------------------------------------------------------

func _next() -> void:
	var next_stage := Game.next_stage_after(puzzle.index)
	if is_daily or next_stage < 0:
		_leave()
		return
	Audio.play(Audio.Sfx.TRANSITION)
	Game.start_stage(next_stage)

func _retry() -> void:
	if is_daily:
		Game.start_daily()
	else:
		Game.start_stage(puzzle.index)

func _leave() -> void:
	Game.goto("daily" if is_daily else "menu")
