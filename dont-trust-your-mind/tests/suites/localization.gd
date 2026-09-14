extends TestSuite

## Checks the language layer, including the behaviour the game's puzzles depend
## on: that character 0 of a word sits on opposite sides of the screen in Arabic
## and English.

var _ts: TextServer = TextServerManager.get_primary_interface()

func suite_name() -> String:
	return "localization and RTL"

func run() -> void:
	var original := Loc.locale
	_test_strings()
	_test_direction()
	_test_bidi_positions()
	_test_font_coverage()
	Loc.set_locale(original)

func _test_strings() -> void:
	for loc in Loc.SUPPORTED:
		Loc.set_locale(loc)
		for key in Loc.all_keys():
			var value := Loc.t(key)
			check(not value.is_empty(), "[%s] string is not empty: %s" % [loc, key])
			check(value != key or key.begins_with("lang."),
					"[%s] string is translated: %s" % [loc, key])
	Loc.set_locale("ar")
	equal(Loc.t("fmt.stage", {"n": 7}), "مرحلة 7", "argument substitution works in Arabic")
	Loc.set_locale("en")
	equal(Loc.t("fmt.stage", {"n": 7}), "Stage 7", "argument substitution works in English")

func _test_direction() -> void:
	Loc.set_locale("ar")
	check(Loc.is_rtl(), "Arabic is right-to-left")
	equal(Loc.layout_direction(), Control.LAYOUT_DIRECTION_RTL, "Arabic layout direction")
	equal(Loc.start_alignment(), HORIZONTAL_ALIGNMENT_RIGHT, "Arabic text starts on the right")
	Loc.set_locale("en")
	check(not Loc.is_rtl(), "English is left-to-right")
	equal(Loc.layout_direction(), Control.LAYOUT_DIRECTION_LTR, "English layout direction")
	equal(Loc.start_alignment(), HORIZONTAL_ALIGNMENT_LEFT, "English text starts on the left")

## The core promise of the "tap the first letter" family of puzzles.
func _test_bidi_positions() -> void:
	var font := GameTheme.bold()

	var arabic := _boxes("بداية", "ar", font)
	check(arabic.size() == 5, "Arabic word shapes into 5 graphemes (got %d)" % arabic.size())
	if arabic.size() == 5:
		check(arabic[0].x > arabic[4].x,
				"Arabic character 0 is drawn to the right of the last character")

	var english := _boxes("START", "en", font)
	check(english.size() == 5, "English word shapes into 5 graphemes (got %d)" % english.size())
	if english.size() == 5:
		check(english[0].x < english[4].x,
				"English character 0 is drawn to the left of the last character")

	# Arabic must stay cursive: shaping a joined word produces glyphs that are
	# narrower than the same letters shaped in isolation would be.
	var joined := _shape("بداية", "ar", font).get_size().x
	var isolated := 0.0
	for letter in ["ب", "د", "ا", "ي", "ة"]:
		isolated += _shape(letter, "ar", font).get_size().x
	check(joined < isolated,
			"Arabic text is shaped cursively, not letter by letter (%.0f < %.0f)"
					% [joined, isolated])

func _test_font_coverage() -> void:
	var font := GameTheme.bold()
	for sample in ["أبجد هوز", "ABC xyz", "0123456789", "؟،.!", "٠١٢٣"]:
		var line := _shape(sample, "ar", font)
		check(line.get_size().x > 0.0, "font renders sample: %s" % sample)
		var rid := line.get_rid()
		var glyphs := _ts.shaped_text_get_glyphs(rid)
		var missing := 0
		for glyph in glyphs:
			if int(glyph.get("index", 1)) == 0:
				missing += 1
		equal(missing, 0, "no missing glyphs in sample: %s" % sample)

func _shape(text: String, loc: String, font: Font) -> TextLine:
	var line := TextLine.new()
	line.direction = TextServer.DIRECTION_RTL if loc == "ar" else TextServer.DIRECTION_LTR
	line.add_string(text, font, 64, loc)
	return line

func _boxes(text: String, loc: String, font: Font) -> Array[Vector2]:
	var line := _shape(text, loc, font)
	var rid := line.get_rid()
	var breaks := _ts.shaped_text_get_character_breaks(rid)
	var out: Array[Vector2] = []
	var start := 0
	for i in range(breaks.size()):
		var ranges := _ts.shaped_text_get_selection(rid, start, breaks[i])
		out.append(ranges[0] if not ranges.is_empty() else Vector2.ZERO)
		start = breaks[i]
	return out
