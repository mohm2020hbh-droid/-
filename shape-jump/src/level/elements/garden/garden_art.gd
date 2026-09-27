class_name GardenArt
## Shared drawing of World 04, THE INVERTED GARDEN: organic shapes (roots,
## leaves, petals, stones, brush strokes) in ink. Everything is drawn once in
## neutral base colours and tinted per role by [GardenLook]; the helpers batch
## many shapes into one triangle list and one line list, so a whole root
## cluster or flower costs two draw calls. Nothing here allocates per frame.
##
## Shapes are generated from fixed seeds: the same element always looks the
## same, run after run.


## Appends a tapered, gently curving stroke from [param from] along
## [param direction] to the batch: [param length] long, [param width] at the
## base, to a point at the tip. [param bend] curls it sideways (px at the tip).
static func tendril(batch: Batch, from: Vector2, direction: Vector2, length: float, width: float, bend: float,
		segments := 7) -> void:
	var dir := direction.normalized()
	var side := dir.orthogonal()
	var left := PackedVector2Array()
	var right := PackedVector2Array()
	for i in segments + 1:
		var k := float(i) / segments
		var centre := from + dir * length * k + side * bend * k * k
		var half := width * 0.5 * (1.0 - k) + 0.6
		left.append(centre + side * half)
		right.append(centre - side * half)
	batch.strip(left, right)


## A lens-shaped leaf around [param centre], [param length] long, pointing
## along [param angle].
static func leaf(centre: Vector2, length: float, width: float, angle: float, points := 10) -> PackedVector2Array:
	var out := PackedVector2Array()
	var axis := Vector2.from_angle(angle)
	var side := axis.orthogonal()
	for i in points:
		var a := TAU * i / points
		out.append(centre + axis * cos(a) * length * 0.5 + side * sin(a) * width * 0.5 * absf(sin(a)) ** 0.2)
	return out


## A rounded, slightly irregular stone of [param radius] (convex).
static func stone(centre: Vector2, radius: float, seed_value: int, points := 11) -> PackedVector2Array:
	var rng := RandomNumberGenerator.new()
	rng.seed = seed_value
	var out := PackedVector2Array()
	for i in points:
		var a := TAU * i / points + rng.randf_range(-0.12, 0.12)
		out.append(centre + Vector2.from_angle(a) * radius * rng.randf_range(0.9, 1.05))
	return out


## A soft cloud-like blob made of overlapping discs (for [method Batch.discs]).
static func puffs(centre: Vector2, radius: float, seed_value: int, count := 5) -> Array[Vector3]:
	var rng := RandomNumberGenerator.new()
	rng.seed = seed_value
	var out: Array[Vector3] = []
	for i in count:
		var off := Vector2(rng.randf_range(-1.0, 1.0) * radius, rng.randf_range(-0.35, 0.3) * radius)
		out.append(Vector3(centre.x + off.x, centre.y + off.y, radius * rng.randf_range(0.45, 0.8)))
	return out


static func closed(points: PackedVector2Array) -> PackedVector2Array:
	var out := points.duplicate()
	out.append(points[0])
	return out


## Collects triangles and lines, then draws them with two calls.
class Batch:
	var points := PackedVector2Array()
	var colors := PackedColorArray()
	var indices := PackedInt32Array()
	var lines := PackedVector2Array()
	var fill := Color.WHITE

	func _init(fill_color := Color.WHITE) -> void:
		fill = fill_color

	## A convex polygon (fan), outlined into the line list if [param outline].
	func poly(shape: PackedVector2Array, outline := true, color := Color(0, 0, 0, 0)) -> void:
		var base := points.size()
		var c := fill if color.a == 0.0 else color
		for p in shape:
			points.append(p)
			colors.append(c)
		for i in range(1, shape.size() - 1):
			indices.append_array([base, base + i, base + i + 1])
		if outline:
			for i in shape.size():
				lines.append(shape[i])
				lines.append(shape[(i + 1) % shape.size()])

	## A strip between two polylines (a tapered stroke), outlined on both sides.
	func strip(left: PackedVector2Array, right: PackedVector2Array, outline := true) -> void:
		var base := points.size()
		var n := left.size()
		for i in n:
			points.append(left[i])
			points.append(right[i])
			colors.append(fill)
			colors.append(fill)
		for i in n - 1:
			var a := base + i * 2
			indices.append_array([a, a + 1, a + 2, a + 1, a + 3, a + 2])
		if outline:
			for i in n - 1:
				lines.append_array([left[i], left[i + 1], right[i], right[i + 1]])

	## Discs (x, y, radius) as polygons, without outlines (soft masses).
	func discs(list: Array[Vector3], sides := 14, color := Color(0, 0, 0, 0)) -> void:
		for d in list:
			var shape := PackedVector2Array()
			for i in sides:
				shape.append(Vector2(d.x, d.y) + Vector2.from_angle(TAU * i / sides) * d.z)
			poly(shape, false, color)

	func draw(ci: CanvasItem, rim: Color, width := 2.0) -> void:
		if not indices.is_empty():
			RenderingServer.canvas_item_add_triangle_array(ci.get_canvas_item(), indices, points, colors)
		if not lines.is_empty() and rim.a > 0.0:
			ci.draw_multiline(lines, rim, width, true)
