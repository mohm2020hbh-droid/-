@tool
class_name PhaseBlock
extends Block
## A platform that is solid for part of a fixed cycle (docs/GDD.md §7).
## It flickers during [member warning_time] before vanishing and is drawn as
## a dashed outline while absent. It never re-solidifies inside the player.

## Full cycle length.
@export_range(0.4, 20.0, 0.05, "suffix:s") var period := 2.4
## Fraction of the cycle during which the block is solid.
@export_range(0.1, 0.9, 0.01) var solid_ratio := 0.55
## Starting point within the cycle, 0..1.
@export_range(0.0, 1.0, 0.01) var phase := 0.0
@export_range(0.0, 1.0, 0.05, "suffix:s") var warning_time := 0.4

var _solid := true
var _time_to_vanish := INF
var _drawn_presence := -1.0
var _query: PhysicsShapeQueryParameters2D


func _ready() -> void:
	super()
	if not Engine.is_editor_hint():
		Level.join(self)


func _enter_tree() -> void:
	if is_node_ready() and not Engine.is_editor_hint():
		Level.join(self)  # Re-entering after a reparent.


func _exit_tree() -> void:
	if not Engine.is_editor_hint():
		Level.leave(self)


func is_solid_at(t: float) -> bool:
	return fposmod(t / period + phase, 1.0) < solid_ratio


func is_solid() -> bool:
	return _solid


func apply_time(t: float) -> void:
	var cycle := fposmod(t / period + phase, 1.0)
	var solid := cycle < solid_ratio
	if solid and not _solid and _overlaps_player():
		solid = false  # Wait until the player is clear instead of trapping it.
	_set_solid(solid)
	_time_to_vanish = (solid_ratio - cycle) * period if solid else INF
	# Redraw only when the picture changes (appear, vanish, flicker frames).
	var presence := _presence() if solid else 0.0
	if presence != _drawn_presence:
		_drawn_presence = presence
		queue_redraw()


func _set_solid(value: bool) -> void:
	if value == _solid:
		return
	_solid = value
	_shape_node.disabled = not value


func _overlaps_player() -> bool:
	if _query == null:
		_query = PhysicsShapeQueryParameters2D.new()
		_query.shape = _shape_node.shape
		_query.collision_mask = GameConst.LAYER_PLAYER
	_query.transform = _shape_node.global_transform
	return not get_world_2d().direct_space_state.intersect_shape(_query, 1).is_empty()


## 1 when steady, flickering between 1 and 0.35 just before vanishing.
func _presence() -> float:
	if _time_to_vanish < warning_time and sin(_time_to_vanish * 70.0) > 0.0:
		return 0.35
	return 1.0


func _draw() -> void:
	if _solid:
		_draw_block(_presence())
	else:
		Neon.dashed_rect(self, get_rect(), Color(Palette.NEON_DIM, 0.75), 2.0, 12.0)
