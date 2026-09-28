@tool
class_name GardenBlock
extends Block
## Terrain of World 04: a floating island of the inverted garden. The same
## solid rectangle as [Block] (so it carries the player, and a moving one on
## an AnimatableBody2D too); drawn as earth with grass and blossoms on each
## face you can stand on ([member top_edge] for the ground side,
## [member bottom_edge] for the ceiling side) and a ragged fringe of soil and
## roots on its back.
##
## [member latchable] false makes it SLICK STONE: wet, glazed rock an attach
## cannot hold (it streams with water and grows nothing), so a TAP TAP
## toward it fails. You can still run on it.
##
## Colours come from [GardenLook]: the body is the soil role, the faces the
## bloom role (a child node), slick glaze the water role.

@export var bottom_edge := false:
	set(value):
		bottom_edge = value
		queue_redraw()
		if _face:
			_face.queue_redraw()
## False: slick stone, not a surface an attach can hold.
@export var latchable := true:
	set(value):
		latchable = value
		queue_redraw()
		if _face:
			_face.queue_redraw()

const FRINGE := 26.0

var _face: _Face


func _ready() -> void:
	super()
	if _face == null:
		_face = _Face.new()
		_face.block = self
		add_child(_face, false, Node.INTERNAL_MODE_FRONT)
	GardenLook.tint(self, &"garden_soil")
	GardenLook.tint(_face, &"garden_water" if not latchable else &"garden_bloom")


func _rebuild() -> void:
	super()
	if _face:
		_face.queue_redraw()


func _draw() -> void:
	var rng := RandomNumberGenerator.new()
	rng.seed = int(absf(position.x) * 3.0 + absf(position.y))
	var w := size.x
	var h := size.y
	var body := Color(1.0, 1.0, 1.0) if latchable else Color(0.72, 0.74, 0.8)
	# An island, not a box: the corners on its back (the face away from the
	# corridor, never reached) are worn away in uneven cuts; the face you
	# stand on keeps its full width, corner to corner.
	var cut := minf(minf(h * 0.35, w * 0.2), 18.0)
	var back_top := not top_edge and cut > 3.0     # A ceiling island: its back is the top.
	var back_bottom := not bottom_edge and cut > 3.0
	var outline := PackedVector2Array()
	if back_top:
		outline.append_array([Vector2(0.0, cut), Vector2(cut * 0.45, cut * 0.35), Vector2(cut, 0.0)])
		outline.append_array([Vector2(w - cut, 0.0), Vector2(w - cut * 0.4, cut * 0.4), Vector2(w, cut)])
	else:
		outline.append_array([Vector2(0.0, 0.0), Vector2(w, 0.0)])
	if back_bottom:
		outline.append_array([Vector2(w, h - cut), Vector2(w - cut * 0.35, h - cut * 0.45), Vector2(w - cut, h)])
		outline.append_array([Vector2(cut, h), Vector2(cut * 0.5, h - cut * 0.3), Vector2(0.0, h - cut)])
	else:
		outline.append_array([Vector2(w, h), Vector2(0.0, h)])
	draw_colored_polygon(outline, body)
	# Strata: soft darker bands inside the island.
	var strata := PackedVector2Array()
	for i in 3:
		var y := h * (0.3 + 0.22 * i)
		if y > 12.0 and y < h - 12.0:
			var x := 0.0
			while x < w:
				var nx := minf(x + 32.0, w)
				strata.append_array([Vector2(x, y + sin(x * 0.03 + i) * 5.0), Vector2(nx, y + sin(nx * 0.03 + i) * 5.0)])
				x = nx
	if not strata.is_empty():
		draw_multiline(strata, Color(0.0, 0.0, 0.0, 0.1), 6.0)
	# Pebbles and roots in the earth (or, on slick stone, sheen).
	if latchable:
		var marks := PackedVector2Array()
		for i in int(w * h / 9000.0) + 2:
			var p := Vector2(rng.randf_range(6.0, w - 6.0), rng.randf_range(10.0, h - 10.0))
			marks.append_array([p, p + Vector2(rng.randf_range(-10.0, 10.0), rng.randf_range(4.0, 12.0))])
		draw_multiline(marks, Color(0.0, 0.0, 0.0, 0.16), 2.0)
	else:
		# Diagonal glaze lines (y = x0 - x), clipped to the block.
		var sheen := PackedVector2Array()
		var x0 := 20.0
		while x0 < w + h:
			var from := Vector2(minf(x0, w), x0 - minf(x0, w))
			var to := Vector2(maxf(x0 - h, 0.0), x0 - maxf(x0 - h, 0.0))
			if from.y <= h and to.y >= 0.0:
				sheen.append_array([from, to])
			x0 += 46.0
		draw_multiline(sheen, Color(1.0, 1.0, 1.0, 0.35), 3.0)
	# The fringe on the back faces (away from the corridor): earth and roots
	# hanging off the underside of a ground island, reaching up off the top
	# of a ceiling island. Outside the collision box, on the side you never
	# reach.
	if not bottom_edge and h > 20.0:
		_fringe(rng, h, 1.0, w, body)
	if not top_edge and h > 20.0:
		_fringe(rng, 0.0, -1.0, w, body)
	draw_multiline(PackedVector2Array([Vector2(1.0, 0.0), Vector2(1.0, h), Vector2(w - 1.0, 0.0), Vector2(w - 1.0, h)]),
		Color(0.0, 0.0, 0.0, 0.22), 2.0)


func _fringe(rng: RandomNumberGenerator, y: float, out: float, w: float, body: Color) -> void:
	var edge := PackedVector2Array([Vector2(0.0, y)])
	var x := 0.0
	while x < w:
		x = minf(x + rng.randf_range(18.0, 34.0), w)
		edge.append(Vector2(x, y + out * rng.randf_range(4.0, FRINGE)))
	edge.append(Vector2(w, y))
	draw_colored_polygon(edge, body)
	var roots := PackedVector2Array()
	for i in int(w / 70.0) + 1:
		var rx := rng.randf_range(8.0, w - 8.0)
		roots.append_array([Vector2(rx, y), Vector2(rx + rng.randf_range(-12.0, 12.0), y + out * rng.randf_range(30.0, 64.0))])
	draw_multiline(roots, Color(body, 0.8), 3.0, true)


## The faces you stand on: grass and small blossoms (or, on slick stone, a
## wet glaze with drips). Drawn once, in its own colour role.
class _Face extends Node2D:
	var block: GardenBlock

	func _draw() -> void:
		if block == null:
			return
		var rng := RandomNumberGenerator.new()
		rng.seed = int(absf(block.position.x)) + 3
		for face: Array in [[block.top_edge, 0.0, -1.0], [block.bottom_edge, block.size.y, 1.0]]:
			if face[0]:
				_face(rng, face[1], face[2])

	func _face(rng: RandomNumberGenerator, y: float, out: float) -> void:
		var w := block.size.x
		if not block.latchable:
			draw_line(Vector2(0.0, y), Vector2(w, y), Color.WHITE, 4.0)
			var drips := PackedVector2Array()
			var x := 10.0
			while x < w - 6.0:
				drips.append_array([Vector2(x, y), Vector2(x, y - out * rng.randf_range(8.0, 22.0))])
				x += rng.randf_range(22.0, 40.0)
			draw_multiline(drips, Color(1.0, 1.0, 1.0, 0.8), 2.5)
			return
		# A clean line on the surface itself (the edge you land on).
		draw_line(Vector2(0.0, y), Vector2(w, y), Color.WHITE, 3.0)
		var blades := PackedVector2Array()
		var x := 3.0
		while x < w - 3.0:
			var tall := rng.randf_range(5.0, 11.0)
			var lean := rng.randf_range(-4.0, 4.0)
			blades.append_array([Vector2(x, y), Vector2(x + lean, y + out * tall)])
			x += rng.randf_range(5.0, 9.0)
		draw_multiline(blades, Color(1.0, 1.0, 1.0, 0.9), 2.0)
		# Blossoms every few tiles: five petals round a dark heart (one batch).
		var petals := GardenArt.Batch.new(Color.WHITE)
		var hearts: Array[Vector3] = []
		x = rng.randf_range(20.0, 90.0)
		while x < w - 12.0:
			var c := Vector2(x, y + out * 10.0)
			for i in 5:
				var p := c + Vector2.from_angle(TAU * i / 5.0) * 4.0
				petals.discs([Vector3(p.x, p.y, 2.6)], 6)
			hearts.append(Vector3(c.x, c.y, 2.0))
			x += rng.randf_range(90.0, 200.0)
		petals.discs(hearts, 6, Color(0.0, 0.0, 0.0, 0.35))
		petals.draw(self, Color(0, 0, 0, 0))
