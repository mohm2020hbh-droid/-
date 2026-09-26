@tool
class_name CeilingTrap
extends Hazard
## CEILING TRAP (World 03): a maw of crystal shards set into a surface,
## usually the ceiling, that bites out and pulls back on a fixed rhythm.
## Harmless in passing while you run on the ground; the moment the ceiling
## is your floor, it is the thing in your way. Shards flicker before every
## move (the STEPS curve, like World 02's machines).
## Origin = the left end, on the surface; [member facing] 1 bites downward
## (from a ceiling), -1 upward (from the ground).

@export var width := 128.0:
	set(value):
		width = value
		_rebuild()
@export var reach := 72.0:
	set(value):
		reach = value
		_rebuild()
@export_range(-1, 1, 2) var facing := 1:
	set(value):
		facing = value
		_rebuild()
@export_range(0.3, 10.0, 0.05, "suffix:s") var period := 1.6
@export_range(0.0, 0.95, 0.01) var hold_ratio := 0.5
@export_range(0.0, 1.0, 0.01) var phase := 0.0
@export_range(0.0, 1.0, 0.05, "suffix:s") var warning_time := 0.3

const PARKED := Vector2(0.0, 100000.0)
const SHARD_WIDTH := 32.0
## Share of the shards' height that kills (the base, where they touch).
const SOLID_SHARE := 0.65

var _teeth: _Teeth
var _shape: CollisionShape2D
var _box: RectangleShape2D


func _ready() -> void:
	super()
	_rebuild()
	apply_time(0.0)


## How far the shards stand out at [param t] (0..reach).
func extension_at(t: float) -> float:
	return reach * Timeline.steps(fposmod(t / period + phase, 1.0), hold_ratio)


func apply_time(t: float) -> void:
	if _teeth == null:
		return
	var out := extension_at(t)
	_teeth.position = Vector2(0.0, facing * (out - reach))
	var cycle := fposmod(t / period + phase, 1.0)
	var flash := Timeline.steps_warning(cycle, hold_ratio, period, warning_time) and fmod(t * 10.0, 1.0) < 0.5
	_teeth.self_modulate = Color(1.7, 1.7, 1.7) if flash else Color.WHITE
	# The deadly part is the solid base of the shards; their thin tips are
	# forgiven (the hitbox never pokes out of the drawing).
	var inset := HITBOX_INSET * 2.0
	var deadly := out * SOLID_SHARE
	if deadly <= inset:
		_shape.position = PARKED
		return
	_box.size = Vector2(width - inset, deadly - inset)
	_shape.position = Vector2(width * 0.5, facing * deadly * 0.5)


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _teeth == null:
		_teeth = _Teeth.new()
		add_child(_teeth, false, Node.INTERNAL_MODE_FRONT)
		_box = RectangleShape2D.new()
		_shape = add_hitbox(_box)
	_teeth.setup(width, reach, facing)
	queue_redraw()


func _draw() -> void:
	# The socket in the surface the shards come out of.
	var depth := 10.0 * -facing
	draw_rect(Rect2(0.0, minf(0.0, depth), width, absf(depth)), GalaxyArt.DANGER_BODY)
	draw_line(Vector2(0.0, 0.0), Vector2(width, 0.0), Color(GalaxyArt.DANGER, 0.8), 2.0)


class _Teeth extends Node2D:
	var width := 128.0
	var reach := 72.0
	var facing := 1

	func setup(w: float, r: float, f: int) -> void:
		width = w
		reach = r
		facing = f
		queue_redraw()

	func _draw() -> void:
		var count := maxi(int(width / CeilingTrap.SHARD_WIDTH), 1)
		var w := width / count
		for i in count:
			var base := Vector2(w * (i + 0.5), 0.0)
			var tall := reach * (1.0 if i % 2 == 0 else 0.8)
			var spike := GalaxyArt.shard(base, Vector2(0.0, facing), tall, w * 0.9)
			draw_colored_polygon(spike, GalaxyArt.DANGER_BODY)
			draw_polyline(GalaxyArt.closed(spike), GalaxyArt.DANGER, 2.0, true)
			draw_line(base, base + Vector2(0.0, facing * tall * 0.7), Color(GalaxyArt.DANGER_CORE, 0.5), 1.5)
