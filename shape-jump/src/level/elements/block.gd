@tool
class_name Block
extends StaticBody2D
## Solid platform of any grid size. Origin = top-left corner, so blocks snap
## cleanly to the 64 px grid in the editor.
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
		queue_redraw()
## Glow multiplier for the neon edges.
@export_range(0.0, 2.0, 0.05) var glow := 1.0:
	set(value):
		glow = value
		queue_redraw()

const DEPTH_FADE_START := 48.0
const DEPTH_FADE_LENGTH := 220.0
const SEAM_SPACING := GameConst.TILE * 3.0

var _shape_node: CollisionShape2D


func _ready() -> void:
	collision_layer = GameConst.LAYER_WORLD
	collision_mask = 0
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
	queue_redraw()


func _draw() -> void:
	_draw_block(1.0)


## Shared by subclasses (e.g. PhaseBlock) to draw with a given presence 0..1.
func _draw_block(presence: float) -> void:
	var rect := get_rect()
	draw_rect(rect, Color(Palette.BLOCK_BODY, presence))
	# Tall slabs sink into darkness so the lower screen recedes instead of
	# reading as a flat wall.
	if size.y > DEPTH_FADE_START:
		var deep := Color(Palette.SKY_TOP.darkened(0.3), presence)
		var body := Color(Palette.BLOCK_BODY, presence)
		var y0 := DEPTH_FADE_START
		var y1 := minf(size.y, DEPTH_FADE_START + DEPTH_FADE_LENGTH)
		draw_polygon(PackedVector2Array([Vector2(0, y0), Vector2(size.x, y0), Vector2(size.x, y1), Vector2(0, y1)]),
			PackedColorArray([body, body, deep, deep]))
		if size.y > y1:
			draw_rect(Rect2(0, y1, size.x, size.y - y1), deep)
		# Faint structural seams, one every few tiles.
		var seam := Color(Palette.BLOCK_FACE, 0.5 * presence)
		var x := SEAM_SPACING
		while x < size.x - 8.0:
			draw_line(Vector2(x, 10.0), Vector2(x, y1), seam, 2.0)
			x += SEAM_SPACING
	# Faint light spill just under the lit top face gives the slab depth.
	var spill := minf(size.y, 48.0)
	var lit := Color(Palette.NEON, 0.10 * presence)
	var dark := Color(Palette.NEON, 0.0)
	draw_polygon(PackedVector2Array([Vector2(0, 0), Vector2(size.x, 0), Vector2(size.x, spill), Vector2(0, spill)]),
		PackedColorArray([lit, lit, dark, dark]))
	# Faint inner face and seams give the block some mass without detail noise.
	var face := rect.grow(-6.0)
	if face.size.x > 0.0 and face.size.y > 0.0:
		draw_rect(face, Color(Palette.BLOCK_FACE, 0.55 * presence), false, 2.0)
	var edge := Color(Palette.NEON, presence)
	var side := Color(Palette.NEON_DIM, 0.8 * presence)
	# Side edges fade into the depth below; only the top is fully lit.
	var fade_depth := minf(size.y, 160.0)
	for x in [1.0, size.x - 1.0]:
		draw_polyline_colors(
			PackedVector2Array([Vector2(x, 0), Vector2(x, fade_depth)]),
			PackedColorArray([side, Color(side, 0.0)]), 2.0, true)
	if top_edge:
		Neon.line(self, Vector2(0, 0), Vector2(size.x, 0), edge, 3.0, glow * presence)
	else:
		draw_line(Vector2(0, 0), Vector2(size.x, 0), side, 2.0, true)
	if size.y <= 160.0:
		draw_line(Vector2(0, size.y), Vector2(size.x, size.y), Color(side, 0.6 * presence), 2.0, true)
