extends Control

## First node in the game. Picks the right entry screen and nothing else.

func _ready() -> void:
	var saved := SaveManager.get_language()
	if saved == "":
		Loc.set_locale(Loc.detect_locale())
		Game.goto("language")
		return
	Loc.set_locale(saved)
	if not bool(SaveManager.data.get("seen_intro", false)):
		Game.goto("intro")
	else:
		Game.goto("menu")
