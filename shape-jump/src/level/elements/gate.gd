@tool
class_name Gate
extends Hazard
## PULSE GATE, SEQUENTIAL GATE and TIMED OPENING (docs/GDD.md §7).
##
## A deadly barrier across the run with one opening. The opening steps
## through [member stops] – each is (centre y, height) – holding at each for
## [member hold_time], then sliding to the next in [member move_time]:
## - one stop: a fixed window (a run of them at different heights is a
##   sequential gate);
## - stops at different heights: a pulse gate;
## - a stop of height 0: a timed opening that shuts.
## Before every move a ghost of the next opening lights up: the telegraph.
##
## Origin = the barrier's centre line at floor level. The slabs reach
## [member reach] px beyond the opening; gates draw just behind the level
## geometry, so the ground and ceilings hide the ends.

@export_range(16.0, 256.0, 1.0, "suffix:px") var width := 40.0:
	set(value):
		width = value
		_rebuild()
@export_range(64.0, 4096.0, 1.0, "suffix:px") var reach := 1200.0:
	set(value):
		reach = value
		_rebuild()
## (centre y, height) of each opening, relative to the origin.
@export var stops: PackedVector2Array = [Vector2(-96, 160)]:
	set(value):
		stops = value
		update_configuration_warnings()
		if is_node_ready():
			apply_time(0.0)
@export_range(0.05, 10.0, 0.05, "suffix:s") var hold_time := 0.9
@export_range(0.05, 5.0, 0.05, "suffix:s") var move_time := 0.25
## Starting point within the cycle, 0..1.
@export_range(0.0, 1.0, 0.01) var phase := 0.0
@export_range(0.0, 2.0, 0.05, "suffix:s") var warning_time := 0.35

var _upper_art: SlabArt
var _lower_art: SlabArt
var _upper_shape: CollisionShape2D
var _lower_shape: CollisionShape2D
## Stop the opening is about to move to (-1 when no move is coming).
var _ghost := -1
var _opening := Vector2.ZERO


func _ready() -> void:
	super()
	_rebuild()
	apply_time(0.0)


func _get_configuration_warnings() -> PackedStringArray:
	return ["Gate needs at least one stop (centre y, height)."] if stops.is_empty() else []


func get_period() -> float:
	return stops.size() * (hold_time + move_time)


## The opening at level time [param t]: x = centre y, y = height.
func opening_at(t: float) -> Vector2:
	var n := stops.size()
	if n <= 1:
		return stops[0] if n == 1 else Vector2.ZERO
	var step := hold_time + move_time
	var u := fposmod(t + phase * step * n, step * n)
	var i := mini(int(u / step), n - 1)
	var local := u - i * step
	if local < hold_time:
		return stops[i]
	return stops[i].lerp(stops[(i + 1) % n], smoothstep(0.0, 1.0, (local - hold_time) / move_time))


func apply_time(t: float) -> void:
	if _upper_art == null:
		return
	_opening = opening_at(t)
	var top_edge := _opening.x - _opening.y * 0.5
	var bottom_edge := _opening.x + _opening.y * 0.5
	_upper_art.position.y = top_edge
	_lower_art.position.y = bottom_edge
	_upper_shape.position.y = top_edge - reach * 0.5
	_lower_shape.position.y = bottom_edge + reach * 0.5
	var ghost := upcoming_stop(t)
	var flash := ghost >= 0 and fmod(t * 10.0, 1.0) < 0.5
	_upper_art.self_modulate = Color(1.6, 1.6, 1.6) if flash else Color.WHITE
	_lower_art.self_modulate = _upper_art.self_modulate
	if ghost != _ghost:
		_ghost = ghost
		queue_redraw()


## Top and bottom y of the opening as last applied (for tests and tools).
func get_opening_span() -> Vector2:
	return Vector2(_opening.x - _opening.y * 0.5, _opening.x + _opening.y * 0.5)


## The stop the opening is about to slide to, while inside the warning time.
func upcoming_stop(t: float) -> int:
	var n := stops.size()
	if n <= 1:
		return -1
	var step := hold_time + move_time
	var u := fposmod(t + phase * step * n, step * n)
	var i := mini(int(u / step), n - 1)
	var local := u - i * step
	if local < hold_time and local >= hold_time - warning_time:
		return (i + 1) % n
	return -1


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _upper_art == null:
		_upper_art = SlabArt.new()
		_lower_art = SlabArt.new()
		add_child(_upper_art, false, Node.INTERNAL_MODE_FRONT)
		add_child(_lower_art, false, Node.INTERNAL_MODE_FRONT)
		_upper_shape = add_hitbox(RectangleShape2D.new())
		_lower_shape = add_hitbox(RectangleShape2D.new())
	_upper_art.rect = Rect2(-width * 0.5, -reach, width, reach)
	_upper_art.hot_face = HazardArt.Face.BOTTOM
	_lower_art.rect = Rect2(-width * 0.5, 0.0, width, reach)
	_lower_art.hot_face = HazardArt.Face.TOP
	var hitbox := Vector2(width, reach) - Vector2.ONE * HITBOX_INSET * 2.0
	(_upper_shape.shape as RectangleShape2D).size = hitbox
	(_lower_shape.shape as RectangleShape2D).size = hitbox
	apply_time(0.0)


func _draw() -> void:
	if _ghost < 0:
		return
	# Where the opening is about to go: a dashed window with a faint light.
	var next := stops[_ghost]
	var rect := Rect2(-width * 0.5 - 6.0, next.x - next.y * 0.5, width + 12.0, next.y)
	if next.y <= 0.0:
		rect = Rect2(-width * 0.5 - 6.0, next.x - 2.0, width + 12.0, 4.0)  # About to shut.
	draw_rect(rect, Color(Palette.HAZARD_CORE, 0.12))
	Neon.dashed_rect(self, rect, Color(Palette.HAZARD_CORE, 0.9), 2.0, 8.0)
