@tool
class_name RotatingArm
extends Hazard
## ROTATING ARMS (docs/GDD.md §7): deadly bars turning around a hub at a
## constant speed. The angle is a pure function of level time, so the gaps
## between the arms arrive at exactly the same moment on every attempt.
## A faint ring shows the reach of the arms. Origin = the hub.

@export_range(1, 4) var arms := 2:
	set(value):
		arms = value
		_rebuild()
## From the hub centre to the tip of an arm.
@export_range(32.0, 640.0, 1.0, "suffix:px") var length := 192.0:
	set(value):
		length = value
		_rebuild()
@export_range(8.0, 64.0, 1.0, "suffix:px") var thickness := 22.0:
	set(value):
		thickness = value
		_rebuild()
## Radians per second; positive turns clockwise.
@export_range(-12.0, 12.0, 0.05, "suffix:rad/s") var speed := 1.6
## Starting angle, in turns (0..1).
@export_range(0.0, 1.0, 0.01) var phase := 0.0
@export_range(8.0, 64.0, 1.0, "suffix:px") var hub_radius := 24.0:
	set(value):
		hub_radius = value
		_rebuild()

var _shapes: Array[CollisionShape2D] = []


func _ready() -> void:
	super()
	_rebuild()
	apply_time(0.0)


func angle_at(t: float) -> float:
	return phase * TAU + speed * t


func apply_time(t: float) -> void:
	rotation = fposmod(angle_at(t), TAU)


func _rebuild() -> void:
	if not is_inside_tree():
		return
	for shape_node in _shapes:
		shape_node.queue_free()
	_shapes.clear()
	# Each arm: from the hub edge to just short of the tip, a little thinner
	# than drawn.
	var arm_length := length - HITBOX_INSET
	for i in arms:
		var rect := RectangleShape2D.new()
		rect.size = Vector2(arm_length, maxf(thickness - HITBOX_INSET * 2.0, 4.0))
		var shape_node := add_hitbox(rect, Vector2.from_angle(TAU * i / arms) * arm_length * 0.5)
		shape_node.rotation = TAU * i / arms
		_shapes.append(shape_node)
	var hub := CircleShape2D.new()
	hub.radius = maxf(hub_radius - HITBOX_INSET, 4.0)
	_shapes.append(add_hitbox(hub))
	queue_redraw()


func _draw() -> void:
	draw_arc(Vector2.ZERO, length, 0.0, TAU, 64, Color(Palette.HAZARD, 0.13), 2.0, true)
	for i in arms:
		var angle := TAU * i / arms
		var xf := Transform2D(angle, Vector2.ZERO)
		var half := thickness * 0.5
		var bar := PackedVector2Array([
			xf * Vector2(0, -half), xf * Vector2(length - half, -half),
			xf * Vector2(length, 0), xf * Vector2(length - half, half), xf * Vector2(0, half)])
		draw_colored_polygon(bar, Palette.HAZARD_BODY)
		Neon.polyline(self, bar, Palette.HAZARD, 2.5, 1.0, true)
		# Hot core line to a bright tip: the tip is what hits first.
		draw_line(xf * Vector2(hub_radius, 0), xf * Vector2(length - half, 0), Color(Palette.HAZARD_CORE, 0.6), 2.0, true)
		Neon.soft_light(self, xf * Vector2(length - half, 0), thickness * 1.3, Color(Palette.HAZARD_CORE, 0.5))
	var hub := PackedVector2Array()
	for i in 6:
		hub.append(Vector2.from_angle(TAU * i / 6.0) * hub_radius)
	draw_colored_polygon(hub, Palette.HAZARD_BODY)
	Neon.polyline(self, hub, Palette.HAZARD, 3.0, 1.2, true)
	draw_circle(Vector2.ZERO, hub_radius * 0.35, Palette.HAZARD_CORE)
