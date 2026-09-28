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
var _seed := 0
## The picture, built once per size: one crystal spear per arm (along +x,
## drawn turned) and a faceted crystal hub.
var _spears: Array[PackedVector2Array] = []
var _veins := PackedVector2Array()
var _hub := PackedVector2Array()


func _ready() -> void:
	super()
	_seed = OrganicArt.seed_of(position, 9)
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
	_spears.clear()
	queue_redraw()


## A spear of void crystal along +x: uneven faceted edges (never inside the
## hitbox's thinner band), a sharp point, a vein of light down its length.
func _build_art() -> void:
	_spears.clear()
	_veins.clear()
	var half := thickness * 0.5
	var slack := minf(HITBOX_INSET * 0.8, half * 0.3)
	for i in arms:
		var k := _seed + i * 97
		var top := PackedVector2Array()
		var bottom := PackedVector2Array()
		var steps := maxi(int((length - half) / 26.0), 2)
		for s in steps + 1:
			var x := lerpf(0.0, length - half, float(s) / steps)
			top.append(Vector2(x, -half + slack * OrganicArt.rand(k, s * 2)))
			bottom.append(Vector2(x, half - slack * OrganicArt.rand(k, s * 2 + 1)))
		var spear := top
		spear.append(Vector2(length, (OrganicArt.rand(k, 99) - 0.5) * 2.0))
		bottom.reverse()
		spear.append_array(bottom)
		_spears.append(spear)
		if i == 0:
			OrganicArt.crooked(_veins, Vector2(hub_radius, 0.0), Vector2(length - half, 0.0), k, half * 0.3, 20.0)
	var ring := PackedVector2Array()
	for i in 7:
		ring.append(Vector2.from_angle(TAU * i / 7.0 + OrganicArt.rand(_seed, 50 + i) * 0.4) * hub_radius
			* (0.95 + 0.12 * OrganicArt.rand(_seed, 60 + i)))
	_hub = ring


func _draw() -> void:
	if _spears.is_empty():
		_build_art()
	draw_arc(Vector2.ZERO, length, 0.0, TAU, 64, Color(Palette.HAZARD, 0.13), 2.0, true)
	var half := thickness * 0.5
	for i in arms:
		var angle := TAU * i / arms
		draw_set_transform(Vector2.ZERO, angle)
		var spear := _spears[i]
		draw_colored_polygon(spear, Palette.HAZARD_BODY)
		# Facets: the ridge of the blade, then its vein of light.
		draw_line(Vector2(hub_radius, 0), Vector2(length, 0), Color(Palette.HAZARD, 0.3), 1.0, true)
		draw_multiline(_veins, Color(Palette.HAZARD_CORE, 0.6), 1.5)
		Neon.polyline(self, spear, Palette.HAZARD, 2.5, 1.0, true)
		# A bright tip: the tip is what hits first.
		Neon.soft_light(self, Vector2(length - half, 0), thickness * 1.3, Color(Palette.HAZARD_CORE, 0.5))
	draw_set_transform(Vector2.ZERO)
	draw_colored_polygon(_hub, Palette.HAZARD_BODY)
	for p in _hub:
		draw_line(p * 0.9, Vector2.ZERO, Color(Palette.HAZARD, 0.35), 1.0, true)
	Neon.polyline(self, _hub, Palette.HAZARD, 3.0, 1.2, true)
	draw_circle(Vector2.ZERO, hub_radius * 0.35, Palette.HAZARD_CORE)
