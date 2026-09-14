class_name GameTheme
extends RefCounted

## Builds the single Theme resource used by every screen, in code, so the look
## stays consistent and there is no .tres to drift out of sync with the palette.

const FONT_REGULAR := "res://assets/fonts/Cairo-Regular.ttf"
const FONT_SEMIBOLD := "res://assets/fonts/Cairo-SemiBold.ttf"
const FONT_BOLD := "res://assets/fonts/Cairo-Bold.ttf"

const SIZE_TITLE := 44
const SIZE_INSTRUCTION := 34
const SIZE_BODY := 26
const SIZE_SMALL := 21
const SIZE_BUTTON := 26

static func regular() -> Font:
	return load(FONT_REGULAR)

static func semibold() -> Font:
	return load(FONT_SEMIBOLD)

static func bold() -> Font:
	return load(FONT_BOLD)

static func build() -> Theme:
	var theme := Theme.new()
	theme.default_font = regular()
	theme.default_font_size = SIZE_BODY

	_style_label(theme)
	_style_button(theme)
	_style_panel(theme)
	_style_misc(theme)
	return theme

static func _style_label(theme: Theme) -> void:
	theme.set_color("font_color", "Label", Palette.TEXT)
	theme.set_color("font_outline_color", "Label", Palette.BG)
	theme.set_constant("outline_size", "Label", 0)

static func flat_box(bg: Color, radius: int = 16, border: int = 0,
		border_color: Color = Palette.LINE) -> StyleBoxFlat:
	var sb := StyleBoxFlat.new()
	sb.bg_color = bg
	sb.corner_radius_top_left = radius
	sb.corner_radius_top_right = radius
	sb.corner_radius_bottom_left = radius
	sb.corner_radius_bottom_right = radius
	if border > 0:
		sb.border_width_left = border
		sb.border_width_right = border
		sb.border_width_top = border
		sb.border_width_bottom = border
		sb.border_color = border_color
	sb.content_margin_left = 22
	sb.content_margin_right = 22
	sb.content_margin_top = 14
	sb.content_margin_bottom = 14
	sb.anti_aliasing = true
	return sb

static func _style_button(theme: Theme) -> void:
	theme.set_font("font", "Button", semibold())
	theme.set_font_size("font_size", "Button", SIZE_BUTTON)
	theme.set_color("font_color", "Button", Palette.TEXT)
	theme.set_color("font_hover_color", "Button", Palette.WHITE)
	theme.set_color("font_pressed_color", "Button", Palette.TEXT_DIM)
	theme.set_color("font_disabled_color", "Button", Palette.TEXT_FAINT)
	theme.set_stylebox("normal", "Button", flat_box(Palette.SURFACE, 16, 2, Palette.LINE))
	theme.set_stylebox("hover", "Button", flat_box(Palette.SURFACE_HI, 16, 2, Palette.LINE))
	theme.set_stylebox("pressed", "Button", flat_box(Palette.LINE, 16, 2, Palette.LINE))
	theme.set_stylebox("disabled", "Button", flat_box(Palette.SURFACE, 16, 2, Palette.SURFACE_HI))
	theme.set_stylebox("focus", "Button", StyleBoxEmpty.new())

static func _style_panel(theme: Theme) -> void:
	theme.set_stylebox("panel", "PanelContainer", flat_box(Palette.SURFACE, 22))
	theme.set_stylebox("panel", "Panel", flat_box(Palette.SURFACE, 22))

static func _style_misc(theme: Theme) -> void:
	theme.set_font("font", "CheckButton", regular())
	theme.set_font_size("font_size", "CheckButton", SIZE_BODY)
	theme.set_color("font_color", "CheckButton", Palette.TEXT)
	theme.set_color("font_pressed_color", "CheckButton", Palette.TEXT)
	theme.set_color("font_hover_color", "CheckButton", Palette.WHITE)
	theme.set_stylebox("normal", "CheckButton", StyleBoxEmpty.new())
	theme.set_stylebox("hover", "CheckButton", StyleBoxEmpty.new())
	theme.set_stylebox("pressed", "CheckButton", StyleBoxEmpty.new())
	theme.set_stylebox("focus", "CheckButton", StyleBoxEmpty.new())

	theme.set_font("font", "HSlider", regular())
	theme.set_stylebox("slider", "HSlider", flat_box(Palette.SURFACE_HI, 6))
	theme.set_stylebox("grabber_area", "HSlider", flat_box(Palette.TEXT_DIM, 6))
	theme.set_stylebox("grabber_area_highlight", "HSlider", flat_box(Palette.TEXT, 6))
