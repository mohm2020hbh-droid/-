class_name ScreenBase
extends Control

## Common scaffolding for every screen: theme, background, writing direction,
## device safe-area insets and the Android back gesture.
##
## Subclasses implement [method build_content] and never touch any of this.

const EDGE_PADDING := 26

var content: VBoxContainer
var _safe_margin: MarginContainer

func _ready() -> void:
	set_anchors_preset(Control.PRESET_FULL_RECT)
	theme = GameTheme.build()
	layout_direction = Loc.layout_direction()
	_add_background()
	_add_safe_area()
	build_content()
	FeedbackFx.fade_in(self, 0.18)

## Override in subclasses. [member content] is an empty centred column.
func build_content() -> void:
	pass

## Override to change what the Android back button does. Default: to the menu.
func on_back() -> void:
	Game.goto("menu")

func _add_background() -> void:
	var bg := ColorRect.new()
	bg.color = Palette.BG
	bg.set_anchors_preset(Control.PRESET_FULL_RECT)
	bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(bg)

## Keeps content clear of notches, punch-holes and gesture bars.
func _add_safe_area() -> void:
	_safe_margin = MarginContainer.new()
	_safe_margin.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(_safe_margin)

	content = VBoxContainer.new()
	content.alignment = BoxContainer.ALIGNMENT_BEGIN
	content.add_theme_constant_override("separation", 18)
	content.layout_direction = Loc.layout_direction()
	_safe_margin.add_child(content)

	_apply_insets()
	get_tree().root.size_changed.connect(_apply_insets)

func _apply_insets() -> void:
	if not is_instance_valid(_safe_margin):
		return
	var safe := DisplayServer.get_display_safe_area()
	var window := DisplayServer.window_get_size()
	var scale_y := 1.0
	var viewport := get_viewport_rect().size
	if window.y > 0:
		scale_y = viewport.y / float(window.y)
	var top := EDGE_PADDING + int(maxf(0.0, float(safe.position.y)) * scale_y)
	var bottom := EDGE_PADDING + int(
			maxf(0.0, float(window.y - (safe.position.y + safe.size.y))) * scale_y)
	_safe_margin.add_theme_constant_override("margin_left", EDGE_PADDING)
	_safe_margin.add_theme_constant_override("margin_right", EDGE_PADDING)
	_safe_margin.add_theme_constant_override("margin_top", top)
	_safe_margin.add_theme_constant_override("margin_bottom", bottom)

func _notification(what: int) -> void:
	if what == NOTIFICATION_WM_GO_BACK_REQUEST:
		on_back()

# --- Small builders shared by screens ----------------------------------------

func make_title(text: String, size: int = GameTheme.SIZE_TITLE) -> Label:
	var label := Label.new()
	label.text = text
	label.add_theme_font_override("font", GameTheme.bold())
	label.add_theme_font_size_override("font_size", size)
	label.add_theme_color_override("font_color", Palette.TEXT)
	label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	label.text_direction = Loc.text_direction()
	label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	return label

func make_body(text: String, color: Color = Palette.TEXT_DIM,
		size: int = GameTheme.SIZE_BODY) -> Label:
	var label := Label.new()
	label.text = text
	label.add_theme_font_size_override("font_size", size)
	label.add_theme_color_override("font_color", color)
	label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	label.text_direction = Loc.text_direction()
	label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	return label

func make_button(text: String, on_press: Callable, primary: bool = false) -> Button:
	var button := Button.new()
	button.text = text
	button.custom_minimum_size = Vector2(0,
			GameTheme.TOUCH_PRIMARY if primary else GameTheme.TOUCH_STANDARD)
	button.layout_direction = Loc.layout_direction()
	if primary:
		button.add_theme_font_size_override("font_size", 30)
		button.add_theme_stylebox_override("normal",
				GameTheme.flat_box(Palette.TEXT, 18))
		button.add_theme_stylebox_override("hover",
				GameTheme.flat_box(Palette.WHITE, 18))
		button.add_theme_stylebox_override("pressed",
				GameTheme.flat_box(Palette.TEXT_DIM, 18))
		button.add_theme_color_override("font_color", Palette.BG)
		button.add_theme_color_override("font_hover_color", Palette.BG)
		button.add_theme_color_override("font_pressed_color", Palette.BG)
	button.pressed.connect(func() -> void:
		Audio.play(Audio.Sfx.BUTTON)
		on_press.call())
	return button

## A short label safe to place inside a tight HBoxContainer row: no autowrap,
## and sized to its own measured text rather than left to be squeezed toward
## zero width by neighbouring siblings. A container-shrunk, autowrap-enabled
## Label breaks its text one character per line — the same failure this
## fixes wherever it was found (puzzle board labels, result-screen stats,
## this menu's streak text); this is the version safe to reuse anywhere a
## short one-line status string sits next to other inline content.
func make_inline_label(text: String, color: Color = Palette.TEXT_FAINT,
		size: int = GameTheme.SIZE_SMALL, bold: bool = false) -> Label:
	var label := Label.new()
	label.text = text
	var font := GameTheme.semibold() if bold else GameTheme.regular()
	label.add_theme_font_override("font", font)
	label.add_theme_font_size_override("font_size", size)
	label.add_theme_color_override("font_color", color)
	label.text_direction = Loc.text_direction()
	label.autowrap_mode = TextServer.AUTOWRAP_OFF
	label.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	label.custom_minimum_size.x = font.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT,
			-1.0, size).x + 2.0
	return label

## Builds "{earned} / {total} {unit}" (e.g. "6 / 150 stars") as separate
## Label nodes in a row, rather than one templated string.
##
## Godot's Label/TextParagraph did not reliably keep two numbers in their
## template order when both sat inside one Arabic string — "6 / 150" could
## render as "150 / 6", which reads as a different (nonsensical) ratio to a
## player and was caught only by looking at an actual rendered frame, not by
## any string-equality test. Laying the pieces out as separate Controls
## sidesteps the ambiguity entirely: HBoxContainer.layout_direction is the
## same RTL-ordering mechanism already proven correct throughout this game
## (every button row, every stat row), so it is used here too instead of
## asking the text shaper to reorder digits inside a single run.
func make_ratio_line(earned: int, total: int, unit_key: String,
		color: Color = Palette.TEXT_FAINT, size: int = GameTheme.SIZE_SMALL) -> Control:
	var row := HBoxContainer.new()
	row.layout_direction = Loc.layout_direction()
	row.alignment = BoxContainer.ALIGNMENT_CENTER
	row.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	row.add_theme_constant_override("separation", 6)
	for text in [str(earned), "/", str(total), Loc.t(unit_key)]:
		var label := Label.new()
		label.text = text
		label.add_theme_font_size_override("font_size", size)
		label.add_theme_color_override("font_color", color)
		row.add_child(label)
	return row

func make_spacer(height: float = 0.0) -> Control:
	var spacer := Control.new()
	if height > 0.0:
		spacer.custom_minimum_size = Vector2(0, height)
	else:
		spacer.size_flags_vertical = Control.SIZE_EXPAND_FILL
	spacer.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return spacer

## A back affordance placed on the side the language starts from.
func make_back_row(on_press: Callable = Callable()) -> Control:
	var row := HBoxContainer.new()
	row.layout_direction = Loc.layout_direction()
	var button := Button.new()
	button.text = "‹  " + Loc.t("hud.back")
	button.custom_minimum_size = Vector2(0, GameTheme.TOUCH_LINK)
	button.add_theme_stylebox_override("normal", StyleBoxEmpty.new())
	button.add_theme_stylebox_override("hover", StyleBoxEmpty.new())
	button.add_theme_stylebox_override("pressed", StyleBoxEmpty.new())
	button.add_theme_color_override("font_color", Palette.TEXT_DIM)
	button.add_theme_font_size_override("font_size", GameTheme.SIZE_SMALL)
	button.pressed.connect(func() -> void:
		Audio.play(Audio.Sfx.BUTTON)
		if on_press.is_valid():
			on_press.call()
		else:
			on_back())
	row.add_child(button)
	row.add_child(make_spacer())
	return row
