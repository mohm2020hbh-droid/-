@tool
class_name InkFlow
extends GardenHazard
## INK FLOW (World 04): black ink welling up from a spring in a surface and
## flowing along it toward the player, as far as [member reach], then
## draining back into the spring. Touching it kills; it is thin, so a jump
## clears it, but a long flow asks to be crossed on the other surface.
## The spring bubbles before every flow ([member warning_time]).
## Anchored to a surface, or to the floor of the moment.
## Origin = on the ground line at the spring (the right end of the flow).
## (Mirrored in tools/levelgen/levelgen.py: InkFlow.)

## How far the ink flows from the spring (px, toward smaller x).
@export var reach := 384.0:
	set(value):
		reach = value
		_rebuild()
@export var thickness := 22.0:
	set(value):
		thickness = value
		_rebuild()
@export_range(0.3, 10.0, 0.05, "suffix:s") var period := 2.4
@export_range(0.0, 0.95, 0.01) var hold_ratio := 0.5
@export_range(0.0, 1.0, 0.01) var phase := 0.0
@export_range(0.0, 1.0, 0.05, "suffix:s") var warning_time := 0.35

var _pool: _Pool
var _front: Node2D
var _spring: Node2D
var _shape: CollisionShape2D
var _box: RectangleShape2D
var _was_warning := false


func _ready() -> void:
	super()
	_rebuild()
	apply_time(0.0)


## How far (px) the ink has flowed from the spring at [param t].
func flowed_at(t: float) -> float:
	return reach * Timeline.steps(fposmod(t / period + phase, 1.0), hold_ratio)


## The world x range (px) it can ever cover (its whole motion).
func x_reach() -> Vector2:
	var x := global_position.x
	return Vector2(x - reach, x + 32.0)


func apply_time(t: float) -> void:
	if _pool == null:
		return
	var start := GardenHazard.cycle_start(t, period, phase)
	var up := on_ceiling_at(start)
	var cycle := fposmod(t / period + phase, 1.0)
	var flowed := flowed_at(t)
	var playing := advance_to(t)
	var surface := surface_y(up)
	var dir := GardenHazard.into(up)
	var warning := Timeline.steps_warning(cycle, hold_ratio, period, warning_time)
	_pool.visible = flowed > 1.0
	_pool.position = Vector2(0.0, surface)
	_pool.scale = Vector2(maxf(flowed / reach, 0.001), -dir)
	_front.visible = flowed > 1.0
	_front.position = Vector2(-flowed, surface)
	_front.scale = Vector2(1.0, -dir)
	_spring.position = Vector2(0.0, surface)
	_spring.scale = Vector2(1.0, -dir * (1.3 if warning and fmod(t * 8.0, 1.0) < 0.5 else 1.0))
	if playing and warning and not _was_warning:
		cue(&"warning", global_position + Vector2(0.0, surface))
	_was_warning = warning
	var span := flowed - HITBOX_INSET * 2.0
	if span <= HITBOX_INSET:
		_shape.position = PARKED
		return
	_box.size = Vector2(span, thickness - HITBOX_INSET)
	_shape.position = Vector2(-flowed * 0.5, surface + dir * (thickness - HITBOX_INSET) * 0.5)


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _pool == null:
		_pool = _Pool.new()
		add_child(_pool, false, Node.INTERNAL_MODE_FRONT)
		_front = _Front.new()
		add_child(_front, false, Node.INTERNAL_MODE_FRONT)
		_spring = _Spring.new()
		add_child(_spring, false, Node.INTERNAL_MODE_FRONT)
		for node: CanvasItem in [_pool, _front, _spring]:
			ink(node)
		_box = RectangleShape2D.new()
		_shape = add_hitbox(_box)
	_pool.setup(reach, thickness)
	(_front as _Front).thickness = thickness
	_front.queue_redraw()


## The ink at full flow, from the spring (x = 0) back to x = -reach, standing
## up (-y) from its surface. Scaled horizontally with the flow.
class _Pool extends Node2D:
	var reach := 384.0
	var thickness := 22.0

	func setup(r: float, th: float) -> void:
		reach = r
		thickness = th
		queue_redraw()

	func _draw() -> void:
		var top := PackedVector2Array()
		var steps := maxi(int(reach / 24.0), 4)
		for i in steps + 1:
			var x := -reach * i / steps
			top.append(Vector2(x, -thickness - (3.0 if i % 2 == 1 else 0.0)))
		var shape := top.duplicate()
		shape.append(Vector2(-reach, 2.0))
		shape.append(Vector2(0.0, 2.0))
		draw_colored_polygon(shape, Color(0.01, 0.01, 0.02))
		draw_polyline(top, GardenLook.INK_RIM, 2.0, true)
		var ripples := PackedVector2Array()
		var x := -18.0
		while x > -reach + 12.0:
			ripples.append_array([Vector2(x, -thickness * 0.5), Vector2(x - 10.0, -thickness * 0.5)])
			x -= 30.0
		draw_multiline(ripples, Color(GardenLook.INK_RIM, 0.35), 1.5)


## The rounded head of the flow.
class _Front extends Node2D:
	var thickness := 22.0

	func _draw() -> void:
		var r := thickness + 3.0
		draw_circle(Vector2(0.0, -thickness * 0.3), r * 0.62, Color(0.01, 0.01, 0.02))
		draw_arc(Vector2(0.0, -thickness * 0.3), r * 0.62, PI * 0.55, PI * 1.6, 12, GardenLook.INK_RIM, 2.0, true)


## The spring: a dark pool welling in the surface.
class _Spring extends Node2D:
	func _draw() -> void:
		var shape := PackedVector2Array()
		for i in 13:
			var a := PI + PI * i / 12.0
			shape.append(Vector2(cos(a) * 30.0, sin(a) * 12.0))
		draw_colored_polygon(shape, Color(0.01, 0.01, 0.02))
		draw_polyline(shape, GardenLook.INK_RIM, 2.0, true)
		draw_circle(Vector2(-8.0, -6.0), 3.0, GardenLook.INK_RIM)
		draw_circle(Vector2(7.0, -4.0), 2.0, GardenLook.INK_RIM)
