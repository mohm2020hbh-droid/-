class_name HazardArt
## Drawing helpers for the deadly-obstacle look (docs/GDD.md §13): a dark red
## body with diagonal hazard stripes, a red neon outline on every side and a
## hot pale edge on the face that strikes. Solid platforms light only their
## top lip, so "outlined all around and striped" always reads as deadly.

enum Face { NONE, TOP, BOTTOM, LEFT, RIGHT }

const STRIPE_SPACING := 22.0
const TOOTH := Vector2(22, 16)


## A deadly slab. [param hot_face] is the face that strikes (lit hottest).
static func slab(ci: CanvasItem, rect: Rect2, hot_face: Face = Face.NONE, glow := 1.0) -> void:
	ci.draw_rect(rect, Palette.HAZARD_BODY)
	stripes(ci, rect.grow(-3.0), STRIPE_SPACING, Palette.HAZARD_STRIPE, 3.0)
	Neon.rect_outline(ci, rect, Palette.HAZARD, 2.5, glow)
	if hot_face != Face.NONE:
		var edge := face_of(rect, hot_face)
		Neon.line(ci, edge[0], edge[1], Palette.HAZARD_CORE, 3.0, 1.3 * glow)


## Triangular teeth along [param face], pointing outward.
static func teeth(ci: CanvasItem, rect: Rect2, face: Face) -> void:
	var edge := face_of(rect, face)
	var along := edge[1] - edge[0]
	var length := along.length()
	if length < TOOTH.x:
		return
	var dir := along / length
	var out := _outward(face)
	var count := int(length / TOOTH.x)
	var start := edge[0] + dir * (length - count * TOOTH.x) * 0.5
	for i in count:
		var a := start + dir * (i * TOOTH.x)
		var b := a + dir * TOOTH.x
		var tip := (a + b) * 0.5 + out * TOOTH.y
		var tri := PackedVector2Array([a, tip, b])
		ci.draw_colored_polygon(tri, Palette.HAZARD_BODY)
		Neon.polyline(ci, tri, Palette.HAZARD_CORE, 2.0, 0.8)


## 45° stripes clipped to [param rect].
static func stripes(ci: CanvasItem, rect: Rect2, spacing: float, color: Color, width: float) -> void:
	if rect.size.x <= 0.0 or rect.size.y <= 0.0:
		return
	# Lines y = x + c for every c that crosses the rect.
	var c := ceilf((rect.position.y - rect.end.x) / spacing) * spacing
	var c_max := rect.end.y - rect.position.x
	while c <= c_max:
		var x0 := maxf(rect.position.x, rect.position.y - c)
		var x1 := minf(rect.end.x, rect.end.y - c)
		if x1 > x0:
			ci.draw_line(Vector2(x0, x0 + c), Vector2(x1, x1 + c), color, width)
		c += spacing


## The two end points of one face of [param rect].
static func face_of(rect: Rect2, face: Face) -> PackedVector2Array:
	match face:
		Face.TOP:
			return PackedVector2Array([rect.position, Vector2(rect.end.x, rect.position.y)])
		Face.BOTTOM:
			return PackedVector2Array([Vector2(rect.position.x, rect.end.y), rect.end])
		Face.LEFT:
			return PackedVector2Array([rect.position, Vector2(rect.position.x, rect.end.y)])
		Face.RIGHT:
			return PackedVector2Array([Vector2(rect.end.x, rect.position.y), rect.end])
	return PackedVector2Array([rect.position, rect.position])


## The face a motion along [param direction] leads with.
static func leading_face(direction: Vector2) -> Face:
	if direction == Vector2.ZERO:
		return Face.NONE
	if absf(direction.y) >= absf(direction.x):
		return Face.BOTTOM if direction.y > 0.0 else Face.TOP
	return Face.RIGHT if direction.x > 0.0 else Face.LEFT


static func _outward(face: Face) -> Vector2:
	match face:
		Face.TOP:
			return Vector2.UP
		Face.BOTTOM:
			return Vector2.DOWN
		Face.LEFT:
			return Vector2.LEFT
		Face.RIGHT:
			return Vector2.RIGHT
	return Vector2.ZERO
