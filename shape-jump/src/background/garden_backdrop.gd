extends Node2D
## World 04's background and look: THE INVERTED GARDEN (docs/GDD.md §9D).
##
## A living painted landscape around the corridor of floating islands: a sky,
## a sun, clouds, far hills with trees, a lake with waterfalls pouring into
## it, flights of birds, light fog and drifting leaves; each layer with its
## own parallax and a horizontal wrap. Everything is drawn once in neutral
## tones (white and greys) and coloured through modulate.
##
## It is also the world's look controller. Each latch turns the world over:
##   GROUND  (the ground is the floor):  a BLUE + WHITE world, sky above;
##   CEILING (the ceiling is the floor): a YELLOW + BLACK world, the whole
##           landscape hanging upside down (the sky below, the lake above,
##           trees growing down, waterfalls pouring up, leaves falling up).
## Over the latch's transition the landscape turns over its horizon (a
## vertical mirror: the run still reads left to right, nothing is mirrored
## sideways), every colour crosses from one state to the other (sky, clouds,
## hills, water, light, fog, particles, the level's terrain, obstacles and
## flora through [GardenLook]), a ring of the new colour breaks out of the
## player and a soft ripple runs over the screen. No flash.
##
## Costs: ~15 canvas items drawn once plus chunked wrap layers; per frame only
## transforms and modulates change (the sky's gradient and the waterfalls'
## water redraw, a few quads). No shaders, no render targets, no textures.

const GROUND_STATE := {
	"sky": [Color("3a86e8"), Color("8cc2f7"), Color("e3f2ff")],
	"sun": Color(1.0, 1.0, 1.0, 0.95), "cloud": Color(1.0, 1.0, 1.0, 0.92),
	"far": Color(0.62, 0.77, 0.96), "mid": Color(0.42, 0.62, 0.93),
	"water": Color(0.55, 0.78, 1.0), "fog": Color(1.0, 1.0, 1.0, 0.35),
	"birds": Color(0.2, 0.36, 0.72), "leaves": Color(1.0, 1.0, 1.0, 0.75),
}
const CEILING_STATE := {
	"sky": [Color("ffb800"), Color("ffd84a"), Color("fff1b0")],
	"sun": Color(0.05, 0.04, 0.02, 0.95), "cloud": Color(0.08, 0.06, 0.02, 0.85),
	"far": Color(0.96, 0.70, 0.10), "mid": Color(0.86, 0.56, 0.04),
	"water": Color(0.98, 0.66, 0.02), "fog": Color(1.0, 0.90, 0.45, 0.35),
	"birds": Color(0.05, 0.04, 0.02), "leaves": Color(0.12, 0.09, 0.03, 0.8),
}

## Parallax of each layer (0 = fixed to the sky, 1 = the world) and its wrap.
const LAYERS := [
	["_Sun", 0.0, 0.0, "sun"], ["_Clouds", 0.04, 4096.0, "cloud"], ["_FarHills", 0.1, 3584.0, "far"],
	["_Lake", 0.16, 3072.0, "water"], ["_Fog", 0.0, 0.0, "fog"], ["_MidGarden", 0.3, 3072.0, "mid"],
	["_Birds", 0.22, 5120.0, "birds"],
]

var _session: Node
var _gravity: GravityState
var _camera: Camera2D
var _pivot: Node2D
var _sky: _Sky
var _layers: Array[Node2D] = []
var _factors: Array[float] = []
var _wraps: Array[float] = []
var _burst: _Burst
var _ripple: _Ripple
var _leaves: CPUParticles2D
## 0 = ground colours, 1 = ceiling colours.
var _blend := 0.0
var _blend_from := 0.0
var _blend_to := 0.0
var _time := 0.0


func _ready() -> void:
	z_index = -10
	_sky = _Sky.new()
	add_child(_sky)
	# Everything but the sky hangs from a pivot at the view's centre, which
	# turns the landscape over its horizon (scale.y 1 -> -1) on a latch.
	_pivot = Node2D.new()
	add_child(_pivot)
	for spec: Array in LAYERS:
		var layer := _make_layer(spec[0])
		layer.set_meta(&"tint", spec[3])
		_pivot.add_child(layer)
		_layers.append(layer)
		_factors.append(spec[1])
		_wraps.append(spec[2])
	_leaves = _make_leaves()
	add_child(_leaves)
	_burst = _Burst.new()
	add_child(_burst)
	var overlay := CanvasLayer.new()
	overlay.layer = 3
	add_child(overlay)
	_ripple = _Ripple.new()
	overlay.add_child(_ripple)
	_apply_blend(0.0)


## Called by the game session when this world starts: the look follows the
## session's gravity (the player's latches) and its levels.
func attach(session: Node) -> void:
	_session = session
	_gravity = session.gravity
	if not _gravity.flipped.is_connected(_on_gravity_flipped):
		_gravity.flipped.connect(_on_gravity_flipped)
	if not session.level_loaded.is_connected(_on_level_loaded):
		session.level_loaded.connect(_on_level_loaded)
	_on_gravity_flipped(_gravity.up, true)


func _exit_tree() -> void:
	if _gravity and _gravity.flipped.is_connected(_on_gravity_flipped):
		_gravity.flipped.disconnect(_on_gravity_flipped)
	if _session and _session.level_loaded.is_connected(_on_level_loaded):
		_session.level_loaded.disconnect(_on_level_loaded)
	GardenLook.blend = 0.0


func _process(delta: float) -> void:
	_time += delta
	if _camera == null or not is_instance_valid(_camera):
		_camera = get_viewport().get_camera_2d()
		if _camera == null:
			return
	var center := _camera.get_screen_center_position()
	_sky.position = center
	_pivot.position = center
	_leaves.position = center
	for i in _layers.size():
		var f := _factors[i]
		var layer := _layers[i]
		if _wraps[i] > 0.0:
			# Content repeats every wrap width; slide it at the layer's rate
			# (x only: vertically the landscape is fixed to the view, so its
			# horizon stays put while the corridor is framed).
			layer.position = Vector2(-fposmod(center.x * f, _wraps[i]) - _wraps[i], 0.0)
		else:
			layer.position = Vector2.ZERO
	if _gravity and _gravity.phase == GravityState.Phase.FLIPPING:
		var k := smoothstep(0.0, 1.0, _gravity.transition_progress())
		_apply_blend(lerpf(_blend_from, _blend_to, k))
	elif _blend != _blend_to:
		_apply_blend(_blend_to)


func _on_level_loaded() -> void:
	if _session and _session.level:
		GardenLook.dress_level(_session.level)
	_apply_blend(_blend, true)


func _on_gravity_flipped(up: bool, instant: bool) -> void:
	_blend_from = _blend
	_blend_to = 1.0 if up else 0.0
	if instant:
		_apply_blend(_blend_to, true)
		return
	var player: Node2D = _session.player if _session else null
	if player:
		_burst.fire(player.global_position, up)
	_ripple.fire(up)


func _apply_blend(b: float, force := false) -> void:
	if not force and is_equal_approx(b, _blend) and b == _blend_to:
		return
	_blend = b
	GardenLook.apply(get_tree(), b)
	var sky: Array = []
	for i in 3:
		sky.append((GROUND_STATE.sky[i] as Color).lerp(CEILING_STATE.sky[i], b))
	# The sky turns over with the landscape: its light end follows the horizon.
	_sky.set_colors(sky, cos(PI * b))
	# Never exactly flat: a zero scale is a singular transform.
	var turn := cos(PI * b)
	_pivot.scale = Vector2(1.0, turn if absf(turn) > 0.02 else 0.02 * signf(0.5 - b))
	for layer in _layers:
		var key: String = layer.get_meta(&"tint")
		layer.modulate = (GROUND_STATE[key] as Color).lerp(CEILING_STATE[key], b)
	_leaves.modulate = (GROUND_STATE.leaves as Color).lerp(CEILING_STATE.leaves, b)
	# Leaves fall toward the floor of the moment.
	_leaves.gravity = Vector2(-10.0, 22.0 * cos(PI * b))
	if _session:
		var player: Node = _session.player
		if player:
			player.fx.modulate = GardenLook.player_color("body", b)
			player.visual.queue_redraw()
		var embers: CPUParticles2D = _session.get_node_or_null(^"%Embers")
		if embers:
			embers.modulate = Color(GardenLook.player_color("glow", b), 0.6)


func _make_layer(kind: String) -> Node2D:
	match kind:
		"_Sun":
			return _Sun.new()
		"_Clouds":
			return _Clouds.new()
		"_FarHills":
			return _Hills.new()
		"_Lake":
			return _Lake.new()
		"_Fog":
			return _Fog.new()
		"_MidGarden":
			return _MidGarden.new()
	return _Birds.new()


func _make_leaves() -> CPUParticles2D:
	var leaves := CPUParticles2D.new()
	leaves.amount = 18
	leaves.lifetime = 7.0
	leaves.preprocess = 7.0
	leaves.emission_shape = CPUParticles2D.EMISSION_SHAPE_RECTANGLE
	leaves.emission_rect_extents = Vector2(900.0, 420.0)
	leaves.direction = Vector2(-1.0, 0.3)
	leaves.spread = 40.0
	leaves.initial_velocity_min = 10.0
	leaves.initial_velocity_max = 36.0
	leaves.angular_velocity_min = -90.0
	leaves.angular_velocity_max = 90.0
	leaves.scale_amount_min = 3.0
	leaves.scale_amount_max = 6.0
	leaves.z_index = 1
	return leaves


## The sky: a vertical gradient quad around the camera, big enough for the
## view; [param turn] 1 = light at the horizon below, -1 = light above.
class _Sky extends Node2D:
	var _colors: Array = [Color.BLACK, Color.BLACK, Color.BLACK]
	var _turn := 1.0

	func set_colors(colors: Array, turn: float) -> void:
		if colors == _colors and is_equal_approx(turn, _turn):
			return
		_colors = colors
		_turn = turn
		queue_redraw()

	func _draw() -> void:
		var w := 2600.0
		var top: Color = _colors[0]
		var mid: Color = _colors[1]
		var low: Color = _colors[2]
		var s := signf(_turn) if absf(_turn) > 0.001 else 1.0
		var h := GardenLook.HORIZON_Y * _turn
		# Deep sky far from the horizon, pale at it, then the light below.
		var far := -900.0 * s
		var near := h - 260.0 * s
		var pts := [far, near, h, h + 600.0 * s]
		var cols := [top, mid, low, low]
		for i in 3:
			var a: float = pts[i]
			var b: float = pts[i + 1]
			draw_polygon(PackedVector2Array([Vector2(-w, a), Vector2(w, a), Vector2(w, b), Vector2(-w, b)]),
				PackedColorArray([cols[i], cols[i], cols[i + 1], cols[i + 1]]))
		var beyond: float = pts[3]
		draw_polygon(PackedVector2Array([Vector2(-w, beyond), Vector2(w, beyond), Vector2(w, beyond + 2000.0 * s),
			Vector2(-w, beyond + 2000.0 * s)]), PackedColorArray([low, low, low, low]))
		draw_polygon(PackedVector2Array([Vector2(-w, far), Vector2(w, far), Vector2(w, far - 2000.0 * s),
			Vector2(-w, far - 2000.0 * s)]), PackedColorArray([top, top, top, top]))


## A wrapped layer cut into chunks (each its own canvas item, culled off
## screen), three copies of the wrap side by side.
class _Wrapped extends Node2D:
	const CHUNK := 1024.0
	var wrap := 4096.0

	func _ready() -> void:
		var count := int(ceilf(wrap / CHUNK))
		for copy in 3:
			for i in count:
				var chunk := _Chunk.new()
				chunk.layer = self
				chunk.x0 = i * CHUNK
				chunk.x1 = minf((i + 1) * CHUNK, wrap)
				chunk.position = Vector2(copy * wrap + chunk.x0, 0.0)
				add_child(chunk)

	func draw_range(_ci: CanvasItem, _x0: float, _x1: float) -> void:
		pass


class _Chunk extends Node2D:
	var layer: _Wrapped
	var x0 := 0.0
	var x1 := 0.0

	func _draw() -> void:
		draw_set_transform(Vector2(-x0, 0.0))
		layer.draw_range(self, x0, x1)


class _Sun extends Node2D:
	func _draw() -> void:
		var c := Vector2(360.0, -250.0)
		Neon.soft_light(self, c, 260.0, Color(1.0, 1.0, 1.0, 0.45))
		draw_circle(c, 62.0, Color(1.0, 1.0, 1.0))
		for i in 12:
			var a := TAU * i / 12.0
			draw_line(c + Vector2.from_angle(a) * 80.0, c + Vector2.from_angle(a) * 112.0, Color(1.0, 1.0, 1.0, 0.7), 4.0, true)


class _Clouds extends _Wrapped:
	func _init() -> void:
		wrap = 4096.0

	func draw_range(ci: CanvasItem, x0: float, x1: float) -> void:
		var rng := RandomNumberGenerator.new()
		rng.seed = 404
		for i in 9:
			var c := Vector2(rng.randf() * wrap, rng.randf_range(-330.0, -150.0))
			var r := rng.randf_range(50.0, 95.0)
			var seed_value := rng.randi()
			if c.x < x0 or c.x >= x1:
				continue
			var batch := GardenArt.Batch.new(Color.WHITE)
			batch.discs(GardenArt.puffs(c, r, seed_value, 6), 18)
			batch.draw(ci, Color(0, 0, 0, 0))
			ci.draw_line(c + Vector2(-r * 1.2, r * 0.35), c + Vector2(r * 1.2, r * 0.35), Color(1.0, 1.0, 1.0, 0.8), 3.0)


## Far hills with little round trees, standing on the horizon.
class _Hills extends _Wrapped:
	func _init() -> void:
		wrap = 3584.0

	func draw_range(ci: CanvasItem, x0: float, x1: float) -> void:
		var base := GardenLook.HORIZON_Y
		var ridge := PackedVector2Array([Vector2(x0, base + 400.0)])
		var x := x0
		while x <= x1 + 1.0:
			ridge.append(Vector2(x, base - 70.0 - 55.0 * sin(x * 0.0021) - 30.0 * sin(x * 0.0057 + 1.3)))
			x += 32.0
		ridge.append(Vector2(x1, base + 400.0))
		ci.draw_colored_polygon(ridge, Color(1.0, 1.0, 1.0, 0.55))
		var rng := RandomNumberGenerator.new()
		rng.seed = int(x0) + 77
		var batch := GardenArt.Batch.new(Color(1.0, 1.0, 1.0, 0.7))
		var trunks := PackedVector2Array()
		var tx := x0 + rng.randf_range(20.0, 120.0)
		while tx < x1 - 10.0:
			var top := base - 70.0 - 55.0 * sin(tx * 0.0021) - 30.0 * sin(tx * 0.0057 + 1.3)
			var h := rng.randf_range(30.0, 70.0)
			trunks.append_array([Vector2(tx, top + 6.0), Vector2(tx, top - h * 0.6)])
			batch.discs([Vector3(tx, top - h * 0.7, h * 0.38)], 12)
			tx += rng.randf_range(60.0, 180.0)
		batch.draw(ci, Color(0, 0, 0, 0))
		ci.draw_multiline(trunks, Color(1.0, 1.0, 1.0, 0.7), 3.0)
		# Far cascades down the hillsides: thin, pale, clearly far away.
		var falls := PackedVector2Array()
		for fx: float in [900.0, 2600.0]:
			if fx >= x0 and fx < x1:
				var top := base - 70.0 - 55.0 * sin(fx * 0.0021) - 30.0 * sin(fx * 0.0057 + 1.3)
				for k in 3:
					falls.append_array([Vector2(fx - 4.0 + 4.0 * k, top + 4.0), Vector2(fx - 4.0 + 4.0 * k, base + 6.0)])
		if not falls.is_empty():
			ci.draw_multiline(falls, Color(1.0, 1.0, 1.0, 0.9), 2.0)


## The lake at the foot of the hills, with lines of light on it and two
## waterfalls pouring into it from floating isles.
class _Lake extends _Wrapped:
	func _init() -> void:
		wrap = 3072.0

	func draw_range(ci: CanvasItem, x0: float, x1: float) -> void:
		var top := GardenLook.HORIZON_Y + 20.0
		ci.draw_rect(Rect2(x0, top, x1 - x0, 900.0), Color(1.0, 1.0, 1.0, 0.8))
		var lines := PackedVector2Array()
		var rng := RandomNumberGenerator.new()
		rng.seed = int(x0) + 5
		for i in 24:
			var p := Vector2(rng.randf_range(x0, x1), top + rng.randf_range(12.0, 160.0))
			var w := rng.randf_range(20.0, 70.0)
			lines.append_array([p, p + Vector2(w, 0.0)])
		ci.draw_multiline(lines, Color(1.0, 1.0, 1.0, 0.9), 2.0)


## Soft bands of mist along the horizon and high in the sky.
class _Fog extends Node2D:
	func _draw() -> void:
		for spec: Array in [[GardenLook.HORIZON_Y - 30.0, 110.0, 0.6], [-260.0, 160.0, 0.35]]:
			var y: float = spec[0]
			var h: float = spec[1]
			var clear := Color(1.0, 1.0, 1.0, 0.0)
			var thick := Color(1.0, 1.0, 1.0, spec[2])
			draw_polygon(PackedVector2Array([Vector2(-2600.0, y - h), Vector2(2600.0, y - h), Vector2(2600.0, y),
				Vector2(-2600.0, y)]), PackedColorArray([clear, clear, thick, thick]))
			draw_polygon(PackedVector2Array([Vector2(-2600.0, y), Vector2(2600.0, y), Vector2(2600.0, y + h),
				Vector2(-2600.0, y + h)]), PackedColorArray([thick, thick, clear, clear]))


## Nearer garden: tall trees, flower stems and grass, dark against the light.
class _MidGarden extends _Wrapped:
	func _init() -> void:
		wrap = 3072.0

	func draw_range(ci: CanvasItem, x0: float, x1: float) -> void:
		var base := GardenLook.HORIZON_Y + 60.0
		var rng := RandomNumberGenerator.new()
		rng.seed = 909
		var x := 0.0
		var batch := GardenArt.Batch.new(Color(1.0, 1.0, 1.0, 0.55))
		var stems := PackedVector2Array()
		while x < wrap:
			var h := rng.randf_range(90.0, 190.0)
			var crown := rng.randf_range(30.0, 58.0)
			var tree := rng.randf() < 0.55
			var gap := rng.randf_range(90.0, 220.0)
			var seed_value := rng.randi()
			if x >= x0 and x < x1:
				if tree:
					batch.poly(PackedVector2Array([Vector2(x - 7.0, base), Vector2(x + 7.0, base),
						Vector2(x + 3.0, base - h), Vector2(x - 3.0, base - h)]), false)
					batch.discs(GardenArt.puffs(Vector2(x, base - h - crown * 0.3), crown, seed_value, 5), 14)
				else:
					# A giant flower on a curving stem.
					stems.append_array([Vector2(x, base), Vector2(x + 12.0, base - h * 0.5),
						Vector2(x + 12.0, base - h * 0.5), Vector2(x, base - h * 0.8)])
					for i in 6:
						var a := TAU * i / 6.0
						batch.poly(GardenArt.leaf(Vector2(x, base - h * 0.8) + Vector2.from_angle(a) * 16.0, 30.0,
							14.0, a), false)
			x += gap
		batch.draw(ci, Color(0, 0, 0, 0))
		if not stems.is_empty():
			ci.draw_multiline(stems, Color(1.0, 1.0, 1.0, 0.55), 5.0, true)
		ci.draw_rect(Rect2(x0, base, x1 - x0, 600.0), Color(1.0, 1.0, 1.0, 0.55))


## Flights of birds far off: V shapes drifting on the wind (scenery).
class _Birds extends _Wrapped:
	func _init() -> void:
		wrap = 5120.0

	func draw_range(ci: CanvasItem, x0: float, x1: float) -> void:
		var rng := RandomNumberGenerator.new()
		rng.seed = 51
		var lines := PackedVector2Array()
		for flight in 6:
			var c := Vector2(rng.randf() * wrap, rng.randf_range(-300.0, -120.0))
			if c.x < x0 or c.x >= x1:
				continue
			for i in rng.randi_range(3, 6):
				var row := (i + 1) / 2
				var side := 1.0 if i % 2 == 0 else -1.0
				var b := c + Vector2(row * 22.0, side * row * 12.0)
				lines.append_array([b, b + Vector2(-8.0, -6.0), b, b + Vector2(8.0, -6.0)])
		if not lines.is_empty():
			ci.draw_multiline(lines, Color.WHITE, 2.5, true)


## A ring of the new state's colour breaking out of the player at a latch.
class _Burst extends Node2D:
	const TIME := 0.45
	var _left := 0.0
	var _color := Color.WHITE

	func _ready() -> void:
		top_level = true
		z_as_relative = false
		z_index = 30

	func fire(at: Vector2, up: bool) -> void:
		global_position = at
		_color = GardenLook.player_color("body", 1.0 if up else 0.0)
		_left = TIME

	func _process(delta: float) -> void:
		if _left <= 0.0:
			return
		_left = maxf(_left - delta, 0.0)
		queue_redraw()

	func _draw() -> void:
		if _left <= 0.0:
			return
		var k := 1.0 - _left / TIME
		var r := 26.0 + 220.0 * (1.0 - pow(1.0 - k, 3.0))
		draw_arc(Vector2.ZERO, r, 0.0, TAU, 48, Color(_color, 0.75 * (1.0 - k)), 5.0 * (1.0 - k) + 1.0, true)
		draw_arc(Vector2.ZERO, r * 0.7, 0.0, TAU, 40, Color(1.0, 1.0, 1.0, 0.45 * (1.0 - k)), 2.0, true)


## A soft ripple over the screen at a latch: the edges breathe in the new
## colour and a wave line sweeps across. Faint and short; never a flash.
class _Ripple extends Control:
	const TIME := 0.42
	var _left := 0.0
	var _color := Color.WHITE
	var _up := false

	func _ready() -> void:
		set_anchors_preset(Control.PRESET_FULL_RECT)
		mouse_filter = Control.MOUSE_FILTER_IGNORE

	func fire(up: bool) -> void:
		_up = up
		_color = GardenLook.player_color("body", 1.0 if up else 0.0)
		_left = TIME

	func _process(delta: float) -> void:
		if _left <= 0.0:
			return
		_left = maxf(_left - delta, 0.0)
		queue_redraw()

	func _draw() -> void:
		if _left <= 0.0:
			return
		var k := 1.0 - _left / TIME
		var fade := sin(PI * k)
		var view := size
		var edge := Color(_color, 0.22 * fade)
		var clear := Color(_color, 0.0)
		var band := view.y * 0.18
		draw_polygon(PackedVector2Array([Vector2.ZERO, Vector2(view.x, 0.0), Vector2(view.x, band), Vector2(0.0, band)]),
			PackedColorArray([edge, edge, clear, clear]))
		draw_polygon(PackedVector2Array([Vector2(0.0, view.y - band), Vector2(view.x, view.y - band), view,
			Vector2(0.0, view.y)]), PackedColorArray([clear, clear, edge, edge]))
		# The wave sweeps toward the new floor.
		var y := view.y * (k if not _up else 1.0 - k)
		var wave := PackedVector2Array()
		for i in 33:
			var x := view.x * i / 32.0
			wave.append(Vector2(x, y + sin(x * 0.02 + k * 12.0) * 10.0))
		draw_polyline(wave, Color(_color, 0.3 * fade), 3.0, true)
