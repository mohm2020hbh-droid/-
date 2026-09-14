extends ScreenBase

## Daily puzzle gate. The puzzle for a date is derived from the date itself, so
## every device agrees without a server and the game stays fully offline.

func build_content() -> void:
	var day := Puzzles.today_key()
	var record := SaveManager.daily_record(day)
	var done := bool(record.get("solved", false))

	content.add_child(make_back_row())
	content.add_child(make_spacer())
	content.add_child(make_title(Loc.t("daily.title"), 38))
	content.add_child(make_body(day, Palette.TEXT_FAINT, GameTheme.SIZE_SMALL))
	content.add_child(make_spacer(24))
	content.add_child(make_body(
			Loc.t("daily.streak", {"n": int(SaveManager.data.get("streak", 0))}),
			Palette.TEXT, GameTheme.SIZE_BODY))
	content.add_child(make_spacer(24))

	if done:
		_build_done(record, day)
	else:
		content.add_child(make_button(Loc.t("daily.start"),
				func() -> void: Game.start_daily(), true))
	content.add_child(make_spacer())

func _build_done(record: Dictionary, day: String) -> void:
	content.add_child(make_body(Loc.t("daily.done"), Palette.GREEN, GameTheme.SIZE_BODY))
	var stars := StarRow.new()
	content.add_child(stars)
	stars.setup(int(record.get("stars", 0)))
	content.add_child(make_spacer(10))
	content.add_child(make_body(
			"%s  %s" % [Loc.t("result.time"),
					Loc.format_seconds(float(record.get("time", 0.0)))],
			Palette.TEXT_DIM, GameTheme.SIZE_SMALL))
	content.add_child(make_spacer(18))
	content.add_child(make_button(Loc.t("result.share"), func() -> void:
		Game.share(Game.share_text_for_daily(day, float(record.get("time", 0.0))))
		_flash_copied()))
	content.add_child(make_spacer(10))
	content.add_child(make_body(Loc.t("daily.come_back"), Palette.TEXT_FAINT, 18))

func _flash_copied() -> void:
	var label := make_body(Loc.t("result.copied"), Palette.GREEN, GameTheme.SIZE_SMALL)
	content.add_child(label)
	var tween := create_tween()
	tween.tween_interval(1.2)
	tween.tween_property(label, "modulate:a", 0.0, 0.4)
	tween.tween_callback(label.queue_free)
