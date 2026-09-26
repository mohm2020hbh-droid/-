@tool
class_name MazePanel
extends Hazard
## ROTATING MAZE (docs/GDD.md §7, World 02): a long panel that turns a quarter
## turn at a time around its centre: it holds, flashes, snaps 90°, holds.
## A few of them together keep rebuilding the path between horizontal and
## vertical walls. The angle is a pure function of level time; the hitbox
## is the panel, turning with it. Origin = the pivot.

@export_range(64.0, 1024.0, 1.0, "suffix:px") var length := 256.0:
	set(value):
		length = value
		_rebuild()
@export_range(16.0, 96.0, 1.0, "suffix:px") var thickness := 32.0:
	set(value):
		thickness = value
		_rebuild()
@export_range(0.1, 10.0, 0.05, "suffix:s") var hold_time := 0.9
@export_range(0.05, 2.0, 0.05, "suffix:s") var turn_time := 0.25
## Offset into the cycle, 0..1 of one hold+turn.
@export_range(0.0, 1.0, 0.01) var phase := 0.0
## Starting angle in quarter turns (0 = horizontal).
@export_range(0, 3) var start_quarter := 0
## 1 turns clockwise, -1 counter-clockwise.
@export_range(-1, 1, 2) var direction := 1
@export_range(0.0, 1.0, 0.05, "suffix:s") var warning_time := 0.3

var _shape_node: CollisionShape2D


func _ready() -> void:
	super()
	_rebuild()
	apply_time(0.0)


func angle_at(t: float) -> float:
	var cycle := hold_time + turn_time
	var u := t + phase * cycle
	var k := floorf(u / cycle)
	var local := u - k * cycle
	var frac := 0.0 if local < hold_time else smoothstep(0.0, 1.0, (local - hold_time) / turn_time)
	return (start_quarter + direction * (fposmod(k, 4.0) + frac)) * PI * 0.5


func apply_time(t: float) -> void:
	rotation = fposmod(angle_at(t), TAU)
	var cycle := hold_time + turn_time
	var local := fposmod(t + phase * cycle, cycle)
	var flash := local >= hold_time - warning_time and local < hold_time and fmod(t * 10.0, 1.0) < 0.5
	self_modulate = Color(1.7, 1.7, 1.7) if flash else Color.WHITE


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _shape_node == null:
		_shape_node = add_hitbox(RectangleShape2D.new())
	(_shape_node.shape as RectangleShape2D).size = Vector2(length, thickness) - Vector2.ONE * HITBOX_INSET * 2.0
	queue_redraw()


func _draw() -> void:
	var rect := Rect2(-length * 0.5, -thickness * 0.5, length, thickness)
	# The panel's sweep: a faint circle, so where it can turn is always visible.
	draw_arc(Vector2.ZERO, length * 0.5, 0.0, TAU, 48, Color(Palette.NEON, 0.08), 2.0, true)
	draw_rect(rect, Palette.HAZARD_BODY)
	HazardArt.stripes(self, rect.grow(-4.0), 26.0, Color(Palette.HAZARD_STRIPE, 0.6), 2.0)
	Neon.rect_outline(self, rect, Palette.HAZARD, 2.0, 0.9)
	draw_circle(Vector2.ZERO, thickness * 0.32, Palette.HAZARD_CORE)
	draw_circle(Vector2.ZERO, thickness * 0.16, Palette.HAZARD_BODY)
