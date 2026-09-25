class_name PlayerVisual
extends Node2D
## Draws "The Core" (a tesseract projection: outer square, hot inner core,
## four depth struts) and animates it procedurally per state:
##   Idle  – the core breathes, the edge glow pulses.
##   Run   – the core spins like a gyroscope.
##   Jump  – vertical stretch, the body spins half a turn per jump.
##   Double jump – a quick extra half flip and a flare of the core. While the
##         double jump is spent the core is hollow, so its availability is
##         always readable on the character itself.
##   Fall  – keeps spinning, the core drifts up from inertia.
##   Land  – squash with elastic recovery, spin snaps to the nearest 90°.
##   Death – hidden; PlayerFx shatters it.
## Purely cosmetic: nothing here feeds back into gameplay or collision.

## Longest step the squash spring integrates at once. A frame hitch (app
## resume, GC) would otherwise make the explicit spring blow up.
const MAX_SPRING_STEP := 1.0 / 30.0

@export var player: Player

@export_group("Shape")
## Inner core size relative to the body.
@export_range(0.2, 0.8, 0.01) var core_ratio := 0.42
@export_group("Squash & Stretch")
@export var jump_stretch := Vector2(0.8, 1.22)
@export var double_jump_stretch := Vector2(0.72, 1.32)
## Squash at the hardest landing; softer landings scale it down.
@export var land_squash := Vector2(1.3, 0.72)
## Fall speed (px/s) that produces the full landing squash.
@export var land_impact_full := 1100.0
@export var spring_stiffness := 320.0
@export var spring_damping := 16.0
@export_group("Spin")
## How fast the body settles on the nearest 90° after landing.
@export var spin_snap_rate := 28.0
@export var core_spin_run := 4.0
@export var core_spin_air := 2.0
## Speed of the extra half flip of the double jump.
@export var flip_speed := 20.0
@export_group("Core Inertia")
@export var inertia_per_speed := 0.006
@export var max_core_offset := 6.0

var _time := 0.0
var _spin := 0.0
var _spin_speed := 0.0
var _core_spin := 0.0
var _core_offset := Vector2.ZERO
var _squash := Vector2.ONE
var _squash_velocity := Vector2.ZERO
var _flip_left := 0.0
var _flare := 0.0


func _ready() -> void:
	# Half a turn over a flat jump, whatever the tuning.
	_spin_speed = PI / player.config.flat_jump_airtime()
	player.jumped.connect(_on_jumped)
	player.double_jumped.connect(_on_double_jumped)
	player.landed.connect(_on_landed)
	player.died.connect(func(_cause: StringName) -> void: visible = false)
	player.respawned.connect(_on_respawned)


func _process(delta: float) -> void:
	_time += delta
	var state := player.state
	var airborne := state == Player.State.JUMP or state == Player.State.FALL

	if _flip_left > 0.0:
		var flip := minf(_flip_left, flip_speed * delta)
		_spin += flip
		_flip_left -= flip
	_flare = maxf(_flare - delta * 4.0, 0.0)
	if airborne:
		_spin = fposmod(_spin + _spin_speed * delta, TAU)
	else:
		var snapped_spin := snappedf(_spin, PI * 0.5)
		_spin = lerpf(_spin, snapped_spin, 1.0 - exp(-spin_snap_rate * delta))

	match state:
		Player.State.RUN:
			_core_spin += core_spin_run * delta
		Player.State.JUMP, Player.State.FALL:
			_core_spin += core_spin_air * delta
		_:
			_core_spin = lerp_angle(_core_spin, 0.0, 1.0 - exp(-4.0 * delta))
	_core_spin = fposmod(_core_spin, TAU)

	# The core lags behind velocity, as if it floated inside a deeper space.
	var target_offset := (-player.velocity * inertia_per_speed).limit_length(max_core_offset)
	target_offset.x *= 0.5
	_core_offset = _core_offset.lerp(target_offset, 1.0 - exp(-10.0 * delta))

	_step_spring(minf(delta, MAX_SPRING_STEP))
	queue_redraw()


func get_squash() -> Vector2:
	return _squash


## Semi-implicit Euler: stable for these stiffness values at <= 1/30 s steps.
func _step_spring(step: float) -> void:
	var force := (Vector2.ONE - _squash) * spring_stiffness - _squash_velocity * spring_damping
	_squash_velocity += force * step
	_squash += _squash_velocity * step


func _draw() -> void:
	var idle := player.state == Player.State.IDLE
	var breathe := sin(_time * 2.6)
	var glow := 0.85 + (0.25 * breathe if idle else 0.1 * sin(_time * 9.0)) + _flare
	var core_scale := 1.0 + (0.1 * breathe if idle else 0.0) + 0.35 * _flare
	var charged := player.has_double_jump()

	# The drawing matches the collision box size (read from the Player).
	var half := player.half_size.x
	# Squash around the feet so landings stay planted, then spin around the centre.
	var xf := Transform2D(0.0, Vector2(0.0, half))
	xf = xf * Transform2D(0.0, _squash, 0.0, Vector2.ZERO)
	xf = xf * Transform2D(_spin, Vector2(0.0, -half))
	draw_set_transform_matrix(xf)

	Neon.soft_light(self, Vector2.ZERO, half * 2.6, Color(Palette.PLAYER_EDGE, 0.42 * glow))

	var outer := PackedVector2Array([
		Vector2(-half, -half), Vector2(half, -half), Vector2(half, half), Vector2(-half, half)])
	draw_colored_polygon(outer, Palette.PLAYER_BODY)

	var core_half := half * core_ratio * core_scale
	var core_xf := Transform2D(_core_spin - _spin, _core_offset.rotated(-_spin))
	var inner := PackedVector2Array()
	for corner in outer:
		inner.append(core_xf * (corner / half * core_half))

	for i in 4:
		draw_line(outer[i] * 0.94, inner[i], Color(Palette.PLAYER_EDGE, 0.55), 2.0, true)
	if charged:
		Neon.polyline(self, inner, Palette.PLAYER_CORE, 2.0, 0.9, true)
		draw_colored_polygon(inner, Palette.PLAYER_CORE)
	else:
		# Double jump spent: a hollow core until the next landing.
		Neon.polyline(self, inner, Color(Palette.PLAYER_CORE, 0.7), 2.0, 0.3, true)
	Neon.polyline(self, outer, Palette.PLAYER_EDGE, 3.0, glow, true)


func _on_jumped() -> void:
	_squash = jump_stretch
	_squash_velocity = Vector2.ZERO


func _on_double_jumped() -> void:
	_squash = double_jump_stretch
	_squash_velocity = Vector2.ZERO
	_flip_left = PI
	_flare = 1.0


func _on_landed(impact_speed: float) -> void:
	var strength := clampf(impact_speed / land_impact_full, 0.25, 1.0)
	_squash = Vector2.ONE.lerp(land_squash, strength)
	_squash_velocity = Vector2.ZERO


func _on_respawned() -> void:
	visible = true
	_spin = 0.0
	_flip_left = 0.0
	_flare = 0.0
	_core_offset = Vector2.ZERO
	# Materialise: pop in from a small, stretched core.
	_squash = Vector2(0.4, 1.5)
	_squash_velocity = Vector2.ZERO
