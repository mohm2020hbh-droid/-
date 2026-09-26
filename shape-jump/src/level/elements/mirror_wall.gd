@tool
class_name MirrorWall
extends Hazard
## MIRROR WALL (docs/GDD.md §7, World 02): two tall glass panels, one from
## above and its mirror image from below, that slide apart and shut again
## around a centre line. Closed, they seal the run; open, a passage appears
## whose size you must read. Faint dashed reflections of both panels hang
## beside them: dashed means not solid, in every World 02 machine.
## Timing: hold shut, open, hold open, close (the STEPS curve); the panels
## flash before each move. Origin = centre line x at floor level.

@export_range(16.0, 256.0, 1.0, "suffix:px") var width := 56.0:
	set(value):
		width = value
		_rebuild()
@export_range(64.0, 4096.0, 1.0, "suffix:px") var reach := 1200.0:
	set(value):
		reach = value
		_rebuild()
## Centre line of the passage, relative to the origin (negative = above).
@export var center_y := -96.0
## Height of the passage when fully open.
@export_range(0.0, 512.0, 1.0, "suffix:px") var max_gap := 160.0
@export_range(0.2, 20.0, 0.05, "suffix:s") var period := 1.6
## Share of the cycle spent holding (half shut, half open).
@export_range(0.0, 0.95, 0.01) var hold_ratio := 0.6
@export_range(0.0, 1.0, 0.01) var phase := 0.0
@export_range(0.0, 1.0, 0.05, "suffix:s") var warning_time := 0.3

var _upper: Node2D
var _lower: Node2D
var _upper_shape: CollisionShape2D
var _lower_shape: CollisionShape2D


func _ready() -> void:
	super()
	_rebuild()
	apply_time(0.0)


func gap_at(t: float) -> float:
	return max_gap * Timeline.steps(fposmod(t / period + phase, 1.0), hold_ratio)


func apply_time(t: float) -> void:
	if _upper == null:
		return
	var half := gap_at(t) * 0.5
	_upper.position.y = center_y - half
	_lower.position.y = center_y + half
	_upper_shape.position.y = center_y - half - reach * 0.5
	_lower_shape.position.y = center_y + half + reach * 0.5
	var cycle := fposmod(t / period + phase, 1.0)
	var flash := Timeline.steps_warning(cycle, hold_ratio, period, warning_time) and fmod(t * 10.0, 1.0) < 0.5
	var tint := Color(1.7, 1.7, 1.7) if flash else Color.WHITE
	_upper.self_modulate = tint
	_lower.self_modulate = tint


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _upper == null:
		_upper = _Panel.new()
		_lower = _Panel.new()
		add_child(_upper, false, Node.INTERNAL_MODE_FRONT)
		add_child(_lower, false, Node.INTERNAL_MODE_FRONT)
		_upper_shape = add_hitbox(RectangleShape2D.new())
		_lower_shape = add_hitbox(RectangleShape2D.new())
	(_upper as _Panel).setup(Rect2(-width * 0.5, -reach, width, reach), true)
	(_lower as _Panel).setup(Rect2(-width * 0.5, 0.0, width, reach), false)
	var hitbox := Vector2(width, reach) - Vector2.ONE * HITBOX_INSET * 2.0
	(_upper_shape.shape as RectangleShape2D).size = hitbox
	(_lower_shape.shape as RectangleShape2D).size = hitbox
	queue_redraw()


func _draw() -> void:
	# Reflections: the passage's closed and open outlines, dashed, beside the wall.
	var c := Color(Palette.NEON, 0.16)
	for dx in [-width * 1.6, width * 1.6]:
		var r := Rect2(dx - width * 0.5, center_y - max_gap * 0.5 - 120.0, width, max_gap + 240.0)
		Neon.dashed_rect(self, r, c, 1.5, 8.0)


## One glass panel, drawn once; the wall moves it.
class _Panel extends Node2D:
	var rect := Rect2()
	var upper := true

	func setup(r: Rect2, is_upper: bool) -> void:
		rect = r
		upper = is_upper
		queue_redraw()

	func _draw() -> void:
		draw_rect(rect, Palette.HAZARD_BODY)
		# Glass: two pale reflection streaks and a bright facing edge.
		for k: float in [0.25, 0.62]:
			var x := rect.position.x + rect.size.x * k
			draw_line(Vector2(x, rect.position.y), Vector2(x + 10.0, rect.end.y), Color(Palette.HAZARD_CORE, 0.14), 6.0)
		Neon.rect_outline(self, rect, Palette.HAZARD, 2.0, 0.8)
		var y := rect.end.y if upper else rect.position.y
		Neon.line(self, Vector2(rect.position.x, y), Vector2(rect.end.x, y), Palette.HAZARD_CORE, 4.0, 1.4)
