extends ScreenBase

## Eight achievements, each tied to a behaviour the game wants to encourage:
## finishing, going fast, going without hints, and coming back tomorrow.

func build_content() -> void:
	content.add_child(make_back_row())
	content.add_child(make_title(Loc.t("ach.title"), 34))
	content.add_child(make_spacer(8))

	var scroll := ScrollContainer.new()
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	content.add_child(scroll)

	var column := VBoxContainer.new()
	column.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	column.layout_direction = Loc.layout_direction()
	column.add_theme_constant_override("separation", 10)
	scroll.add_child(column)

	for id in Game.achievement_ids():
		column.add_child(_row(id))

func _row(id: String) -> Control:
	var unlocked := SaveManager.has_achievement(id)
	var panel := PanelContainer.new()
	panel.layout_direction = Loc.layout_direction()
	panel.add_theme_stylebox_override("panel", GameTheme.flat_box(
			Palette.SURFACE, 16, 2, Palette.GOLD if unlocked else Palette.SURFACE))

	var box := VBoxContainer.new()
	box.add_theme_constant_override("separation", 3)
	box.layout_direction = Loc.layout_direction()
	panel.add_child(box)

	var title := Label.new()
	title.text = Loc.t("ach.%s" % id)
	title.add_theme_font_override("font", GameTheme.semibold())
	title.add_theme_font_size_override("font_size", GameTheme.SIZE_BODY)
	title.add_theme_color_override("font_color",
			Palette.TEXT if unlocked else Palette.TEXT_FAINT)
	title.horizontal_alignment = Loc.start_alignment()
	box.add_child(title)

	var body := Label.new()
	body.text = Loc.t("ach.%s.d" % id) if unlocked else Loc.t("ach.locked")
	body.add_theme_font_size_override("font_size", 19)
	body.add_theme_color_override("font_color", Palette.TEXT_FAINT)
	body.horizontal_alignment = Loc.start_alignment()
	body.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	box.add_child(body)
	return panel
