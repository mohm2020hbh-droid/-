extends Node

## Flow between screens, plus the rules that sit above a single puzzle:
## which stage is next, what unlocks, achievements and share text.

signal achievement_granted(id: String)

const SCENES := {
	"boot": "res://src/ui/screens/boot.tscn",
	"language": "res://src/ui/screens/language_select.tscn",
	"intro": "res://src/ui/screens/intro.tscn",
	"menu": "res://src/ui/screens/main_menu.tscn",
	"stages": "res://src/ui/screens/stage_select.tscn",
	"puzzle": "res://src/ui/screens/puzzle_screen.tscn",
	"settings": "res://src/ui/screens/settings_screen.tscn",
	"daily": "res://src/ui/screens/daily_screen.tscn",
	"achievements": "res://src/ui/screens/achievements_screen.tscn",
}

## Stages stay reachable a little beyond the furthest solved one so a single
## hard puzzle can never wall the player off from the rest of the game.
const UNLOCK_LOOKAHEAD := 2

var pending_stage: int = 1
var pending_daily: bool = false

func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS

## Deferred on purpose: the boot screen routes away during its own _ready, and
## swapping scenes while the tree is still parenting the current one makes Godot
## refuse the internal remove_child. Deferring makes every caller safe, including
## the ones that navigate from inside a signal handler.
func goto(screen: String) -> void:
	var path: String = SCENES.get(screen, "")
	if path == "":
		push_error("Unknown screen: %s" % screen)
		return
	get_tree().change_scene_to_file.call_deferred(path)

func start_stage(stage: int) -> void:
	pending_stage = stage
	pending_daily = false
	goto("puzzle")

func start_daily() -> void:
	pending_daily = true
	goto("puzzle")

func highest_unlocked() -> int:
	var furthest := 1
	for key in SaveManager.data["stages"]:
		var rec: Dictionary = SaveManager.data["stages"][key]
		if rec.get("solved", false) or rec.get("skipped", false):
			furthest = maxi(furthest, int(key) + 1)
	return clampi(furthest + UNLOCK_LOOKAHEAD - 1, 1, Puzzles.total_stages())

func is_unlocked(stage: int) -> bool:
	return stage <= highest_unlocked()

func next_stage_after(stage: int) -> int:
	return stage + 1 if stage < Puzzles.total_stages() else -1

func continue_stage() -> int:
	var s := int(SaveManager.data.get("current_stage", 1))
	return clampi(s, 1, Puzzles.total_stages())

## Applies a finished campaign attempt to the profile and checks achievements.
func submit_result(result: PuzzleResult) -> void:
	SaveManager.record_result(result)
	if result.solved():
		SaveManager.touch_streak(Puzzles.today_key())
	_check_achievements(result)

func submit_daily(day: String, result: PuzzleResult) -> void:
	SaveManager.record_daily(day, result)
	if result.solved():
		SaveManager.touch_streak(day)
	_check_achievements(result)

func _check_achievements(result: PuzzleResult) -> void:
	var granted: Array[String] = []

	if result.solved() and result.stage_index == 1:
		granted.append("first_blood")
	if result.solved() and result.elapsed < 3.0:
		granted.append("speed")
	if result.solved() and result.wrong_attempts >= 10:
		granted.append("stubborn")

	var solved := SaveManager.solved_count()
	if solved >= 25:
		granted.append("half")
	if solved >= Puzzles.total_stages() and Puzzles.total_stages() > 0:
		granted.append("all")
	if _count_hintless_solves() >= 10:
		granted.append("no_hints_10")
	if int(SaveManager.data.get("best_streak", 0)) >= 7:
		granted.append("week")
	for c in range(1, Puzzles.CHAPTER_COUNT + 1):
		if _chapter_is_perfect(c):
			granted.append("perfect_chapter")
			break

	for id in granted:
		if SaveManager.grant_achievement(id):
			achievement_granted.emit(id)

func _count_hintless_solves() -> int:
	var n := 0
	for key in SaveManager.data["stages"]:
		var rec: Dictionary = SaveManager.data["stages"][key]
		if rec.get("solved", false) and int(rec.get("stars", 0)) == 3:
			n += 1
	return n

func _chapter_is_perfect(chapter: int) -> bool:
	var stages := Puzzles.stages_in_chapter(chapter)
	if stages.is_empty():
		return false
	for s in stages:
		if SaveManager.stars_for(s) < 3:
			return false
	return true

func achievement_ids() -> PackedStringArray:
	return PackedStringArray([
		"first_blood", "speed", "stubborn", "no_hints_10",
		"perfect_chapter", "half", "week", "all",
	])

# --- Sharing ------------------------------------------------------------------

## Deliberately reveals a time and a stage number but never the solution.
func share_text_for_stage(stage: int, seconds: float) -> String:
	return Loc.t("share.stage", {"n": stage, "t": Loc.format_seconds(seconds)})

func share_text_for_daily(day: String, seconds: float) -> String:
	return Loc.t("share.daily", {"d": day, "t": Loc.format_seconds(seconds)})

func share(text: String) -> void:
	DisplayServer.clipboard_set(text)
	Audio.play(Audio.Sfx.REVEAL)
