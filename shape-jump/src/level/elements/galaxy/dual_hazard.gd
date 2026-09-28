@tool
class_name DualHazard
extends Hazard
## DUAL HAZARD (World 03): twin crystal spires, one rising from the ground
## and one hanging from the ceiling at the same x, growing and shrinking on
## a fixed rhythm (together, or in turn with [member alternate]). Neither
## surface is safe while they stand: you cross them in the air, or on the
## surface whose spire is down. They flicker before they move.
## Origin = on the ground line at the spires' x.

@export var ceiling := -384.0:
	set(value):
		ceiling = value
		_rebuild()
@export var width := 40.0:
	set(value):
		width = value
		_rebuild()
## Spire height when down (a stub that marks the spot) and when up.
@export var low := 20.0
@export var high := 140.0:
	set(value):
		high = value
		_rebuild()
@export_range(0.3, 10.0, 0.05, "suffix:s") var period := 1.6
@export_range(0.0, 0.95, 0.01) var hold_ratio := 0.5
@export_range(0.0, 1.0, 0.01) var phase := 0.0
## The ceiling spire runs half a cycle behind the ground one.
@export var alternate := false
@export_range(0.0, 1.0, 0.05, "suffix:s") var warning_time := 0.3

## The narrow tip (px) is forgiven: the box ends below it.
const TIP := 14.0

var _spires: Array[Node2D] = []
var _boxes: Array[RectangleShape2D] = []
var _shapes: Array[CollisionShape2D] = []


func _ready() -> void:
	super()
	_rebuild()
	apply_time(0.0)


## Spire heights at [param t]: x = ground spire, y = ceiling spire.
func heights_at(t: float) -> Vector2:
	var a := fposmod(t / period + phase, 1.0)
	var b := fposmod(a + (0.5 if alternate else 0.0), 1.0)
	return Vector2(low + (high - low) * Timeline.steps(a, hold_ratio),
		low + (high - low) * Timeline.steps(b, hold_ratio))


func apply_time(t: float) -> void:
	if _spires.is_empty():
		return
	var h := heights_at(t)
	# Ground spire: tip at -h.x, the rest inside the ground. Ceiling spire:
	# tip at ceiling + h.y. The deadly box stops short of the narrow tip.
	_spires[0].position = Vector2(0.0, -h.x)
	_spires[1].position = Vector2(0.0, ceiling - high + h.y)
	var down := maxf(h.x - TIP, 1.0)
	var up := maxf(h.y - TIP, 1.0)
	_boxes[0].size = Vector2(width - HITBOX_INSET * 2.0, down)
	_shapes[0].position = Vector2(0.0, -down * 0.5)
	_boxes[1].size = Vector2(width - HITBOX_INSET * 2.0, up)
	_shapes[1].position = Vector2(0.0, ceiling + up * 0.5)
	var a := fposmod(t / period + phase, 1.0)
	var b := fposmod(a + (0.5 if alternate else 0.0), 1.0)
	var blink := fmod(t * 10.0, 1.0) < 0.5
	_spires[0].self_modulate = Color(1.7, 1.7, 1.7) if blink and Timeline.steps_warning(a, hold_ratio, period, warning_time) else Color.WHITE
	_spires[1].self_modulate = Color(1.7, 1.7, 1.7) if blink and Timeline.steps_warning(b, hold_ratio, period, warning_time) else Color.WHITE


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _spires.is_empty():
		for i in 2:
			var spire := _Spire.new()
			add_child(spire, false, Node.INTERNAL_MODE_FRONT)
			_spires.append(spire)
			var box := RectangleShape2D.new()
			_boxes.append(box)
			_shapes.append(add_hitbox(box))
	var seed := OrganicArt.seed_of(position, 19)
	(_spires[0] as _Spire).setup(width, high, -1, seed)
	(_spires[1] as _Spire).setup(width, high, 1, seed + 1)
	queue_redraw()


func _draw() -> void:
	# Sockets in both surfaces (cracked rock round the spire), and a faint
	# arc between them: one machine.
	for y: float in [0.0, ceiling]:
		var socket := OrganicArt.brush(Vector2(-width * 0.75, y), Vector2(width * 0.75, y), 9.0, int(y) + 3)
		draw_colored_polygon(socket, GalaxyArt.DANGER_BODY)
		draw_polyline(GalaxyArt.closed(socket), Color(GalaxyArt.DANGER, 0.8), 1.5, true)
	GalaxyArt.dashed(self, Vector2(0.0, ceiling), Vector2(0.0, 0.0), Color(GalaxyArt.DANGER, 0.14), 1.5, 10.0)


## One spire, drawn once at full height; the machine slides it in and out
## of its surface (the hidden part stays inside the block).
class _Spire extends Node2D:
	var width := 40.0
	var tall := 140.0
	var facing := -1
	var seed := 0
	var _body := PackedVector2Array()
	var _facets := PackedVector2Array()

	func setup(w: float, h: float, f: int, s: int) -> void:
		width = w
		tall = h
		facing = f
		seed = s
		_body.clear()
		queue_redraw()

	## A spire of cosmic crystal: its sides taper in uneven steps (as wide as
	## the deadly box all the way up, the narrow tip forgiven), a split near
	## the tip, and facet lines up its length.
	func _build() -> void:
		var base_y := tall if facing < 0 else 0.0
		var tip_y := 0.0 if facing < 0 else tall
		var up := -1.0 if facing < 0 else 1.0  # From the base toward the tip.
		var left := PackedVector2Array()
		var right := PackedVector2Array()
		var steps := 5
		for i in steps:
			var f := float(i) / steps
			var w := width * 0.5 * (1.0 - 0.42 * f)
			var y := base_y + up * (tall - 8.0) * f
			left.append(Vector2(-w - 1.5 * OrganicArt.rand(seed, i), y))
			right.append(Vector2(w + 1.5 * OrganicArt.rand(seed, 20 + i), y + up * 4.0 * OrganicArt.rand(seed, 40 + i)))
		var lean := (OrganicArt.rand(seed, 60) - 0.5) * width * 0.25
		_body = left
		_body.append(Vector2(lean, tip_y))
		right.reverse()
		_body.append_array(right)
		_facets.clear()
		_facets.append_array([Vector2(0.0, base_y), Vector2(lean, tip_y)])
		_facets.append_array([Vector2(-width * 0.3, base_y), Vector2(lean * 0.5 - width * 0.08, base_y + up * tall * 0.7)])
		_facets.append_array([Vector2(width * 0.28, base_y), Vector2(lean * 0.5 + width * 0.1, base_y + up * tall * 0.55)])

	func _draw() -> void:
		if _body.is_empty():
			_build()
		var tip_y := 0.0 if facing < 0 else tall
		Neon.soft_light(self, Vector2(0.0, tip_y), width * 1.6, Color(GalaxyArt.DANGER, 0.3))
		draw_colored_polygon(_body, GalaxyArt.DANGER_BODY)
		draw_multiline(_facets, Color(GalaxyArt.DANGER_CORE, 0.35), 1.5)
		draw_polyline(GalaxyArt.closed(_body), GalaxyArt.DANGER, 2.5, true)
