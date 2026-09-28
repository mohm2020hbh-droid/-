@tool
class_name GalaxyBlock
extends Block
## A slab of World 03's floating architecture. Same solid rectangle as
## [Block], drawn as a lump of dark space rock ([OrganicArt] ROCK: worn,
## uneven sides, strata, a few mineral glints) with a straight, lit edge on
## each face you can stand on: [member top_edge] for the ground side,
## [member bottom_edge] for the ceiling side. A block lit on both faces is an
## INVERTED WALL: a platform in one gravity, an overhang (or a wall) in the
## other.

@export var bottom_edge := false:
	set(value):
		bottom_edge = value
		_art = null
		queue_redraw()


## The rock's body: straight on every face you can stand on.
func get_art() -> OrganicArt.Shape:
	if _art == null:
		_art = OrganicArt.build(get_rect(), _art_seed, OrganicArt.Style.ROCK, HazardArt.Face.NONE, top_edge, true, 0.6,
			bottom_edge)
	return _art


func _draw() -> void:
	var art := get_art()
	if art.fillable:
		draw_colored_polygon(art.outline, Palette.BLOCK_BODY)
	else:
		draw_rect(get_rect(), Palette.BLOCK_BODY)
	# Depth: the faces you stand on are lit, the inside of the rock sinks.
	var fade := minf(size.y * 0.5, 90.0)
	var lit := Color(Palette.NEON, 0.12)
	var dark := Color(Palette.NEON, 0.0)
	var x0 := 4.0
	var x1 := size.x - 4.0
	if top_edge:
		draw_polygon(PackedVector2Array([Vector2(x0, 0), Vector2(x1, 0), Vector2(x1, fade), Vector2(x0, fade)]),
			PackedColorArray([lit, lit, dark, dark]))
	if bottom_edge:
		var y := size.y
		draw_polygon(PackedVector2Array([Vector2(x0, y), Vector2(x1, y), Vector2(x1, y - fade),
			Vector2(x0, y - fade)]), PackedColorArray([lit, lit, dark, dark]))
	# Strata and mineral glints.
	if not art.detail.is_empty():
		draw_multiline(art.detail, Color(Palette.BLOCK_FACE, 1.0), 1.5)
	if not art.glow.is_empty():
		draw_multiline(art.glow, Color(Palette.NEON_DIM, 0.45), 1.5)
	draw_polyline(art.loop, Color(Palette.NEON_DIM, 0.7), 2.0, true)
	if top_edge:
		Neon.line(self, Vector2.ZERO, Vector2(size.x, 0.0), Palette.NEON, 3.0, glow)
	if bottom_edge:
		Neon.line(self, Vector2(0.0, size.y), Vector2(size.x, size.y), Palette.NEON, 3.0, glow)
