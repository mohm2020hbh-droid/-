extends Node

## UI strings + writing direction.
##
## Puzzle text is NOT routed through here: each puzzle carries its own per-locale
## spec, because puzzles can differ in content and in answer between languages.
## This manager owns the shell around them.

signal locale_changed(code: String)

const STRINGS_PATH := "res://content/locale/ui_strings.json"
const SUPPORTED := ["ar", "en"]
const RTL_LOCALES := ["ar"]

var locale: String = "en"
var _strings: Dictionary = {}

func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	_load_strings()

func _load_strings() -> void:
	var f := FileAccess.open(STRINGS_PATH, FileAccess.READ)
	if f == null:
		push_error("Missing UI strings at %s" % STRINGS_PATH)
		return
	var parsed = JSON.parse_string(f.get_as_text())
	f.close()
	if typeof(parsed) != TYPE_DICTIONARY:
		push_error("UI strings file is not a JSON object.")
		return
	_strings = parsed

## Best-guess locale for a first run, from the device language.
func detect_locale() -> String:
	var sys := OS.get_locale_language()
	return sys if sys in SUPPORTED else "en"

func set_locale(code: String) -> void:
	if code not in SUPPORTED:
		code = "en"
	if code == locale:
		return
	locale = code
	TranslationServer.set_locale(code)
	locale_changed.emit(code)

func is_rtl() -> bool:
	return locale in RTL_LOCALES

func layout_direction() -> int:
	return Control.LAYOUT_DIRECTION_RTL if is_rtl() else Control.LAYOUT_DIRECTION_LTR

func text_direction() -> int:
	return TextServer.DIRECTION_RTL if is_rtl() else TextServer.DIRECTION_LTR

func base_direction() -> int:
	return TextServer.DIRECTION_RTL if is_rtl() else TextServer.DIRECTION_LTR

## Horizontal alignment that means "the side text starts on".
func start_alignment() -> int:
	return HORIZONTAL_ALIGNMENT_RIGHT if is_rtl() else HORIZONTAL_ALIGNMENT_LEFT

func end_alignment() -> int:
	return HORIZONTAL_ALIGNMENT_LEFT if is_rtl() else HORIZONTAL_ALIGNMENT_RIGHT

## Look up a UI string. Missing keys surface loudly rather than silently blank.
func t(key: String, args: Dictionary = {}) -> String:
	var entry = _strings.get(key, null)
	if entry == null:
		push_warning("Missing UI string: %s" % key)
		return key
	var text := str(entry.get(locale, entry.get("en", key)))
	for k in args:
		text = text.replace("{%s}" % k, str(args[k]))
	return text

func has_key(key: String) -> bool:
	return _strings.has(key)

func all_keys() -> Array:
	return _strings.keys()

## Formats seconds as "12.4s" / "١٢٫٤ث"-free plain digits, kept LTR-safe inside
## Arabic sentences by relying on the BiDi algorithm rather than manual flipping.
func format_seconds(seconds: float) -> String:
	return t("fmt.seconds", {"v": "%.1f" % seconds})

func format_stage(n: int) -> String:
	return t("fmt.stage", {"n": n})
