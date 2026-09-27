@tool
class_name SweepingBranch
extends GardenHazard
## SWEEPING BRANCH (World 04): a long leafy branch swinging across the
## corridor like a pendulum, left -> centre -> right -> back, at a steady
## rhythm you can read from afar. It hangs from the ceiling (or grows from
## the ground). The whole branch and its crown of leaves kill.
## Origin = on the ground line below the pivot. (Mirrored in
## tools/levelgen/levelgen.py: SweepingBranch.)

## Branch length from the pivot to the crown's centre (px).
@export var length := 200.0:
	set(value):
		length = value
		_rebuild()
@export var thickness := 22.0:
	set(value):
		thickness = value
		_rebuild()
## Radius of the crown of leaves at its end.
@export var crown := 34.0:
	set(value):
		crown = value
		_rebuild()
## Swing either side of hanging straight (radians).
@export_range(0.0, 1.4, 0.01) var amplitude := 0.8
@export_range(0.3, 10.0, 0.05, "suffix:s") var period := 2.0
@export_range(0.0, 1.0, 0.01) var phase := 0.0

var _limb: _Limb
var _bar: CollisionShape2D
var _crown_shape: CollisionShape2D


func _ready() -> void:
	super()
	_rebuild()
	apply_time(0.0)


func angle_at(t: float) -> float:
	return amplitude * sin(TAU * (t / period + phase))


## Where the branch points at [param angle] (from its pivot).
func direction(angle: float) -> Vector2:
	return Vector2(sin(angle), cos(angle)) if anchor == Anchor.CEILING else Vector2(sin(angle), -cos(angle))


func pivot() -> Vector2:
	return Vector2(0.0, ceiling if anchor == Anchor.CEILING else 0.0)


## The world x range (px) it can ever cover (its whole motion).
func x_reach() -> Vector2:
	var x := global_position.x
	return Vector2(x - length - crown, x + length + crown)


func apply_time(t: float) -> void:
	if _limb == null:
		return
	advance_to(t)
	var a := angle_at(t)
	var dir := direction(a)
	var base := pivot()
	_limb.position = base
	if anchor == Anchor.CEILING:
		_limb.rotation = -a
		_limb.scale = Vector2.ONE
	else:
		_limb.rotation = a
		_limb.scale = Vector2(1.0, -1.0)
	var bar := length * 0.92
	_bar.position = base + dir * bar * 0.5
	_bar.rotation = dir.angle()
	_crown_shape.position = base + dir * length


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _limb == null:
		_limb = _Limb.new()
		add_child(_limb, false, Node.INTERNAL_MODE_FRONT)
		ink(_limb)
		_bar = add_hitbox(RectangleShape2D.new())
		_crown_shape = add_hitbox(CircleShape2D.new())
	(_bar.shape as RectangleShape2D).size = Vector2(length * 0.92 - HITBOX_INSET * 2.0,
		maxf(thickness - HITBOX_INSET * 2.0, 4.0))
	(_crown_shape.shape as CircleShape2D).radius = crown * 0.8
	_limb.setup(length, thickness, crown, int(absf(position.x)) + 3)
	queue_redraw()


func _draw() -> void:
	# The arc it sweeps, dotted faintly: its reach is always readable.
	var base := pivot()
	var points := PackedVector2Array()
	for i in 17:
		var a := lerpf(-amplitude, amplitude, i / 16.0)
		points.append(base + direction(a) * length)
	draw_polyline(points, Color(GardenLook.INK_BODY, 0.28), 2.0, true)


## The branch, drawn once hanging straight down (+y) from its pivot.
class _Limb extends Node2D:
	var length := 200.0
	var thickness := 22.0
	var crown := 34.0
	var seed_value := 3

	func setup(l: float, th: float, c: float, s: int) -> void:
		length = l
		thickness = th
		crown = c
		seed_value = s
		queue_redraw()

	func _draw() -> void:
		var rng := RandomNumberGenerator.new()
		rng.seed = seed_value
		var batch := GardenArt.Batch.new(GardenLook.INK_BODY)
		# The limb: a bar as thick as its hitbox all the way, then the crown.
		var half := thickness * 0.5
		batch.poly(PackedVector2Array([Vector2(-half, -6.0), Vector2(half, -6.0), Vector2(half * 0.9, length),
			Vector2(-half * 0.9, length)]))
		for i in 3:
			var at := Vector2(0.0, length * (0.35 + 0.18 * i))
			var side := 1.0 if i % 2 == 0 else -1.0
			GardenArt.tendril(batch, at, Vector2(side, 0.7), length * 0.22, 8.0, side * 6.0, 4)
		# The crown: overlapping leaves around the end.
		var tip := Vector2(0.0, length)
		batch.poly(GardenArt.stone(tip, crown * 0.86, seed_value + 5), false, GardenLook.INK_DEEP)
		for i in 9:
			var a := TAU * i / 9.0 + rng.randf_range(-0.2, 0.2)
			var c := tip + Vector2.from_angle(a) * crown * 0.55
			batch.poly(GardenArt.leaf(c, crown * 0.95, crown * 0.42, a))
		batch.draw(self, GardenLook.INK_RIM, 2.0)
		var veins := PackedVector2Array()
		for i in 6:
			var a := TAU * i / 6.0
			veins.append_array([tip, tip + Vector2.from_angle(a) * crown * 0.7])
		draw_multiline(veins, Color(GardenLook.INK_RIM, 0.45), 1.5)
