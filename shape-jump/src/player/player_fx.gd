class_name PlayerFx
extends Node2D
## Particles, light trail and the death flash for the player. All emitters
## exist up front and are restarted on demand: nothing is instantiated
## during play. Purely cosmetic.

const TRAIL_POINTS := 14
const DEATH_RING_TIME := 0.4
const DEATH_RING_RADIUS := 130.0

@export var player: Player

var _trail_points := PackedVector2Array()
var _death_ring_left := 0.0
var _death_ring_center := Vector2.ZERO

@onready var _run_sparks: CPUParticles2D = $RunSparks
@onready var _jump_burst: CPUParticles2D = $JumpBurst
@onready var _land_dust: CPUParticles2D = $LandDust
@onready var _death_shatter: CPUParticles2D = $DeathShatter
@onready var _trail: Line2D = $Trail


func _ready() -> void:
	player.jumped.connect(func() -> void: _jump_burst.restart())
	player.landed.connect(func(_impact: float) -> void: _land_dust.restart())
	player.died.connect(_on_died)
	player.respawned.connect(_on_respawned)
	player.state_changed.connect(_on_state_changed)
	# The trail lives in world space so it streams out behind the player.
	_trail.top_level = true
	_trail.global_position = Vector2.ZERO


func _physics_process(_delta: float) -> void:
	if player.is_dead():
		_fade_trail()
		return
	_trail_points.append(player.global_position)
	if _trail_points.size() > TRAIL_POINTS:
		_trail_points.remove_at(0)
	_trail.points = _trail_points


func _process(delta: float) -> void:
	if _death_ring_left > 0.0:
		_death_ring_left = maxf(_death_ring_left - delta, 0.0)
		queue_redraw()


func _draw() -> void:
	if _death_ring_left <= 0.0:
		return
	var t := 1.0 - _death_ring_left / DEATH_RING_TIME
	var eased := 1.0 - pow(1.0 - t, 3.0)
	var local_center := to_local(_death_ring_center)
	var radius := DEATH_RING_RADIUS * eased
	var alpha := 1.0 - t
	draw_arc(local_center, radius, 0.0, TAU, 48, Color(Palette.PLAYER_EDGE, 0.35 * alpha), 10.0 * alpha + 1.0, true)
	draw_arc(local_center, radius, 0.0, TAU, 48, Color(Palette.PLAYER_CORE, 0.8 * alpha), 2.0, true)
	if t < 0.25:
		Neon.soft_light(self, local_center, 90.0, Color(Palette.PLAYER_CORE, 1.0 - t * 4.0))


func _fade_trail() -> void:
	if _trail_points.size() > 0:
		_trail_points.remove_at(0)
		_trail.points = _trail_points


func _on_state_changed(new_state: Player.State, _old_state: Player.State) -> void:
	_run_sparks.emitting = new_state == Player.State.RUN


func _on_died(_cause: StringName) -> void:
	_run_sparks.emitting = false
	_death_ring_center = player.global_position
	_death_ring_left = DEATH_RING_TIME
	_death_shatter.restart()


func _on_respawned() -> void:
	_trail_points.clear()
	_trail.points = _trail_points
	_run_sparks.emitting = player.state == Player.State.RUN
