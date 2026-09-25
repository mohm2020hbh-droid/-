class_name RouteRunner
extends RefCounted
## Plays the real game scene with a scripted "finger": presses jump when the
## player's centre passes each x in a route. Because the level is
## deterministic (x(t) is linear and every timed element is f(level time)),
## a route is a complete, replayable solution of a level.

const GAME_SCENE := preload("res://src/game/game.tscn")

var host: Node
var route_tiles: PackedFloat32Array
## Route indices to skip once (to provoke a death on purpose).
var skip: PackedInt32Array = []
var stop_on_death := true
var max_seconds := 120.0

var game: GameSession
var deaths: Array[Dictionary] = []
var ticks := 0


func _init(host_node: Node, route: PackedFloat32Array) -> void:
	host = host_node
	route_tiles = route


## Runs until the level is complete, the player dies (if [member stop_on_death])
## or time runs out. Returns a summary Dictionary.
func run() -> Dictionary:
	game = GAME_SCENE.instantiate()
	host.add_child(game)
	await host.get_tree().physics_frame
	game.player.died.connect(func(cause: StringName) -> void:
		deaths.append({"x": game.player.global_position.x / GameConst.TILE, "cause": cause, "tick": ticks}))
	game.press_jump()  # Tap to start.
	var next := 0
	var max_ticks := int(max_seconds * Engine.physics_ticks_per_second)
	while ticks < max_ticks:
		await host.get_tree().physics_frame
		ticks += 1
		if game.state == GameSession.State.COMPLETE:
			break
		if stop_on_death and not deaths.is_empty():
			break
		if game.state != GameSession.State.PLAYING:
			continue
		var x := game.player.global_position.x / GameConst.TILE
		# After a respawn the player is behind already-used route points: rewind.
		while next > 0 and route_tiles[next - 1] > x + 0.5:
			next -= 1
		if next < route_tiles.size() and x >= route_tiles[next]:
			var skip_at := skip.find(next)
			if skip_at >= 0:
				skip.remove_at(skip_at)  # Skip once; replay it normally after a respawn.
			else:
				game.press_jump()
			next += 1
	return {
		"completed": game.state == GameSession.State.COMPLETE,
		"deaths": deaths,
		"ticks": ticks,
		"score": game.score.get_score(),
		"shards": game.score.shards,
		"x": game.player.global_position.x,
	}


func free_game() -> void:
	if is_instance_valid(game):
		game.queue_free()
