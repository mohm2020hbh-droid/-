@tool
class_name GravityMine
extends Hazard
## GRAVITY MINE (World 03): a spiked geometric mine that always rests on the
## current floor. It hovers just off the ground; when gravity turns, it falls
## across to the ceiling (accelerating, like everything else), and back.
## Where it is depends only on the level's gravity schedule, so it is at the
## same place at the same moment on every attempt.
## Origin = on the ground line under the mine; [member ceiling] is the
## ceiling's underside relative to it.

@export var ceiling := -384.0:
	set(value):
		ceiling = value
		queue_redraw()
@export_range(8.0, 64.0, 1.0, "suffix:px") var radius := 22.0:
	set(value):
		radius = value
		queue_redraw()
## Gap between the mine and the floor it rests on.
@export var lift := 10.0
## Acceleration of its fall to the other surface.
@export var fall_accel := 2600.0
@export var bob := 5.0
@export var bob_speed := 3.0
@export_range(0.0, 1.0, 0.01) var phase := 0.0

const HITBOX_SCALE := 0.72

var _body: _Mine
var _shape: CollisionShape2D


func _ready() -> void:
	super()
	_body = _Mine.new()
	add_child(_body, false, Node.INTERNAL_MODE_FRONT)
	_body.radius = radius
	var circle := CircleShape2D.new()
	circle.radius = radius * HITBOX_SCALE
	_shape = add_hitbox(circle)
	apply_time(0.0)


## Centre height (relative to the origin) of a mine resting on the floor of
## gravity [param up].
func rest_y(up: bool) -> float:
	return ceiling + radius + lift if up else -radius - lift


func center_at(t: float, up: bool, since: float) -> float:
	var y := rest_y(up)
	var from := rest_y(not up)
	var gap := absf(y - from)
	var fallen := 0.5 * fall_accel * since * since if since >= 0.0 else gap
	if fallen < gap:
		y = from + signf(y - from) * fallen
	return y + bob * sin(t * bob_speed + phase * TAU)


func apply_time(t: float) -> void:
	var level := Level.of(self)
	var up := level.gravity_up_at(t) if level else false
	var since := t - level.last_gravity_change(t) if level else INF
	var y := center_at(t, up, since)
	_body.position = Vector2(0.0, y)
	_body.rotation = t * 0.8 + phase * TAU
	_shape.position = Vector2(0.0, y)


func _draw() -> void:
	if Engine.is_editor_hint():
		for up: bool in [false, true]:
			draw_arc(Vector2(0.0, rest_y(up)), radius, 0.0, TAU, 24, Color(GalaxyArt.state_color(up), 0.4), 1.0)


## The mine itself: an eight-point star of shards around a hot core.
class _Mine extends Node2D:
	var radius := 22.0:
		set(value):
			radius = value
			queue_redraw()

	func _draw() -> void:
		Neon.soft_light(self, Vector2.ZERO, radius * 2.6, Color(GalaxyArt.DANGER, 0.3))
		for i in 8:
			var dir := Vector2.from_angle(TAU * i / 8.0)
			var length := radius * (1.0 if i % 2 == 0 else 0.7)
			var spike := GalaxyArt.shard(dir * radius * 0.35, dir, length * 0.75, radius * 0.45)
			draw_colored_polygon(spike, GalaxyArt.DANGER_BODY)
			draw_polyline(GalaxyArt.closed(spike), GalaxyArt.DANGER, 2.0, true)
		draw_circle(Vector2.ZERO, radius * 0.45, GalaxyArt.DANGER_BODY)
		draw_arc(Vector2.ZERO, radius * 0.45, 0.0, TAU, 20, GalaxyArt.DANGER, 2.0, true)
		draw_circle(Vector2.ZERO, radius * 0.18, GalaxyArt.DANGER_CORE)
