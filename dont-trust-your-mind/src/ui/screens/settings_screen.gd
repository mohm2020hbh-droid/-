extends ScreenBase

## Settings. Changing language rebuilds the screen in place so the player sees
## the whole interface flip direction immediately, with no progress lost.

func build_content() -> void:
	content.add_child(make_back_row())
	content.add_child(make_title(Loc.t("settings.title"), 34))
	content.add_child(make_spacer(10))

	content.add_child(_section(Loc.t("settings.language")))
	content.add_child(_language_row())
	content.add_child(make_body(Loc.t("settings.lang_note"), Palette.TEXT_FAINT, 18))
	content.add_child(make_spacer(16))

	content.add_child(_section(Loc.t("settings.volume")))
	content.add_child(_volume_row())
	content.add_child(_toggle(Loc.t("settings.sfx"), "sfx"))
	content.add_child(_toggle(Loc.t("settings.haptics"), "haptics"))
	content.add_child(_toggle(Loc.t("settings.reduce_motion"), "reduce_motion"))

	content.add_child(make_spacer())
	content.add_child(_reset_button())
	content.add_child(make_body(Loc.t("settings.credits"), Palette.TEXT_FAINT, 16))

func _section(text: String) -> Label:
	var label := Label.new()
	label.text = text
	label.add_theme_font_size_override("font_size", 19)
	label.add_theme_color_override("font_color", Palette.TEXT_FAINT)
	label.horizontal_alignment = Loc.start_alignment()
	return label

func _language_row() -> Control:
	var row := HBoxContainer.new()
	row.layout_direction = Loc.layout_direction()
	row.add_theme_constant_override("separation", 10)
	for code in Loc.SUPPORTED:
		var button := Button.new()
		button.text = "العربية" if code == "ar" else "English"
		button.custom_minimum_size = Vector2(0, GameTheme.TOUCH_STANDARD)
		button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		if code == Loc.locale:
			button.add_theme_stylebox_override("normal",
					GameTheme.flat_box(Palette.SURFACE_HI, 16, 2, Palette.TEXT_DIM))
		button.pressed.connect(_switch_language.bind(code))
		row.add_child(button)
	return row

func _switch_language(code: String) -> void:
	if code == Loc.locale:
		return
	Audio.play(Audio.Sfx.BUTTON)
	SaveManager.set_language(code)
	SaveManager.save_now()
	Loc.set_locale(code)
	Game.goto("settings")

func _volume_row() -> Control:
	var slider := HSlider.new()
	slider.min_value = 0.0
	slider.max_value = 1.0
	slider.step = 0.05
	slider.value = float(SaveManager.get_setting("volume", 0.8))
	slider.custom_minimum_size = Vector2(0, 56)
	slider.layout_direction = Loc.layout_direction()
	slider.value_changed.connect(func(value: float) -> void:
		SaveManager.set_setting("volume", value))
	slider.drag_ended.connect(func(changed: bool) -> void:
		if changed:
			Audio.play(Audio.Sfx.BUTTON))
	return slider

func _toggle(label: String, key: String) -> Control:
	var button := CheckButton.new()
	button.text = label
	button.button_pressed = bool(SaveManager.get_setting(key, true))
	button.custom_minimum_size = Vector2(0, 76)
	button.layout_direction = Loc.layout_direction()
	button.toggled.connect(func(on: bool) -> void:
		SaveManager.set_setting(key, on)
		Audio.play(Audio.Sfx.BUTTON))
	return button

func _reset_button() -> Control:
	var button := Button.new()
	button.text = Loc.t("settings.reset")
	button.custom_minimum_size = Vector2(0, GameTheme.TOUCH_STANDARD)
	button.layout_direction = Loc.layout_direction()
	button.add_theme_color_override("font_color", Palette.RED)
	button.pressed.connect(_confirm_reset)
	return button

func _confirm_reset() -> void:
	Audio.play(Audio.Sfx.BUTTON)
	var dialog := ConfirmationDialog.new()
	dialog.dialog_text = Loc.t("settings.reset_confirm")
	dialog.ok_button_text = Loc.t("settings.yes")
	dialog.cancel_button_text = Loc.t("settings.no")
	dialog.theme = theme
	add_child(dialog)
	dialog.confirmed.connect(func() -> void:
		SaveManager.reset_progress()
		Audio.play(Audio.Sfx.TRANSITION)
		Game.goto("menu"))
	dialog.popup_centered()
