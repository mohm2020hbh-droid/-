extends ScreenBase

## First-run language picker. Deliberately shows both options in their own
## script, so a player who cannot read one of them can still choose correctly.

func on_back() -> void:
	pass  # Nothing to go back to.

func build_content() -> void:
	content.alignment = BoxContainer.ALIGNMENT_CENTER
	content.add_child(make_spacer())
	content.add_child(make_title(Loc.t("lang.pick_title"), 38))
	content.add_child(make_body(Loc.t("lang.pick_note"), Palette.TEXT_DIM, GameTheme.SIZE_SMALL))
	content.add_child(make_spacer(28))
	content.add_child(_option("العربية", "ar"))
	content.add_child(make_spacer(14))
	content.add_child(_option("English", "en"))
	content.add_child(make_spacer())

func _option(label: String, code: String) -> Button:
	var button := Button.new()
	button.text = label
	button.custom_minimum_size = Vector2(0, 92)
	button.add_theme_font_size_override("font_size", 32)
	button.add_theme_font_override("font", GameTheme.semibold())
	button.pressed.connect(func() -> void:
		Audio.play(Audio.Sfx.CORRECT)
		SaveManager.set_language(code)
		SaveManager.save_now()
		Loc.set_locale(code)
		Game.goto("intro"))
	return button
