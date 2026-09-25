@tool
class_name PrismBeam
extends Hazard
## PRISM SWEEP (docs/GDD.md §7): a horizontal beam of light held between
## two prisms. Give it an [Oscillator] child with a vertical travel and it
## sweeps up and down across the run: low, it must be jumped; high, run
## under it; in between, only a well-timed double jump clears it. The
## oscillator's track shows the whole sweep. Origin = centre of the beam.

@export_range(64.0, 1024.0, 1.0, "suffix:px") var span := 320.0:
	set(value):
		span = value
		_rebuild()
## Drawn thickness of the beam's core; the hitbox is this minus the inset.
@export_range(8.0, 40.0, 1.0, "suffix:px") var thickness := 14.0:
	set(value):
		thickness = value
		_rebuild()

const PRISM_SIZE := Vector2(18, 26)

var _shape_node: CollisionShape2D


func _ready() -> void:
	super()
	_rebuild()


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _shape_node == null:
		_shape_node = add_hitbox(RectangleShape2D.new())
	# Between the prism centres: the beam kills, the outer halves of the prisms do not.
	(_shape_node.shape as RectangleShape2D).size = Vector2(span, maxf(thickness - HITBOX_INSET, 6.0))
	queue_redraw()


func _draw() -> void:
	var half := span * 0.5
	# Beam: wide soft glow, then a hot core.
	draw_line(Vector2(-half, 0), Vector2(half, 0), Color(Palette.HAZARD, 0.18), thickness * 2.6)
	draw_line(Vector2(-half, 0), Vector2(half, 0), Color(Palette.HAZARD, 0.55), thickness)
	draw_line(Vector2(-half, 0), Vector2(half, 0), Palette.HAZARD_CORE, thickness * 0.3)
	for x in [-half, half]:
		var prism := PackedVector2Array([
			Vector2(x, -PRISM_SIZE.y), Vector2(x + PRISM_SIZE.x * 0.5, 0),
			Vector2(x, PRISM_SIZE.y), Vector2(x - PRISM_SIZE.x * 0.5, 0)])
		Neon.soft_light(self, Vector2(x, 0), 40.0, Color(Palette.HAZARD_CORE, 0.45))
		draw_colored_polygon(prism, HazardArt.BODY)
		Neon.polyline(self, prism, Palette.HAZARD_CORE, 2.5, 1.2, true)
