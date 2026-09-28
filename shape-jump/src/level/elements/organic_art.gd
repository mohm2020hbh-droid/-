class_name OrganicArt
extends RefCounted
## Organic, world-made pictures for the obstacles and ledges of Worlds 01-03
## (docs/GDD.md §13B): nothing reads as a plain box, a placeholder or a
## generic wall any more, and each world keeps its own language.
##   CRYSTAL (World 01, the Red Void): obsidian shards grown into the shape,
##     cut facets, crimson veins lit from inside; a striking face is a row of
##     uneven thorns.
##   INK (World 02, the Monochrome Void): a brush-stroke body, its edge
##     wobbling like ink on paper, dry-brush streaks down its length; a
##     striking face is a clean, tapered stroke.
##   ROCK (World 03, the Horrifying Galaxy): a lump of dark space rock with
##     worn, lumpy edges, strata and a faint mineral glow.
## The collision never changes (a plain rectangle, as before). The picture
## is honest about it: its outline follows the rectangle within a few px
## (thorn tips land on the striking face itself), so nothing hides where it
## kills, and it never needs a particle or a shader.
##
## A [OrganicArt.Shape] is built once from a seed (the owner's position):
## the same shape on every attempt, rebuilt only when the size changes, and
## drawn with one filled polygon, one outline and one multiline of detail.

enum Style { CRYSTAL, INK, ROCK }


## Everything one outlined body needs, built once.
class Shape extends RefCounted:
	var rect := Rect2()
	## The body's outline (clockwise from the top-left).
	var outline := PackedVector2Array()
	## [member outline] closed (first point repeated), for polylines.
	var loop := PackedVector2Array()
	## The striking face's part of the outline (empty: none).
	var hot := PackedVector2Array()
	## Faint inner lines (facets, strokes, strata), as segment pairs.
	var detail := PackedVector2Array()
	## Brighter inner lines (veins, highlights), as segment pairs.
	var glow := PackedVector2Array()
	## False when the outline could not be filled (a fallback rect is drawn).
	var fillable := true


## The style of the world being played (from its palette).
static func theme_style() -> Style:
	if Palette.is_mono():
		return Style.INK
	if Palette.is_galaxy():
		return Style.ROCK
	return Style.CRYSTAL


## A deterministic value in [0, 1) for ([param seed], [param i]).
static func rand(seed: int, i: int) -> float:
	var h := (seed * 73856093) ^ (i * 19349663) ^ 0x5bd1e995
	h = (h ^ (h >> 15)) * 2246822519
	h = (h ^ (h >> 13)) * 3266489917
	h = h ^ (h >> 16)
	return float(h & 0xFFFFFF) / float(0x1000000)


## A seed from a node's resting place in the level (stable across attempts).
static func seed_of(at: Vector2, salt := 0) -> int:
	return int(roundf(at.x)) * 92821 + int(roundf(at.y)) * 68917 + salt * 7919


## Builds the body of [param rect] in [param style]. [param hot] is the face
## that strikes (HazardArt.Face); [param flat_top] keeps the top face a
## straight line (a ledge you stand on), [param flat_bottom] the bottom one
## (a ceiling you stand on upside down). [param detail] adds the inner lines;
## [param roughness] scales how far the other faces stray (ground walls you
## can run into stay closer to their line than a hazard's back).
static func build(rect: Rect2, seed: int, style: Style, hot: int = HazardArt.Face.NONE,
		flat_top := false, detail := true, roughness := 1.0, flat_bottom := false) -> Shape:
	var shape := Shape.new()
	shape.rect = rect
	var small := minf(rect.size.x, rect.size.y)
	var corners := [rect.position, Vector2(rect.end.x, rect.position.y), rect.end, Vector2(rect.position.x, rect.end.y)]
	var faces := [HazardArt.Face.TOP, HazardArt.Face.RIGHT, HazardArt.Face.BOTTOM, HazardArt.Face.LEFT]
	var counter := 0
	for side in 4:
		var a: Vector2 = corners[side]
		var b: Vector2 = corners[(side + 1) % 4]
		var face: int = faces[side]
		var along := b - a
		var length := along.length()
		var dir := along / length
		var out := Vector2(dir.y, -dir.x)  # Clockwise outline: this points out of the body.
		var chamfer := _chamfer(style, seed, side, small)
		# Corners are cut, except the ends of a flat face: a ledge's walkable
		# line keeps its full width, corner to corner.
		var start_cut := 0.0 if (flat_top and (side == 0 or side == 1)) or (flat_bottom and (side == 2 or side == 3)) \
			else chamfer
		var end_cut := 0.0 if (flat_top and (side == 0 or side == 3)) or (flat_bottom and (side == 2 or side == 1)) \
			else chamfer
		var t := start_cut
		var first := shape.outline.size()
		var start := a + dir * start_cut
		if shape.outline.is_empty() or not shape.outline[shape.outline.size() - 1].is_equal_approx(start):
			shape.outline.append(start)
		else:
			first -= 1
		var is_hot := face == hot
		var is_flat := (flat_top and side == 0) or (flat_bottom and side == 2)
		var spacing := _spacing(style, is_hot)
		var k := 0
		while true:
			var step := spacing * (0.7 + 0.6 * rand(seed, counter))
			counter += 1
			t += step
			if t > length - end_cut - spacing * 0.45:
				break
			var d := 0.0 if is_flat else _offset(style, seed, counter, k, t, is_hot, small) * (1.0 if is_hot else roughness)
			shape.outline.append(a + dir * t + out * d)
			k += 1
		var end := b - dir * end_cut
		if side < 3 or not end.is_equal_approx(shape.outline[0]):
			shape.outline.append(end)
		if is_hot:
			shape.hot = shape.outline.slice(first)
	shape.loop = shape.outline.duplicate()
	shape.loop.append(shape.outline[0])
	shape.fillable = not Geometry2D.triangulate_polygon(shape.outline).is_empty()
	if detail:
		match style:
			Style.CRYSTAL:
				_facets(shape, seed)
			Style.INK:
				_strokes(shape, seed)
			Style.ROCK:
				_strata(shape, seed)
	return shape


## Draws [param shape] as a deadly body: [param body] fill, [param edge] rim
## with a neon glow, the striking face [param core] and its inner lines.
static func draw_hazard(ci: CanvasItem, shape: Shape, body: Color, edge: Color, core: Color, glow := 1.0) -> void:
	_fill(ci, shape, body)
	if not shape.detail.is_empty():
		ci.draw_multiline(shape.detail, Color(edge, edge.a * 0.22), 1.5)
	if not shape.glow.is_empty():
		ci.draw_multiline(shape.glow, Color(core, core.a * 0.7), 1.5)
	# The rim and the striking face: one soft glow pass under a sharp core
	# each (two draw commands, where a neon line takes four).
	ci.draw_polyline(shape.loop, Color(edge, edge.a * 0.14 * glow), 9.0)
	ci.draw_polyline(shape.loop, edge, 2.5, true)
	if shape.hot.size() > 1:
		ci.draw_polyline(shape.hot, Color(core, core.a * 0.22 * glow), 10.0)
		ci.draw_polyline(shape.hot, core, 3.0, true)


## Draws [param shape] as solid ground: a quiet body, faint inner lines, and
## the side and bottom rims in [param rim] (the lit top is the owner's).
static func draw_ground(ci: CanvasItem, shape: Shape, body: Color, rim: Color, vein: Color) -> void:
	_fill(ci, shape, body)
	if not shape.detail.is_empty():
		ci.draw_multiline(shape.detail, rim, 1.5)
	if not shape.glow.is_empty():
		ci.draw_multiline(shape.glow, vein, 1.5)
	ci.draw_polyline(shape.loop, rim, 2.0, true)


static func _fill(ci: CanvasItem, shape: Shape, body: Color) -> void:
	if shape.fillable:
		ci.draw_colored_polygon(shape.outline, body)
	else:
		ci.draw_rect(shape.rect, body)


static func _chamfer(style: Style, seed: int, side: int, small: float) -> float:
	var base: float
	match style:
		Style.CRYSTAL:
			base = 4.0 + 6.0 * rand(seed, 900 + side)
		Style.INK:
			base = 3.0 + 4.0 * rand(seed, 900 + side)
		_:
			base = 7.0 + 7.0 * rand(seed, 900 + side)
	return minf(base, small * 0.22)


static func _spacing(style: Style, hot: bool) -> float:
	match style:
		Style.CRYSTAL:
			return 17.0 if hot else 21.0
		Style.INK:
			return 11.0
		_:
			return 22.0


## How far (px, + out of the body) the outline strays from the rectangle at
## this point. Always small: the picture never lies about the hitbox.
static func _offset(style: Style, seed: int, counter: int, k: int, t: float, hot: bool, small: float) -> float:
	var r := rand(seed, counter * 3 + 1)
	var limit := clampf(small / 6.0, 1.5, 9.0)
	match style:
		Style.CRYSTAL:
			if hot:
				# Uneven thorns: every other point a tip on the face, a valley between.
				return clampf(-(3.0 + 6.0 * r) if k % 2 == 0 else 0.5, -limit, 1.0)
			# Angular facets: mostly near the edge, now and then a deeper notch.
			return clampf(1.0 - 6.5 * r * r * (1.4 if k % 3 == 1 else 0.7), -limit, 1.5)
		Style.INK:
			var phase := rand(seed, 17) * TAU
			var phase2 := rand(seed, 29) * TAU
			var wobble := 2.4 * sin(t * 0.045 + phase) + 1.3 * sin(t * 0.19 + phase2) + (r - 0.5) * 1.2
			return clampf(wobble * (0.35 if hot else 1.0), -limit, 2.5)
		_:
			return clampf(-(1.5 + 5.0 * r) + 2.0 * sin(t * 0.07 + rand(seed, 3) * TAU), -limit, 1.5)


## CRYSTAL: facet lines from the rim to a few inner nodes, and veins that
## branch in from the striking face (or along the body's length).
static func _facets(shape: Shape, seed: int) -> void:
	var rect := shape.rect.grow(-6.0)
	if rect.size.x <= 4.0 or rect.size.y <= 4.0:
		return
	var long_x := rect.size.x >= rect.size.y
	var length := rect.size.x if long_x else rect.size.y
	var count := clampi(int(length / 80.0) + 1, 1, 40)
	var nodes := PackedVector2Array()
	for i in count:
		var u := (i + 0.5 + (rand(seed, 400 + i) - 0.5) * 0.6) / count
		var v := 0.3 + 0.4 * rand(seed, 500 + i)
		nodes.append(rect.position + (Vector2(u, v) if long_x else Vector2(v, u)) * rect.size)
	# Each rim point is cut toward its nearest node (every other one, for air).
	for i in range(0, shape.outline.size(), 2):
		var p := shape.outline[i]
		var best := nodes[0]
		for n in nodes:
			if n.distance_squared_to(p) < best.distance_squared_to(p):
				best = n
		if best.distance_to(p) < 110.0:
			shape.detail.append(p.lerp(best, 0.08))
			shape.detail.append(best)
	# Veins: short crooked lines lit from inside, linking the facet nodes.
	for i in nodes.size():
		var from := nodes[i]
		var to := nodes[i + 1] if i + 1 < nodes.size() else from + Vector2(rand(seed, 600 + i) - 0.5, rand(seed, 650 + i) - 0.5) * 30.0
		var mid := from.lerp(to, 0.5) + Vector2(rand(seed, 700 + i) - 0.5, rand(seed, 750 + i) - 0.5) * 18.0
		if rand(seed, 800 + i) < 0.75:
			shape.glow.append_array(PackedVector2Array([from, mid, mid, to]))
	# Thorn veins: from each thorn tip a short hot crack back into the body.
	for i in range(1, shape.hot.size() - 1, 2):
		var tip := shape.hot[i]
		var back := tip.lerp(shape.rect.get_center(), clampf(18.0 / maxf(tip.distance_to(shape.rect.get_center()), 1.0), 0.0, 0.5))
		shape.glow.append(tip)
		shape.glow.append(back)


## INK: dry-brush streaks down the body's length, broken into dashes of
## uneven length, as a brush leaves them.
static func _strokes(shape: Shape, seed: int) -> void:
	var rect := shape.rect.grow(-5.0)
	if rect.size.x <= 4.0 or rect.size.y <= 4.0:
		return
	var long_x := rect.size.x >= rect.size.y
	var across := rect.size.y if long_x else rect.size.x
	var length := rect.size.x if long_x else rect.size.y
	var lanes := clampi(int(across / 9.0), 1, 6)
	for lane in lanes:
		var v := (lane + 0.5 + (rand(seed, 40 + lane) - 0.5) * 0.5) / lanes
		var u := rand(seed, 60 + lane) * 30.0
		var i := 0
		while u < length - 6.0 and i < 60:
			var dash := 24.0 + 90.0 * rand(seed, 1000 + lane * 97 + i)
			var end := minf(u + dash, length - 4.0)
			var drift := (rand(seed, 2000 + lane * 97 + i) - 0.5) * 3.0
			var p0 := Vector2(u, v * across) if long_x else Vector2(v * across, u)
			var p1 := Vector2(end, v * across + drift) if long_x else Vector2(v * across + drift, end)
			(shape.glow if lane == 0 and i % 3 == 0 else shape.detail).append_array(
				PackedVector2Array([rect.position + p0, rect.position + p1]))
			u = end + 10.0 + 40.0 * rand(seed, 3000 + lane * 97 + i)
			i += 1


## ROCK: strata across the body and a few pits.
static func _strata(shape: Shape, seed: int) -> void:
	var rect := shape.rect.grow(-7.0)
	if rect.size.x <= 6.0 or rect.size.y <= 6.0:
		return
	var layers := clampi(int(rect.size.y / 22.0), 1, 12)
	for layer in layers:
		var y := rect.position.y + rect.size.y * (layer + 0.5 + (rand(seed, 80 + layer) - 0.5) * 0.4) / layers
		var x := rect.position.x + rand(seed, 90 + layer) * rect.size.x * 0.3
		var end := rect.end.x - rand(seed, 95 + layer) * rect.size.x * 0.3
		var prev := Vector2(x, y)
		var steps := clampi(int((end - x) / 26.0), 1, 30)
		for s in steps:
			var nx := x + (end - x) * (s + 1) / steps
			var next := Vector2(nx, y + (rand(seed, 5000 + layer * 31 + s) - 0.5) * 5.0)
			shape.detail.append_array(PackedVector2Array([prev, next]))
			prev = next
	var pits := clampi(int(rect.get_area() / 5000.0), 0, 10)
	for i in pits:
		var c := rect.position + Vector2(rand(seed, 6000 + i), rand(seed, 6100 + i)) * rect.size
		var r := 3.0 + 5.0 * rand(seed, 6200 + i)
		# A crater seen from the side: a shallow bowl.
		var bowl := PackedVector2Array([c + Vector2(-r, 0), c + Vector2(-r * 0.45, r * 0.5), c + Vector2(r * 0.45, r * 0.5),
			c + Vector2(r, 0)])
		shape.glow.append_array(PackedVector2Array([bowl[0], bowl[1], bowl[1], bowl[2], bowl[2], bowl[3]]))


## Appends to [param out] (as segment pairs) a crooked line from [param a]
## to [param b]: bends every [param step] px, straying up to [param amp] px
## sideways (an energy strand, a crack, a vein).
static func crooked(out: PackedVector2Array, a: Vector2, b: Vector2, seed: int, amp: float, step := 14.0) -> void:
	var along := b - a
	var length := along.length()
	if length <= 0.0:
		return
	var side := Vector2(-along.y, along.x) / length
	var count := maxi(int(length / step), 1)
	var prev := a
	for i in range(1, count + 1):
		var p := a + along * (float(i) / count)
		if i < count:
			p += side * (rand(seed, 7000 + i) * 2.0 - 1.0) * amp
		out.append(prev)
		out.append(p)
		prev = p


## [param points] (a convex outline) with every edge broken once or twice
## and pulled in by up to [param inward] px: a cut stone, not a clean polygon.
static func roughen(points: PackedVector2Array, seed: int, inward: float) -> PackedVector2Array:
	var result := PackedVector2Array()
	var centre := Vector2.ZERO
	for p in points:
		centre += p
	centre /= maxf(points.size(), 1)
	for i in points.size():
		var a := points[i]
		var b := points[(i + 1) % points.size()]
		result.append(a)
		var breaks := 1 + int(rand(seed, 300 + i) * 2.0)
		for k in breaks:
			var u := (k + 0.3 + 0.4 * rand(seed, 320 + i * 3 + k)) / breaks
			var p := a.lerp(b, u)
			result.append(p + (centre - p).normalized() * inward * (0.35 + 0.65 * rand(seed, 340 + i * 3 + k)))
	return result


## A crystal shard growing from [param base] along [param dir]: an uneven,
## faceted blade [param length] long and [param width] wide.
static func shard(base: Vector2, dir: Vector2, length: float, width: float, seed: int) -> PackedVector2Array:
	var d := dir.normalized()
	var n := Vector2(-d.y, d.x)
	var skew := (rand(seed, 1) - 0.5) * width * 0.5
	var shoulder := 0.45 + 0.3 * rand(seed, 2)
	return PackedVector2Array([
		base - n * width * 0.5,
		base + d * length * shoulder - n * width * (0.35 + 0.2 * rand(seed, 3)),
		base + d * length + n * skew,
		base + d * length * (shoulder + 0.12) + n * width * (0.3 + 0.2 * rand(seed, 4)),
		base + n * width * 0.5])


## A tapered brush stroke from [param a] to [param b], [param width] wide at
## its fullest, thin at both ends, its edges a little uneven (ink on paper).
static func brush(a: Vector2, b: Vector2, width: float, seed: int, steps := 8) -> PackedVector2Array:
	var along := b - a
	var length := along.length()
	if length <= 0.0:
		return PackedVector2Array()
	var n := Vector2(-along.y, along.x) / length
	var upper := PackedVector2Array()
	var lower := PackedVector2Array()
	for i in steps + 1:
		var u := float(i) / steps
		var w := width * 0.5 * (0.3 + 0.7 * sin(PI * u))
		var wobble := (rand(seed, 8000 + i) - 0.5) * width * 0.25
		var p := a + along * u
		upper.append(p + n * (w + wobble))
		lower.append(p - n * (w - wobble))
	lower.reverse()
	upper.append_array(lower)
	return upper


## An ink blot of [param radius] around [param centre]: a circle whose rim
## spreads unevenly, as ink does on paper.
static func blot(centre: Vector2, radius: float, seed: int, points := 12) -> PackedVector2Array:
	var result := PackedVector2Array()
	for i in points:
		var r := radius * (0.86 + 0.24 * rand(seed, 8500 + i))
		result.append(centre + Vector2.from_angle(TAU * i / points) * r)
	return result
