@tool
class_name HangingVines
extends GardenHazard
## HANGING VINES (World 04): a curtain of vines hanging from the ceiling (or
## the sky side), that lets itself down to close part of the corridor and
## draws back up to open it, on a fixed rhythm; the leaves shiver before it
## moves. The foliage mass kills; the loose strands below it do not.
## Swaying with the wind is only drawn; the gusts follow the floor you run
## on, so after an attach the vines sway the other way.
## Origin = on the ground line at the left end. (Mirrored in
## tools/levelgen/levelgen.py: HangingVines.)

@export var width := 96.0:
	set(value):
		width = value
		_rebuild()
## How far the foliage hangs from its surface, drawn back and let down (px).
@export var short := 60.0:
	set(value):
		short = value
		_rebuild()
@export var long := 200.0:
	set(value):
		long = value
		_rebuild()
@export_range(0.3, 10.0, 0.05, "suffix:s") var period := 2.0
@export_range(0.0, 0.95, 0.01) var hold_ratio := 0.5
@export_range(0.0, 1.0, 0.01) var phase := 0.0
@export_range(0.0, 1.0, 0.05, "suffix:s") var warning_time := 0.3

var _mass: _Mass
var _shape: CollisionShape2D
var _box: RectangleShape2D
var _was_warning := false
var _sway_up := false
var _time := 0.0


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
	if _mass == null:
		return
	var start := GardenHazard.cycle_start(t, period, phase)
	var up := on_ceiling_at(start)
	var cycle := fposmod(t / period + phase, 1.0)
	var hang := length_at(t)
	var playing := advance_to(t)
	var surface := surface_y(up)
	var dir := GardenHazard.into(up)
	var warning := Timeline.steps_warning(cycle, hold_ratio, period, warning_time)
	_mass.position = Vector2(0.0, surface)
	_mass.scale = Vector2(1.0, dir * hang / long)
	_mass.modulate = Color(1.5, 1.5, 1.5) if warning and fmod(t * 10.0, 1.0) < 0.5 else Color.WHITE
	if playing and warning and not _was_warning:
		cue(&"warning", global_position + Vector2(width * 0.5, surface))
	_was_warning = warning
	var level := Level.of(self) if not Engine.is_editor_hint() else null
	_sway_up = level.gravity.up if level and level.gravity else false
	var deadly := hang * _Mass.SOLID
	_box.size = Vector2(width - HITBOX_INSET * 2.0, deadly - HITBOX_INSET)
	_shape.position = Vector2(width * 0.5, surface + dir * (deadly - HITBOX_INSET) * 0.5)


func _process(delta: float) -> void:
	super(delta)
	if Engine.is_editor_hint() or _mass == null:
		return
	_time += delta
	# A gentle sway (drawing only), leaning with the wind of the floor you are on.
	_mass.skew = sin(_time * 1.7 + position.x * 0.01) * 0.08 * (-1.0 if _sway_up else 1.0)


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _mass == null:
		_mass = _Mass.new()
		add_child(_mass, false, Node.INTERNAL_MODE_FRONT)
		ink(_mass)
		_box = RectangleShape2D.new()
		_shape = add_hitbox(_box)
	_mass.setup(width, long, int(absf(position.x)) + 13)


## The vines at full length hanging down (+y) from y = 0: a dense mass of
## leaves over [constant SOLID] of the length, strands and tips below.
class _Mass extends Node2D:
	const SOLID := 0.8
	var width := 96.0
	var long := 200.0
	var seed_value := 13

	func setup(w: float, l: float, s: int) -> void:
		width = w
		long = l
		seed_value = s
		queue_redraw()

	func _draw() -> void:
		var rng := RandomNumberGenerator.new()
		rng.seed = seed_value
		var solid := long * SOLID
		var mass := PackedVector2Array([Vector2(0.0, -2.0), Vector2(width, -2.0)])
		var lobes := maxi(int(width / 20.0), 3)
		for i in range(lobes, -1, -1):
			mass.append(Vector2(width * i / lobes, solid + (10.0 if i % 2 == 1 else 0.0)))
		draw_colored_polygon(mass, GardenLook.INK_DEEP)
		var batch := GardenArt.Batch.new(GardenLook.INK_BODY)
		for i in lobes * 2:
			var x := width * (i + 0.5) / (lobes * 2)
			var along := rng.randf_range(0.2, 0.95)
			batch.poly(GardenArt.leaf(Vector2(x, solid * along), 26.0, 12.0, PI * 0.5 + rng.randf_range(-0.6, 0.6)))
		batch.draw(self, GardenLook.INK_RIM, 1.5)
		var strands := PackedVector2Array()
		for i in lobes:
			var x := width * (i + 0.5) / lobes
			strands.append_array([Vector2(x, solid * 0.5), Vector2(x + rng.randf_range(-6.0, 6.0), long)])
		draw_multiline(strands, GardenLook.INK_BODY, 3.0, true)
		draw_polyline(mass.slice(2), GardenLook.INK_RIM, 2.0, true)
