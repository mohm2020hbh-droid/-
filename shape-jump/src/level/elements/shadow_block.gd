@tool
class_name ShadowBlock
extends Node2D
## SHADOW GAP (docs/GDD.md §7, World 02): a slab of black shadow that looks
## like ground and is not: it has no collision at all. Its tells are always
## on screen: the top lip is broken into dashes where real ground has one
## solid white line, the city shows through it, and pale static crawls
## down its face.
## Origin = top-left corner.

@export var size := Vector2(256, 640):
	set(value):
		size = value.max(Vector2(16, 16))
		queue_redraw()

var _time := 0.0


func _ready() -> void:
	if not Engine.is_editor_hint():
		_time = position.x * 0.01


func get_rect() -> Rect2:
	return Rect2(Vector2.ZERO, size)


func _process(delta: float) -> void:
	if Engine.is_editor_hint():
		return
	_time += delta
	# A faint, uneven flicker: real ground never moves.
	var k := 0.82 + 0.1 * sin(_time * 7.3) + 0.08 * sin(_time * 17.9)
	modulate = Color(1.0, 1.0, 1.0, k)


func _draw() -> void:
	# Half see-through, fading out with depth: the city shows through a
	# shadow, never through real ground.
	var fade := minf(size.y, 260.0)
	var top := Color(Palette.BLOCK_BODY, 0.62)
	var clear := Color(Palette.BLOCK_BODY, 0.18)
	draw_polygon(PackedVector2Array([Vector2.ZERO, Vector2(size.x, 0.0), Vector2(size.x, fade), Vector2(0.0, fade)]),
		PackedColorArray([top, top, clear, clear]))
	if size.y > fade:
		draw_rect(Rect2(0.0, fade, size.x, size.y - fade), clear)
	var x := 4.0
	while x < size.x - 4.0:
		draw_line(Vector2(x, 1.5), Vector2(minf(x + 12.0, size.x - 4.0), 1.5), Color(Palette.NEON, 0.6), 3.0)
		x += 24.0
	# Static: short pale ticks down the face (a fixed pattern per slab).
	var rng := RandomNumberGenerator.new()
	rng.seed = int(position.x) * 31 + int(size.x)
	var count := int(size.x * minf(size.y, 320.0) / 1400.0)
	for i in count:
		var p := Vector2(rng.randf_range(6.0, size.x - 18.0), rng.randf_range(10.0, minf(size.y, 320.0)))
		draw_line(p, p + Vector2(rng.randf_range(4.0, 14.0), 0.0), Color(Palette.NEON, rng.randf_range(0.05, 0.22)), 1.5)
