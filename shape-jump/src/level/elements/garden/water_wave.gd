@tool
class_name WaterWave
extends GardenHazard
## WATER WAVE (World 04): a wave that swells up out of a spring in a surface,
## then rolls along it toward the player and sinks back. You see it form
## (the telegraph) before it moves. Jump it, or be on the other surface.
## On the ground it runs along the ground; anchored to the floor of the
## moment, it runs along whichever surface you are on when it forms.
##
## Each cycle: [member form_time] swelling at the spring, then rolling
## [member run] px toward the start of the level at [member speed], then
## [constant SINK] s sinking. Its body is a hump: the hitbox is a trapezoid
## inside the drawn crest.
## Origin = on the ground line at the spring (the right end of its run).
## (Mirrored in tools/levelgen/levelgen.py: WaterWave.)

@export var height := 70.0:
	set(value):
		height = value
		_rebuild()
@export var width := 150.0:
	set(value):
		width = value
		_rebuild()
## How far it rolls (px, toward smaller x).
@export var run := 640.0
@export var speed := 320.0
@export_range(0.5, 10.0, 0.05, "suffix:s") var period := 3.2
@export_range(0.0, 1.0, 0.01) var phase := 0.0
@export_range(0.1, 1.0, 0.05, "suffix:s") var form_time := 0.45

const SINK := 0.25
## The hitbox trapezoid: its top is this share of the base.
const TOP_SHARE := 0.34

var _crest: Node2D
var _spring: Node2D
var _shape: CollisionShape2D
var _hump: ConvexPolygonShape2D
var _last_into := INF


func _ready() -> void:
	super()
	_rebuild()
	apply_time(0.0)


## Crest centre (x, local) and height at [param into_cycle] s into a cycle,
## as Vector2(x, h); h = 0 when there is no wave.
func crest_at(into_cycle: float) -> Vector2:
	if into_cycle < form_time:
		return Vector2(0.0, height * smoothstep(0.0, 1.0, into_cycle / form_time))
	var rolling := into_cycle - form_time
	var travel := run / speed
	if rolling < travel:
		return Vector2(-rolling * speed, height)
	var sinking := rolling - travel
	if sinking < SINK:
		return Vector2(-run, height * (1.0 - smoothstep(0.0, 1.0, sinking / SINK)))
	return Vector2(-run, 0.0)


## The world x range (px) it can ever cover (its whole motion).
func x_reach() -> Vector2:
	var x := global_position.x
	return Vector2(x - run - width * 0.5, x + width * 0.5)


func apply_time(t: float) -> void:
	if _crest == null:
		return
	var start := GardenHazard.cycle_start(t, period, phase)
	var into_cycle := t - start
	var up := on_ceiling_at(start)
	var c := crest_at(into_cycle)
	var playing := advance_to(t)
	var surface := surface_y(up)
	var dir := GardenHazard.into(up)
	_crest.visible = c.y > 1.0
	_crest.position = Vector2(c.x, surface)
	_crest.scale = Vector2(1.0, -dir * maxf(c.y / height, 0.01))
	_spring.position = Vector2(0.0, surface)
	_spring.scale = Vector2(1.0, -dir)
	if playing and into_cycle < _last_into:
		cue(&"warning", global_position + Vector2(0.0, surface))
	_last_into = into_cycle
	var h := c.y - HITBOX_INSET
	if h <= HITBOX_INSET:
		_shape.position = PARKED
		return
	var half := width * 0.5 - HITBOX_INSET
	var top := half * TOP_SHARE
	_hump.points = PackedVector2Array([Vector2(-half, 0.0), Vector2(half, 0.0), Vector2(top, dir * h),
		Vector2(-top, dir * h)])
	_shape.position = Vector2(c.x, surface)


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _crest == null:
		_crest = _Crest.new()
		add_child(_crest, false, Node.INTERNAL_MODE_FRONT)
		_spring = _Spring.new()
		add_child(_spring, false, Node.INTERNAL_MODE_FRONT)
		ink(_crest)
		ink(_spring)
		_hump = ConvexPolygonShape2D.new()
		_shape = add_hitbox(_hump)
	(_crest as _Crest).setup(height, width)
	queue_redraw()


func _draw() -> void:
	# The channel the wave runs in, on each surface it can use.
	for up: bool in ([false, true] if anchor >= Anchor.FLOOR else [anchor == Anchor.CEILING]):
		var y := surface_y(up)
		var d := GardenHazard.into(up)
		draw_line(Vector2(-run - width * 0.5, y + d * 2.0), Vector2(width * 0.5, y + d * 2.0),
			Color(GardenLook.INK_BODY, 0.35), 4.0)


## The wave, drawn once at full height standing up (-y) from its base.
class _Crest extends Node2D:
	var height := 70.0
	var width := 150.0

	func setup(h: float, w: float) -> void:
		height = h
		width = w
		queue_redraw()

	func _draw() -> void:
		var half := width * 0.5
		var shape := PackedVector2Array()
		var steps := 18
		for i in steps + 1:
			var k := float(i) / steps
			var x := lerpf(-half, half, k)
			# A breaking wave: steep front (left, facing the player), long back.
			var e := k * 1.35 if k < 0.74 else 1.0 - (k - 0.74) / 0.26
			var y := -height * (sin(clampf(e, 0.0, 1.0) * PI * 0.5) ** 0.7)
			shape.append(Vector2(x, y))
		shape.append(Vector2(half, 2.0))
		shape.append(Vector2(-half, 2.0))
		draw_colored_polygon(shape, GardenLook.INK_BODY)
		draw_polyline(shape.slice(0, steps + 1), GardenLook.INK_RIM, 3.0, true)
		# The curl of foam at the crest and inner currents.
		var tip := shape[int(steps * 0.55)]
		draw_arc(tip + Vector2(-8.0, 10.0), 12.0, PI * 1.1, PI * 2.2, 10, GardenLook.INK_RIM, 2.5, true)
		var lines := PackedVector2Array()
		for i in 3:
			var y := -height * (0.25 + 0.2 * i)
			lines.append_array([Vector2(-half * 0.3, y), Vector2(half * (0.55 - 0.15 * i), y + 6.0)])
		draw_multiline(lines, Color(GardenLook.INK_RIM, 0.5), 1.5, true)


## The spring the wave rises from: a ring of ripples in the surface.
class _Spring extends Node2D:
	func _draw() -> void:
		for i in 3:
			var r := 14.0 + 12.0 * i
			draw_arc(Vector2.ZERO, r, PI, TAU, 14, Color(GardenLook.INK_BODY, 0.7 - 0.2 * i), 3.0, true)
