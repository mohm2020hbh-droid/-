@tool
class_name BirdFlock
extends GardenHazard
## BIRD FLOCK (World 04): a tight V of birds sweeping up and down across the
## corridor at one place, on a fixed, calm rhythm: a moving barrier. It
## closes part of the way while it passes; read its sweep and go under or
## over it, attach past it, or time the gap. Flying into the flock is fatal.
## It is on screen long before you reach it (it enters from the right as the
## view moves on), and its sweep is traced faintly.
## Where it flies is the same whichever surface you run on: over the ground
## runner's head, under the ceiling runner's feet.
## Origin = on the ground line at the sweep's x. (Mirrored in
## tools/levelgen/levelgen.py: BirdFlock.)

## Centre height of the sweep, relative to the origin (px, negative: up).
@export var middle := -160.0
## Half the vertical sweep (px).
@export var sweep := 110.0
## Sideways drift either way (px).
@export var drift := 36.0
@export_range(0.5, 10.0, 0.05, "suffix:s") var period := 2.4
@export_range(0.0, 1.0, 0.01) var phase := 0.0
## The flock's size (the hitbox is its dense core).
@export var span := Vector2(110.0, 56.0):
	set(value):
		span = value
		_rebuild()

const CORE := 0.72

var _wings: Array[Node2D] = []
var _body: Node2D
var _shape: CollisionShape2D
var _box: RectangleShape2D


func _ready() -> void:
	super()
	_rebuild()
	apply_time(0.0)


func centre_at(t: float) -> Vector2:
	var a := TAU * (t / period + phase)
	return Vector2(drift * sin(a * 2.0), middle + sweep * sin(a))


## The world x range (px) it can ever cover (its whole motion).
func x_reach() -> Vector2:
	var x := global_position.x
	return Vector2(x - drift - span.x * 0.5, x + drift + span.x * 0.5)


func apply_time(t: float) -> void:
	if _body == null:
		return
	advance_to(t)
	var c := centre_at(t)
	_body.position = c
	# Two wing poses, swapped on a beat (no redraw).
	var up := fmod(t * 7.0, 1.0) < 0.5
	_wings[0].visible = up
	_wings[1].visible = not up
	for wing in _wings:
		wing.position = c
	_shape.position = c


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _body == null:
		_body = _Flock.new()
		add_child(_body, false, Node.INTERNAL_MODE_FRONT)
		for pose in 2:
			var wing := _Flock.new()
			wing.wings = pose + 1
			add_child(wing, false, Node.INTERNAL_MODE_FRONT)
			_wings.append(wing)
		for node: CanvasItem in [_body] + _wings:
			ink(node)
		_box = RectangleShape2D.new()
		_shape = add_hitbox(_box)
	_box.size = span * CORE - Vector2.ONE * HITBOX_INSET * 2.0
	for node in [_body] + _wings:
		(node as _Flock).span = span
		node.queue_redraw()
	queue_redraw()


func _draw() -> void:
	# The sweep, traced faintly.
	draw_dashed_line(Vector2(0.0, middle - sweep), Vector2(0.0, middle + sweep), Color(GardenLook.INK_BODY, 0.2),
		2.0, 10.0)


## The flock (wings = 0: the bodies and a soft mass over the core; 1 / 2:
## the wings up or down).
class _Flock extends Node2D:
	var span := Vector2(110.0, 56.0)
	var wings := 0

	func _birds() -> Array[Vector2]:
		var out: Array[Vector2] = []
		# A V pointing toward the player (left), seven birds.
		for i in 7:
			var row := (i + 1) / 2
			var side := 0.0 if i == 0 else (1.0 if i % 2 == 1 else -1.0)
			out.append(Vector2(-span.x * 0.42 + row * span.x * 0.26, side * row * span.y * 0.3))
		return out

	func _draw() -> void:
		if wings == 0:
			# The deadly core as a murmuration: a soft, uneven cloud of birds
			# (an ellipse around the core box, so it covers all of it).
			var half := span * CORE * 0.5 + Vector2(4.0, 4.0)
			var cloud := PackedVector2Array()
			for i in 14:
				var a := TAU * i / 14.0
				var r := 1.42 * (0.94 + 0.1 * sin(i * 2.7))
				cloud.append(Vector2(cos(a) * half.x * r, sin(a) * half.y * r))
			draw_colored_polygon(cloud, Color(GardenLook.INK_BODY, 0.5))
			for b in _birds():
				draw_circle(b, 6.0, GardenLook.INK_BODY)
				draw_arc(b, 6.0, 0.0, TAU, 10, GardenLook.INK_RIM, 1.5, true)
			return
		var lines := PackedVector2Array()
		var lift := -10.0 if wings == 1 else 8.0
		for b in _birds():
			lines.append_array([b, b + Vector2(-9.0, lift), b, b + Vector2(9.0, lift)])
		draw_multiline(lines, GardenLook.INK_BODY, 4.0, true)
		draw_multiline(lines, GardenLook.INK_RIM, 1.5, true)
