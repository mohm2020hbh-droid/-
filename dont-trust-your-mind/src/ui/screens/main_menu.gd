extends ScreenBase

## Hub. Keeps the play button dominant; everything else is secondary.

func on_back() -> void:
	get_tree().quit()

func build_content() -> void:
	content.alignment = BoxContainer.ALIGNMENT_CENTER
	content.add_child(make_spacer(10))
	content.add_child(_header())
	content.add_child(make_spacer())
	content.add_child(_motif())
	content.add_child(make_spacer())

	var stage := Game.continue_stage()
	var solved := SaveManager.solved_count()
	var label := Loc.t("menu.play") if solved == 0 \
			else "%s  ·  %s" % [Loc.t("menu.continue"), Loc.format_stage(stage)]
	content.add_child(make_button(label, func() -> void: Game.start_stage(stage), true))
	content.add_child(make_spacer(12))
	content.add_child(make_button(Loc.t("menu.stages"), func() -> void: Game.goto("stages")))
	content.add_child(make_spacer(10))
	content.add_child(make_button(_daily_label(), func() -> void: Game.goto("daily")))
	content.add_child(make_spacer(10))

	var row := HBoxContainer.new()
	row.layout_direction = Loc.layout_direction()
	row.add_theme_constant_override("separation", 10)
	var achievements := make_button(Loc.t("menu.achievements"),
			func() -> void: Game.goto("achievements"))
	achievements.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var settings := make_button(Loc.t("menu.settings"),
			func() -> void: Game.goto("settings"))
	settings.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(achievements)
	row.add_child(settings)
	content.add_child(row)
	content.add_child(make_spacer(16))
	content.add_child(_stats_line())

func _header() -> Control:
	var box := VBoxContainer.new()
	box.add_theme_constant_override("separation", 6)
	box.add_child(make_title(Loc.t("app.title"), 42))
	box.add_child(make_body(Loc.t("app.tagline"), Palette.TEXT_FAINT, GameTheme.SIZE_SMALL))
	return box

## The game's mark: a ring with an off-centre pupil that never quite looks where
## you expect. Drawn, not imported, so it costs nothing and scales cleanly.
func _motif() -> Control:
	var motif := _Motif.new()
	motif.custom_minimum_size = Vector2(0, 150)
	motif.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return motif

class _Motif extends Control:
	func _draw() -> void:
		var centre := size * 0.5
		var radius := minf(size.y, 150.0) * 0.42
		draw_arc(centre, radius, 0.0, TAU, 64, Palette.LINE, 3.0, true)
		draw_arc(centre, radius * 0.66, 0.0, TAU, 48, Palette.SURFACE_HI, 2.0, true)
		draw_circle(centre + Vector2(radius * 0.22, -radius * 0.1), radius * 0.16, Palette.RED)

func _daily_label() -> String:
	var day := Puzzles.today_key()
	var done := bool(SaveManager.daily_record(day).get("solved", false))
	if done:
		return "%s  ✓" % Loc.t("menu.daily")
	return Loc.t("menu.daily")

func _stats_line() -> Control:
	var row := HBoxContainer.new()
	row.layout_direction = Loc.layout_direction()
	row.alignment = BoxContainer.ALIGNMENT_CENTER
	row.add_theme_constant_override("separation", 12)
	row.add_child(make_ratio_line(SaveManager.total_stars(), Puzzles.total_stages() * 3,
			"stages.stars_unit"))
	if int(SaveManager.data.get("streak", 0)) > 0:
		row.add_child(make_inline_label("·", Palette.TEXT_FAINT, GameTheme.SIZE_SMALL))
		row.add_child(make_inline_label(
				Loc.t("daily.streak", {"n": int(SaveManager.data.get("streak", 0))}),
				Palette.TEXT_FAINT, GameTheme.SIZE_SMALL))
	return row
