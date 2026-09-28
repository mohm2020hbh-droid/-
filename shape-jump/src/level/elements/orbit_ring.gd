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
var _seed := 0
## The picture, built once: each solid arc between two openings as one
## stroke of ink (its edges a little uneven), a dry-brush streak along it,
## and a brush dab across each end of an opening.
var _arcs: Array[PackedVector2Array] = []
var _rims: Array[PackedVector2Array] = []
var _streaks := PackedVector2Array()
var _caps: Array[PackedVector2Array] = []


func _ready() -> void:
	super()
	_seed = OrganicArt.seed_of(position, 17)
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
	_arcs.clear()
	queue_redraw()


func _build_art() -> void:
	_arcs.clear()
	_rims.clear()
	_caps.clear()
	_streaks.clear()
	var step := TAU / segments
	var per := segments / openings
	for o in openings:
		var a0 := (o * per + gap_segments) * step
		var a1 := (o + 1) * per * step
		var count := maxi(int((a1 - a0) * radius / 12.0), 2)
		var outer := PackedVector2Array()
		var inner := PackedVector2Array()
		for i in count + 1:
			var a := lerpf(a0, a1, float(i) / count)
			var edge := 0.0 if i == 0 or i == count else 1.0
			outer.append(Vector2.from_angle(a) * (radius + thickness * 0.5 + edge * (OrganicArt.rand(_seed, o * 200 + i) - 0.5) * 3.0))
			inner.append(Vector2.from_angle(a) * (radius - thickness * 0.5 + edge * (OrganicArt.rand(_seed, o * 200 + 100 + i) - 0.5) * 3.0))
		_rims.append(outer.duplicate())
		inner.reverse()
		outer.append_array(inner)
		_arcs.append(outer)
		# A streak along the stroke, broken where the brush ran dry.
		var s := a0 + 0.06
		while s < a1 - 0.08:
			var e := minf(s + 0.18 + 0.3 * OrganicArt.rand(_seed, int(s * 100.0)), a1 - 0.05)
			var r := radius + (OrganicArt.rand(_seed, int(s * 100.0) + 7) - 0.5) * thickness * 0.4
			_streaks.append(Vector2.from_angle(s) * r)
			_streaks.append(Vector2.from_angle(e) * r)
			s = e + 0.1 + 0.15 * OrganicArt.rand(_seed, int(s * 100.0) + 13)
		for a in [a0, a1]:
			var d := Vector2.from_angle(a)
			_caps.append(OrganicArt.brush(d * (radius - thickness * 0.65), d * (radius + thickness * 0.65), 5.0,
				_seed + int(a * 1000.0)))


func _draw() -> void:
	if _arcs.is_empty():
		_build_art()
	# The orbit's track, faint, so the whole circle reads before it arrives.
	draw_arc(Vector2.ZERO, radius, 0.0, TAU, 64, Color(Palette.NEON, 0.1), thickness + 10.0, true)
	for i in _arcs.size():
		draw_colored_polygon(_arcs[i], Palette.HAZARD_BODY)
		draw_polyline(_arcs[i] + PackedVector2Array([_arcs[i][0]]), Color(Palette.HAZARD, 0.6), 1.5, true)
		draw_polyline(_rims[i], Palette.HAZARD, 2.5, true)
	if not _streaks.is_empty():
		draw_multiline(_streaks, Color(Palette.HAZARD_CORE, 0.28), 2.0)
	# Bright dabs where an opening begins and ends: the openings are what you read.
	for cap in _caps:
		draw_colored_polygon(cap, Palette.HAZARD_CORE)
	# The pivot: a fixed white point and faint spokes that show the turn.
	var step := TAU / segments
	for o in openings:
		var d := Vector2.from_angle((o * (segments / openings) + gap_segments * 0.5) * step)
		draw_line(Vector2.ZERO, d * (radius - thickness), Color(Palette.NEON, 0.12), 1.0, true)
	draw_colored_polygon(OrganicArt.blot(Vector2.ZERO, 6.0, _seed, 8), Palette.HAZARD_CORE)
