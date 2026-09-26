@tool
class_name ShadowChaser
extends Hazard
## SHADOW CHASER (docs/GDD.md §7, World 02): a wall of living shadow that
## follows the run a few tiles behind, swallowing the ground already passed.
## Every [member lunge_period] it lunges: its cracks flare white for
## [member warning_time], then a low tongue of shadow shoots along the floor
## past you. Be in the air when it passes. Everything is a function of level
## time: the body keeps pace with the run ([member spawn_x] + speed x t -
## [member lag]), so the same lunge reaches you at the same moment every
## attempt. Active from [member t_start] to [member t_end]; before and after
## it stays out of reach behind the run. Origin = floor level (y), x unused.

@export var spawn_x := 256.0
@export_range(100.0, 2000.0, 1.0, "suffix:px/s") var speed := 520.0
## How far behind the player's centre the body's front stays.
@export_range(64.0, 1024.0, 1.0, "suffix:px") var lag := 190.0
@export var t_start := 0.0
@export var t_end := 10.0
@export_range(0.5, 10.0, 0.05, "suffix:s") var lunge_period := 1.6
@export_range(0.0, 1.0, 0.01) var lunge_phase := 0.0
## How far past the body's front the tongue reaches at full extension.
@export_range(64.0, 1024.0, 1.0, "suffix:px") var lunge_reach := 300.0
@export_range(16.0, 256.0, 1.0, "suffix:px") var tongue_height := 56.0
@export_range(0.0, 1.0, 0.05, "suffix:s") var warning_time := 0.35

const BODY_WIDTH := 1600.0
const BODY_HEIGHT := 1400.0
const EXTEND := 0.16
const HOLD := 0.2
const RETRACT := 0.3

var _body: Node2D
var _tongue: Node2D
var _body_shape: CollisionShape2D
var _tongue_shape: CollisionShape2D


func _ready() -> void:
	super()
	z_index = 2  # In front of the ground it swallows.
	_rebuild()
	apply_time(0.0)


## x of the body's front edge at level time [param t].
func front_at(t: float) -> float:
	var active_t := clampf(t, t_start, t_end)
	var front := spawn_x + speed * active_t - lag
	if t < t_start:
		front -= (t_start - t) * speed * 2.0  # Not here yet: rushing in from far behind.
	elif t > t_end:
		front -= (t - t_end) * speed * 2.0  # Falls back.
	return front


## Tongue length at [param t] (0 when not lunging or not active).
func tongue_at(t: float) -> float:
	if t < t_start or t > t_end:
		return 0.0
	var u := fposmod(t / lunge_period + lunge_phase, 1.0) * lunge_period
	var w := warning_time
	if u < w:
		return 0.0
	if u < w + EXTEND:
		return lunge_reach * smoothstep(0.0, 1.0, (u - w) / EXTEND)
	if u < w + EXTEND + HOLD:
		return lunge_reach
	if u < w + EXTEND + HOLD + RETRACT:
		return lunge_reach * (1.0 - smoothstep(0.0, 1.0, (u - w - EXTEND - HOLD) / RETRACT))
	return 0.0


func is_warning(t: float) -> bool:
	if t < t_start or t > t_end:
		return false
	return fposmod(t / lunge_period + lunge_phase, 1.0) * lunge_period < warning_time


func apply_time(t: float) -> void:
	if _body == null:
		return
	var front := front_at(t) - position.x
	var tongue := tongue_at(t)
	_body.position.x = front
	_body_shape.position = Vector2(front - BODY_WIDTH * 0.5, -BODY_HEIGHT * 0.5 + 200.0)
	_tongue.position.x = front
	_tongue.scale.x = maxf(tongue, 0.01) / 100.0
	_tongue.visible = tongue > 1.0
	# A retracted tongue parks its hitbox deep inside the body.
	var reach := maxf(tongue, 1.0)
	(_tongue_shape.shape as RectangleShape2D).size = Vector2(maxf(reach - HITBOX_INSET, 1.0), tongue_height - HITBOX_INSET * 2.0)
	_tongue_shape.position = Vector2(front + reach * 0.5 - HITBOX_INSET * 0.5, -tongue_height * 0.5)
	var warn := is_warning(t) and fmod(t * 12.0, 1.0) < 0.5
	_body.self_modulate = Color(1.8, 1.8, 1.8) if warn else Color.WHITE
	if advance_to(t) and warn and fposmod(t / lunge_period + lunge_phase, 1.0) * lunge_period < 1.0 / 60.0 + 0.001:
		cue(&"warning", Vector2(front_at(t), position.y))


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _body == null:
		_body = _Body.new()
		_tongue = _Tongue.new()
		add_child(_body, false, Node.INTERNAL_MODE_FRONT)
		add_child(_tongue, false, Node.INTERNAL_MODE_FRONT)
		_body_shape = add_hitbox(RectangleShape2D.new())
		_tongue_shape = add_hitbox(RectangleShape2D.new())
	(_body_shape.shape as RectangleShape2D).size = Vector2(BODY_WIDTH - HITBOX_INSET * 2.0, BODY_HEIGHT)
	(_tongue as _Tongue).height = tongue_height
	_tongue.queue_redraw()


## The mass of shadow, drawn once with its front edge at x = 0.
class _Body extends Node2D:
	func _draw() -> void:
		var rect := Rect2(-ShadowChaser.BODY_WIDTH, -ShadowChaser.BODY_HEIGHT + 200.0, ShadowChaser.BODY_WIDTH, ShadowChaser.BODY_HEIGHT)
		draw_rect(rect, Color(0.0, 0.0, 0.0, 0.96))
		# A ragged front: white fracture lines reaching back into the dark.
		var rng := RandomNumberGenerator.new()
		rng.seed = 7
		var y := rect.position.y + 40.0
		var pts := PackedVector2Array()
		while y < 200.0:
			pts.append(Vector2(-rng.randf_range(0.0, 34.0), y))
			y += rng.randf_range(20.0, 46.0)
		pts.append(Vector2(0.0, 200.0))
		Neon.polyline(self, pts, Palette.HAZARD_CORE, 2.0, 1.0)
		for i in 26:
			var p := Vector2(-rng.randf_range(20.0, 420.0), rng.randf_range(rect.position.y + 60.0, 190.0))
			draw_line(p, p + Vector2(-rng.randf_range(20.0, 90.0), rng.randf_range(-14.0, 14.0)), Color(Palette.HAZARD_CORE, rng.randf_range(0.08, 0.3)), 1.5)


## The tongue, drawn once at 100 px long; stretched along x.
class _Tongue extends Node2D:
	var height := 56.0

	func _draw() -> void:
		var poly := PackedVector2Array([Vector2(0, -height), Vector2(92, -height * 0.7), Vector2(100, 0), Vector2(0, 0)])
		draw_colored_polygon(poly, Color(0.0, 0.0, 0.0, 0.97))
		draw_polyline(PackedVector2Array([Vector2(0, -height), Vector2(92, -height * 0.7), Vector2(100, 0)]), Palette.HAZARD_CORE, 2.0, true)
