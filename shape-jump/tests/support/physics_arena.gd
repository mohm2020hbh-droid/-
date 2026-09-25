class_name PhysicsArena
extends Node2D
## A throwaway playground on the real physics server: tests build blocks and
## a player in code and tick the world.

const PLAYER_SCENE := preload("res://src/player/player.tscn")
const T := GameConst.TILE


## A solid block; [param moving] builds it on an AnimatableBody2D.
func block(pos: Vector2, size: Vector2, moving := false) -> Block:
	var body: Object = AnimatableBody2D.new() if moving else StaticBody2D.new()
	body.set_script(Block)
	var result := body as Block
	result.position = pos
	result.size = size
	add_child(result)
	return result


## A player standing with its feet on [param feet], optionally already running.
func spawn_player(feet: Vector2, run := true, kill_y := 400.0) -> Player:
	var player: Player = PLAYER_SCENE.instantiate()
	add_child(player)
	player.kill_y = kill_y
	player.respawn_at(feet, run)
	return player


func ticks(n: int) -> void:
	for i in n:
		await get_tree().physics_frame


## Half width of the player's body, read from the scene (before any spawn).
static func player_half() -> float:
	var probe := PLAYER_SCENE.instantiate()
	var half: float = ((probe.get_node("BodyShape") as CollisionShape2D).shape as RectangleShape2D).size.x * 0.5
	probe.free()
	return half
