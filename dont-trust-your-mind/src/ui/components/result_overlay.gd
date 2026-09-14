class_name ResultOverlay
extends Control

## Shown after every attempt. Two jobs, in priority order: make the primary
## next action completely unmissable, and explain why the answer was the
## answer — the game is only fair if the player always learns that.
##
## The single biggest mistake a result screen can make is letting its primary
## action look like just another button. So there is exactly one button on
## this screen sized and coloured to read as "the thing to press": Next Stage
## on a solved campaign stage, Share on a solved daily (there is no next
## stage to chain into), or Retry on a failed/skipped attempt. Everything
## else is visibly secondary.

signal next_requested()
signal retry_requested()
signal menu_requested()

const PRIMARY_HEIGHT := GameTheme.TOUCH_PRIMARY
const SECONDARY_HEIGHT := GameTheme.TOUCH_STANDARD
const BADGE_SIZE := 76

var _panel: PanelContainer

## Forces a control to exactly fill its parent's rect: full-rect anchors AND
## explicitly zeroed offsets.
##
## set_anchors_preset(PRESET_FULL_RECT) alone is not safe here: on a Control
## built entirely in code and shown by calling a method after add_child()
## (rather than through the normal _ready() lifecycle every other screen in
## this game uses), its keep_offset=false default did not reliably reset
## offset_right/offset_bottom to 0. The observed result was a full-screen
## overlay silently rendering at size (0, 0) — invisible, and with every
## child (including the Next Stage button) sitting at zero size too, meaning
## nothing on the result screen was actually tappable despite the tree
## looking correct in every other respect. This was caught only by rendering
## an actual frame and inspecting pixels; it produced no error, and every
## signal-level and logic-level test passed regardless, because emitting a
## button's "pressed" signal directly (as those tests do) does not go
## through hit-testing and so never exercises the button's real screen rect.
static func _fill_parent(control: Control) -> void:
	control.set_anchors_preset(Control.PRESET_FULL_RECT)
	control.offset_left = 0
	control.offset_top = 0
	control.offset_right = 0
	control.offset_bottom = 0

func show_result(puzzle: PuzzleDefinition, result: PuzzleResult, is_daily: bool,
		day: String = "") -> void:
	_fill_parent(self)
	mouse_filter = Control.MOUSE_FILTER_STOP

	var dim := ColorRect.new()
	dim.color = Color(Palette.BG, 0.92)
	add_child(dim)
	_fill_parent(dim)

	var centre := MarginContainer.new()
	for side in ["left", "right"]:
		centre.add_theme_constant_override("margin_%s" % side, 22)
	centre.add_theme_constant_override("margin_top", 32)
	centre.add_theme_constant_override("margin_bottom", 28)
	add_child(centre)
	_fill_parent(centre)

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
	column.add_theme_constant_override("separation", 14)
	column.layout_direction = Loc.layout_direction()
	_panel.add_child(column)

	_build_body(column, puzzle, result, is_daily, day)
	FeedbackFx.fade_in(self, 0.2)

func _build_body(column: VBoxContainer, puzzle: PuzzleDefinition, result: PuzzleResult,
		is_daily: bool, day: String) -> void:
	if result.solved():
		column.add_child(_badge(true))
	column.add_child(_headline(result))
	if result.solved():
		var stars := StarRow.new()
		column.add_child(stars)
		stars.setup(result.stars)

	column.add_child(_spacer(4))
	column.add_child(_stat_row(puzzle, result))

	if not puzzle.explanation.is_empty():
		column.add_child(_divider())
		column.add_child(_label(Loc.t("result.why"), 17, Palette.TEXT_FAINT))
		column.add_child(_label(puzzle.explanation, 22, Palette.TEXT_DIM))

	column.add_child(_spacer(10))
	column.add_child(_primary_action(puzzle, result, is_daily, day))
	column.add_child(_spacer(6))
	column.add_child(_secondary_actions(puzzle, result, is_daily, day))

	# The primary button IS "Back" itself exactly when a solved campaign
	# stage has no next stage to chain into (the last stage in the game).
	# Showing the small text link too would just repeat the same destination.
	var primary_is_back := result.solved() and not is_daily \
			and Game.next_stage_after(puzzle.index) <= 0
	if not primary_is_back:
		column.add_child(_spacer(4))
		column.add_child(_menu_link())

## A drawn checkmark, not a font glyph — a Unicode ✓ depends on the active
## font actually mapping that codepoint, and a missing glyph here would be
## the very first thing a player sees after winning. Drawing it costs nothing
## and can never come up blank.
func _badge(success: bool) -> Control:
	var badge := _CheckBadge.new()
	badge.success = success
	badge.custom_minimum_size = Vector2(BADGE_SIZE, BADGE_SIZE)
	badge.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	badge.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return badge

class _CheckBadge extends Control:
	var success := true
	func _draw() -> void:
		var centre := size * 0.5
		var radius := minf(size.x, size.y) * 0.5
		var ring := Palette.GREEN if success else Palette.TEXT_DIM
		draw_circle(centre, radius, Color(ring, 0.16))
		draw_arc(centre, radius - 3.0, 0.0, TAU, 40, ring, 4.0, true)
		if success:
			var a := centre + Vector2(-radius * 0.42, radius * 0.02)
			var b := centre + Vector2(-radius * 0.08, radius * 0.36)
			var c := centre + Vector2(radius * 0.46, -radius * 0.32)
			draw_polyline(PackedVector2Array([a, b, c]), ring, 6.0, true)

func _headline(result: PuzzleResult) -> Label:
	var key := "result.stage_complete"
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
	var label := _label(Loc.t(key), 38, color)
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

## Reserves real width for each stat, the same fix applied to puzzle board
## labels: an HBoxContainer squeezes an unclaimed-width Label toward zero,
## and AUTOWRAP_WORD_SMART then breaks the squeezed text one character per
## line — exactly what happened here before this measured the text first.
func _stat(caption: String, value: String) -> Control:
	var box := VBoxContainer.new()
	box.add_theme_constant_override("separation", 2)
	box.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	var value_label := _label(value, 22, Palette.TEXT)
	var caption_label := _label(caption, 16, Palette.TEXT_FAINT)
	var widest := maxf(
			GameTheme.semibold().get_string_size(value, HORIZONTAL_ALIGNMENT_LEFT, -1.0, 22).x,
			GameTheme.regular().get_string_size(caption, HORIZONTAL_ALIGNMENT_LEFT, -1.0, 16).x)
	for label in [value_label, caption_label]:
		label.autowrap_mode = TextServer.AUTOWRAP_OFF
		label.custom_minimum_size.x = widest + 4.0
	box.add_child(value_label)
	box.add_child(caption_label)
	return box

## The one action on this screen sized to look unmissable. Exactly one of
## Next Stage / Share / Retry is shown here, chosen by what actually applies:
## Next Stage whenever a solved campaign stage has one, Share for a solved
## daily (nothing to chain into), Retry otherwise.
func _primary_action(puzzle: PuzzleDefinition, result: PuzzleResult, is_daily: bool,
		day: String) -> Control:
	if result.solved() and not is_daily and Game.next_stage_after(puzzle.index) > 0:
		var next := _button(Loc.t("hud.next_stage"), func() -> void: next_requested.emit(),
				true, PRIMARY_HEIGHT)
		next.name = "NextStageButton"
		return next

	if result.solved():
		var label := Loc.t("result.share") if is_daily else Loc.t("hud.back")
		var target := Callable(self, "_do_share").bind(puzzle, result, is_daily, day) \
				if is_daily else func() -> void: menu_requested.emit()
		var button := _button(label, target, true, PRIMARY_HEIGHT)
		button.name = "PrimaryShareOrDoneButton"
		return button

	var retry := _button(Loc.t("hud.retry"), func() -> void: retry_requested.emit(),
			true, PRIMARY_HEIGHT)
	retry.name = "RetryButton"
	return retry

func _do_share(puzzle: PuzzleDefinition, result: PuzzleResult, is_daily: bool,
		day: String) -> void:
	var text := Game.share_text_for_daily(day, result.elapsed) if is_daily \
			else Game.share_text_for_stage(puzzle.index, result.elapsed)
	Game.share(text)
	_toast(Loc.t("result.copied"))

## Whatever the primary button did not cover: Retry (if not already primary)
## and Share (if not already primary), side by side, visibly smaller than
## the primary action above them.
func _secondary_actions(puzzle: PuzzleDefinition, result: PuzzleResult, is_daily: bool,
		day: String) -> Control:
	var row := HBoxContainer.new()
	row.layout_direction = Loc.layout_direction()
	row.add_theme_constant_override("separation", 10)

	var primary_is_next := result.solved() and not is_daily \
			and Game.next_stage_after(puzzle.index) > 0
	var primary_is_share := result.solved() and is_daily

	if primary_is_next or primary_is_share:
		var retry := _button(Loc.t("hud.retry"), func() -> void: retry_requested.emit(),
				false, SECONDARY_HEIGHT)
		retry.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		row.add_child(retry)

	if primary_is_next and result.solved():
		var share := _button(Loc.t("result.share"), func() -> void:
			_do_share(puzzle, result, is_daily, day), false, SECONDARY_HEIGHT)
		share.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		row.add_child(share)

	if row.get_child_count() == 0:
		row.add_theme_constant_override("separation", 0)  # Stays empty; costs nothing.
	return row

## Leaving is always possible, but never competes visually with the primary
## action: a plain text link, not a button.
func _menu_link() -> Control:
	var button := Button.new()
	button.text = Loc.t("hud.back")
	button.custom_minimum_size = Vector2(0, 52)
	button.layout_direction = Loc.layout_direction()
	button.add_theme_stylebox_override("normal", StyleBoxEmpty.new())
	button.add_theme_stylebox_override("hover", StyleBoxEmpty.new())
	button.add_theme_stylebox_override("pressed", StyleBoxEmpty.new())
	button.add_theme_color_override("font_color", Palette.TEXT_FAINT)
	button.add_theme_font_size_override("font_size", 19)
	button.pressed.connect(func() -> void:
		Audio.play(Audio.Sfx.BUTTON)
		menu_requested.emit())
	return button

func _button(text: String, action: Callable, primary: bool, height: float) -> Button:
	var button := Button.new()
	button.text = text
	button.custom_minimum_size = Vector2(0, height)
	button.layout_direction = Loc.layout_direction()
	if primary:
		button.add_theme_font_size_override("font_size", 30)
		button.add_theme_font_override("font", GameTheme.bold())
		button.add_theme_stylebox_override("normal", GameTheme.flat_box(Palette.GREEN, 18))
		button.add_theme_stylebox_override("hover",
				GameTheme.flat_box(Palette.GREEN.lightened(0.1), 18))
		button.add_theme_stylebox_override("pressed",
				GameTheme.flat_box(Palette.GREEN.darkened(0.15), 18))
		button.add_theme_color_override("font_color", Palette.BLACK)
		button.add_theme_color_override("font_hover_color", Palette.BLACK)
		button.add_theme_color_override("font_pressed_color", Palette.BLACK)
	else:
		button.add_theme_font_size_override("font_size", GameTheme.SIZE_BUTTON)
	button.pressed.connect(func() -> void:
		Audio.play(Audio.Sfx.BUTTON)
		action.call())
	return button

func _label(text: String, size: int, color: Color) -> Label:
	var label := Label.new()
	label.text = text
	label.add_theme_font_size_override("font_size", size)
	label.add_theme_color_override("font_color", color)
	label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	label.text_direction = Loc.text_direction()
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
