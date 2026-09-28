@tool
class_name CanvasCurtain
extends GardenHazard
## CANVAS CURTAIN (World 04): a long painted banner hanging from its surface
## on a rod. The wind unrolls it across part of the corridor and rolls it
## back up, on a fixed rhythm (it flutters before each move). It covers a
## part of the way, never all of it, and never for long: pass under it or
## over it, or wait for it to roll up. Touching the cloth is fatal: it is
## heavy, wet paint. Anchored to the ceiling, the ground, or the sky side of
## the moment. Origin = on the ground line at the left end.
## (Mirrored in tools/levelgen/levelgen.py: CanvasCurtain.)

@export var width := 120.0:
	set(value):
		width = value
		_rebuild()
## Hanging length rolled up and let down (px).
@export var short := 40.0:
	set(value):
		short = value
		_rebuild()
@export var long := 190.0:
	set(value):
		long = value
		_rebuild()
@export_range(0.3, 10.0, 0.05, "suffix:s") var period := 2.2
@export_range(0.0, 0.95, 0.01) var hold_ratio := 0.5
@export_range(0.0, 1.0, 0.01) var phase := 0.0
@export_range(0.0, 1.0, 0.05, "suffix:s") var warning_time := 0.35

var _cloth: _Cloth
var _rod: Node2D
var _shape: CollisionShape2D
var _box: RectangleShape2D
var _was_warning := false


func _ready() -> void:
	super()
	_rebuild()
	apply_time(0.0)


func length_at(t: float) -> float:
	return short + (long - short) * Timeline.steps(fposmod(t / period + phase, 1.0), hold_ratio)


## The world x range (px) it can ever cover (its whole motion).
func x_reach() -> Vector2:
	var x := global_position.x
	return Vector2(x, x + width)


func apply_time(t: float) -> void:
	if _cloth == null:
		return
	var start := GardenHazard.cycle_start(t, period, phase)
	var up := on_ceiling_at(start)
	var cycle := fposmod(t / period + phase, 1.0)
	var hang := length_at(t)
	var playing := advance_to(t)
	var surface := surface_y(up)
	var dir := GardenHazard.into(up)
	var warning := Timeline.steps_warning(cycle, hold_ratio, period, warning_time)
	_cloth.position = Vector2(0.0, surface)
	_cloth.scale = Vector2(1.0, dir * hang / long)
	_cloth.skew = sin(t * 30.0) * 0.06 if warning else 0.0
	_rod.position = Vector2(0.0, surface)
	_rod.scale = Vector2(1.0, dir)
	if playing and warning and not _was_warning:
		cue(&"warning", global_position + Vector2(width * 0.5, surface))
	_was_warning = warning
	_box.size = Vector2(width - HITBOX_INSET * 2.0, hang - HITBOX_INSET * 2.0)
	_shape.position = Vector2(width * 0.5, surface + dir * hang * 0.5)


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _cloth == null:
		_cloth = _Cloth.new()
		add_child(_cloth, false, Node.INTERNAL_MODE_FRONT)
		_rod = _Rod.new()
		add_child(_rod, false, Node.INTERNAL_MODE_FRONT)
		ink(_cloth)
		ink(_rod)
		_box = RectangleShape2D.new()
		_shape = add_hitbox(_box)
	_cloth.setup(width, long, int(absf(position.x)) + 5)
	(_rod as _Rod).width = width
	_rod.queue_redraw()


## The banner let all the way down (+y from the rod), painted once: a dark
## cloth with brushed blossoms and waves, a fringe at the hem.
class _Cloth extends Node2D:
	var width := 120.0
	var long := 190.0
	var seed_value := 5

	func setup(w: float, l: float, s: int) -> void:
		width = w
		long = l
		seed_value = s
		queue_redraw()

	func _draw() -> void:
		var rng := RandomNumberGenerator.new()
		rng.seed = seed_value
		# A banner, not a board: the sides bow a little as the cloth hangs and
		# the hem is cut in scallops, all inside the cloth's reach (the
		# hitbox is the full width and length, less a few px).
		var shape := PackedVector2Array([Vector2(0.0, 0.0)])
		var steps := 8
		for i in range(1, steps + 1):
			var y := long * i / steps
			shape.append(Vector2(2.5 * sin(PI * i / steps) + rng.randf_range(-0.8, 0.8), y if i < steps else long - 6.0))
		var scallops := maxi(int(width / 26.0), 2)
		for i in scallops + 1:
			var x := width * i / scallops
			shape.append(Vector2(x, long))
			if i < scallops:
				shape.append(Vector2(x + width / scallops * 0.5, long - 11.0))
		for i in range(steps, 0, -1):
			var y := long * i / steps
			shape.append(Vector2(width - 2.5 * sin(PI * i / steps) + rng.randf_range(-0.8, 0.8), y if i < steps else long - 6.0))
		shape.append(Vector2(width, 0.0))
		draw_colored_polygon(shape, GardenLook.INK_BODY)
		var loop := shape.duplicate()
		loop.append(shape[0])
		draw_polyline(loop, GardenLook.INK_RIM, 2.0, true)
		# Painted motif: brush waves and a blossom.
		var strokes := PackedVector2Array()
		for row in 3:
			var y := long * (0.25 + 0.25 * row)
			var x := 8.0
			var prev := Vector2(x, y)
			while x < width - 8.0:
				x += 14.0
				var p := Vector2(minf(x, width - 8.0), y + sin(x * 0.12 + row) * 6.0)
				strokes.append_array([prev, p])
				prev = p
		draw_multiline(strokes, Color(GardenLook.INK_RIM, 0.55), 2.5, true)
		var c := Vector2(width * rng.randf_range(0.35, 0.65), long * 0.5)
		for i in 5:
			var a := TAU * i / 5.0
			draw_circle(c + Vector2.from_angle(a) * 12.0, 8.0, Color(GardenLook.INK_RIM, 0.35))
		draw_circle(c, 5.0, GardenLook.INK_RIM)
		var fringe := PackedVector2Array()
		var fx := 6.0
		while fx < width - 4.0:
			fringe.append_array([Vector2(fx, long - 4.0), Vector2(fx + 1.5, long - 14.0)])
			fx += 9.0
		draw_multiline(fringe, GardenLook.INK_RIM, 1.5)


class _Rod extends Node2D:
	var width := 120.0

	func _draw() -> void:
		draw_line(Vector2(-10.0, 4.0), Vector2(width + 10.0, 4.0), GardenLook.INK_BODY, 7.0, true)
		draw_circle(Vector2(-10.0, 4.0), 6.0, GardenLook.INK_RIM)
		draw_circle(Vector2(width + 10.0, 4.0), 6.0, GardenLook.INK_RIM)
