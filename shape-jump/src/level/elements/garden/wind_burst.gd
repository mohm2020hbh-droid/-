@tool
class_name WindBurst
extends Node2D
## WIND BURST (World 04): gusts through a stretch of the corridor. A gust
## never pushes the player back or forward (the run is always left to right,
## at one speed): it lifts it away from its floor, or presses it down onto
## it, a little ([member lift]), so jumps carry higher or fall short and a
## surface that was out of reach comes within it, or goes out of it. On the
## ceiling the same wind blows the other way in the world: it acts relative
## to the floor you run on. An attach in progress ignores it.
##
## Each cycle (the Timeline STEPS curve): calm, a rising gust with streaks
## and whirling leaves as the warning, the gust at full strength, easing off.
## Nothing here kills. Origin = on the ground line at the left end.
## (Mirrored in tools/levelgen/levelgen.py: WindBurst.)

@export var width := 512.0:
	set(value):
		width = value
		queue_redraw()
@export var ceiling := -320.0:
	set(value):
		ceiling = value
		queue_redraw()
## Acceleration at full strength: negative lifts away from the floor (an
## updraft), positive presses toward it (px/s²). Kept well under gravity.
@export_range(-1200.0, 1200.0, 10.0, "suffix:px/s²") var lift := -900.0
@export_range(0.3, 10.0, 0.05, "suffix:s") var period := 2.4
@export_range(0.0, 0.95, 0.01) var hold_ratio := 0.4
@export_range(0.0, 1.0, 0.01) var phase := 0.0
@export_range(0.0, 1.0, 0.05, "suffix:s") var warning_time := 0.35

const STREAKS := 26

var _strength := 0.0
var _floor_up := false
var _time := 0.0
var _was_rising := false
var _streaks: _Streaks


func _ready() -> void:
	z_index = -1
	_streaks = _Streaks.new()
	add_child(_streaks, false, Node.INTERNAL_MODE_FRONT)
	GardenLook.tint(_streaks, &"garden_leaf")
	_streaks.setup(width, ceiling)
	if not Engine.is_editor_hint():
		Level.join(self)
	apply_time(0.0)


func _enter_tree() -> void:
	if is_node_ready() and not Engine.is_editor_hint():
		Level.join(self)


func _exit_tree() -> void:
	if not Engine.is_editor_hint():
		Level.leave(self)


## 0 calm .. 1 full gust at [param t].
func strength_at(t: float) -> float:
	return Timeline.steps(fposmod(t / period + phase, 1.0), hold_ratio)


## Acceleration toward the floor (+) or away (-) for a body centred at
## world x [param x] at level time [param t] ([param up]: the ceiling is the
## floor; the push is relative to the floor either way).
func wind(x: float, t: float, _up: bool) -> float:
	var local := x - global_position.x
	if local < 0.0 or local > width:
		return 0.0
	return lift * strength_at(t)


func apply_time(t: float) -> void:
	_strength = strength_at(t)
	var level := Level.of(self) if not Engine.is_editor_hint() else null
	_floor_up = level.gravity.up if level and level.gravity else false
	var cycle := fposmod(t / period + phase, 1.0)
	var rising := Timeline.steps_warning(cycle, hold_ratio, period, warning_time) and _strength < 0.5
	if rising and not _was_rising and level:
		level.report_cue(&"warning", global_position + Vector2(width * 0.5, ceiling * 0.5))
	_was_rising = rising
	_streaks.visible = _strength > 0.02 or rising
	_streaks.modulate.a = clampf(_strength + (0.35 if rising else 0.0), 0.0, 1.0)


func _process(delta: float) -> void:
	if Engine.is_editor_hint() or not _streaks.visible:
		return
	_time += delta
	# The streaks stream away from the floor for an updraft, toward it for a
	# downdraft, and drift with the run.
	var away := -1.0 if lift < 0.0 else 1.0
	var toward_ceiling := away * (1.0 if not _floor_up else -1.0)
	var span := absf(ceiling)
	_streaks.position = Vector2(fposmod(_time * 90.0, 64.0) - 64.0,
		fposmod(_time * 260.0 * toward_ceiling * -1.0, span * 0.5) - span * 0.25)


func _draw() -> void:
	# The zone's edges: faint vertical brush marks, so its extent is known.
	for x: float in [0.0, width]:
		draw_dashed_line(Vector2(x, ceiling), Vector2(x, 0.0), Color(GardenLook.INK_BODY, 0.18), 2.0, 16.0)


## Streaks and whirling leaves, drawn once over the zone (and a margin, so
## the scrolling never shows an edge).
class _Streaks extends Node2D:
	var width := 512.0
	var ceiling := -320.0

	func setup(w: float, c: float) -> void:
		width = w
		ceiling = c
		queue_redraw()

	func _draw() -> void:
		var rng := RandomNumberGenerator.new()
		rng.seed = int(width) + 17
		var lines := PackedVector2Array()
		var span := absf(ceiling)
		for i in WindBurst.STREAKS:
			var p := Vector2(rng.randf_range(0.0, width + 64.0), rng.randf_range(ceiling - span * 0.25, span * 0.25))
			var stroke := rng.randf_range(26.0, 60.0)
			lines.append_array([p, p + Vector2(stroke * 0.35, -stroke)])
		draw_multiline(lines, Color(1.0, 1.0, 1.0, 0.8), 2.0, true)
		var batch := GardenArt.Batch.new(Color(1.0, 1.0, 1.0, 0.85))
		for i in 8:
			var p := Vector2(rng.randf_range(0.0, width), rng.randf_range(ceiling, 0.0))
			batch.poly(GardenArt.leaf(p, 16.0, 7.0, rng.randf_range(0.0, TAU)), false)
		batch.draw(self, Color(0, 0, 0, 0))
