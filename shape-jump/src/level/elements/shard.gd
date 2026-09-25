@tool
class_name Shard
extends Area2D
## Collectible diamond (docs/GDD.md §7, §11). Origin = centre.
## Bobs and shimmers; collected when the player's body touches it. The Level
## records when it was collected so a checkpoint rewind can bring it back.

signal collected(shard: Shard)

const HALF_SIZE := Vector2(12, 17)
const BOB_HEIGHT := 5.0

var is_collected := false
## Level time at which it was collected (-1 while available).
var collected_at := -1.0

var _time := 0.0

@onready var _burst: CPUParticles2D = $Burst


func _ready() -> void:
	collision_layer = GameConst.LAYER_PICKUP
	collision_mask = GameConst.LAYER_PLAYER
	monitorable = false
	_time = position.x * 0.013  # De-synchronise neighbours.
	if not Engine.is_editor_hint():
		add_to_group(&"shards")
		body_entered.connect(_on_body_entered)


func _process(delta: float) -> void:
	if Engine.is_editor_hint() or is_collected:
		return
	_time += delta
	queue_redraw()


func restore() -> void:
	is_collected = false
	collected_at = -1.0
	set_deferred(&"monitoring", true)
	queue_redraw()


## Only the player's body can trigger this: collision_mask is the player layer.
func _on_body_entered(_body: Node2D) -> void:
	if is_collected:
		return
	is_collected = true
	set_deferred(&"monitoring", false)
	_burst.restart()
	queue_redraw()
	collected.emit(self)


func _draw() -> void:
	if is_collected:
		return
	var bob := sin(_time * 3.2) * BOB_HEIGHT
	var shimmer := 0.5 + 0.5 * sin(_time * 5.0)
	var c := Vector2(0.0, bob)
	var diamond := PackedVector2Array([
		c + Vector2(0, -HALF_SIZE.y), c + Vector2(HALF_SIZE.x, 0),
		c + Vector2(0, HALF_SIZE.y), c + Vector2(-HALF_SIZE.x, 0)])
	Neon.soft_light(self, c, 34.0, Color(Palette.SHARD_EDGE, 0.3 + 0.15 * shimmer))
	draw_colored_polygon(diamond, Color(Palette.SHARD_EDGE, 0.85))
	# Facet: a brighter inner diamond reads as a cut gem, not a spike.
	var facet := PackedVector2Array()
	for p in diamond:
		facet.append(c + (p - c) * 0.5 + Vector2(0, -2))
	draw_colored_polygon(facet, Palette.SHARD_CORE.lerp(Color.WHITE, shimmer * 0.5))
	Neon.polyline(self, diamond, Palette.SHARD_CORE, 1.5, 0.8, true)
