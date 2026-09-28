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
var _seed := 0
## The picture, built once per span: the crystal emitters at both ends and
## a crooked arc of energy wound around the straight core.
var _crystals: Array[PackedVector2Array] = []
var _arc := PackedVector2Array()


func _ready() -> void:
	super()
	_seed = OrganicArt.seed_of(position, 7)
	_rebuild()


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _shape_node == null:
		_shape_node = add_hitbox(RectangleShape2D.new())
	# Between the prism centres: the beam kills, the outer halves of the prisms do not.
	(_shape_node.shape as RectangleShape2D).size = Vector2(span, maxf(thickness - HITBOX_INSET, 6.0))
	_crystals.clear()
	queue_redraw()


## Each end: a cluster of void crystal (two shards across the beam, one
## along it) where the prism was a clean diamond.
func _build_art() -> void:
	var half := span * 0.5
	_arc.clear()
	OrganicArt.crooked(_arc, Vector2(-half + PRISM_SIZE.x * 0.4, 0.0), Vector2(half - PRISM_SIZE.x * 0.4, 0.0),
		_seed, thickness * 0.3, 18.0)
	for end in 2:
		var x := -half if end == 0 else half
		var k := _seed + end * 10
		_crystals.append(OrganicArt.shard(Vector2(x, 2.0), Vector2(0.15 if end == 0 else -0.15, -1.0),
			PRISM_SIZE.y * (0.9 + 0.3 * OrganicArt.rand(k, 1)), PRISM_SIZE.x * 0.8, k + 1))
		_crystals.append(OrganicArt.shard(Vector2(x, -2.0), Vector2(-0.1 if end == 0 else 0.1, 1.0),
			PRISM_SIZE.y * (0.8 + 0.3 * OrganicArt.rand(k, 2)), PRISM_SIZE.x * 0.7, k + 2))
		_crystals.append(OrganicArt.shard(Vector2(x + (4.0 if end == 0 else -4.0), 0.0), Vector2(-1.0 if end == 0 else 1.0, 0.1),
			PRISM_SIZE.x * 0.9, PRISM_SIZE.x * 0.6, k + 3))


func _draw() -> void:
	if _crystals.is_empty():
		_build_art()
	var half := span * 0.5
	# Beam: wide soft glow, then a hot core; a crooked strand winds around it.
	draw_line(Vector2(-half, 0), Vector2(half, 0), Color(Palette.HAZARD, 0.18), thickness * 2.6)
	draw_line(Vector2(-half, 0), Vector2(half, 0), Color(Palette.HAZARD, 0.55), thickness)
	draw_multiline(_arc, Color(Palette.HAZARD_CORE, 0.45), 1.5)
	draw_line(Vector2(-half, 0), Vector2(half, 0), Palette.HAZARD_CORE, thickness * 0.3)
	for x in [-half, half]:
		Neon.soft_light(self, Vector2(x, 0), 40.0, Color(Palette.HAZARD_CORE, 0.45))
	for crystal in _crystals:
		draw_colored_polygon(crystal, Palette.HAZARD_BODY)
		Neon.polyline(self, crystal, Palette.HAZARD_CORE, 2.0, 1.0, true)
