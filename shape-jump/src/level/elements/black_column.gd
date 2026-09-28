@tool
class_name BlackColumn
extends Hazard
## BLACK COLUMN (docs/GDD.md §7, World 02): a tall black monolith that rises
## and sinks (give it an [Oscillator] child, usually the STEPS wave, which
## flashes it before each move). Rows of them, rising from the floor and
## hanging from above, open and close the spaces you jump through.
## Drawn as a monolith of ink (World 02's organic look): a brush-stroke body
## with dry-brush streaks down its length and a thick, tapered white stroke
## across each end. Origin = top-left corner.

@export var size := Vector2(64, 448):
	set(value):
		size = value.max(Vector2(16, 16))
		_rebuild()

var _shape_node: CollisionShape2D
var _seed := 0
var _art: OrganicArt.Shape
var _bands: Array[PackedVector2Array] = []


func _ready() -> void:
	super()
	_seed = OrganicArt.seed_of(position, 11)  # Where it rests: the same stroke every attempt.
	_rebuild()


func get_rect() -> Rect2:
	return Rect2(Vector2.ZERO, size)


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _shape_node == null:
		_shape_node = add_hitbox(RectangleShape2D.new())
	(_shape_node.shape as RectangleShape2D).size = size - Vector2.ONE * HITBOX_INSET * 2.0
	_shape_node.position = size * 0.5
	_art = null
	queue_redraw()


func _draw() -> void:
	if _art == null:
		_art = OrganicArt.build(get_rect(), _seed, OrganicArt.theme_style())
		_bands.clear()
		for end in 2:
			var y := 8.0 if end == 0 else size.y - 8.0
			_bands.append(OrganicArt.brush(Vector2(3.0, y), Vector2(size.x - 3.0, y), 7.0, _seed + end))
	OrganicArt.draw_hazard(self, _art, Palette.HAZARD_BODY, Palette.HAZARD, Palette.HAZARD_CORE, 0.8)
	# Its ends, where it strikes as it rises and sinks: bright strokes.
	for band in _bands:
		draw_colored_polygon(band, Color(Palette.HAZARD_CORE, 0.9))
