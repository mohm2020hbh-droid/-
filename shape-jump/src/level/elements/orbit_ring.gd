@tool
class_name OrbitRing
extends Hazard
## ORBIT RING (docs/GDD.md §7, World 02): a heavy ring turning around a fixed
## point, solid except for [member openings] gaps spread evenly around it.
## You run through the ring: in through one gap, out through another, so
## the jump (and often the double jump) must meet both. The spin is a pure
## function of level time; the hitbox is the ring itself, segment by
## segment, turning with it. Origin = the centre.

@export_range(48.0, 512.0, 1.0, "suffix:px") var radius := 150.0:
	set(value):
		radius = value
		_rebuild()
@export_range(8.0, 64.0, 1.0, "suffix:px") var thickness := 22.0:
	set(value):
		thickness = value
		_rebuild()
@export_range(8, 64) var segments := 24:
	set(value):
		segments = value
		_rebuild()
## Segments left out at each opening.
@export_range(1, 8) var gap_segments := 3:
	set(value):
		gap_segments = value
		_rebuild()
@export_range(1, 4) var openings := 2:
	set(value):
		openings = value
		_rebuild()
## Radians per second; positive turns clockwise.
@export_range(-12.0, 12.0, 0.05, "suffix:rad/s") var spin := 1.2
## Starting angle, in turns (0..1).
@export_range(0.0, 1.0, 0.01) var phase := 0.0

var _shapes: Array[CollisionShape2D] = []


func _ready() -> void:
	super()
	_rebuild()
	apply_time(0.0)


func apply_time(t: float) -> void:
	rotation = fposmod(phase * TAU + spin * t, TAU)


func is_gap(i: int) -> bool:
	return i % (segments / openings) < gap_segments


## Corners of segment [param i] (local), grown by [param inset] (negative shrinks).
func segment_polygon(i: int, inset: float) -> PackedVector2Array:
	var step := TAU / segments
	var a0 := i * step + inset / radius
	var a1 := (i + 1) * step - inset / radius
	var r0 := radius - thickness * 0.5 + inset
	var r1 := radius + thickness * 0.5 - inset
	return PackedVector2Array([Vector2.from_angle(a0) * r0, Vector2.from_angle(a0) * r1,
		Vector2.from_angle(a1) * r1, Vector2.from_angle(a1) * r0])


func _rebuild() -> void:
	if not is_inside_tree():
		return
	for node in _shapes:
		node.queue_free()
	_shapes.clear()
	for i in segments:
		if is_gap(i):
			continue
		var shape := ConvexPolygonShape2D.new()
		shape.points = segment_polygon(i, HITBOX_INSET)
		_shapes.append(add_hitbox(shape))
	queue_redraw()


func _draw() -> void:
	# The orbit's track, faint, so the whole circle reads before it arrives.
	draw_arc(Vector2.ZERO, radius, 0.0, TAU, 64, Color(Palette.NEON, 0.1), thickness + 10.0, true)
	for i in segments:
		if is_gap(i):
			continue
		var poly := segment_polygon(i, 0.0)
		draw_colored_polygon(poly, Palette.HAZARD_BODY)
		draw_line(poly[1], poly[2], Palette.HAZARD, 2.5, true)
		draw_line(poly[0], poly[3], Color(Palette.HAZARD, 0.6), 1.5, true)
	# Bright caps where a gap begins and ends: the openings are what you read.
	var step := TAU / segments
	for o in openings:
		var first := o * (segments / openings)
		for a in [first * step, (first + gap_segments) * step]:
			var d := Vector2.from_angle(a)
			Neon.line(self, d * (radius - thickness * 0.6), d * (radius + thickness * 0.6), Palette.HAZARD_CORE, 3.0, 1.2)
	# The pivot: a fixed white point and faint spokes that show the turn.
	for o in openings:
		var d := Vector2.from_angle((o * (segments / openings) + gap_segments * 0.5) * step)
		draw_line(Vector2.ZERO, d * (radius - thickness), Color(Palette.NEON, 0.12), 1.0, true)
	draw_circle(Vector2.ZERO, 6.0, Palette.HAZARD_CORE)
