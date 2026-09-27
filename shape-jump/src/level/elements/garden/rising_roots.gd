@tool
class_name RisingRoots
extends GardenHazard
## RISING ROOTS (World 04): a knot of roots that bursts out of a surface,
## blocks the way, holds, and sinks back. On the ground they rise; anchored
## to the ceiling (or to the floor while you run on the ceiling) they hang.
##
## Each cycle: [member warning_time] of cracking soil and root tips feeling
## their way out (harmless, the telegraph), a fast rise ([constant GROW]),
## [member hold_time] at full height, a slower sink ([constant SINK]), rest.
## Only the thick lower part of the roots kills ([constant SOLID_SHARE]): the
## thin tips are forgiven, and the hitbox never pokes out of the drawing.
## Origin = on the ground line at the left end of the knot.
## (Mirrored in tools/levelgen/levelgen.py: RisingRoots.)

@export var width := 128.0:
	set(value):
		width = value
		_rebuild()
## How far the roots reach out of their surface at full height (px).
@export var reach := 110.0:
	set(value):
		reach = value
		_rebuild()
@export_range(0.5, 10.0, 0.05, "suffix:s") var period := 1.6
@export_range(0.0, 1.0, 0.01) var phase := 0.0
@export_range(0.0, 1.0, 0.05, "suffix:s") var warning_time := 0.35
@export_range(0.0, 5.0, 0.05, "suffix:s") var hold_time := 0.35

const GROW := 0.14
const SINK := 0.26
const SOLID_SHARE := 0.62

var _roots: _Roots
var _crack: _Crack
var _shape: CollisionShape2D
var _box: RectangleShape2D
var _last_into := INF


func _ready() -> void:
	super()
	_rebuild()
	apply_time(0.0)


## Share of the full reach the roots stand out at, [param into] s into a cycle.
func extension(into_cycle: float) -> float:
	var t := into_cycle - warning_time
	if t < 0.0:
		return 0.0
	if t < GROW:
		var k := t / GROW
		return 1.0 - (1.0 - k) * (1.0 - k)
	t -= GROW
	if t < hold_time:
		return 1.0
	t -= hold_time
	if t < SINK:
		return 1.0 - smoothstep(0.0, 1.0, t / SINK)
	return 0.0


## The world x range (px) it can ever cover (its whole motion).
func x_reach() -> Vector2:
	var x := global_position.x
	return Vector2(x, x + width)


func apply_time(t: float) -> void:
	if _roots == null:
		return
	var start := GardenHazard.cycle_start(t, period, phase)
	var into_cycle := t - start
	var up := on_ceiling_at(start)
	var ext := extension(into_cycle)
	var playing := advance_to(t)
	var surface := surface_y(up)
	var dir := GardenHazard.into(up)
	_roots.visible = ext > 0.001
	_roots.position = Vector2(0.0, surface)
	_roots.scale = Vector2(1.0, maxf(ext, 0.001) * -dir)
	var warning := into_cycle < warning_time
	_crack.visible = warning
	if warning:
		_crack.position = Vector2(0.0, surface)
		_crack.scale = Vector2(1.0, -dir)
		_crack.modulate.a = 0.5 + 0.5 * absf(sin(into_cycle * 26.0))
		if playing and into_cycle < _last_into:
			cue(&"warning", global_position + Vector2(width * 0.5, surface))
	_last_into = into_cycle
	var deadly := reach * ext * SOLID_SHARE
	if deadly <= HITBOX_INSET * 2.0:
		_shape.position = PARKED
		return
	_box.size = Vector2(width - HITBOX_INSET * 2.0, deadly - HITBOX_INSET)
	_shape.position = Vector2(width * 0.5, surface + dir * (deadly - HITBOX_INSET) * 0.5)


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _roots == null:
		_roots = _Roots.new()
		add_child(_roots, false, Node.INTERNAL_MODE_FRONT)
		_crack = _Crack.new()
		add_child(_crack, false, Node.INTERNAL_MODE_FRONT)
		ink(_roots)
		ink(_crack)
		_box = RectangleShape2D.new()
		_shape = add_hitbox(_box)
	_roots.setup(width, reach, int(absf(position.x)) + 7)
	_crack.width = width
	_crack.queue_redraw()
	queue_redraw()


func _draw() -> void:
	# Where the roots live: a faint seam in the surface (always visible).
	for up: bool in ([false, true] if anchor >= Anchor.FLOOR else [anchor == Anchor.CEILING]):
		var y := surface_y(up)
		draw_line(Vector2(0.0, y), Vector2(width, y), Color(GardenLook.INK_BODY, 0.5), 3.0)


## The roots, drawn once at full reach pointing up (-y) from y = 0; the
## element scales and mirrors this node to grow them from either surface.
class _Roots extends Node2D:
	var width := 128.0
	var reach := 110.0
	var seed_value := 7

	func setup(w: float, r: float, s: int) -> void:
		width = w
		reach = r
		seed_value = s
		queue_redraw()

	func _draw() -> void:
		var rng := RandomNumberGenerator.new()
		rng.seed = seed_value
		# The tangle: solid ink up to the deadly height, with a knotted top.
		var solid := reach * RisingRoots.SOLID_SHARE
		var tangle := PackedVector2Array([Vector2(0.0, 2.0)])
		var bumps := maxi(int(width / 22.0), 3)
		for i in bumps + 1:
			var x := width * i / bumps
			tangle.append(Vector2(x, -solid - (8.0 if i % 2 == 1 else 0.0)))
		tangle.append(Vector2(width, 2.0))
		draw_colored_polygon(tangle, GardenLook.INK_DEEP)
		var batch := GardenArt.Batch.new(GardenLook.INK_BODY)
		var count := maxi(int(width / 30.0), 2)
		for i in count:
			var x := width * (i + 0.5) / count + rng.randf_range(-6.0, 6.0)
			var tall := reach * rng.randf_range(0.85, 1.12)
			var bend := rng.randf_range(-22.0, 22.0)
			var lean := Vector2(rng.randf_range(-0.18, 0.18), -1.0)
			GardenArt.tendril(batch, Vector2(x, 4.0), lean, tall, width / count * 1.5, bend)
			# A side shoot from the lower half.
			var at := Vector2(x, 0.0) + lean.normalized() * tall * rng.randf_range(0.3, 0.55)
			GardenArt.tendril(batch, at, Vector2(signf(bend + 0.01), -0.8), tall * 0.35, 7.0, -bend * 0.4, 4)
		batch.draw(self, GardenLook.INK_RIM, 2.0)
		# Bark lines inside the thick roots.
		var marks := PackedVector2Array()
		for i in count:
			var x := width * (i + 0.5) / count
			marks.append_array([Vector2(x - 3.0, -reach * 0.15), Vector2(x + 2.0, -reach * 0.45)])
		draw_multiline(marks, Color(GardenLook.INK_RIM, 0.35), 1.5)


## The warning: the surface cracks open and tips feel their way out.
class _Crack extends Node2D:
	var width := 128.0

	func _draw() -> void:
		var batch := GardenArt.Batch.new(GardenLook.INK_BODY)
		var count := maxi(int(width / 30.0), 2)
		for i in count:
			var x := width * (i + 0.5) / count
			GardenArt.tendril(batch, Vector2(x, 2.0), Vector2(0.1 * (i % 2 * 2 - 1), -1.0), 16.0, 9.0, 3.0, 3)
		batch.draw(self, GardenLook.INK_RIM, 2.0)
		var crack := PackedVector2Array()
		var x := 0.0
		var i := 0
		while x < width:
			crack.append(Vector2(x, -3.0 if i % 2 == 0 else 3.0))
			x += 12.0
			i += 1
		draw_polyline(crack, GardenLook.INK_RIM, 2.0, true)
