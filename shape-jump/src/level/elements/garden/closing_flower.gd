@tool
class_name ClosingFlower
extends GardenHazard
## CLOSING FLOWER (World 04): a giant flower set in a surface. Open, its
## petals lie flat along the surface: harmless, run over it. Closed, it is a
## tall bud standing across the way: jump it, or be on the other surface.
## The petals tremble and brighten before every move ([member warning_time]),
## so the rhythm is always announced. Anchored to a surface, or to the floor
## of the moment (the flower opens where you run).
## Origin = on the ground line at the flower's centre. (Mirrored in
## tools/levelgen/levelgen.py: ClosingFlower.)

## Width of the closed bud (px).
@export var width := 72.0:
	set(value):
		width = value
		_rebuild()
## Height of the closed bud out of its surface (px).
@export var height := 118.0:
	set(value):
		height = value
		_rebuild()
@export_range(0.3, 10.0, 0.05, "suffix:s") var period := 1.8
## Share of the cycle spent holding open and closed (the rest is moving).
@export_range(0.0, 0.95, 0.01) var hold_ratio := 0.6
@export_range(0.0, 1.0, 0.01) var phase := 0.0
@export_range(0.0, 1.0, 0.05, "suffix:s") var warning_time := 0.35

const PETALS := 5
## The deadly box, inside the closed bud's silhouette: this share of its
## width either side of the centre, and of its height.
const HIT_HALF_WIDTH := 0.24
const HIT_HEIGHT := 0.86

var _petals: Array[Node2D] = []
var _bud: Node2D
var _heart: Node2D
var _shape: CollisionShape2D
var _box: RectangleShape2D
var _was_warning := false


func _ready() -> void:
	super()
	_rebuild()
	apply_time(0.0)


## 0 open .. 1 closed at [param t].
func closure_at(t: float) -> float:
	return Timeline.steps(fposmod(t / period + phase, 1.0), hold_ratio)


## The world x range (px) it can ever cover (its whole motion).
func x_reach() -> Vector2:
	var x := global_position.x
	return Vector2(x - width * 0.5, x + width * 0.5)


func apply_time(t: float) -> void:
	if _petals.is_empty():
		return
	var start := GardenHazard.cycle_start(t, period, phase)
	var up := on_ceiling_at(start)
	var cycle := fposmod(t / period + phase, 1.0)
	var k := Timeline.steps(cycle, hold_ratio)
	var playing := advance_to(t)
	var surface := surface_y(up)
	var dir := GardenHazard.into(up)
	var warning := Timeline.steps_warning(cycle, hold_ratio, period, warning_time)
	var tremble := sin(t * 40.0) * 0.08 if warning else 0.0
	for i in _petals.size():
		var petal := _petals[i]
		# Flat along the surface when open (fanned out), upright when closed.
		var side := (float(i) / (PETALS - 1)) * 2.0 - 1.0
		var flat := side * (PI * 0.5 - 0.12)
		var upright := side * 0.16
		petal.position = Vector2(side * width * 0.18, surface)
		petal.rotation = lerpf(flat, upright, k) + tremble * (1.0 - k)
		petal.scale = Vector2(1.0, -dir)
	_bud.visible = k > 0.01
	_bud.position = Vector2(0.0, surface)
	_bud.scale = Vector2(1.0, -dir * maxf(k, 0.01))
	_heart.position = Vector2(0.0, surface)
	_heart.scale = Vector2(1.0, -dir)
	_heart.modulate = Color(1.6, 1.6, 1.6) if warning and fmod(t * 10.0, 1.0) < 0.5 else Color.WHITE
	if playing and warning and not _was_warning:
		cue(&"warning", global_position + Vector2(0.0, surface))
	_was_warning = warning
	var deadly := height * HIT_HEIGHT * k
	if deadly <= HITBOX_INSET * 2.0:
		_shape.position = PARKED
		return
	_box.size = Vector2(width * HIT_HALF_WIDTH * 2.0, deadly)
	_shape.position = Vector2(0.0, surface + dir * deadly * 0.5)


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _petals.is_empty():
		for i in PETALS:
			var petal := _Petal.new()
			add_child(petal, false, Node.INTERNAL_MODE_FRONT)
			ink(petal)
			_petals.append(petal)
		_bud = _Bud.new()
		add_child(_bud, false, Node.INTERNAL_MODE_FRONT)
		ink(_bud)
		_heart = _Heart.new()
		add_child(_heart, false, Node.INTERNAL_MODE_FRONT)
		ink(_heart)
		_box = RectangleShape2D.new()
		_shape = add_hitbox(_box)
	for petal in _petals:
		(petal as _Petal).setup(height, width)
	(_bud as _Bud).setup(height, width)
	(_heart as _Heart).width = width
	_heart.queue_redraw()


## One petal, drawn upright (pointing -y) from its base at the origin. Its
## outer half is wide enough that the upright petals overlap into one bud
## covering the hitbox.
class _Petal extends Node2D:
	var length := 118.0
	var width := 72.0

	func setup(l: float, w: float) -> void:
		length = l
		width = w
		queue_redraw()

	func _draw() -> void:
		var batch := GardenArt.Batch.new(GardenLook.INK_BODY)
		var shape := PackedVector2Array()
		var steps := 12
		for i in steps + 1:
			var k := float(i) / steps
			var y := -length * k
			var half := width * 0.36 * sin(PI * minf(k * 1.1, 1.0)) + width * 0.12 * (1.0 - k)
			shape.append(Vector2(half, y))
		for i in range(steps, -1, -1):
			var k := float(i) / steps
			var y := -length * k
			var half := width * 0.36 * sin(PI * minf(k * 1.1, 1.0)) + width * 0.12 * (1.0 - k)
			shape.append(Vector2(-half, y))
		draw_colored_polygon(shape, GardenLook.INK_BODY)
		draw_polyline(GardenArt.closed(shape), GardenLook.INK_RIM, 2.0, true)
		draw_line(Vector2(0.0, -4.0), Vector2(0.0, -length * 0.8), Color(GardenLook.INK_RIM, 0.45), 1.5, true)
		batch.draw(self, GardenLook.INK_RIM)


## The closed bud, drawn once standing up (-y) from the origin; the element
## scales it with the closure. An ellipse-like silhouette, wider than the
## hitbox at every height the hitbox reaches.
class _Bud extends Node2D:
	var height := 118.0
	var width := 72.0

	func setup(h: float, w: float) -> void:
		height = h
		width = w
		queue_redraw()

	func _draw() -> void:
		var left := PackedVector2Array()
		var right := PackedVector2Array()
		var steps := 16
		for i in steps + 1:
			var f := float(i) / steps
			var e := (f - 0.45) / 0.55
			var half := width * 0.5 * sqrt(maxf(1.0 - e * e, 0.0))
			left.append(Vector2(-half, -height * f))
			right.append(Vector2(half, -height * f))
		var outline := right.duplicate()
		for i in range(left.size() - 1, -1, -1):
			outline.append(left[i])
		draw_colored_polygon(outline, GardenLook.INK_BODY)
		draw_polyline(GardenArt.closed(outline), GardenLook.INK_RIM, 2.5, true)
		# Folded petals: seams up the bud.
		var seams := PackedVector2Array()
		for side: float in [-0.5, 0.0, 0.5]:
			seams.append_array([Vector2(side * width * 0.3, -4.0), Vector2(side * width * 0.12, -height * 0.94)])
		draw_multiline(seams, Color(GardenLook.INK_RIM, 0.5), 1.5, true)


## The flower's heart and stem stub, at the surface.
class _Heart extends Node2D:
	var width := 72.0

	func _draw() -> void:
		var r := width * 0.2
		draw_circle(Vector2(0.0, -r * 0.6), r, GardenLook.INK_DEEP)
		draw_arc(Vector2(0.0, -r * 0.6), r, 0.0, TAU, 20, GardenLook.INK_RIM, 2.0, true)
		for i in 6:
			var a := TAU * i / 6.0
			draw_circle(Vector2(0.0, -r * 0.6) + Vector2.from_angle(a) * r * 0.55, 2.5, GardenLook.INK_RIM)
