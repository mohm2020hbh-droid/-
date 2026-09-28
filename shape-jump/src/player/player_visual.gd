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
## With [member galaxy_style] (World 03) it is a WHITE SQUARE: a solid white
## body with a crisp dark edge and a small heart in the colour of the current
## gravity (solid while the double jump is available), bright against both
## of the galaxy's skies (see [method _draw_white_square]).
## With [member garden_style] (World 04) it is "The Seed": a faceted shell in
## the colour of its surface (yellow on the ground, blue on the ceiling), a
## dark or light edge that stands off both worlds, and inside it a diamond
## in the other surface's colour, solid while an attach can start. The first
## tap of the gesture crouches it and closes a ring on it; an attach turns it
## over to face the new floor within the crossing, stretches it toward the
## surface, and lands it with a pulse (see [method _draw_seed]).
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
@export_group("Galaxy")
## Draw World 03's white square.
@export var galaxy_style := false:
	set(value):
		galaxy_style = value
		queue_redraw()
@export_group("Seed")
## Draw the World 04 seed.
@export var garden_style := false:
	set(value):
		garden_style = value
		queue_redraw()
## Turns per second while the seed turns over to face a new floor (it must
## finish within an attach's crossing, about 0.18 s).
@export var attach_turn_speed := 4.5

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
## How far the drawing is turned over to stand on the ceiling: 0 on the
## ground, 1 on the ceiling. It is a vertical flip (scale.y = cos(PI * k)),
## never a rotation: the front stays on the right, so the player never looks
## back the way it came. Purely visual: the collision box never turns.
var _face := 0.0
var _face_target := 0.0
var _probe: PhysicsShapeQueryParameters2D
# Seed style state.
var _attach_glow := 0.0
var _landing_pulse := 0.0
var _miss_flicker := 0.0
## The first tap of the gesture is in (fades over the gesture's window).
var _armed := 0.0


func _ready() -> void:
	# Half a turn over a flat jump, whatever the tuning.
	_spin_speed = PI / player.config.flat_jump_airtime()
	player.jumped.connect(_on_jumped)
	player.double_jumped.connect(_on_double_jumped)
	player.landed.connect(_on_landed)
	player.died.connect(_on_died)
	player.respawned.connect(_on_respawned)
	player.attach_armed.connect(_on_attach_armed)
	player.attach_started.connect(_on_attach_started)
	player.attached.connect(_on_attached)
	player.attach_failed.connect(_on_attach_failed)
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
	if _face != _face_target:
		# Same pace as the half turn it replaces: (1 / 2 turn) s per flip.
		var turn := attach_turn_speed if garden_style else 1.6
		_face = move_toward(_face, _face_target, delta * 2.0 * turn)
		_apply_face()
	if garden_style:
		_attach_glow = maxf(_attach_glow - delta * 3.0, 0.0)
		_landing_pulse = maxf(_landing_pulse - delta * 3.2, 0.0)
		_miss_flicker = maxf(_miss_flicker - delta * 4.0, 0.0)
		_armed = maxf(_armed - delta / maxf(player.config.attach_window, 0.05), 0.0) if player.is_attach_pending() else 0.0
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
	target_offset.y *= _face_sign()
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


## Gravity turned (Worlds 03 and 04): the body turns over (a vertical flip)
## to stand on the new floor, with a pulse from inside. [param instant]: a
## restore, no motion.
func face_gravity(up: bool, instant: bool) -> void:
	_face_target = 1.0 if up else 0.0
	if instant:
		_face = _face_target
		_apply_face()
	else:
		_flare = 1.0
		_twist_left += PI


## Upright on the ground, upside down (mirrored top to bottom only) on the
## ceiling; the drawing is squeezed flat halfway through a flip.
func _apply_face() -> void:
	rotation = 0.0
	var y := cos(PI * _face)
	if absf(y) < 0.05:
		y = 0.05 if _face < 0.5 else -0.05  # Never a degenerate (zero-height) transform.
	scale = Vector2(1.0, y)


## True while the body is turned over (or turning) to stand on the ceiling.
func is_upside_down() -> bool:
	return _face > 0.5


## +1 upright, -1 turned over: world directions seen by the drawing.
func _face_sign() -> float:
	return -1.0 if _face > 0.5 else 1.0


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
	if garden_style:
		_draw_seed()
		return
	if galaxy_style:
		_draw_white_square()
		return
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
	var aim := (Vector2(_gaze.x, _gaze.y * _face_sign()).rotated(-_spin)).angle()
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


## World 03's player: a white square (see the class notes). Same size as the
## collision box, same squash, stretch and spin as every world's player.
func _draw_white_square() -> void:
	var half := player.half_size.x
	var xf := Transform2D(0.0, Vector2(0.0, half))
	xf = xf * Transform2D(0.0, _squash, 0.0, Vector2.ZERO)
	xf = xf * Transform2D(_spin, Vector2(0.0, -half))
	draw_set_transform_matrix(xf)
	var idle := player.state == Player.State.IDLE
	var breathe := 0.5 + 0.5 * sin(_time * (2.6 if idle else 6.0))
	# A white glow: the square stands out on the blue sky and the amber one.
	Neon.soft_light(self, Vector2.ZERO, half * (2.5 + 0.8 * _flare), Color(1.0, 1.0, 1.0, 0.3 + 0.1 * breathe + 0.3 * _flare))
	var body := Rect2(-half, -half, half * 2.0, half * 2.0)
	draw_rect(body, Color(1.0, 1.0, 1.0))
	# A crisp dark edge and a faint bevel, so the silhouette never melts into
	# a bright background.
	draw_rect(body, Color(0.07, 0.05, 0.14, 0.9), false, 2.0)
	draw_rect(body.grow(-5.0), Color(0.75, 0.78, 0.9, 0.55), false, 1.5)
	# The heart: the colour of the gravity the square stands in; solid while
	# the double jump is there, a ring once it is spent.
	var up := player.gravity != null and player.gravity.up
	var heart := GalaxyArt.state_color(up).darkened(0.25)
	var r := half * (0.3 + 0.12 * _flare)
	var core := Rect2(-r, -r, r * 2.0, r * 2.0)
	if player.has_double_jump():
		draw_rect(core, heart)
	else:
		draw_rect(core, heart, false, 2.5)


## World 04's "Seed": see the class notes. Geometry only: an octagonal shell,
## a turning diamond, corner facets; colour and light carry the state.
func _draw_seed() -> void:
	var half := player.half_size.x
	var xf := Transform2D(0.0, Vector2(0.0, half))
	xf = xf * Transform2D(0.0, _squash, 0.0, Vector2.ZERO)
	xf = xf * Transform2D(_spin, Vector2(0.0, -half))
	draw_set_transform_matrix(xf)

	var body := GardenLook.player_color("body")
	var edge := GardenLook.player_color("edge")
	var seed := GardenLook.player_color("seed")
	var glow := GardenLook.player_color("glow")
	var idle := player.state == Player.State.IDLE
	var breathe := 0.5 + 0.5 * sin(_time * (2.6 if idle else 7.0))
	# A halo in the body's own light: never swallowed by either world.
	Neon.soft_light(self, Vector2.ZERO, half * (2.4 + 0.8 * _attach_glow + 0.6 * _landing_pulse + 0.5 * _armed),
		Color(glow, 0.38 + 0.12 * breathe + 0.3 * _attach_glow + 0.25 * _armed))

	var cut := half * 0.3
	var shell := PackedVector2Array([
		Vector2(-half + cut, -half), Vector2(half - cut, -half), Vector2(half, -half + cut), Vector2(half, half - cut),
		Vector2(half - cut, half), Vector2(-half + cut, half), Vector2(-half, half - cut), Vector2(-half, -half + cut)])
	draw_colored_polygon(shell, body)
	# Facets: a lighter upper-left plane, a deeper lower-right one.
	draw_colored_polygon(PackedVector2Array([shell[7], shell[0], shell[1], Vector2.ZERO]),
		Color(1.0, 1.0, 1.0, 0.22))
	draw_colored_polygon(PackedVector2Array([shell[3], shell[4], shell[5], Vector2.ZERO]),
		Color(0.0, 0.0, 0.0, 0.16))

	# The seed: the other surface's colour, turning; solid while an attach
	# can start, hollow while crossing or off a surface.
	var r := half * (0.42 + 0.12 * _flare + 0.18 * _attach_glow + 0.14 * _armed)
	var turn := _core_spin * 0.5
	var diamond := PackedVector2Array()
	for i in 4:
		diamond.append(Vector2.from_angle(turn + PI * 0.5 * i) * r + _core_offset * 0.6)
	var loop := diamond.duplicate()
	loop.append(diamond[0])
	if player.can_attach() or player.is_attaching():
		draw_colored_polygon(diamond, seed)
		draw_polyline(loop, edge, 2.0, true)
	else:
		draw_polyline(loop, Color(seed, 0.9), 2.5, true)
	if _miss_flicker > 0.0:
		# A failed attach: the seed flickers where it reached for nothing.
		draw_polyline(loop, Color(edge, _miss_flicker), 1.5, true)

	var outline := shell.duplicate()
	outline.append(shell[0])
	draw_polyline(outline, edge, 3.0, true)
	# The first tap: a ring closing in on the seed while the second is awaited.
	if _armed > 0.0:
		draw_arc(Vector2.ZERO, half * (1.05 + 0.9 * _armed), 0.0, TAU, 28, Color(glow, 0.75 * _armed), 2.5, true)
	# Landing on a new surface: a ring of the new colour snaps out.
	if _landing_pulse > 0.0:
		var ring := half * (1.1 + 1.2 * (1.0 - _landing_pulse))
		draw_arc(Vector2.ZERO, ring, 0.0, TAU, 32, Color(glow, 0.8 * _landing_pulse), 3.0, true)


func _on_attach_armed() -> void:
	_armed = 1.0
	# A small crouch against the surface: the gesture has begun.
	_squash = Vector2(1.15, 0.86)
	_squash_velocity = Vector2.ZERO


## The gesture took hold: stretched toward the surface it is crossing to, and
## already turning over to stand on it (gravity follows on the touch).
func _on_attach_started(_landing: Vector2, to_up: bool) -> void:
	_armed = 0.0
	_attach_glow = 1.0
	_squash = Vector2(0.7, 1.4)
	_squash_velocity = Vector2.ZERO
	face_gravity(to_up, false)


func _on_attached(_up: bool) -> void:
	_landing_pulse = 1.0  # Locked onto the new surface.
	_squash = land_squash
	_squash_velocity = Vector2.ZERO


func _on_attach_failed() -> void:
	_armed = 0.0
	_miss_flicker = 1.0


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
	if garden_style and _attach_glow > 0.0:
		_landing_pulse = 1.0  # The end of a crossing: locked onto the new surface.
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
	_attach_glow = 0.0
	_landing_pulse = 0.0
	_miss_flicker = 0.0
	_armed = 0.0
	_core_offset = Vector2.ZERO
	# Materialise: pop in from a small, stretched core.
	_squash = Vector2(0.4, 1.5)
	_squash_velocity = Vector2.ZERO
