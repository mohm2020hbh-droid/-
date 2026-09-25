@tool
class_name WallPanel
extends Hazard
## MOVING WALL PANEL (docs/GDD.md §7): a deadly slab that slides to block
## the run high or low. Give it an [Oscillator] child (the STEPS wave holds
## it at each end and flashes it before it moves); without one it is a
## static deadly wall. Origin = top-left corner.

@export var size := Vector2(48, 224):
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
	HazardArt.slab(self, get_rect())
	# Chevrons down the middle mark it as a moving machine, not a gate.
	var mid := size.x * 0.5
	var y := 24.0
	while y < size.y - 16.0:
		draw_polyline(PackedVector2Array([Vector2(mid - 10, y - 6), Vector2(mid, y + 2), Vector2(mid + 10, y - 6)]),
			Color(Palette.HAZARD_CORE, 0.55), 2.0, true)
		y += 28.0
