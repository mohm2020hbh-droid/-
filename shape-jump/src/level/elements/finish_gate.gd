@tool
class_name FinishGate
extends Area2D
## End-of-level gate. Origin = ground point at the gate's centre line.

signal reached

const HEIGHT := 260.0
const WIDTH := 110.0

var _lit := 0.0
var _time := 0.0
var _triggered := false


func _ready() -> void:
	collision_layer = GameConst.LAYER_TRIGGER
	collision_mask = GameConst.LAYER_PLAYER
	monitorable = false
	var shape := RectangleShape2D.new()
	shape.size = Vector2(24, HEIGHT)
	var shape_node := CollisionShape2D.new()
	shape_node.shape = shape
	shape_node.position = Vector2(0, -HEIGHT * 0.5)
	add_child(shape_node, false, Node.INTERNAL_MODE_FRONT)
	if not Engine.is_editor_hint():
		body_entered.connect(_on_body_entered)


func _process(delta: float) -> void:
	if Engine.is_editor_hint():
		return
	_time += delta
	if _triggered:
		_lit = minf(_lit + delta * 3.0, 1.0)
	queue_redraw()


## Only the player's body can trigger this: collision_mask is the player layer.
func _on_body_entered(_body: Node2D) -> void:
	if _triggered:
		return
	_triggered = true
	reached.emit()


func _draw() -> void:
	var half := WIDTH * 0.5
	var frame := PackedVector2Array([
		Vector2(-half, 0), Vector2(-half, -HEIGHT), Vector2(half, -HEIGHT), Vector2(half, 0)])
	var pulse := 0.8 + 0.2 * sin(_time * 3.0)
	var inner_rect := Rect2(Vector2(-half + 8, -HEIGHT + 8), Vector2(WIDTH - 16, HEIGHT - 8))
	draw_rect(inner_rect, Color(Palette.NEON, 0.06 + 0.25 * _lit))
	Neon.polyline(self, frame, Palette.NEON, 4.0, pulse + _lit)
	# Inner tesseract motif echoes the player: the gate is "home".
	var core := Rect2(Vector2(-18, -HEIGHT * 0.5 - 18), Vector2(36, 36))
	Neon.rect_outline(self, core, Palette.PLAYER_CORE.lerp(Palette.NEON, 1.0 - _lit), 2.0, 0.6 + _lit)
	if _lit > 0.0:
		Neon.soft_light(self, core.get_center(), 160.0 * _lit, Color(Palette.PLAYER_CORE, _lit))
