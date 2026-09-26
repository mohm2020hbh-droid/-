@tool
class_name GalaxyBlock
extends Block
## A slab of World 03's floating architecture. Same solid rectangle as
## [Block], drawn as dark cut stone with a lit edge on each face you can
## stand on: [member top_edge] for the ground side, [member bottom_edge] for
## the ceiling side. A block lit on both faces is an INVERTED WALL: a
## platform in one gravity, an overhang (or a wall) in the other.

@export var bottom_edge := false:
	set(value):
		bottom_edge = value
		queue_redraw()

const FACET_SPACING := GameConst.TILE * 2.0


func _draw() -> void:
	var rect := get_rect()
	draw_rect(rect, Palette.BLOCK_BODY)
	# Depth: the faces you stand on are lit, the inside of the slab sinks.
	var fade := minf(size.y * 0.5, 90.0)
	var lit := Color(Palette.NEON, 0.12)
	var dark := Color(Palette.NEON, 0.0)
	if top_edge:
		draw_polygon(PackedVector2Array([Vector2.ZERO, Vector2(size.x, 0), Vector2(size.x, fade), Vector2(0, fade)]),
			PackedColorArray([lit, lit, dark, dark]))
	if bottom_edge:
		var y := size.y
		draw_polygon(PackedVector2Array([Vector2(0, y), Vector2(size.x, y), Vector2(size.x, y - fade),
			Vector2(0, y - fade)]), PackedColorArray([lit, lit, dark, dark]))
	# Faceted seams: diagonal cuts every few tiles, like old carved stone.
	var seam := Color(Palette.BLOCK_FACE, 0.9)
	var x := FACET_SPACING * 0.5
	while x < size.x - 12.0:
		var h := minf(size.y, 120.0)
		var y0 := 0.0 if top_edge or not bottom_edge else size.y - h
		draw_line(Vector2(x, y0 + 8.0), Vector2(x + 18.0, y0 + h - 8.0), seam, 2.0)
		x += FACET_SPACING
	var face := rect.grow(-6.0)
	if face.size.x > 0.0 and face.size.y > 0.0:
		draw_rect(face, Color(Palette.BLOCK_FACE, 0.6), false, 2.0)
	var side := Color(Palette.NEON_DIM, 0.7)
	for sx: float in [1.0, size.x - 1.0]:
		draw_line(Vector2(sx, 0.0), Vector2(sx, size.y), side, 2.0)
	if top_edge:
		Neon.line(self, Vector2.ZERO, Vector2(size.x, 0.0), Palette.NEON, 3.0, glow)
	else:
		draw_line(Vector2.ZERO, Vector2(size.x, 0.0), side, 2.0)
	if bottom_edge:
		Neon.line(self, Vector2(0.0, size.y), Vector2(size.x, size.y), Palette.NEON, 3.0, glow)
	else:
		draw_line(Vector2(0.0, size.y), Vector2(size.x, size.y), side, 2.0)
