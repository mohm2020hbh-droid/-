class_name PlayerFx
extends Node2D
## Particles, light trail and the death flash for the player. All emitters
## exist up front and are restarted on demand: nothing is instantiated
## during play. Purely cosmetic.

@export var player: Player
## Trail length in physics ticks (14 ≈ 0.23 s of history).
@export_range(2, 60) var trail_points := 14
@export_range(0.05, 2.0, 0.05, "suffix:s") var death_ring_time := 0.4
@export var death_ring_radius := 130.0
## The double jump pushes off a flat ring of light left hanging in the air.
@export_range(0.05, 1.0, 0.05, "suffix:s") var air_ring_time := 0.3
@export var air_ring_radius := 64.0

var _trail_points := PackedVector2Array()
var _death_ring_left := 0.0
var _death_ring_center := Vector2.ZERO
var _air_ring_left := 0.0
var _air_ring_center := Vector2.ZERO

@onready var _run_sparks: CPUParticles2D = $RunSparks
@onready var _jump_burst: CPUParticles2D = $JumpBurst
@onready var _air_burst: CPUParticles2D = $AirBurst
@onready var _land_dust: CPUParticles2D = $LandDust
@onready var _death_shatter: CPUParticles2D = $DeathShatter
@onready var _trail: Line2D = $Trail


func _ready() -> void:
	player.jumped.connect(func() -> void: _jump_burst.restart())
	player.double_jumped.connect(_on_double_jumped)
	player.landed.connect(func(_impact: float) -> void: _land_dust.restart())
	player.died.connect(_on_died)
	player.respawned.connect(_on_respawned)
	player.state_changed.connect(_on_state_changed)
	# The trail lives in world space so it streams out behind the player.
	_trail.top_level = true
	_trail.global_position = Vector2.ZERO


## Gravity turned (World 03): the emitters face the new floor (the trail
## lives in world space and needs nothing).
func face_gravity(up: bool, _instant: bool) -> void:
	rotation = PI if up else 0.0


## Re-colors the emitters and the trail for the current [Palette] theme.
func refresh_colors() -> void:
	for node in [_trail] + find_children("*", "CPUParticles2D", false, false):
		var key := &"gradient" if node is Line2D else &"color_ramp"
		if not node.has_meta(&"red_gradient"):
			node.set_meta(&"red_gradient", node.get(key))
		var red: Gradient = node.get_meta(&"red_gradient")
		if red:
			node.set(key, red if Palette.theme == &"red" else UiLook.grey_gradient(red))
	queue_redraw()


func _physics_process(_delta: float) -> void:
	if player.is_dead():
		_fade_trail()
		return
	_trail_points.append(player.global_position)
	if _trail_points.size() > trail_points:
		_trail_points.remove_at(0)
	_trail.points = _trail_points


func _process(delta: float) -> void:
	if _death_ring_left > 0.0 or _air_ring_left > 0.0:
		_death_ring_left = maxf(_death_ring_left - delta, 0.0)
		_air_ring_left = maxf(_air_ring_left - delta, 0.0)
		queue_redraw()


func _draw() -> void:
	if _air_ring_left > 0.0:
		_draw_air_ring()
	if _death_ring_left <= 0.0:
		return
	var t := 1.0 - _death_ring_left / death_ring_time
	var eased := 1.0 - pow(1.0 - t, 3.0)
	var local_center := to_local(_death_ring_center)
	var radius := death_ring_radius * eased
	var alpha := 1.0 - t
	draw_arc(local_center, radius, 0.0, TAU, 48, Color(Palette.PLAYER_EDGE, 0.35 * alpha), 10.0 * alpha + 1.0, true)
	draw_arc(local_center, radius, 0.0, TAU, 48, Color(Palette.PLAYER_CORE, 0.8 * alpha), 2.0, true)
	if t < 0.25:
		Neon.soft_light(self, local_center, 90.0, Color(Palette.PLAYER_CORE, 1.0 - t * 4.0))


## A flat ellipse at the feet where the double jump started, expanding and fading.
func _draw_air_ring() -> void:
	var t := 1.0 - _air_ring_left / air_ring_time
	var eased := 1.0 - pow(1.0 - t, 3.0)
	var radius := Vector2(air_ring_radius, air_ring_radius * 0.28) * (0.35 + 0.65 * eased)
	var alpha := 1.0 - t
	var center := to_local(_air_ring_center)
	var points := PackedVector2Array()
	for i in 25:
		var angle := TAU * i / 24.0
		points.append(center + Vector2(cos(angle) * radius.x, sin(angle) * radius.y))
	draw_polyline(points, Color(Palette.PLAYER_EDGE, 0.4 * alpha), 8.0 * alpha + 1.0, true)
	draw_polyline(points, Color(Palette.PLAYER_CORE, 0.9 * alpha), 2.0, true)


func _fade_trail() -> void:
	if _trail_points.size() > 0:
		_trail_points.remove_at(0)
		_trail.points = _trail_points


func _on_state_changed(new_state: Player.State, _old_state: Player.State) -> void:
	_run_sparks.emitting = new_state == Player.State.RUN


func _on_double_jumped() -> void:
	_air_burst.restart()
	_air_ring_center = player.get_feet_position()
	_air_ring_left = air_ring_time


func _on_died(_cause: StringName) -> void:
	_run_sparks.emitting = false
	_death_ring_center = player.global_position
	_death_ring_left = death_ring_time
	_death_shatter.restart()


func _on_respawned() -> void:
	_air_ring_left = 0.0
	_trail_points.clear()
	_trail.points = _trail_points
	_run_sparks.emitting = player.state == Player.State.RUN
