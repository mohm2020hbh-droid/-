class_name GameHarness
extends RefCounted
## Drives the real game scene tick by tick for integration tests, with a
## scripted "finger" that taps when the player's centre passes each x of a
## route. Everything goes through GameSession.press_jump(), like real input.

const GAME_SCENE := preload("res://src/game/game.tscn")

var host: Node
var route: PackedFloat32Array
## Route indices to skip once (to provoke a death on purpose).
var skip: PackedInt32Array = []
var game: GameSession
var deaths: Array[Dictionary] = []
var ticks := 0

var _next := 0


func _init(host_node: Node, route_tiles: PackedFloat32Array = PackedFloat32Array()) -> void:
	host = host_node
	route = route_tiles


func start() -> void:
	game = GAME_SCENE.instantiate()
	host.add_child(game)
	await host.get_tree().physics_frame
	game.player.died.connect(func(cause: StringName) -> void:
		deaths.append({"x": player_x(), "cause": cause, "tick": ticks}))


## Player centre x in tiles.
func player_x() -> float:
	return game.player.global_position.x / GameConst.TILE


## Advances one physics tick; the route finger taps before the tick runs.
func tick() -> void:
	_route_finger()
	await host.get_tree().physics_frame
	ticks += 1


func run_ticks(count: int) -> void:
	for i in count:
		await tick()


## Ticks until [param condition] returns true; false if it never did.
func run_until(condition: Callable, max_ticks: int = 60 * 120) -> bool:
	for i in max_ticks:
		if condition.call():
			return true
		await tick()
	return condition.call()


func is_state(state: GameSession.State) -> Callable:
	return func() -> bool: return game.state == state


func free_game() -> void:
	if is_instance_valid(game):
		game.queue_free()


func _route_finger() -> void:
	if game.state != GameSession.State.PLAYING:
		return
	var x := player_x()
	# After a respawn the player is behind already-used taps: rewind.
	while _next > 0 and route[_next - 1] > x + 0.5:
		_next -= 1
	if _next < route.size() and x >= route[_next]:
		var skip_at := skip.find(_next)
		if skip_at >= 0:
			skip.remove_at(skip_at)  # Skip once; replay it normally after a respawn.
		else:
			game.press_jump()
		_next += 1
