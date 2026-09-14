extends ScreenBase

## Two sentences of contract-setting before the first puzzle: the game promises
## that answers are logical, and warns that they are not the first thing you think of.

func on_back() -> void:
	pass

func build_content() -> void:
	content.alignment = BoxContainer.ALIGNMENT_CENTER
	content.add_child(make_spacer())
	var title := make_title(Loc.t("app.title"), 40)
	title.add_theme_color_override("font_color", Palette.TEXT)
	content.add_child(title)
	content.add_child(make_spacer(40))
	content.add_child(make_body(Loc.t("intro.line1"), Palette.TEXT, GameTheme.SIZE_INSTRUCTION - 4))
	content.add_child(make_spacer(16))
	content.add_child(make_body(Loc.t("intro.line2"), Palette.TEXT_DIM, GameTheme.SIZE_BODY))
	content.add_child(make_spacer())
	content.add_child(make_button(Loc.t("intro.begin"), _begin, true))
	content.add_child(make_spacer(20))

func _begin() -> void:
	SaveManager.data["seen_intro"] = true
	SaveManager.save_now()
	Game.start_stage(Game.continue_stage())
