class_name UiLook
## Dresses the UI in the current world's look (see [Palette]): World 01 keeps
## the scenes' own reds; World 02 gets a black-and-white UI theme and World 03
## a cold violet one, and every colored label is remapped by role (accent,
## text, muted) to the palette.

const MONO_THEME := preload("res://src/ui/theme_mono.tres")
const GALAXY_THEME := preload("res://src/ui/theme_galaxy.tres")
const META := &"ui_look_original"


## Applies [param look] to every Control under [param root].
static func apply(root: Node, look: StringName) -> void:
	for node in root.find_children("*", "Control", true, false):
		var control := node as Control
		if control.get_parent() is CanvasLayer or control.get_parent() == root:
			control.theme = _theme_for(look)
		if control is Label and control.has_theme_color_override(&"font_color"):
			if not control.has_meta(META):
				control.set_meta(META, control.get_theme_color(&"font_color"))
			var original: Color = control.get_meta(META)
			control.add_theme_color_override(&"font_color", original if look == &"red" else _mono(original))
		control.queue_redraw()


static func _theme_for(look: StringName) -> Theme:
	match look:
		&"mono":
			return MONO_THEME
		&"galaxy":
			return GALAXY_THEME
	return null


## The stand-in for a World 01 UI color in the current palette, by its role.
static func _mono(c: Color) -> Color:
	if c.s > 0.45 and c.v > 0.5:
		return Color(Palette.UI_ACCENT, c.a)
	if c.v > 0.85:
		return Color(Palette.UI_TEXT, c.a)
	return Color(Palette.UI_MUTED, c.a)


## A grey copy of [param g] (same stops and alpha, lightness kept).
static func grey_gradient(g: Gradient) -> Gradient:
	var out := g.duplicate() as Gradient
	var colors := out.colors
	for i in colors.size():
		var c := colors[i]
		var v := clampf(maxf(c.r, maxf(c.g, c.b)) * 0.95 + 0.05, 0.0, 1.0)
		colors[i] = Color(v, v, v, c.a)
	out.colors = colors
	return out
