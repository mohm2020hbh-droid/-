@tool
class_name Block
extends StaticBody2D
## Solid platform of any grid size. Origin = top-left corner, so blocks snap
## cleanly to the 64 px grid in the editor.
##
## Drawn in the world's organic language ([OrganicArt]): obsidian shards in
## World 01, a brush-stroke of ink in World 02. The walkable top is always a
## straight, lit line from corner to corner; the other faces stray from the
## collision rectangle by a few px at most.
##
## Use on a StaticBody2D node for static ground, or on an AnimatableBody2D
## node plus an [Oscillator] child for a moving platform (it carries the
## player correctly). The collision shape is generated from [member size].

@export var size := Vector2(256, 64):
	set(value):
		size = value.max(Vector2(8, 8))
		_rebuild()
## Draw the neon lip on the top face (the walkable side).
@export var top_edge := true:
	set(value):
		top_edge = value
		_art = null
		queue_redraw()
## Glow multiplier for the neon edges.
@export_range(0.0, 2.0, 0.05) var glow := 1.0:
	set(value):
		glow = value
		queue_redraw()

const DEPTH_FADE_START := 48.0
const DEPTH_FADE_LENGTH := 220.0

var _shape_node: CollisionShape2D
var _art: OrganicArt.Shape
var _art_seed := 0


func _ready() -> void:
	collision_layer = GameConst.LAYER_WORLD
	collision_mask = 0
	# Where it rests (before any oscillator moves it): the same shape every attempt.
	_art_seed = OrganicArt.seed_of(position, 5)
	_rebuild()


func get_rect() -> Rect2:
	return Rect2(Vector2.ZERO, size)


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _shape_node == null:
		# Generated child: not owned, so it is never saved into the scene file.
		_shape_node = CollisionShape2D.new()
		_shape_node.shape = RectangleShape2D.new()
		add_child(_shape_node, false, Node.INTERNAL_MODE_FRONT)
	(_shape_node.shape as RectangleShape2D).size = size
	_shape_node.position = size * 0.5
	_art = null
	queue_redraw()


func _draw() -> void:
	_draw_block(1.0)


## The organic body (built once per size).
func get_art() -> OrganicArt.Shape:
	if _art == null:
		_art = OrganicArt.build(get_rect(), _art_seed, OrganicArt.theme_style(), HazardArt.Face.NONE, true, true, 0.45)
	return _art


## Shared by subclasses (e.g. PhaseBlock) to draw with a given presence 0..1.
func _draw_block(presence: float) -> void:
	var rect := get_rect()
	var art := get_art()
	var body := Color(Palette.BLOCK_BODY, presence)
	if art.fillable:
		draw_colored_polygon(art.outline, body)
	else:
		draw_rect(rect, body)
	# Tall slabs sink into darkness so the lower screen recedes instead of
	# reading as a flat wall (inside the outline: the rim keeps the body's tone).
	var core := Rect2(5.0, 0.0, size.x - 10.0, size.y - 5.0)
	if size.y > DEPTH_FADE_START and core.size.x > 0.0:
		var deep := Color(Palette.SKY_TOP.darkened(0.3), presence)
		var y0 := DEPTH_FADE_START
		var y1 := minf(core.end.y, DEPTH_FADE_START + DEPTH_FADE_LENGTH)
		if y1 > y0:
			draw_polygon(PackedVector2Array([Vector2(core.position.x, y0), Vector2(core.end.x, y0),
				Vector2(core.end.x, y1), Vector2(core.position.x, y1)]), PackedColorArray([body, body, deep, deep]))
		if core.end.y > y1:
			draw_rect(Rect2(core.position.x, y1, core.size.x, core.end.y - y1), deep)
	# Facets (or brush strokes) and a few veins lit from inside.
	if not art.detail.is_empty():
		draw_multiline(art.detail, Color(Palette.BLOCK_FACE, 0.9 * presence), 1.5)
	if not art.glow.is_empty():
		var vein := Color(Palette.NEON_DIM, (0.14 if Palette.is_mono() else 0.3) * presence)
		draw_multiline(art.glow, vein, 1.5)
	# Faint light spill just under the lit top face gives the slab depth.
	var spill := minf(size.y, 48.0)
	var lit := Color(Palette.NEON, 0.10 * presence)
	var dark := Color(Palette.NEON, 0.0)
	draw_polygon(PackedVector2Array([Vector2(3, 0), Vector2(size.x - 3, 0), Vector2(size.x - 3, spill), Vector2(3, spill)]),
		PackedColorArray([lit, lit, dark, dark]))
	# The rim: lit near the top, fading into the depth below.
	var side := Color(Palette.NEON_DIM, 0.8 * presence)
	var shades := PackedColorArray()
	for p in art.loop:
		var fade := 1.0 - clampf(p.y / 160.0, 0.0, 1.0) if size.y > 160.0 else 0.75
		shades.append(Color(side, side.a * fade))
	draw_polyline_colors(art.loop, shades, 2.0, true)
	var edge := Color(Palette.NEON, presence)
	if top_edge:
		Neon.line(self, Vector2(0, 0), Vector2(size.x, 0), edge, 3.0, glow * presence)
	else:
		draw_line(Vector2(0, 0), Vector2(size.x, 0), side, 2.0, true)
