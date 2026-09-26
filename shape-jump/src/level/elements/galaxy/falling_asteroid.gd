@tool
class_name FallingAsteroid
extends Hazard
## FALLING ASTEROID (World 03): a rock that breaks out of the sky side of the
## corridor and falls across it, on a fixed rhythm. "Down" is the gravity of
## the moment its warning starts: it falls from the ceiling to the ground
## while gravity pulls down, and from the ground up to the ceiling while it
## pulls up. Once warned, a fall keeps its direction (a flip mid-fall does not
## turn a rock around), so what the warning shows is what happens.
##
## Each cycle: [member warning_time] of a glowing crack and a dashed line
## down the column, then the fall at [member speed], then nothing.
## Origin = on the ground line at the column's x.

@export var ceiling := -384.0:
	set(value):
		ceiling = value
		queue_redraw()
@export_range(8.0, 64.0, 1.0, "suffix:px") var radius := 20.0
@export_range(0.3, 10.0, 0.05, "suffix:s") var period := 1.6
@export_range(0.0, 1.0, 0.01) var phase := 0.0
@export_range(0.0, 2.0, 0.05, "suffix:s") var warning_time := 0.45
@export var speed := 900.0

const HITBOX_SCALE := 0.75
## Where an idle rock's hitbox waits: far outside every level.
const PARKED := Vector2(0.0, 100000.0)

var _rock: _Rock
var _crack: _Crack
var _shape: CollisionShape2D
var _last_into := INF


func _ready() -> void:
	super()
	_crack = _Crack.new()
	add_child(_crack, false, Node.INTERNAL_MODE_FRONT)
	_rock = _Rock.new()
	add_child(_rock, false, Node.INTERNAL_MODE_FRONT)
	_rock.setup(radius, int(absf(position.x)) + 11)
	var circle := CircleShape2D.new()
	circle.radius = radius * HITBOX_SCALE
	_shape = add_hitbox(circle)
	apply_time(0.0)


func apply_time(t: float) -> void:
	# (Mirrored in tools/levelgen/levelgen.py: FallingAsteroid.)
	var level := Level.of(self)
	var k := floorf(t / period + phase)
	var start := (k - phase) * period
	var into := t - start
	var up := level.gravity_up_at(start) if level else false
	var dir := -1.0 if up else 1.0
	var from := radius if up else ceiling - radius
	var travel := absf(ceiling) + radius * 2.0
	var moving := into - warning_time
	var active := moving >= 0.0 and moving * speed <= travel
	var y := from + dir * maxf(moving, 0.0) * speed
	var playing := advance_to(t)
	_rock.visible = active
	_rock.position = Vector2(0.0, y)
	_rock.rotation = t * 2.2
	_shape.position = Vector2(0.0, y) if active else PARKED
	var warning := into < warning_time
	_crack.visible = warning
	if warning:
		_crack.show_side(up, ceiling)
		_crack.modulate.a = 0.45 + 0.55 * (into / warning_time)
		if playing and into < _last_into:
			cue(&"warning", global_position + Vector2(0.0, 0.0 if up else ceiling))
	_last_into = into


func _draw() -> void:
	# The column the rocks use: faint, always visible.
	GalaxyArt.dashed(self, Vector2(0.0, ceiling), Vector2(0.0, 0.0), Color(GalaxyArt.DANGER, 0.12), 2.0, 12.0)


class _Rock extends Node2D:
	var shape := PackedVector2Array()
	var radius := 20.0

	func setup(r: float, seed_value: int) -> void:
		radius = r
		shape = GalaxyArt.rock(r * 1.05, seed_value)
		queue_redraw()

	func _draw() -> void:
		Neon.soft_light(self, Vector2.ZERO, radius * 2.4, Color(GalaxyArt.DANGER, 0.35))
		draw_colored_polygon(shape, GalaxyArt.DANGER_BODY)
		draw_polyline(GalaxyArt.closed(shape), GalaxyArt.DANGER, 2.5, true)
		draw_line(shape[1] * 0.4, shape[5] * 0.5, Color(GalaxyArt.DANGER_CORE, 0.6), 1.5)


## The warning: a glowing crack where the rock will break out, and a dashed
## line along its path.
class _Crack extends Node2D:
	var _up := false
	var _ceiling := -384.0

	func show_side(up: bool, ceiling: float) -> void:
		if up == _up and ceiling == _ceiling:
			return
		_up = up
		_ceiling = ceiling
		queue_redraw()

	func _draw() -> void:
		var y := 0.0 if _up else _ceiling
		var into := 1.0 if _up else -1.0  # Into the surface the rock comes from.
		Neon.soft_light(self, Vector2(0.0, y), 70.0, Color(GalaxyArt.DANGER, 0.55))
		var crack := PackedVector2Array([Vector2(-26.0, y), Vector2(-8.0, y + into * 6.0), Vector2(0.0, y - into * 4.0),
			Vector2(10.0, y + into * 7.0), Vector2(26.0, y)])
		draw_polyline(crack, GalaxyArt.DANGER_CORE, 3.0, true)
		GalaxyArt.dashed(self, Vector2(0.0, y), Vector2(0.0, _ceiling if _up else 0.0), Color(GalaxyArt.DANGER, 0.6), 2.0, 14.0)
		var tip := -into
		draw_polyline(PackedVector2Array([Vector2(-12.0, y + tip * 30.0), Vector2(0.0, y + tip * 44.0),
			Vector2(12.0, y + tip * 30.0)]), GalaxyArt.DANGER, 3.0, true)
