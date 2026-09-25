@tool
class_name Checkpoint
extends Area2D
## Vertical light beam that stores a respawn point (docs/GDD.md §11).
## Origin = the point on the ground where the player respawns (feet).
## The trigger spans the full beam height so it fires at any jump height.

signal reached(checkpoint: Checkpoint)

const BEAM_HEIGHT := 320.0
const TRIGGER_WIDTH := 24.0

var is_active := false
## Level time at which the player passed it (set by the Level).
var level_time := 0.0

var _flash := 0.0


func _ready() -> void:
	collision_layer = GameConst.LAYER_TRIGGER
	collision_mask = GameConst.LAYER_PLAYER
	monitorable = false
	var shape := RectangleShape2D.new()
	shape.size = Vector2(TRIGGER_WIDTH, BEAM_HEIGHT)
	var shape_node := CollisionShape2D.new()
	shape_node.shape = shape
	shape_node.position = Vector2(0, -BEAM_HEIGHT * 0.5)
	add_child(shape_node, false, Node.INTERNAL_MODE_FRONT)
	if not Engine.is_editor_hint():
		add_to_group(&"checkpoints")
		body_entered.connect(_on_body_entered)


func _process(delta: float) -> void:
	if _flash > 0.0:
		_flash = maxf(_flash - delta * 2.5, 0.0)
		queue_redraw()


func _on_body_entered(body: Node2D) -> void:
	if is_active or not body is Player:
		return
	is_active = true
	_flash = 1.0
	queue_redraw()
	reached.emit(self)


func _draw() -> void:
	var color := Palette.NEON if is_active else Palette.NEON_DIM
	var alpha := 0.9 if is_active else 0.45
	var top := Vector2(0, -BEAM_HEIGHT)
	# Beam fades toward the sky.
	var widths: Array[float] = [18.0, 8.0, 2.0]
	var alphas: Array[float] = [0.08, 0.18, 0.9]
	for i in 3:
		var w := widths[i] * (1.0 + _flash)
		var a := alphas[i] * alpha
		draw_polyline_colors(PackedVector2Array([Vector2.ZERO, top]),
			PackedColorArray([Color(color, a), Color(color, 0.0)]), w, true)
	var base := PackedVector2Array([Vector2(-10, 0), Vector2(0, -12), Vector2(10, 0)])
	draw_colored_polygon(base, Color(color, alpha))
	if _flash > 0.0:
		Neon.soft_light(self, Vector2(0, -40), 120.0 * _flash, Color(Palette.PLAYER_CORE, _flash))
