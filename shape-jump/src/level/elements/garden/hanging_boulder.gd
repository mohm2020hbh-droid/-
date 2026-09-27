@tool
class_name HangingBoulder
extends GardenHazard
## HANGING BOULDER (World 04): a great stone slung in a net of roots from the
## ceiling (or propped on a stem from the ground), swinging on a steady
## pendulum. The stone kills; the rope of roots does not. Its whole arc is
## traced faintly, so its reach is never a surprise.
## Origin = on the ground line below the pivot. (Mirrored in
## tools/levelgen/levelgen.py: HangingBoulder.)

@export var rope := 190.0:
	set(value):
		rope = value
		_rebuild()
@export_range(12.0, 96.0, 1.0, "suffix:px") var radius := 40.0:
	set(value):
		radius = value
		_rebuild()
@export_range(0.0, 1.4, 0.01) var amplitude := 0.7
@export_range(0.3, 10.0, 0.05, "suffix:s") var period := 2.2
@export_range(0.0, 1.0, 0.01) var phase := 0.0

const HITBOX_SCALE := 0.82

var _swing: _Swing
var _shape: CollisionShape2D


func _ready() -> void:
	super()
	_rebuild()
	apply_time(0.0)


func angle_at(t: float) -> float:
	return amplitude * sin(TAU * (t / period + phase))


func pivot() -> Vector2:
	return Vector2(0.0, ceiling if anchor == Anchor.CEILING else 0.0)


func centre_at(t: float) -> Vector2:
	var a := angle_at(t)
	var down := 1.0 if anchor == Anchor.CEILING else -1.0
	return pivot() + Vector2(sin(a), down * cos(a)) * rope


## The world x range (px) it can ever cover (its whole motion).
func x_reach() -> Vector2:
	var x := global_position.x
	return Vector2(x - rope * sin(minf(amplitude, PI * 0.5)) - radius, x + rope * sin(minf(amplitude, PI * 0.5)) + radius)


func apply_time(t: float) -> void:
	if _swing == null:
		return
	advance_to(t)
	var a := angle_at(t)
	_swing.position = pivot()
	if anchor == Anchor.CEILING:
		_swing.rotation = -a
		_swing.scale = Vector2.ONE
	else:
		_swing.rotation = a
		_swing.scale = Vector2(1.0, -1.0)
	_shape.position = centre_at(t)


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _swing == null:
		_swing = _Swing.new()
		add_child(_swing, false, Node.INTERNAL_MODE_FRONT)
		ink(_swing)
		_shape = add_hitbox(CircleShape2D.new())
	(_shape.shape as CircleShape2D).radius = radius * HITBOX_SCALE
	_swing.setup(rope, radius, int(absf(position.x)) + 41)
	queue_redraw()


func _draw() -> void:
	var down := 1.0 if anchor == Anchor.CEILING else -1.0
	var points := PackedVector2Array()
	for i in 21:
		var a := lerpf(-amplitude, amplitude, i / 20.0)
		points.append(pivot() + Vector2(sin(a), down * cos(a)) * rope)
	draw_polyline(points, Color(GardenLook.INK_BODY, 0.25), 2.0, true)


## The rope of roots and the stone, drawn once hanging straight down (+y).
class _Swing extends Node2D:
	var rope := 190.0
	var radius := 40.0
	var seed_value := 41

	func setup(r: float, rad: float, s: int) -> void:
		rope = r
		radius = rad
		seed_value = s
		queue_redraw()

	func _draw() -> void:
		# Two twisted root ropes.
		var left := PackedVector2Array()
		var right := PackedVector2Array()
		for i in 13:
			var k := i / 12.0
			var y := (rope - radius * 0.6) * k
			left.append(Vector2(sin(k * 9.0) * 4.0 - 2.0, y))
			right.append(Vector2(-sin(k * 9.0) * 4.0 + 2.0, y))
		draw_polyline(left, GardenLook.INK_BODY, 4.0, true)
		draw_polyline(right, GardenLook.INK_BODY, 3.0, true)
		var c := Vector2(0.0, rope)
		var stone := GardenArt.stone(c, radius * 1.02, seed_value)
		draw_colored_polygon(stone, GardenLook.INK_BODY)
		# The net of roots holding it.
		var net := PackedVector2Array()
		for i in 5:
			var a := PI * (0.15 + 0.175 * i) + PI
			net.append_array([c + Vector2(0.0, -radius), c + Vector2.from_angle(a + PI) * radius * 0.95])
		draw_multiline(net, Color(GardenLook.INK_RIM, 0.55), 2.0, true)
		draw_polyline(GardenArt.closed(stone), GardenLook.INK_RIM, 3.0, true)
		draw_arc(c + Vector2(-radius * 0.25, -radius * 0.2), radius * 0.5, PI * 1.05, PI * 1.6, 8,
			Color(GardenLook.INK_RIM, 0.6), 2.0, true)
