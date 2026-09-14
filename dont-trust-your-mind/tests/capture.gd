extends Node

## Screenshot harness. Run under a virtual display:
##   xvfb-run -a godot --resolution 720x1280 res://tests/capture.tscn
## Writes PNGs of key screens and puzzles so layout and Arabic shaping can be
## inspected for real rather than assumed.

const OUT := "res://tests/shots/"
const SETTLE_FRAMES := 6

func _ready() -> void:
	await get_tree().process_frame
	SaveManager.data = SaveManager.default_data()
	SaveManager.data["seen_intro"] = true
	SaveManager.data["stages"] = {
		"1": {"solved": true, "stars": 3, "best_time": 4.2, "best_attempts": 0, "plays": 1},
		"2": {"solved": true, "stars": 2, "best_time": 9.9, "best_attempts": 1, "plays": 2},
		"3": {"solved": true, "stars": 1, "best_time": 21.0, "best_attempts": 4, "plays": 3},
	}
	SaveManager.data["current_stage"] = 4
	SaveManager.data["streak"] = 3
	SaveManager.data["achievements"] = ["first_blood", "speed"]

	var plan := _plan()
	for entry in plan:
		Loc.set_locale(entry["loc"])
		await _shoot(entry)
	print("captured %d screenshots" % plan.size())
	get_tree().quit()

func _plan() -> Array:
	var shots := []
	for loc in ["ar", "en"]:
		for screen in ["language", "intro", "menu", "stages", "settings", "daily",
				"achievements"]:
			shots.append({"loc": loc, "screen": screen,
					"name": "%s_screen_%s" % [loc, screen]})
		# One puzzle per chapter, plus the stages that exercise the trickiest layout.
		for stage in [1, 4, 8, 15, 19, 21, 27, 31, 42, 47, 50]:
			shots.append({"loc": loc, "stage": stage,
					"name": "%s_stage_%02d" % [loc, stage]})
	return shots

func _shoot(entry: Dictionary) -> void:
	var instance: Node
	if entry.has("stage"):
		Game.pending_daily = false
		Game.pending_stage = entry["stage"]
		instance = load(Game.SCENES["puzzle"]).instantiate()
	else:
		instance = load(Game.SCENES[entry["screen"]]).instantiate()
	add_child(instance)
	for i in SETTLE_FRAMES:
		await get_tree().process_frame
	await RenderingServer.frame_post_draw
	var image := get_viewport().get_texture().get_image()
	image.save_png("%s%s.png" % [OUT, entry["name"]])
	instance.queue_free()
	await get_tree().process_frame
