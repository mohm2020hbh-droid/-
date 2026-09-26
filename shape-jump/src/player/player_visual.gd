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
## With [member void_style] (World 02) the same body is drawn as "The Void":
## a clean black shell with something geometric trapped inside (see
## [method _draw_void]).
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
@export_group("Void")
## Draw the World 02 entity instead of the tesseract core.
@export var void_style := false:
	set(value):
		void_style = value
		queue_redraw()
## Hazards closer than this (px from the centre) unsettle the entity.
@export var danger_radius := 110.0
## How long the void keeps collapsing on screen after a death.
@export_range(0.1, 1.0, 0.05, "suffix:s") var void_death_time := 0.42

## Spike lengths of the trapped shape (relative to its radius), irregular on
## purpose; [member _glitch] briefly swaps to the second set.
const VOID_SPIKES := [1.0, 0.46, 0.78, 0.4, 0.92, 0.52, 0.64]
const VOID_SPIKES_ALT := [0.7, 0.9, 0.38, 1.0, 0.44, 0.84, 0.5]
## Seconds between the entity's rare pattern changes, and their length.
const GLITCH_EVERY := 3.7
const GLITCH_TIME := 0.14

var _time := 0.0
var _spin := 0.0
var _spin_speed := 0.0
var _core_spin := 0.0
var _core_offset := Vector2.ZERO
var _squash := Vector2.ONE
var _squash_velocity := Vector2.ZERO
var _flip_left := 0.0
var _flare := 0.0
# Void style state.
var _twist := 0.0
var _twist_left := 0.0
var _danger := 0.0
var _danger_target := 0.0
var _gaze := Vector2.RIGHT
var _gaze_target := Vector2.RIGHT
var _dying := 0.0
var _settle := 0.0
## Node rotation that puts the drawing's feet on the current floor (World 03:
## PI while gravity pulls up). Purely visual: the collision box never turns.
var _face_target := 0.0
var _probe: PhysicsShapeQueryParameters2D


func _ready() -> void:
	# Half a turn over a flat jump, whatever the tuning.
	_spin_speed = PI / player.config.flat_jump_airtime()
	player.jumped.connect(_on_jumped)
	player.double_jumped.connect(_on_double_jumped)
	player.landed.connect(_on_landed)
	player.died.connect(_on_died)
	player.respawned.connect(_on_respawned)
	# Built once and reused: one small shape query per tick, no allocations.
	var circle := CircleShape2D.new()
	circle.radius = danger_radius
	_probe = PhysicsShapeQueryParameters2D.new()
	_probe.shape = circle
	_probe.collision_mask = GameConst.LAYER_HAZARD
	_probe.collide_with_areas = true
	_probe.collide_with_bodies = false


func _physics_process(_delta: float) -> void:
	if not void_style or player.is_dead() or not is_inside_tree():
		return
	# One shape query per tick: how close is the nearest hazard, and where.
	_probe.transform = Transform2D(0.0, player.global_position)
	var info := get_world_2d().direct_space_state.get_rest_info(_probe)
	if info.is_empty():
		_danger_target = 0.0
		_gaze_target = Vector2.RIGHT
	else:
		var to_point: Vector2 = info.point - player.global_position
		_danger_target = clampf(1.0 - to_point.length() / danger_radius, 0.0, 1.0)
		if to_point.length() > 1.0:
			_gaze_target = to_point.normalized()


func _process(delta: float) -> void:
	_time += delta
	var state := player.state
	var airborne := state == Player.State.JUMP or state == Player.State.FALL

	if _flip_left > 0.0:
		var flip := minf(_flip_left, flip_speed * delta)
		_spin += flip
		_flip_left -= flip
	_flare = maxf(_flare - delta * 4.0, 0.0)
	if not is_equal_approx(rotation, _face_target):
		rotation = rotate_toward(rotation, _face_target, delta * TAU * 1.6)
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
	if void_style:
		_step_void(delta)
	queue_redraw()


func _step_void(delta: float) -> void:
	if _twist_left > 0.0:
		var turn := minf(_twist_left, 14.0 * delta)
		_twist += turn
		_twist_left -= turn
	# Calm while running: a slow drift; it only stirs when something happens.
	_twist = fposmod(_twist + (0.5 + 3.0 * _danger) * delta, TAU)
	_danger = lerpf(_danger, _danger_target, 1.0 - exp(-12.0 * delta))
	_gaze = _gaze.slerp(_gaze_target, 1.0 - exp(-8.0 * delta)).normalized()
	_settle = maxf(_settle - delta * 0.6, 0.0)
	if _dying > 0.0:
		_dying = maxf(_dying - delta, 0.0)
		if _dying == 0.0:
			visible = false


## Gravity turned (World 03): the entity rolls over to stand on the new
## floor, with a pulse from inside. [param instant]: a restore, no motion.
func face_gravity(up: bool, instant: bool) -> void:
	_face_target = PI if up else 0.0
	if instant:
		rotation = _face_target
	else:
		_flare = 1.0
		_twist_left += PI


## The level is complete: the thing inside goes quiet and bright.
func settle() -> void:
	_settle = 1.0
	_danger_target = 0.0


func get_squash() -> Vector2:
	return _squash


## Semi-implicit Euler: stable for these stiffness values at <= 1/30 s steps.
func _step_spring(step: float) -> void:
	var force := (Vector2.ONE - _squash) * spring_stiffness - _squash_velocity * spring_damping
	_squash_velocity += force * step
	_squash += _squash_velocity * step


func _draw() -> void:
	if void_style:
		_draw_void()
		return
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


## "Something is trapped inside this geometric shape": a clean black shell
## with a white frame, and inside it an irregular, jagged star around a void.
## Its longest spike turns toward the nearest danger, so it seems to watch;
## no face, eyes or anatomy, only geometry. The small square at the centre is
## the double jump: solid while available, hollow once spent.
func _draw_void() -> void:
	var half := player.half_size.x
	var death_t := 1.0 - _dying / void_death_time if _dying > 0.0 else 0.0
	var xf := Transform2D(0.0, Vector2(0.0, half))
	xf = xf * Transform2D(0.0, _squash, 0.0, Vector2.ZERO)
	xf = xf * Transform2D(_spin, Vector2(0.0, -half))
	draw_set_transform_matrix(xf)

	var speed := absf(player.velocity.x) / maxf(player.config.run_speed, 1.0)
	var shake := _danger * _danger * 1.6 + death_t * 2.5
	var jitter := Vector2(sin(_time * 71.0), cos(_time * 53.0)) * shake
	Neon.soft_light(self, Vector2.ZERO, half * 2.4, Color(1, 1, 1, 0.1 + 0.12 * _flare + 0.2 * _settle))

	var outer := PackedVector2Array([
		Vector2(-half, -half), Vector2(half, -half), Vector2(half, half), Vector2(-half, half)])
	draw_colored_polygon(outer, Palette.PLAYER_BODY)

	# Fracture lines from the corners, kinked, never straight struts.
	var inner_r := half * (0.62 + 0.3 * _flare + 0.5 * death_t)
	for i in 4:
		var corner := outer[i] * 0.9
		var kink := corner * 0.62 + corner.orthogonal() * (0.16 + 0.1 * sin(_time * 0.7 + i))
		var end := corner.normalized() * inner_r * 0.55
		var line := Color(1, 1, 1, 0.28 + 0.4 * _danger + 0.5 * death_t)
		draw_polyline(PackedVector2Array([corner, kink, end]), line, 1.5, true)

	# The trapped shape: spikes rotate slowly; the longest one points at the
	# gaze direction (the nearest hazard, or ahead).
	var spikes: Array = VOID_SPIKES_ALT if fposmod(_time, GLITCH_EVERY) < GLITCH_TIME else VOID_SPIKES
	var count := spikes.size()
	var aim := (_gaze.rotated(-_spin)).angle()
	var base := lerp_angle(_twist, aim, 0.35 + 0.6 * _danger)
	var star := PackedVector2Array()
	for i in count * 2:
		var angle := base + TAU * i / (count * 2)
		var r: float
		if i % 2 == 0:
			var reach: float = spikes[i / 2]
			r = inner_r * (reach + (0.18 * _danger + 0.25 * _flare) * reach * reach)
			r *= 1.0 - 0.55 * _settle
		else:
			r = inner_r * (0.26 + 0.08 * sin(_time * 3.1 + i))
		star.append(Vector2.from_angle(angle) * r + jitter)
	# Speed drags the shape back inside its shell, as if it lagged behind.
	var drag := clampf(speed - 1.1, 0.0, 0.4) * half * 0.35
	for i in star.size():
		star[i].x -= drag * (0.5 + 0.5 * (star[i].x / maxf(inner_r, 1.0)))
	var star_closed := star.duplicate()
	star_closed.append(star[0])
	draw_colored_polygon(star, Color(0, 0, 0, 1))
	var edge_alpha := 0.9 if fposmod(_time, GLITCH_EVERY) >= GLITCH_TIME else 0.5
	draw_polyline(star_closed, Color(1, 1, 1, edge_alpha), 1.6 + _flare, true)

	# The void centre: grows over everything as it dies.
	var hole := half * (0.2 + 0.08 * _flare + 0.9 * death_t)
	draw_circle(jitter * 0.5, hole, Color(0, 0, 0, 1))
	var dj := half * 0.13
	var core := PackedVector2Array([
		Vector2(-dj, 0.0), Vector2(0.0, -dj), Vector2(dj, 0.0), Vector2(0.0, dj)])
	var core_xf := Transform2D(-_twist * 1.7, jitter * 0.5)
	for i in core.size():
		core[i] = core_xf * core[i]
	if death_t == 0.0:
		if player.has_double_jump():
			draw_colored_polygon(core, Palette.PLAYER_CORE)
		else:
			var loop := core.duplicate()
			loop.append(core[0])
			draw_polyline(loop, Color(1, 1, 1, 0.75), 1.5, true)

	# Double jump: a hard square pulse inside the shell.
	if _flare > 0.0:
		var pulse := half * (0.3 + 0.62 * (1.0 - _flare))
		draw_rect(Rect2(-pulse, -pulse, pulse * 2.0, pulse * 2.0), Color(1, 1, 1, 0.7 * _flare), false, 2.0)
	# Dying: cracks run from the void to the shell, which stays whole.
	if death_t > 0.0:
		for i in 5:
			var a := 0.9 + i * 1.37
			var tip := Vector2.from_angle(a) * half * minf(0.25 + death_t * 1.1, 0.98)
			var mid := Vector2.from_angle(a + 0.3) * half * 0.45 * death_t
			draw_polyline(PackedVector2Array([Vector2.ZERO, mid, tip]), Color(1, 1, 1, 1.0 - death_t * 0.5), 1.5)
	# The shell: a crisp white frame and corner brackets. Near danger it
	# flickers; it never breaks, so the silhouette always reads.
	var frame_alpha := 1.0 - 0.35 * _danger * (0.5 + 0.5 * sin(_time * 40.0))
	var loop_outer := outer.duplicate()
	loop_outer.append(outer[0])
	draw_polyline(loop_outer, Color(Palette.PLAYER_EDGE, frame_alpha), 3.0, true)
	var b := half * 0.72
	var arm := half * 0.22
	for c in [Vector2(-1, -1), Vector2(1, -1), Vector2(1, 1), Vector2(-1, 1)]:
		var p: Vector2 = c * b
		draw_polyline(PackedVector2Array([p - Vector2(c.x * arm, 0.0), p, p - Vector2(0.0, c.y * arm)]),
			Color(1, 1, 1, 0.55), 1.2, true)


func _on_jumped() -> void:
	_squash = jump_stretch
	_squash_velocity = Vector2.ZERO
	_twist_left += PI * 0.25


func _on_double_jumped() -> void:
	_squash = double_jump_stretch
	_squash_velocity = Vector2.ZERO
	_flip_left = PI
	_flare = 1.0
	_twist_left += PI * 0.6


func _on_landed(impact_speed: float) -> void:
	var strength := clampf(impact_speed / land_impact_full, 0.25, 1.0)
	_squash = Vector2.ONE.lerp(land_squash, strength)
	_squash_velocity = Vector2.ZERO


func _on_died(_cause: StringName) -> void:
	if void_style:
		# The shell stays on screen while the void inside collapses.
		_dying = void_death_time
		_flare = 0.0
	else:
		visible = false


func _on_respawned() -> void:
	visible = true
	_dying = 0.0
	_danger = 0.0
	_danger_target = 0.0
	_settle = 0.0
	_spin = 0.0
	_flip_left = 0.0
	_flare = 0.0
	_core_offset = Vector2.ZERO
	# Materialise: pop in from a small, stretched core.
	_squash = Vector2(0.4, 1.5)
	_squash_velocity = Vector2.ZERO
