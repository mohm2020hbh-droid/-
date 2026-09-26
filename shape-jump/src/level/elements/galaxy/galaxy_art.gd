class_name GalaxyArt
## Shared look of World 03's machines (The Horrifying Galaxy): the two
## gravity colours, crystal shards and dashed guides. Everything is drawn
## once and moved by transforms; nothing here allocates per frame.

## The ground state (gravity down): deep blue / violet.
const GROUND := Color("7f8cff")
## The ceiling state (gravity up): orange / amber.
const CEILING := Color("ffad4d")
## Hazards in both states: a hot magenta that neither tint swallows.
const DANGER := Color("ff3d8b")
const DANGER_CORE := Color("ffd6ec")
const DANGER_BODY := Color("1c0718")


## The colour of a gravity direction.
static func state_color(up: bool) -> Color:
	return CEILING if up else GROUND


## A crystal shard pointing along [param direction] from [param base]:
## [param length] long, [param width] wide at the base.
static func shard(base: Vector2, direction: Vector2, length: float, width: float) -> PackedVector2Array:
	var side := direction.orthogonal() * width * 0.5
	return PackedVector2Array([base - side, base + direction * length, base + side])


## An irregular convex rock of [param radius] around the origin, fixed by
## [param seed_value] (the same shape every run).
static func rock(radius: float, seed_value: int, points := 9) -> PackedVector2Array:
	var rng := RandomNumberGenerator.new()
	rng.seed = seed_value
	var out := PackedVector2Array()
	for i in points:
		var angle := TAU * i / points + rng.randf_range(-0.18, 0.18)
		out.append(Vector2.from_angle(angle) * radius * rng.randf_range(0.82, 1.08))
	return out


static func closed(points: PackedVector2Array) -> PackedVector2Array:
	var out := points.duplicate()
	out.append(points[0])
	return out


## A dashed straight line (one draw command for all its dashes).
static func dashed(ci: CanvasItem, from: Vector2, to: Vector2, color: Color, width := 2.0, dash := 10.0) -> void:
	var length := from.distance_to(to)
	if length <= 0.0:
		return
	var step := (to - from) / length
	var segments := PackedVector2Array()
	var at := 0.0
	while at < length:
		segments.append(from + step * at)
		segments.append(from + step * minf(at + dash, length))
		at += dash * 2.0
	ci.draw_multiline(segments, color, width)


## Chevrons along a vertical span, pointing up ([param up]) or down, in one
## draw command.
static func chevrons(ci: CanvasItem, x: float, top: float, bottom: float, up: bool, color: Color,
		spacing := 48.0, size := 14.0) -> void:
	var segments := PackedVector2Array()
	chevron_segments(segments, x, top, bottom, up, spacing, size)
	if not segments.is_empty():
		ci.draw_multiline(segments, color, 3.0, true)


## Appends the line segments of [method chevrons] to [param out], to draw
## many columns with a single draw_multiline.
static func chevron_segments(out: PackedVector2Array, x: float, top: float, bottom: float, up: bool,
		spacing := 48.0, size := 14.0) -> void:
	var dir := -1.0 if up else 1.0
	var y := top + spacing * 0.5
	while y < bottom:
		var tip := Vector2(x, y + dir * size * 0.5)
		out.append_array([Vector2(x - size, y - dir * size * 0.5), tip, tip, Vector2(x + size, y - dir * size * 0.5)])
		y += spacing


## Fills and outlines several convex polygons with two draw commands (a
## triangle list and a line list) instead of two per polygon: a canvas
## polygon is a draw call of its own, and machines made of many shards
## would otherwise cost dozens.
static func polygons(ci: CanvasItem, polys: Array[PackedVector2Array], fill: Color, edge: Color, width: float) -> void:
	var points := PackedVector2Array()
	var indices := PackedInt32Array()
	var lines := PackedVector2Array()
	for poly in polys:
		var base := points.size()
		points.append_array(poly)
		for i in range(1, poly.size() - 1):
			indices.append_array([base, base + i, base + i + 1])
		for i in poly.size():
			lines.append(poly[i])
			lines.append(poly[(i + 1) % poly.size()])
	var colors := PackedColorArray()
	colors.resize(points.size())
	colors.fill(fill)
	RenderingServer.canvas_item_add_triangle_array(ci.get_canvas_item(), indices, points, colors)
	ci.draw_multiline(lines, edge, width, true)
