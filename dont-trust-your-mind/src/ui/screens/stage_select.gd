extends ScreenBase

## Chapter-grouped stage grid. Locked stages stay visible so the shape of the
## game is legible from the first minute.

func build_content() -> void:
	content.add_child(make_back_row())
	content.add_child(make_title(Loc.t("stages.title"), 34))
	content.add_child(_summary())

	var scroll := ScrollContainer.new()
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	content.add_child(scroll)

	var column := VBoxContainer.new()
	column.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	column.layout_direction = Loc.layout_direction()
	column.add_theme_constant_override("separation", 22)
	scroll.add_child(column)

	for chapter in range(1, Puzzles.CHAPTER_COUNT + 1):
		var stages := Puzzles.stages_in_chapter(chapter)
		if stages.is_empty():
			continue
		column.add_child(_chapter_header(chapter))
		column.add_child(_chapter_grid(stages))

func _summary() -> Control:
	return make_ratio_line(SaveManager.total_stars(), Puzzles.total_stages() * 3,
			"stages.stars_unit")

func _chapter_header(chapter: int) -> Control:
	var box := VBoxContainer.new()
	box.add_theme_constant_override("separation", 2)
	box.layout_direction = Loc.layout_direction()

	var title := Label.new()
	title.text = "%s · %s" % [Loc.t("stages.chapter", {"n": chapter}),
			Loc.t("chapter.%d" % chapter)]
	title.add_theme_font_override("font", GameTheme.semibold())
	title.add_theme_font_size_override("font_size", GameTheme.SIZE_BODY)
	title.add_theme_color_override("font_color", Palette.TEXT)
	title.horizontal_alignment = Loc.start_alignment()
	box.add_child(title)

	var sub := Label.new()
	sub.text = Loc.t("chapter.%d.sub" % chapter)
	sub.add_theme_font_size_override("font_size", 18)
	sub.add_theme_color_override("font_color", Palette.TEXT_FAINT)
	sub.horizontal_alignment = Loc.start_alignment()
	box.add_child(sub)
	return box

func _chapter_grid(stages: Array[int]) -> Control:
	var grid := GridContainer.new()
	grid.columns = 5
	grid.layout_direction = Loc.layout_direction()
	grid.add_theme_constant_override("h_separation", 10)
	grid.add_theme_constant_override("v_separation", 10)
	for stage in stages:
		grid.add_child(_stage_tile(stage))
	return grid

## Every tile reserves the same height whether or not it has stars, so the grid
## stays on a single baseline. Locked tiles are dimmed rather than badged with an
## emoji, which would depend on a system font the game does not ship.
func _stage_tile(stage: int) -> Control:
	var unlocked := Game.is_unlocked(stage)
	var stars := SaveManager.stars_for(stage)

	var button := Button.new()
	button.custom_minimum_size = Vector2(0, 92)
	button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	button.size_flags_vertical = Control.SIZE_EXPAND_FILL
	button.disabled = not unlocked
	button.layout_direction = Loc.layout_direction()
	button.text = str(stage)
	button.add_theme_font_size_override("font_size", 28)
	if not unlocked:
		button.add_theme_color_override("font_disabled_color", Palette.TEXT_FAINT)
		button.add_theme_stylebox_override("disabled",
				GameTheme.flat_box(Palette.BG, 14, 2, Palette.SURFACE))
	elif stars > 0:
		var tint := Palette.GOLD if stars == 3 else Palette.SURFACE_HI
		button.add_theme_stylebox_override("normal",
				GameTheme.flat_box(Palette.SURFACE, 14, 2, tint))
	button.pressed.connect(func() -> void:
		Audio.play(Audio.Sfx.BUTTON)
		Game.start_stage(stage))

	var stack := VBoxContainer.new()
	stack.add_theme_constant_override("separation", 4)
	stack.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	stack.custom_minimum_size = Vector2(0, 116)
	stack.add_child(button)

	var row := StarRow.new()
	row.custom_minimum_size = Vector2(0, 14)
	stack.add_child(row)
	row.setup(stars if unlocked else 0, 3, 12.0)
	row.modulate.a = 1.0 if stars > 0 else 0.0
	return stack
