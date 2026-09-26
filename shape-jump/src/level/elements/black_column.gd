@tool
class_name BlackColumn
extends Hazard
## BLACK COLUMN (docs/GDD.md §7, World 02): a tall black monolith that rises
## and sinks (give it an [Oscillator] child, usually the STEPS wave, which
## flashes it before each move). Rows of them, rising from the floor and
## hanging from above, open and close the spaces you jump through.
## Origin = top-left corner.

@export var size := Vector2(64, 448):
	set(value):
		size = value.max(Vector2(16, 16))
		_rebuild()

var _shape_node: CollisionShape2D


func _ready() -> void:
	super()
	_rebuild()


func get_rect() -> Rect2:
	return Rect2(Vector2.ZERO, size)


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _shape_node == null:
		_shape_node = add_hitbox(RectangleShape2D.new())
	(_shape_node.shape as RectangleShape2D).size = size - Vector2.ONE * HITBOX_INSET * 2.0
	_shape_node.position = size * 0.5
	queue_redraw()


func _draw() -> void:
	var rect := get_rect()
	draw_rect(rect, Palette.HAZARD_BODY)
	Neon.rect_outline(self, rect, Palette.HAZARD, 2.0, 0.9)
	# Two hairline seams down the face and bright bands at both ends.
	for k: float in [0.33, 0.67]:
		var x := size.x * k
		draw_line(Vector2(x, 10.0), Vector2(x, size.y - 10.0), Color(Palette.HAZARD_CORE, 0.18), 1.0)
	for y: float in [8.0, size.y - 8.0]:
		draw_line(Vector2(6.0, y), Vector2(size.x - 6.0, y), Color(Palette.HAZARD_CORE, 0.85), 3.0)
