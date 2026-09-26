@tool
class_name GravityGate
extends Node2D
## GRAVITY GATE (World 03, docs/GDD.md §9C): a full-height portal between
## the floor and the ceiling. When the player's centre reaches its x,
## gravity turns to [member target_up] (a gate that already matches does
## nothing). It spans every height, so it is crossed on every route and the
## flip happens at one fixed moment of the level: the level turns its x into
## a time ([method gravity_events]), which is why a respawn or a rewind always
## restores the right gravity and why a gate can never fire twice.
##
## Telegraph: it wakes up [member warn_distance] ahead of the player (glow,
## chevrons in the new direction, a sound), and burns out once passed.
## Origin = on the ground line at the gate's x; it reaches up to [member top].

## Gravity after the gate: true = the ceiling becomes the floor.
@export var target_up := true:
	set(value):
		target_up = value
		queue_redraw()
## Top of the portal, relative to the origin (negative = up).
@export var top := -384.0:
	set(value):
		top = value
		queue_redraw()
## How far ahead of the player the gate starts to charge.
@export var warn_distance := 360.0

const WIDTH := 44.0

var _charge := 0.0
var _passed := false
var _membrane: _Membrane
var _last_time := 0.0


func _ready() -> void:
	# Over the blocks: the emitters are set into the floor and the ceiling.
	z_index = 1
	_membrane = _Membrane.new()
	add_child(_membrane, false, Node.INTERNAL_MODE_FRONT)
	_membrane.setup(top, target_up)
	if not Engine.is_editor_hint():
		Level.join(self)


func _enter_tree() -> void:
	if is_node_ready() and not Engine.is_editor_hint():
		Level.join(self)


func _exit_tree() -> void:
	if not Engine.is_editor_hint():
		Level.leave(self)


## The gravity changes this gate makes: (x, 1 for up / 0 for down).
func gravity_events() -> Array[Vector2]:
	return [Vector2(global_position.x, 1.0 if target_up else 0.0)]


func apply_time(t: float) -> void:
	var level := Level.of(self)
	if level == null:
		return
	var ahead := global_position.x - level.run_x_at(t)
	var charge := clampf(1.0 - ahead / warn_distance, 0.0, 1.0) if ahead > 0.0 else 0.0
	var playing := t - _last_time > 0.0 and t - _last_time < 0.1
	if playing and _charge <= 0.0 and charge > 0.0:
		level.report_cue(&"gravity_warning", global_position + Vector2(0.0, top * 0.5))
	_last_time = t
	_charge = charge
	_passed = ahead <= 0.0
	_membrane.modulate = Color(1.0, 1.0, 1.0, 0.35 + 0.65 * _charge if not _passed else 0.18)
	_membrane.scale = Vector2(1.0 + 0.5 * _charge, 1.0)


func _draw() -> void:
	var color := GalaxyArt.state_color(target_up)
	# Two emitters set into the floor and the ceiling (drawn over the blocks).
	for y: float in [0.0, top]:
		var out := 1.0 if y == 0.0 else -1.0
		var base := PackedVector2Array([Vector2(-WIDTH * 0.5, y), Vector2(WIDTH * 0.5, y),
			Vector2(WIDTH * 0.2, y + out * 26.0), Vector2(-WIDTH * 0.2, y + out * 26.0)])
		draw_colored_polygon(base, Palette.BLOCK_BODY)
		draw_polyline(GalaxyArt.closed(base), color, 2.5, true)
		Neon.soft_light(self, Vector2(0.0, y + out * 20.0), 40.0, Color(color, 0.5))
	# The direction it sends you, large enough to read from far away.
	var mid := top * 0.5
	var dir := -1.0 if target_up else 1.0
	var arrow := PackedVector2Array([Vector2(-22.0, mid - dir * 8.0), Vector2(0.0, mid + dir * 16.0),
		Vector2(22.0, mid - dir * 8.0)])
	Neon.polyline(self, arrow, color, 4.0, 1.2)


## The shimmering portal between the pylons, drawn once and pulsed by
## transform and modulate only.
class _Membrane extends Node2D:
	var span := -384.0
	var up := true

	func setup(top: float, target_up: bool) -> void:
		span = top
		up = target_up
		queue_redraw()

	func _draw() -> void:
		var color := GalaxyArt.state_color(up)
		var h := -span
		draw_rect(Rect2(-6.0, span + 20.0, 12.0, h - 40.0), Color(color, 0.18))
		draw_line(Vector2(0.0, span + 20.0), Vector2(0.0, -20.0), Color(color, 0.9), 2.0)
		for x: float in [-10.0, 10.0]:
			GalaxyArt.dashed(self, Vector2(x, span + 26.0), Vector2(x, -26.0), Color(color, 0.55), 1.5, 8.0)
		GalaxyArt.chevrons(self, 0.0, span + 30.0, -30.0, up, Color(color, 0.8), 56.0, 12.0)
