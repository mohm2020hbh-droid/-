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
	_test_multi_number_bidi_order()
	Loc.set_locale(original)

static func _strip_isolates(text: String) -> String:
	return text.replace(char(0x2068), "").replace(char(0x2069), "")

func _test_strings() -> void:
	for loc in Loc.SUPPORTED:
		Loc.set_locale(loc)
		for key in Loc.all_keys():
			var value := Loc.t(key)
			check(not value.is_empty(), "[%s] string is not empty: %s" % [loc, key])
			check(value != key or key.begins_with("lang."),
					"[%s] string is translated: %s" % [loc, key])
	# Substituted values are wrapped in bidi isolates (U+2068/U+2069) so a
	# template with more than one number can't have them visually reorder in
	# Arabic (see Loc.t()'s docstring for the concrete bug this prevents).
	# The isolates are invisible but present in the string, so compare with
	# them stripped rather than asserting exact equality against plain text.
	Loc.set_locale("ar")
	equal(_strip_isolates(Loc.t("fmt.stage", {"n": 7})), "مرحلة 7",
			"argument substitution works in Arabic")
	Loc.set_locale("en")
	equal(_strip_isolates(Loc.t("fmt.stage", {"n": 7})), "Stage 7",
			"argument substitution works in English")

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

## Proves the actual bug found on the stage-select screen is fixed: a
## template with two numbers ("6 / 150 stars") rendered in Arabic with the
## numbers visually swapped — a pure string-equality test cannot catch this,
## since the character order in the string was already correct; only the
## on-screen pixel order was wrong. This shapes the real templated string
## through TextServer and checks where "6" and "150" actually land.
func _test_multi_number_bidi_order() -> void:
	Loc.set_locale("ar")
	var font := GameTheme.regular()
	var text := Loc.t("stages.stars", {"a": 6, "b": 150})
	check(text.contains(char(0x2068)), "substituted numbers are wrapped in bidi isolates")

	var line := _shape(text, "ar", font)
	var rid := line.get_rid()

	var pos_a := text.find("6")
	var pos_b := text.find("150")
	check(pos_a >= 0 and pos_b >= 0 and pos_a < pos_b,
			"sanity: '6' still precedes '150' in logical (character) order")

	var box_a := _ts.shaped_text_get_selection(rid, pos_a, pos_a + 1)
	var box_b := _ts.shaped_text_get_selection(rid, pos_b, pos_b + 3)
	check(not box_a.is_empty() and not box_b.is_empty(),
			"both numbers resolve to a visual position in the shaped line")
	if box_a.is_empty() or box_b.is_empty():
		return

	# RTL reading order: the value that comes first in the template ("a" = 6,
	# the earned star count) must sit to the right of the one that comes
	# second ("b" = 150, the total) — the same "first = rightmost" rule the
	# rest of this suite already proves for word-initial characters.
	var x_a: float = box_a[0].x
	var x_b: float = box_b[0].x
	check(x_a > x_b,
			"the first number (6) sits to the right of the second (150) in Arabic (x=%.0f vs x=%.0f)"
					% [x_a, x_b])

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
