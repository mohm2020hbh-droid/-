class_name Autoplay
extends Node
## QA switch (never on in normal play): `-- --autoplay` makes the game play
## itself with each level's intended route (World01Routes), the same way the
## integration tests do, so a real build (the web export included) can be
## run from the first level to WORLD 01 COMPLETE without a person.
##   --autoplay-die=50,80   skip the first tap at or after 50% and 80% of
##                          every level once: a death, the 25-point penalty
##                          and a checkpoint respawn at each.
##   --autoplay-from=N      start at level N (1-based) instead of 1.
##   --autoplay-loops=N     play the whole world N times (stress runs).
##   --autoplay-quit        quit when done (headless runs).
## Every event is printed with an [autoplay] prefix (the browser console on
## the web), ending with "[autoplay] WORLD COMPLETE".

var game: GameSession
var die_at: Array[float] = []

var _route: PackedFloat32Array
var _next := 0
var _skip: Array[int] = []
var _was_state := -1
var _deaths_seen := 0
var _loop := 1


static func requested() -> bool:
	return "--autoplay" in OS.get_cmdline_user_args()


static func option(name: String) -> String:
	for arg in OS.get_cmdline_user_args():
		if arg.begins_with(name + "="):
			return arg.get_slice("=", 1)
	return ""


func _ready() -> void:
	process_physics_priority = Level.PHYSICS_PRIORITY - 10  # Tap before the level and the player move.
	for part in option("--autoplay-die").split(",", false):
		die_at.append(float(part))
	game.state_changed.connect(_on_state_changed)
	game.player.died.connect(_on_died)
	game.level_loaded.connect(_on_level_loaded)
	_on_level_loaded()


func _on_level_loaded() -> void:
	_route = World01Routes.get_route(game.level_index)
	_next = 0
	_skip.clear()
	for percent in die_at:
		for i in _route.size():
			if game.progress.percent_at(_route[i] * GameConst.TILE) >= percent:
				_skip.append(i)
				break
	var cps: Array[String] = []
	for cp in game.level.get_checkpoints():
		cps.append("%.1f%%" % game.progress.percent_at(cp.global_position.x))
	_log("level %d %s loaded: %d taps, checkpoints at %s" % [game.level_index + 1, game.level.data.display_name,
		_route.size(), ", ".join(cps)])
	get_tree().create_timer(0.6).timeout.connect(func() -> void:
		if game.state == GameSession.State.READY:
			game.press_jump())


func _physics_process(_delta: float) -> void:
	if game.state != GameSession.State.PLAYING:
		return
	var x := game.player.global_position.x / GameConst.TILE
	while _next > 0 and _route[_next - 1] > x + 0.5:
		_next -= 1  # Back behind used taps after a respawn.
	if _next < _route.size() and x >= _route[_next]:
		if _skip.has(_next):
			_skip.erase(_next)  # Miss it once on purpose.
		else:
			game.press_jump()
		_next += 1


func _on_state_changed(state: GameSession.State) -> void:
	match state:
		GameSession.State.PLAYING:
			if _was_state == GameSession.State.DYING:
				_log("respawned at x=%.1f (%.1f%% of the level), progress %.1f%%" % [
					game.player.global_position.x / GameConst.TILE,
					game.progress.percent_at(game.player.global_position.x), game.progress.percent])
		GameSession.State.COMPLETE:
			_log("level %d complete: progress %.0f%%, attempts %d" % [game.level_index + 1, game.progress.percent,
				game.attempts])
			get_tree().create_timer(2.5).timeout.connect(_after_complete)
	_was_state = state


func _on_died(cause: StringName) -> void:
	_deaths_seen += 1
	# The penalty is applied in the same frame: read it on the next one.
	await get_tree().process_frame
	_log("death (%s) at x=%.1f: progress now %.1f%%" % [cause, game.player.global_position.x / GameConst.TILE,
		game.progress.percent])


func _after_complete() -> void:
	if game.level_index + 1 < game.world.levels.size():
		game.play_level(game.level_index + 1)
	elif _loop < maxi(int(option("--autoplay-loops")), 1):
		_loop += 1
		_log("loop %d: back to level 1" % _loop)
		game.play_level(0)
	else:
		_log("WORLD COMPLETE (%d deaths in all)" % _deaths_seen)
		if "--autoplay-quit" in OS.get_cmdline_user_args():
			get_tree().quit()


func _log(text: String) -> void:
	print("[autoplay] %s | vram %.1f MB, textures %.1f MB, objects %d, nodes %d, viewport %s" % [text,
		Performance.get_monitor(Performance.RENDER_VIDEO_MEM_USED) / 1048576.0,
		Performance.get_monitor(Performance.RENDER_TEXTURE_MEM_USED) / 1048576.0,
		Performance.get_monitor(Performance.OBJECT_COUNT), Performance.get_monitor(Performance.OBJECT_NODE_COUNT),
		get_viewport().get_visible_rect().size])
