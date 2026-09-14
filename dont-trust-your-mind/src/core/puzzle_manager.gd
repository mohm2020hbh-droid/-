extends Node

## Owns puzzle content: loads the JSON chapters once, hands out typed
## [PuzzleDefinition]s resolved for the active locale, and picks the daily puzzle.

const CHAPTER_FILES := [
	"res://content/puzzles/chapter_01.json",
	"res://content/puzzles/chapter_02.json",
	"res://content/puzzles/chapter_03.json",
	"res://content/puzzles/chapter_04.json",
	"res://content/puzzles/chapter_05.json",
	"res://content/puzzles/chapter_06.json",
	"res://content/puzzles/chapter_07.json",
	"res://content/puzzles/chapter_08.json",
	"res://content/puzzles/chapter_09.json",
	"res://content/puzzles/chapter_10.json",
]
const DAILY_FILE := "res://content/puzzles/daily.json"
const CHAPTER_COUNT := 10

var _raw: Array[Dictionary] = []          ## Campaign puzzles in stage order.
var _daily_raw: Array[Dictionary] = []
var _load_errors: PackedStringArray = PackedStringArray()

func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	reload()

func reload() -> void:
	_raw.clear()
	_daily_raw.clear()
	_load_errors.clear()
	for path in CHAPTER_FILES:
		_load_file(path, _raw)
	_load_file(DAILY_FILE, _daily_raw)
	if not _load_errors.is_empty():
		for err in _load_errors:
			push_error(err)

func _load_file(path: String, into: Array[Dictionary]) -> void:
	var f := FileAccess.open(path, FileAccess.READ)
	if f == null:
		_load_errors.append("Cannot open puzzle file: %s" % path)
		return
	var parsed = JSON.parse_string(f.get_as_text())
	f.close()
	if typeof(parsed) != TYPE_DICTIONARY:
		_load_errors.append("Puzzle file is not a JSON object: %s" % path)
		return
	var doc: Dictionary = parsed
	for entry in doc.get("puzzles", []):
		if typeof(entry) != TYPE_DICTIONARY:
			_load_errors.append("Non-object puzzle entry in %s" % path)
			continue
		var p: Dictionary = entry
		if not p.has("chapter"):
			p["chapter"] = int(doc.get("chapter", 1))
		into.append(p)

func load_errors() -> PackedStringArray:
	return _load_errors

func total_stages() -> int:
	return _raw.size()

func raw_campaign() -> Array[Dictionary]:
	return _raw

func raw_daily() -> Array[Dictionary]:
	return _daily_raw

## [param stage] is 1-based. Returns null when out of range or missing a locale.
func get_puzzle(stage: int, loc: String = "") -> PuzzleDefinition:
	if stage < 1 or stage > _raw.size():
		return null
	return PuzzleDefinition.from_dict(_raw[stage - 1], _resolve_locale(loc), stage)

func chapter_of(stage: int) -> int:
	if stage < 1 or stage > _raw.size():
		return 1
	return int(_raw[stage - 1].get("chapter", 1))

func stages_in_chapter(chapter: int) -> Array[int]:
	var out: Array[int] = []
	for i in range(_raw.size()):
		if int(_raw[i].get("chapter", 1)) == chapter:
			out.append(i + 1)
	return out

func first_stage_of_chapter(chapter: int) -> int:
	var stages := stages_in_chapter(chapter)
	return stages[0] if not stages.is_empty() else 1

# --- Daily --------------------------------------------------------------------

## Today's date as YYYY-MM-DD in the device's own timezone, so "tomorrow" means
## what the player expects rather than what UTC says.
static func today_key() -> String:
	var d := Time.get_datetime_dict_from_system(false)
	return "%04d-%02d-%02d" % [d["year"], d["month"], d["day"]]

## Deterministic pick: every device with the same date selects the same puzzle,
## with no server and no stored schedule.
func daily_index_for(day: String) -> int:
	if _daily_raw.is_empty():
		return -1
	var h := day.sha256_text().substr(0, 8).hex_to_int()
	return int(h % _daily_raw.size())

func get_daily(day: String = "", loc: String = "") -> PuzzleDefinition:
	var key := day if day != "" else today_key()
	var idx := daily_index_for(key)
	if idx < 0:
		return null
	# Stage index 0 marks "not a campaign stage" for anything that records results.
	return PuzzleDefinition.from_dict(_daily_raw[idx], _resolve_locale(loc), 0)

func _resolve_locale(loc: String) -> String:
	return loc if loc != "" else Loc.locale
