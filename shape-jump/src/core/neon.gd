class_name Neon
## Stateless drawing helpers for glowing neon edges.
## Glow is faked with a few wide, translucent strokes under a sharp core line,
## which is far cheaper on mobile GPUs than a post-process bloom.

## Relative width and alpha of each glow pass, widest first.
const _GLOW_PASSES: Array[Vector2] = [Vector2(7.0, 0.05), Vector2(4.0, 0.1), Vector2(2.2, 0.22)]


static func polyline(ci: CanvasItem, points: PackedVector2Array, color: Color, width: float,
		glow: float = 1.0, closed: bool = false) -> void:
	var pts := points
	if closed and points.size() > 2:
		# Start and end halfway along the first edge so the butt caps meet on a
		# straight segment and every corner gets a proper joint.
		var mid := (points[0] + points[1]) * 0.5
		pts = PackedVector2Array([mid])
		pts.append_array(points.slice(1))
		pts.append(points[0])
		pts.append(mid)
	if glow > 0.0:
		for pass_info in _GLOW_PASSES:
			ci.draw_polyline(pts, Color(color, color.a * pass_info.y * glow), width * pass_info.x, true)
	ci.draw_polyline(pts, color, width, true)


static func rect_outline(ci: CanvasItem, rect: Rect2, color: Color, width: float, glow: float = 1.0) -> void:
	if glow > 0.0:
		for pass_info in _GLOW_PASSES:
			ci.draw_rect(rect, Color(color, color.a * pass_info.y * glow), false, width * pass_info.x)
	ci.draw_rect(rect, color, false, width)


static func line(ci: CanvasItem, from: Vector2, to: Vector2, color: Color, width: float, glow: float = 1.0) -> void:
	if glow > 0.0:
		for pass_info in _GLOW_PASSES:
			ci.draw_line(from, to, Color(color, color.a * pass_info.y * glow), width * pass_info.x, true)
	ci.draw_line(from, to, color, width, true)


static var _light_texture: GradientTexture2D


## Soft round light, e.g. behind the player core or a saw hub. One textured
## quad with a smooth radial falloff (no banding, one draw call).
static func soft_light(ci: CanvasItem, center: Vector2, radius: float, color: Color) -> void:
	var extent := Vector2(radius, radius)
	ci.draw_texture_rect(light_texture(), Rect2(center - extent, extent * 2.0), false, color)


static func light_texture() -> GradientTexture2D:
	if _light_texture == null:
		var gradient := Gradient.new()
		gradient.offsets = PackedFloat32Array([0.0, 0.25, 0.6, 1.0])
		gradient.colors = PackedColorArray([
			Color(1, 1, 1, 1), Color(1, 1, 1, 0.45), Color(1, 1, 1, 0.1), Color(1, 1, 1, 0)])
		_light_texture = GradientTexture2D.new()
		_light_texture.gradient = gradient
		_light_texture.fill = GradientTexture2D.FILL_RADIAL
		_light_texture.fill_from = Vector2(0.5, 0.5)
		_light_texture.fill_to = Vector2(1.0, 0.5)
		_light_texture.width = 128
		_light_texture.height = 128
	return _light_texture


## Dashed outline, used for phase blocks while they are absent.
static func dashed_rect(ci: CanvasItem, rect: Rect2, color: Color, width: float, dash: float = 10.0) -> void:
	var corners := [rect.position, Vector2(rect.end.x, rect.position.y), rect.end, Vector2(rect.position.x, rect.end.y)]
	for i in 4:
		ci.draw_dashed_line(corners[i], corners[(i + 1) % 4], color, width, dash)
