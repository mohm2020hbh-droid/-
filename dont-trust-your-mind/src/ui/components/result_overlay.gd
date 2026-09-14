class_name ResultOverlay
extends Control

## Shown after every attempt. Its real job is the explanation: the game is only
## fair if the player always learns exactly why the answer was the answer.

signal next_requested()
signal retry_requested()
signal menu_requested()

var _panel: PanelContainer

func show_result(puzzle: PuzzleDefinition, result: PuzzleResult, is_daily: bool,
		day: String = "") -> void:
	set_anchors_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_STOP

	var dim := ColorRect.new()
	dim.color = Color(Palette.BG, 0.90)
	dim.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(dim)

	var centre := MarginContainer.new()
	centre.set_anchors_preset(Control.PRESET_FULL_RECT)
	for side in ["left", "right"]:
		centre.add_theme_constant_override("margin_%s" % side, 24)
	centre.add_theme_constant_override("margin_top", 40)
	centre.add_theme_constant_override("margin_bottom", 40)
	add_child(centre)

	var scroll := ScrollContainer.new()
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	centre.add_child(scroll)

	_panel = PanelContainer.new()
	_panel.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_panel.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	_panel.layout_direction = Loc.layout_direction()
	scroll.add_child(_panel)

	var column := VBoxContainer.new()
	column.add_theme_constant_override("separation", 16)
	column.layout_direction = Loc.layout_direction()
	_panel.add_child(column)

	_build_body(column, puzzle, result, is_daily, day)
	FeedbackFx.fade_in(self, 0.22)

func _build_body(column: VBoxContainer, puzzle: PuzzleDefinition, result: PuzzleResult,
		is_daily: bool, day: String) -> void:
	column.add_child(_headline(result))
	if result.solved():
		var stars := StarRow.new()
		column.add_child(stars)
		stars.setup(result.stars)

	column.add_child(_stat_row(puzzle, result))

	if not puzzle.explanation.is_empty():
		column.add_child(_divider())
		column.add_child(_label(Loc.t("result.why"), GameTheme.SIZE_SMALL, Palette.TEXT_FAINT))
		column.add_child(_label(puzzle.explanation, GameTheme.SIZE_BODY, Palette.TEXT))

	column.add_child(_spacer(8))
	column.add_child(_actions(puzzle, result, is_daily, day))

func _headline(result: PuzzleResult) -> Label:
	var key := "result.solved"
	var color := Palette.GREEN
	match result.status:
		PuzzleResult.Status.FAILED_OUT_OF_TIME:
			key = "result.failed_time"
			color = Palette.RED
		PuzzleResult.Status.FAILED_OUT_OF_ATTEMPTS:
			key = "result.failed_tries"
			color = Palette.RED
		PuzzleResult.Status.SKIPPED:
			key = "result.skipped"
			color = Palette.TEXT_DIM
	var label := _label(Loc.t(key), 36, color)
	label.add_theme_font_override("font", GameTheme.bold())
	return label

func _stat_row(puzzle: PuzzleDefinition, result: PuzzleResult) -> Control:
	var row := HBoxContainer.new()
	row.layout_direction = Loc.layout_direction()
	row.alignment = BoxContainer.ALIGNMENT_CENTER
	row.add_theme_constant_override("separation", 26)
	row.add_child(_stat(Loc.t("result.time"), Loc.format_seconds(result.elapsed)))
	row.add_child(_stat(Loc.t("result.tries"), str(result.wrong_attempts)))
	if puzzle.index > 0:
		var best := float(SaveManager.stage_record(puzzle.index).get("best_time", 0.0))
		if best > 0.0:
			row.add_child(_stat(Loc.t("result.best"), Loc.format_seconds(best)))
	return row

func _stat(caption: String, value: String) -> Control:
	var box := VBoxContainer.new()
	box.add_theme_constant_override("separation", 2)
	box.add_child(_label(value, GameTheme.SIZE_BODY, Palette.TEXT))
	box.add_child(_label(caption, 18, Palette.TEXT_FAINT))
	return box

func _actions(puzzle: PuzzleDefinition, result: PuzzleResult, is_daily: bool,
		day: String) -> Control:
	var box := VBoxContainer.new()
	box.add_theme_constant_override("separation", 10)

	if result.solved():
		box.add_child(_button(Loc.t("result.share"), func() -> void:
			var text := Game.share_text_for_daily(day, result.elapsed) if is_daily \
					else Game.share_text_for_stage(puzzle.index, result.elapsed)
			Game.share(text)
			_toast(Loc.t("result.copied"))))

	var row := HBoxContainer.new()
	row.layout_direction = Loc.layout_direction()
	row.add_theme_constant_override("separation", 10)

	var retry := _button(Loc.t("hud.retry"), func() -> void: retry_requested.emit())
	retry.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(retry)

	if not is_daily and Game.next_stage_after(puzzle.index) > 0:
		var next := _button(Loc.t("hud.next"), func() -> void: next_requested.emit(), true)
		next.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		row.add_child(next)
	box.add_child(row)

	box.add_child(_button(Loc.t("hud.back"), func() -> void: menu_requested.emit()))
	return box

func _button(text: String, action: Callable, primary: bool = false) -> Button:
	var button := Button.new()
	button.text = text
	button.custom_minimum_size = Vector2(0, 70)
	button.layout_direction = Loc.layout_direction()
	if primary:
		button.add_theme_stylebox_override("normal", GameTheme.flat_box(Palette.TEXT, 14))
		button.add_theme_stylebox_override("hover", GameTheme.flat_box(Palette.WHITE, 14))
		button.add_theme_stylebox_override("pressed", GameTheme.flat_box(Palette.TEXT_DIM, 14))
		button.add_theme_color_override("font_color", Palette.BG)
		button.add_theme_color_override("font_hover_color", Palette.BG)
		button.add_theme_color_override("font_pressed_color", Palette.BG)
	button.pressed.connect(func() -> void:
		Audio.play(Audio.Sfx.TAP)
		action.call())
	return button

func _label(text: String, size: int, color: Color) -> Label:
	var label := Label.new()
	label.text = text
	label.add_theme_font_size_override("font_size", size)
	label.add_theme_color_override("font_color", color)
	label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	label.text_direction = Control.TEXT_DIRECTION_AUTO
	return label

func _divider() -> Control:
	var line := ColorRect.new()
	line.color = Palette.LINE
	line.custom_minimum_size = Vector2(0, 1)
	return line

func _spacer(height: float) -> Control:
	var spacer := Control.new()
	spacer.custom_minimum_size = Vector2(0, height)
	return spacer

func _toast(text: String) -> void:
	var label := _label(text, GameTheme.SIZE_SMALL, Palette.GREEN)
	label.set_anchors_preset(Control.PRESET_CENTER_BOTTOM)
	label.offset_top = -120
	label.offset_bottom = -80
	label.offset_left = -160
	label.offset_right = 160
	add_child(label)
	var tween := create_tween()
	tween.tween_interval(1.1)
	tween.tween_property(label, "modulate:a", 0.0, 0.4)
	tween.tween_callback(label.queue_free)
