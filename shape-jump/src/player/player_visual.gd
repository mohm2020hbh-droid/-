class_name PlayerVisual
extends Node2D
## Draws "The Core" (a tesseract projection: outer square, hot inner core,
## four depth struts) and animates it procedurally per state:
##   Idle  – the core breathes, the edge glow pulses.
##   Run   – the core spins like a gyroscope.
##   Jump  – vertical stretch, the body spins half a turn per jump.
##   Fall  – keeps spinning, the core drifts up from inertia.
##   Land  – squash with elastic recovery, spin snaps to the nearest 90°.
##   Death – hidden; PlayerFx shatters it.
## Purely cosmetic: nothing here feeds back into gameplay or collision.

const HALF := Player.SIZE * 0.5
const CORE_RATIO := 0.42
## Spring pulling the squash scale back to 1:1.
const SQUASH_STIFFNESS := 320.0
const SQUASH_DAMPING := 16.0
const SPIN_SNAP_RATE := 28.0
const INERTIA_PER_SPEED := 0.006
const MAX_CORE_OFFSET := 6.0

@export var player: Player

var _time := 0.0
var _spin := 0.0
var _spin_speed := 0.0
var _core_spin := 0.0
var _core_offset := Vector2.ZERO
var _squash := Vector2.ONE
var _squash_velocity := Vector2.ZERO


func _ready() -> void:
	# Half a turn over a flat jump, whatever the tuning.
	_spin_speed = PI / player.config.flat_jump_airtime()
	player.jumped.connect(_on_jumped)
	player.landed.connect(_on_landed)
	player.died.connect(func(_cause: StringName) -> void: visible = false)
	player.respawned.connect(_on_respawned)


func _process(delta: float) -> void:
	_time += delta
	var state := player.state
	var airborne := state == Player.State.JUMP or state == Player.State.FALL

	if airborne:
		_spin += _spin_speed * delta
	else:
		var snapped_spin := snappedf(_spin, PI * 0.5)
		_spin = lerpf(_spin, snapped_spin, 1.0 - exp(-SPIN_SNAP_RATE * delta))

	match state:
		Player.State.RUN:
			_core_spin += 4.0 * delta
		Player.State.JUMP, Player.State.FALL:
			_core_spin += 2.0 * delta
		_:
			_core_spin = lerp_angle(_core_spin, 0.0, 1.0 - exp(-4.0 * delta))

	# The core lags behind velocity, as if it floated inside a deeper space.
	var target_offset := (-player.velocity * INERTIA_PER_SPEED).limit_length(MAX_CORE_OFFSET)
	target_offset.x *= 0.5
	_core_offset = _core_offset.lerp(target_offset, 1.0 - exp(-10.0 * delta))

	var force := (Vector2.ONE - _squash) * SQUASH_STIFFNESS - _squash_velocity * SQUASH_DAMPING
	_squash_velocity += force * delta
	_squash += _squash_velocity * delta

	queue_redraw()


func _draw() -> void:
	var idle := player.state == Player.State.IDLE
	var breathe := sin(_time * 2.6)
	var glow := 0.85 + (0.25 * breathe if idle else 0.1 * sin(_time * 9.0))
	var core_scale := 1.0 + (0.1 * breathe if idle else 0.0)

	# Squash around the feet so landings stay planted, then spin around the centre.
	var xf := Transform2D(0.0, Vector2(0.0, HALF))
	xf = xf * Transform2D(0.0, _squash, 0.0, Vector2.ZERO)
	xf = xf * Transform2D(_spin, Vector2(0.0, -HALF))
	draw_set_transform_matrix(xf)

	Neon.soft_light(self, Vector2.ZERO, HALF * 2.6, Color(Palette.PLAYER_EDGE, 0.42 * glow))

	var outer := PackedVector2Array([
		Vector2(-HALF, -HALF), Vector2(HALF, -HALF), Vector2(HALF, HALF), Vector2(-HALF, HALF)])
	draw_colored_polygon(outer, Palette.PLAYER_BODY)

	var core_half := HALF * CORE_RATIO * core_scale
	var core_xf := Transform2D(_core_spin - _spin, _core_offset.rotated(-_spin))
	var inner := PackedVector2Array()
	for corner in outer:
		inner.append(core_xf * (corner / HALF * core_half))

	for i in 4:
		draw_line(outer[i] * 0.94, inner[i], Color(Palette.PLAYER_EDGE, 0.55), 2.0, true)
	Neon.polyline(self, inner, Palette.PLAYER_CORE, 2.0, 0.9, true)
	draw_colored_polygon(inner, Palette.PLAYER_CORE)
	Neon.polyline(self, outer, Palette.PLAYER_EDGE, 3.0, glow, true)


func _on_jumped() -> void:
	_squash = Vector2(0.8, 1.22)
	_squash_velocity = Vector2.ZERO


func _on_landed(impact_speed: float) -> void:
	var strength := clampf(impact_speed / 1100.0, 0.25, 1.0)
	_squash = Vector2(1.0 + 0.3 * strength, 1.0 - 0.28 * strength)
	_squash_velocity = Vector2.ZERO


func _on_respawned() -> void:
	visible = true
	_spin = 0.0
	_core_offset = Vector2.ZERO
	# Materialise: pop in from a small, stretched core.
	_squash = Vector2(0.4, 1.5)
	_squash_velocity = Vector2.ZERO
