extends Node

## Durable player state. Plain JSON in user://, written atomically with a
## checksum and a rolling backup so a kill mid-write can never leave the player
## with a corrupt profile.

signal progress_changed()
signal settings_changed()

const SAVE_PATH := "user://profile.json"
const BACKUP_PATH := "user://profile.backup.json"
const TEMP_PATH := "user://profile.tmp.json"
const SCHEMA := 1

var data: Dictionary = {}
var _dirty := false
var _flush_timer: SceneTreeTimer

static func default_data() -> Dictionary:
	return {
		"schema": SCHEMA,
		"language": "",              # "" = not chosen yet -> first-run picker
		"current_stage": 1,
		"stages": {},                # "12" -> {stars, best_time, best_attempts, solved}
		"total_points": 0,
		"streak": 0,
		"best_streak": 0,
		"last_played_day": "",
		"daily": {},                 # "2026-09-14" -> {solved, time, stars}
		"achievements": [],
		"seen_intro": false,
		"settings": {
			"sfx": true,
			"haptics": true,
			"reduce_motion": false,
			"volume": 0.8,
		},
	}

func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	load_profile()

func load_profile() -> void:
	data = _read(SAVE_PATH)
	if data.is_empty():
		data = _read(BACKUP_PATH)
		if not data.is_empty():
			push_warning("Primary save unreadable; recovered from backup.")
	if data.is_empty():
		data = default_data()
	else:
		_migrate()

func _read(path: String) -> Dictionary:
	if not FileAccess.file_exists(path):
		return {}
	var f := FileAccess.open(path, FileAccess.READ)
	if f == null:
		return {}
	var text := f.get_as_text()
	f.close()
	var parsed = JSON.parse_string(text)
	if typeof(parsed) != TYPE_DICTIONARY:
		return {}
	var envelope: Dictionary = parsed
	if not envelope.has("payload") or not envelope.has("sum"):
		return {}
	if str(envelope["sum"]) != _checksum(envelope["payload"]):
		push_warning("Save checksum mismatch at %s; treating as corrupt." % path)
		return {}
	return envelope["payload"]

static func _checksum(payload) -> String:
	return str(JSON.stringify(payload, "", true).sha256_text()).substr(0, 16)

func save_now() -> bool:
	var envelope := {"sum": _checksum(data), "payload": data}
	var text := JSON.stringify(envelope)

	var tmp := FileAccess.open(TEMP_PATH, FileAccess.WRITE)
	if tmp == null:
		push_error("Cannot open temp save file.")
		return false
	tmp.store_string(text)
	tmp.close()

	# Verify what actually landed on disk before promoting it.
	var check := FileAccess.open(TEMP_PATH, FileAccess.READ)
	if check == null or check.get_as_text() != text:
		if check != null:
			check.close()
		push_error("Temp save verification failed; keeping previous save.")
		return false
	check.close()

	var dir := DirAccess.open("user://")
	if dir == null:
		return false
	if dir.file_exists(SAVE_PATH.get_file()):
		dir.copy(SAVE_PATH, BACKUP_PATH)
	dir.rename(TEMP_PATH, SAVE_PATH)
	_dirty = false
	return true

## Coalesces bursts of *non-critical* writes (dragging a volume slider fires
## many value_changed signals a frame apart) into one flush shortly after they
## stop. Never used for progress: a debounced write is a race against the app
## being killed before the timer fires, which is exactly the bug that made
## solved stages vanish on Android when the player closed the app right after
## a solve. Progress is committed synchronously instead — see save_now()
## callers in GameManager.submit_result()/submit_daily().
func mark_dirty() -> void:
	_dirty = true
	if _flush_timer != null and _flush_timer.time_left > 0.0:
		return
	_flush_timer = get_tree().create_timer(0.4, true, false, true)
	_flush_timer.timeout.connect(func() -> void:
		if _dirty:
			save_now())

func _notification(what: int) -> void:
	if what == NOTIFICATION_WM_CLOSE_REQUEST or what == NOTIFICATION_APPLICATION_PAUSED \
			or what == NOTIFICATION_WM_GO_BACK_REQUEST:
		if _dirty:
			save_now()

func _migrate() -> void:
	var base := default_data()
	for key in base:
		if not data.has(key):
			data[key] = base[key]
	var settings: Dictionary = data.get("settings", {})
	for key in base["settings"]:
		if not settings.has(key):
			settings[key] = base["settings"][key]
	data["settings"] = settings
	data["schema"] = SCHEMA

# --- Settings -----------------------------------------------------------------

func get_setting(key: String, fallback = null):
	return data["settings"].get(key, fallback)

func set_setting(key: String, value) -> void:
	data["settings"][key] = value
	mark_dirty()
	settings_changed.emit()

func get_language() -> String:
	return str(data.get("language", ""))

func set_language(code: String) -> void:
	data["language"] = code
	mark_dirty()

# --- Progress -----------------------------------------------------------------

func stage_record(stage: int) -> Dictionary:
	return data["stages"].get(str(stage), {})

func is_stage_solved(stage: int) -> bool:
	return bool(stage_record(stage).get("solved", false))

func stars_for(stage: int) -> int:
	return int(stage_record(stage).get("stars", 0))

func total_stars() -> int:
	var sum := 0
	for key in data["stages"]:
		sum += int(data["stages"][key].get("stars", 0))
	return sum

func solved_count() -> int:
	var n := 0
	for key in data["stages"]:
		if data["stages"][key].get("solved", false):
			n += 1
	return n

## Records an attempt. Bests only ever improve, so replaying is never punished.
func record_result(result: PuzzleResult) -> void:
	var key := str(result.stage_index)
	var rec: Dictionary = data["stages"].get(key, {
		"solved": false, "stars": 0, "best_time": 0.0, "best_attempts": -1, "plays": 0,
	})
	rec["plays"] = int(rec.get("plays", 0)) + 1
	if result.solved():
		var was_new: bool = not bool(rec.get("solved", false))
		rec["solved"] = true
		rec["stars"] = maxi(int(rec.get("stars", 0)), result.stars)
		var best_t := float(rec.get("best_time", 0.0))
		rec["best_time"] = result.elapsed if best_t <= 0.0 else minf(best_t, result.elapsed)
		var best_a := int(rec.get("best_attempts", -1))
		rec["best_attempts"] = result.wrong_attempts if best_a < 0 \
				else mini(best_a, result.wrong_attempts)
		if was_new:
			data["total_points"] = int(data.get("total_points", 0)) + result.points
	elif result.status == PuzzleResult.Status.SKIPPED:
		rec["skipped"] = true
	data["stages"][key] = rec

	if result.solved() and result.stage_index >= int(data.get("current_stage", 1)):
		data["current_stage"] = result.stage_index + 1
	# Not mark_dirty(): this write is progress, and progress is flushed
	# synchronously by the caller (GameManager.submit_result), not on a timer.
	_dirty = true
	progress_changed.emit()

func reset_progress() -> void:
	var lang := get_language()
	var settings: Dictionary = data["settings"].duplicate(true)
	data = default_data()
	data["language"] = lang
	data["settings"] = settings
	data["seen_intro"] = true
	save_now()
	progress_changed.emit()

# --- Streak -------------------------------------------------------------------

## Keeps a day-streak alive across consecutive calendar days, resetting on a gap.
func touch_streak(today: String) -> void:
	var last := str(data.get("last_played_day", ""))
	if last == today:
		return
	if last == "" or _is_previous_day(last, today):
		data["streak"] = int(data.get("streak", 0)) + 1
	else:
		data["streak"] = 1
	data["best_streak"] = maxi(int(data.get("best_streak", 0)), int(data["streak"]))
	data["last_played_day"] = today
	_dirty = true  # Flushed synchronously by the caller; see mark_dirty()'s docstring.

static func _is_previous_day(last: String, today: String) -> bool:
	var a := _to_unix_day(last)
	var b := _to_unix_day(today)
	return a > 0 and b > 0 and b - a == 86400

static func _to_unix_day(iso: String) -> int:
	var parts := iso.split("-")
	if parts.size() != 3:
		return 0
	return int(Time.get_unix_time_from_datetime_dict({
		"year": int(parts[0]), "month": int(parts[1]), "day": int(parts[2]),
		"hour": 0, "minute": 0, "second": 0,
	}))

# --- Daily --------------------------------------------------------------------

func daily_record(day: String) -> Dictionary:
	return data["daily"].get(day, {})

func record_daily(day: String, result: PuzzleResult) -> void:
	if data["daily"].has(day) and bool(data["daily"][day].get("solved", false)):
		return
	data["daily"][day] = {
		"solved": result.solved(),
		"time": result.elapsed,
		"stars": result.stars,
		"attempts": result.wrong_attempts,
	}
	if result.solved():
		data["total_points"] = int(data.get("total_points", 0)) + result.points
	_dirty = true  # Flushed synchronously by the caller; see mark_dirty()'s docstring.
	progress_changed.emit()

# --- Achievements -------------------------------------------------------------

func has_achievement(id: String) -> bool:
	return data["achievements"].has(id)

func grant_achievement(id: String) -> bool:
	if has_achievement(id):
		return false
	data["achievements"].append(id)
	_dirty = true  # Flushed synchronously by the caller; see mark_dirty()'s docstring.
	return true
