@tool
class_name Shard
extends Area2D
## Collectible diamond (docs/GDD.md §7, §11). Origin = centre.
## Bobs and shimmers; collected when the player's body touches it. The Level
## records when it was collected so a checkpoint rewind can bring it back.
## The picture (child "Gem") is drawn once; animating it only changes its
## position and tint, so hundreds of shards cost almost nothing per frame.

signal collected(shard: Shard)

const BOB_HEIGHT := 5.0

var is_collected := false
## Level time at which it was collected (-1 while available).
var collected_at := -1.0

var _time := 0.0

@onready var _burst: CPUParticles2D = $Burst
@onready var _gem: Node2D = $Gem


func _ready() -> void:
	collision_layer = GameConst.LAYER_PICKUP
	collision_mask = GameConst.LAYER_PLAYER
	monitorable = false
	_time = position.x * 0.013  # De-synchronise neighbours.
	if not Engine.is_editor_hint():
		body_entered.connect(_on_body_entered)
		Level.join(self)


func _enter_tree() -> void:
	if is_node_ready() and not Engine.is_editor_hint():
		Level.join(self)  # Re-entering after a reparent.


func _exit_tree() -> void:
	if not Engine.is_editor_hint():
		Level.leave(self)


func _process(delta: float) -> void:
	if Engine.is_editor_hint():
		return
	_time += delta
	_gem.position.y = sin(_time * 3.2) * BOB_HEIGHT
	var shimmer := 0.85 + 0.15 * sin(_time * 5.0)
	_gem.self_modulate = Color(shimmer, shimmer, shimmer, 1.0)


func restore() -> void:
	is_collected = false
	collected_at = -1.0
	set_deferred(&"monitoring", true)
	_gem.show()
	set_process(true)


## Only the player's body can trigger this: collision_mask is the player layer.
func _on_body_entered(_body: Node2D) -> void:
	if is_collected:
		return
	is_collected = true
	set_deferred(&"monitoring", false)
	_burst.restart()
	_gem.hide()
	set_process(false)
	collected.emit(self)

