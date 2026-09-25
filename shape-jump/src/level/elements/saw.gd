@tool
class_name Saw
extends Area2D
## Spinning toothed rotor (docs/GDD.md §7). Origin = centre.
## The spin is cosmetic; the hitbox is a circle inside the teeth.
## Add an [Oscillator] child to make it move.

@export_range(16.0, 256.0, 1.0, "suffix:px") var radius := 44.0:
	set(value):
		radius = value
		_rebuild()
@export_range(5, 24) var teeth := 12:
	set(value):
		teeth = value
		queue_redraw()
@export_range(-20.0, 20.0, 0.1, "suffix:rad/s") var spin_speed := 7.0
@export_range(0.3, 1.0, 0.05) var hitbox_scale := 0.72:
	set(value):
		hitbox_scale = value
		_rebuild()

var _shape_node: CollisionShape2D


func _ready() -> void:
	collision_layer = GameConst.LAYER_HAZARD
	collision_mask = 0
	monitoring = false
	_rebuild()


func _process(delta: float) -> void:
	if Engine.is_editor_hint():
		return
	rotation = wrapf(rotation + spin_speed * delta, 0.0, TAU)
	HazardPulse.apply(self, position.x * 0.01)


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _shape_node == null:
		_shape_node = CollisionShape2D.new()
		_shape_node.shape = CircleShape2D.new()
		add_child(_shape_node, false, Node.INTERNAL_MODE_FRONT)
	(_shape_node.shape as CircleShape2D).radius = radius * hitbox_scale
	queue_redraw()


func _draw() -> void:
	Neon.soft_light(self, Vector2.ZERO, radius * 1.7, Color(Palette.HAZARD, 0.32))
	var star := PackedVector2Array()
	for i in teeth * 2:
		var r := radius if i % 2 == 0 else radius * 0.74
		star.append(Vector2.from_angle(TAU * i / (teeth * 2)) * r)
	draw_colored_polygon(star, Palette.BLOCK_BODY)
	Neon.polyline(self, star, Palette.HAZARD, 2.5, 1.0, true)
	draw_arc(Vector2.ZERO, radius * 0.42, 0.0, TAU, 32, Palette.HAZARD, 4.0, true)
	draw_circle(Vector2.ZERO, radius * 0.16, Palette.HAZARD_CORE)
