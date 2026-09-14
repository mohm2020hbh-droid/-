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
	label.text_direction = Control.TEXT_DIRECTION_AUTO
	label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	return label

func make_body(text: String, color: Color = Palette.TEXT_DIM,
		size: int = GameTheme.SIZE_BODY) -> Label:
	var label := Label.new()
	label.text = text
	label.add_theme_font_size_override("font_size", size)
	label.add_theme_color_override("font_color", color)
	label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	label.text_direction = Control.TEXT_DIRECTION_AUTO
	label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	return label

func make_button(text: String, on_press: Callable, primary: bool = false) -> Button:
	var button := Button.new()
	button.text = text
	button.custom_minimum_size = Vector2(0, 76)
	button.layout_direction = Loc.layout_direction()
	if primary:
		button.add_theme_stylebox_override("normal",
				GameTheme.flat_box(Palette.TEXT, 16))
		button.add_theme_stylebox_override("hover",
				GameTheme.flat_box(Palette.WHITE, 16))
		button.add_theme_stylebox_override("pressed",
				GameTheme.flat_box(Palette.TEXT_DIM, 16))
		button.add_theme_color_override("font_color", Palette.BG)
		button.add_theme_color_override("font_hover_color", Palette.BG)
		button.add_theme_color_override("font_pressed_color", Palette.BG)
	button.pressed.connect(func() -> void:
		Audio.play(Audio.Sfx.TAP)
		on_press.call())
	return button

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
	button.custom_minimum_size = Vector2(0, 60)
	button.add_theme_stylebox_override("normal", StyleBoxEmpty.new())
	button.add_theme_stylebox_override("hover", StyleBoxEmpty.new())
	button.add_theme_stylebox_override("pressed", StyleBoxEmpty.new())
	button.add_theme_color_override("font_color", Palette.TEXT_DIM)
	button.add_theme_font_size_override("font_size", GameTheme.SIZE_SMALL)
	button.pressed.connect(func() -> void:
		Audio.play(Audio.Sfx.TAP)
		if on_press.is_valid():
			on_press.call()
		else:
			on_back())
	row.add_child(button)
	row.add_child(make_spacer())
	return row
