extends Node
## Dev tool: audits World 01 levels on the real physics (docs/ARCHITECTURE.md §10).
##
##   godot --headless --path . --fixed-fps 60 res://tests/tools/level_audit.tscn -- --level=3 --windows --exploits
##
## - route: plays the intended route; reports deaths, duration, jumps.
## - --windows: for every tap, how many ticks earlier/later it could come
##   (all other taps unchanged) and still survive to the next checkpoint.
## - --exploits: lazy strategies (never tap, tap on a fixed rhythm) must die.

const WORLD := preload("res://levels/world_01/world_01.tres")
const HORIZON_TAPS := 3
const MAX_SHIFT := 24

var level_index := 0
var route: PackedFloat32Array
var checkpoints: Array[float] = []
var speed_tiles := 0.0


func _ready() -> void:
	SaveSystem.save_path = "user://audit_tmp.cfg"
	get_tree().root.size = Vector2i(1280, 720)
	await get_tree().process_frame
	var args := OS.get_cmdline_user_args()
	for arg in args:
		if arg.begins_with("--level="):
			level_index = int(arg.trim_prefix("--level=")) - 1
	route = World01Routes.get_route(level_index)
	await _play_route()
	if "--windows" in args:
		await _windows(args)
	if "--exploits" in args:
		await _exploits()
	get_tree().quit()


func _play_route() -> void:
	var h := GameHarness.new(self, route)
	await h.start(level_index)
	var level := h.game.level
	speed_tiles = h.game.player.get_run_speed() / GameConst.TILE
	for cp in level.get_checkpoints():
		checkpoints.append(cp.global_position.x / GameConst.TILE)
	var jumps: Array[int] = [0, 0]
	h.game.player.jumped.connect(func() -> void: jumps[0] += 1)
	h.game.player.double_jumped.connect(func() -> void: jumps[1] += 1)
	h.game.press_jump()
	await h.run_until(func() -> bool:
		return h.game.state == GameSession.State.COMPLETE or not h.deaths.is_empty(), 60 * 400)
	var data := WORLD.get_level(level_index)
	print("LEVEL %d %s  speed %.0f px/s  finish x=%.1f  checkpoints %s" % [level_index + 1, data.display_name,
		h.game.player.get_run_speed(), level.get_finish().global_position.x / GameConst.TILE, checkpoints])
	if h.deaths.is_empty():
		print("  ROUTE OK  %.1f s  taps %d  jumps %d  double jumps %d  shards %d/%d" % [h.ticks / 60.0,
			route.size(), jumps[0], jumps[1], h.game.score.shards, level.get_shard_count()])
		for shard in level.find_children("*", "Shard", true, false):
			if not (shard as Shard).is_collected:
				print("  SHARD MISSED at x=%.2f y=%.2f" % [(shard as Shard).global_position.x / GameConst.TILE,
					(shard as Shard).global_position.y / GameConst.TILE])
	else:
		print("  ROUTE DIES  %s" % [h.deaths[0]])
	h.free_game()
	await get_tree().physics_frame


## The checkpoint x (tiles) to start from for a tap at [param x], or -1 for the spawn.
func _start_before(x: float) -> float:
	var best := -1.0
	for cp in checkpoints:
		if cp < x - 1.0:
			best = cp
	return best


func _horizon(j: int) -> float:
	var x := route[mini(j + HORIZON_TAPS, route.size() - 1)] + 1.0
	for cp in checkpoints:
		if cp > route[j]:
			return minf(x, cp - 0.5) if j + HORIZON_TAPS < route.size() else cp - 0.5
	return x if j + HORIZON_TAPS < route.size() else 1e9


## True when the run with tap [param j] moved by [param shift] ticks survives to the horizon.
func _survives(j: int, shift: int) -> bool:
	var taps := route.duplicate()
	taps[j] += shift * speed_tiles / 60.0
	var h := GameHarness.new(self, taps)
	await h.start(level_index)
	var from := _start_before(route[j])
	if from >= 0.0:
		h.start_run_at(Vector2(from * GameConst.TILE, _checkpoint_y(h, from)))
	else:
		h.game.press_jump()
	var goal := _horizon(j)
	await h.run_until(func() -> bool:
		return not h.deaths.is_empty() or h.player_x() >= goal or h.game.state == GameSession.State.COMPLETE, 60 * 120)
	# Alive at the horizon, and not already falling through a pit (a fall is
	# only detected well below the ground).
	var ok := h.deaths.is_empty() and h.game.player.global_position.y < GameConst.TILE
	h.free_game()
	await get_tree().physics_frame
	return ok


func _checkpoint_y(h: GameHarness, x: float) -> float:
	for cp in h.game.level.get_checkpoints():
		if is_equal_approx(cp.global_position.x / GameConst.TILE, x):
			return cp.global_position.y
	return 0.0


func _windows(args: PackedStringArray) -> void:
	var only := -1
	for arg in args:
		if arg.begins_with("--tap="):
			only = int(arg.trim_prefix("--tap=")) - 1
	var sizes: Array[float] = []
	for j in route.size():
		if only >= 0 and j != only:
			continue
		var lo := 0
		var hi := 0
		if not await _survives(j, 0):
			print("  T%02d x=%7.2f  FAILS UNSHIFTED" % [j + 1, route[j]])
			sizes.append(0.0)
			continue
		for k in range(1, MAX_SHIFT + 1):
			if not await _survives(j, -k):
				break
			lo = -k
		for k in range(1, MAX_SHIFT + 1):
			if not await _survives(j, k):
				break
			hi = k
		var ms := (hi - lo + 1) * 1000.0 / 60.0
		sizes.append(ms)
		print("  T%02d x=%7.2f  [%+3d, %+3d] ticks  %4d ms%s" % [j + 1, route[j], lo, hi, int(ms),
			"  (open)" if lo == -MAX_SHIFT or hi == MAX_SHIFT else ""])
	if sizes.size() > 1:
		var sorted := sizes.duplicate()
		sorted.sort()
		print("  WINDOWS min %d ms  median %d ms  (%d taps)" % [int(sorted[0]), int(sorted[sorted.size() / 2]), sorted.size()])


func _exploits() -> void:
	var finish := 0.0
	var probe := GameHarness.new(self)
	await probe.start(level_index)
	finish = probe.game.level.get_finish().global_position.x / GameConst.TILE
	probe.free_game()
	await get_tree().physics_frame
	var strategies: Array[Array] = [["never tap", 0, 0]]
	for every in [10, 14, 18, 22, 26, 30, 36, 42, 50, 60]:
		strategies.append(["tap every %d ticks" % every, every, 0])
	for every in [30, 40, 50]:
		strategies.append(["jump + double jump every %d ticks" % every, every, 16])
	var worst := 0.0
	for strategy in strategies:
		var h := GameHarness.new(self)
		await h.start(level_index)
		h.game.press_jump()
		var every: int = strategy[1]
		var second: int = strategy[2]
		var tick := 0
		await h.run_until(func() -> bool:
			tick += 1
			if every > 0 and tick % every == 0:
				h.game.press_jump()
			if second > 0 and tick % every == second:
				h.game.press_jump()
			return not h.deaths.is_empty() or h.game.state == GameSession.State.COMPLETE, 60 * 400)
		var reached := h.player_x() / finish
		worst = maxf(worst, reached)
		print("  EXPLOIT %-34s %s at %3d%%" % [strategy[0],
			"COMPLETES" if h.game.state == GameSession.State.COMPLETE else "dies", int(reached * 100.0)])
		h.free_game()
		await get_tree().physics_frame
	print("  EXPLOITS furthest %d%% of the level" % int(worst * 100.0))
