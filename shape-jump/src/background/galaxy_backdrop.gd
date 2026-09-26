extends Node2D
## World 03's background and look: THE HORRIFYING GALAXY (docs/GDD.md §9C).
##
## A deep-space backdrop in world space (so it turns with the camera when
## gravity flips): a sky, stars, nebulae, giant planets, an impossible ruined
## city both standing and hanging, portals, fog and drifting debris, each
## layer with its own parallax (and a horizontal wrap). Every layer is drawn
## once in neutral tones; colour comes from modulate.
##
## It is also the world's look controller. Gravity has two colours:
##   GROUND (gravity down): deep blue / violet;
##   CEILING (gravity up):  orange / amber;
## and on every flip everything crosses from one to the other over the flip's
## transition: sky, nebulae, planets, ruins, fog, stars, the level's glow
## tint, the floating embers; with a burst of light around the player and a
## brief screen pulse. Gameplay colours (hazard magenta, the player's black
## and white) stay readable in both states.
##
## Costs: ~10 canvas items drawn once; per frame only transforms and
## modulates change (the sky's gradient redraws while a flip is in progress).

const GROUND_STATE := {
	"sky": [Color("020209"), Color("0a0826"), Color("1c1250")],
	"nebula": Color(0.36, 0.62, 1.0), "planet": Color(0.55, 0.60, 1.0), "ruins": Color(0.62, 0.56, 1.0),
	"fog": Color(0.34, 0.24, 0.85, 0.22), "stars": Color(0.82, 0.86, 1.0),
	"level": Color(0.80, 0.84, 1.0), "embers": Color(0.62, 0.66, 1.0),
}
const CEILING_STATE := {
	"sky": [Color("0a0201"), Color("2a0c04"), Color("5e2008")],
	"nebula": Color(1.0, 0.78, 0.30), "planet": Color(1.0, 0.64, 0.36), "ruins": Color(1.0, 0.62, 0.32),
	"fog": Color(0.92, 0.40, 0.14, 0.22), "stars": Color(1.0, 0.90, 0.76),
	"level": Color(1.0, 0.86, 0.72), "embers": Color(1.0, 0.72, 0.42),
}

## Parallax factor of each layer (0 = fixed on the sky, 1 = world).
const LAYERS := [
	["_Stars", 0.02, 4096.0], ["_Nebula", 0.03, 5120.0], ["_Planets", 0.05, 6144.0],
	["_Portals", 0.08, 4608.0], ["_FarRuins", 0.12, 3584.0], ["_Fog", 0.0, 0.0],
	["_MidRuins", 0.3, 3072.0], ["_Debris", 0.5, 2560.0],
]

var _session: Node
var _gravity: GravityState
var _camera: Camera2D
var _sky: _Sky
var _layers: Array[Node2D] = []
var _factors: Array[float] = []
var _wraps: Array[float] = []
var _burst: _Burst
var _pulse: _Pulse
## 0 = ground colours, 1 = ceiling colours.
var _blend := 0.0
var _blend_from := 0.0
var _blend_to := 0.0


func _ready() -> void:
	z_index = -10
	_sky = _Sky.new()
	add_child(_sky)
	for spec: Array in LAYERS:
		var layer: Node2D = _make_layer(spec[0])
		add_child(layer)
		_layers.append(layer)
		_factors.append(spec[1])
		_wraps.append(spec[2])
	_burst = _Burst.new()
	add_child(_burst)
	var overlay := CanvasLayer.new()
	overlay.layer = 3
	add_child(overlay)
	_pulse = _Pulse.new()
	overlay.add_child(_pulse)
	_apply_blend(0.0)


## Called by the game session when this world starts: the look follows the
## session's gravity and its levels.
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


func _process(_delta: float) -> void:
	if _camera == null or not is_instance_valid(_camera):
		_camera = get_viewport().get_camera_2d()
		if _camera == null:
			return
	var center := _camera.get_screen_center_position()
	_sky.position = center
	for i in _layers.size():
		var f := _factors[i]
		var layer := _layers[i]
		if _wraps[i] > 0.0:
			# Content repeats every wrap width; slide it at the layer's rate.
			layer.position = Vector2(center.x - fposmod(center.x * f, _wraps[i]), center.y * (1.0 - f * 0.6))
		else:
			layer.position = Vector2(center.x, center.y * 0.85)
	if _gravity and _gravity.phase == GravityState.Phase.FLIPPING:
		var k := smoothstep(0.0, 1.0, _gravity.transition_progress())
		_apply_blend(lerpf(_blend_from, _blend_to, k))
	elif _blend != _blend_to:
		_apply_blend(_blend_to)


func _on_level_loaded() -> void:
	_apply_blend(_blend)


func _on_gravity_flipped(up: bool, instant: bool) -> void:
	_blend_from = _blend
	_blend_to = 1.0 if up else 0.0
	if instant:
		_apply_blend(_blend_to)
		return
	var player: Node2D = _session.player if _session else null
	if player:
		_burst.fire(player.global_position, up)
	_pulse.fire(up)


func _apply_blend(b: float) -> void:
	_blend = b
	var sky: Array = []
	for i in 3:
		sky.append((GROUND_STATE.sky[i] as Color).lerp(CEILING_STATE.sky[i], b))
	_sky.set_colors(sky)
	for layer in _layers:
		var key: String = layer.get_meta(&"tint")
		var from: Color = GROUND_STATE[key]
		var to: Color = CEILING_STATE[key]
		layer.modulate = from.lerp(to, b)
	if _session:
		var level: Node2D = _session.level
		if level:
			level.modulate = (GROUND_STATE.level as Color).lerp(CEILING_STATE.level, b)
		var embers: CPUParticles2D = _session.get_node_or_null(^"%Embers")
		if embers:
			embers.modulate = (GROUND_STATE.embers as Color).lerp(CEILING_STATE.embers, b)
			# The embers stir while the world turns.
			embers.speed_scale = 1.0 + 3.0 * sin(PI * clampf(absf(b - _blend_from) / maxf(absf(_blend_to - _blend_from), 0.001), 0.0, 1.0))


func _make_layer(kind: String) -> Node2D:
	var layer: Node2D
	match kind:
		"_Stars":
			layer = _Stars.new()
			layer.set_meta(&"tint", "stars")
		"_Nebula":
			layer = _Nebula.new()
			layer.set_meta(&"tint", "nebula")
		"_Planets":
			layer = _Planets.new()
			layer.set_meta(&"tint", "planet")
		"_Portals":
			layer = _Portals.new()
			layer.set_meta(&"tint", "nebula")
		"_FarRuins":
			layer = _Ruins.new()
			(layer as _Ruins).setup(3584.0, 11, Color(0.30, 0.30, 0.38, 0.45), 0.25, 0.8)
			layer.set_meta(&"tint", "ruins")
		"_Fog":
			layer = _Fog.new()
			layer.set_meta(&"tint", "fog")
		"_MidRuins":
			layer = _Ruins.new()
			(layer as _Ruins).setup(3072.0, 5, Color(0.05, 0.05, 0.08, 0.72), 0.4, 1.0)
			layer.set_meta(&"tint", "ruins")
		_:
			layer = _Debris.new()
			layer.set_meta(&"tint", "ruins")
	return layer


## The sky: a gradient quad around the camera, big enough for any rotation.
class _Sky extends Node2D:
	var _colors: Array = [Color.BLACK, Color.BLACK, Color.BLACK]

	func set_colors(colors: Array) -> void:
		if colors == _colors:
			return
		_colors = colors
		queue_redraw()

	func _draw() -> void:
		var w := 2600.0
		var top: Color = _colors[0]
		var mid: Color = _colors[1]
		var low: Color = _colors[2]
		draw_polygon(PackedVector2Array([Vector2(-w, -w), Vector2(w, -w), Vector2(w, -200.0), Vector2(-w, -200.0)]),
			PackedColorArray([top, top, mid, mid]))
		draw_polygon(PackedVector2Array([Vector2(-w, -200.0), Vector2(w, -200.0), Vector2(w, 500.0), Vector2(-w, 500.0)]),
			PackedColorArray([mid, mid, low, low]))
		draw_polygon(PackedVector2Array([Vector2(-w, 500.0), Vector2(w, 500.0), Vector2(w, w), Vector2(-w, w)]),
			PackedColorArray([low, low, mid, mid]))


## A wrapped layer, cut into chunks: each chunk is its own canvas item, so
## the renderer culls the ones off screen (a whole wrap drawn as one item
## would be drawn in full every frame). Three copies of the wrap cover any
## view; only the chunks on screen cost anything.
class _Wrapped extends Node2D:
	const CHUNK := 1024.0
	var wrap := 4096.0

	func _ready() -> void:
		build()

	func build() -> void:
		for child in get_children():
			child.queue_free()
		var count := int(ceilf(wrap / CHUNK))
		for copy in [-1, 0, 1]:
			for i in count:
				var chunk := _Chunk.new()
				chunk.layer = self
				chunk.x0 = i * CHUNK
				chunk.x1 = minf((i + 1) * CHUNK, wrap)
				chunk.position = Vector2(copy * wrap + chunk.x0, 0.0)
				add_child(chunk)

	## Draws, on [param ci] (offset by -x0), everything anchored in [x0, x1).
	func draw_range(_ci: CanvasItem, _x0: float, _x1: float) -> void:
		pass


class _Chunk extends Node2D:
	var layer: _Wrapped
	var x0 := 0.0
	var x1 := 0.0

	func _draw() -> void:
		draw_set_transform(Vector2(-x0, 0.0))
		layer.draw_range(self, x0, x1)


class _Stars extends _Wrapped:
	func _init() -> void:
		wrap = 4096.0

	func draw_range(ci: CanvasItem, x0: float, x1: float) -> void:
		var rng := RandomNumberGenerator.new()
		rng.seed = 303
		for i in 220:
			var p := Vector2(rng.randf() * wrap, rng.randf_range(-900.0, 900.0))
			var s := rng.randf_range(1.0, 2.8)
			var a := rng.randf_range(0.3, 1.0)
			if p.x >= x0 and p.x < x1:
				ci.draw_rect(Rect2(p, Vector2(s, s)), Color(1.0, 1.0, 1.0, a))
		for i in 10:
			var p := Vector2(rng.randf() * wrap, rng.randf_range(-700.0, 700.0))
			if p.x >= x0 and p.x < x1:
				Neon.soft_light(ci, p, 14.0, Color(1.0, 1.0, 1.0, 0.8))
				ci.draw_line(p - Vector2(11.0, 0.0), p + Vector2(11.0, 0.0), Color(1.0, 1.0, 1.0, 0.7), 1.0)
				ci.draw_line(p - Vector2(0.0, 11.0), p + Vector2(0.0, 11.0), Color(1.0, 1.0, 1.0, 0.7), 1.0)


## Nebulae: layered soft clouds, the brightest colour on screen after play.
class _Nebula extends _Wrapped:
	func _init() -> void:
		wrap = 5120.0

	func draw_range(ci: CanvasItem, x0: float, x1: float) -> void:
		var rng := RandomNumberGenerator.new()
		rng.seed = 404
		for i in 7:
			var c := Vector2(rng.randf() * wrap, rng.randf_range(-420.0, 380.0))
			var puffs := rng.randi_range(4, 7)
			var size := rng.randf_range(260.0, 480.0)
			for k in puffs:
				var p := c + Vector2(rng.randf_range(-1.0, 1.0) * size, rng.randf_range(-0.5, 0.5) * size)
				var r := size * rng.randf_range(0.5, 1.0)
				var a := rng.randf_range(0.16, 0.30)
				if c.x >= x0 and c.x < x1:
					Neon.soft_light(ci, p, r, Color(1.0, 1.0, 1.0, a))


class _Planets extends _Wrapped:
	func _init() -> void:
		wrap = 6144.0

	func draw_range(ci: CanvasItem, x0: float, x1: float) -> void:
		for spec: Array in [[Vector2(700.0, -260.0), 250.0, true], [Vector2(2900.0, 170.0), 120.0, false],
				[Vector2(4700.0, -330.0), 70.0, false], [Vector2(5500.0, 260.0), 180.0, true]]:
			var c: Vector2 = spec[0]
			if c.x >= x0 and c.x < x1:
				_planet(ci, c, spec[1], spec[2])

	func _planet(ci: CanvasItem, c: Vector2, r: float, ringed: bool) -> void:
		Neon.soft_light(ci, c, r * 2.0, Color(1.0, 1.0, 1.0, 0.18))
		ci.draw_circle(c, r, Color(0.34, 0.34, 0.40))
		# Bands, a lit limb and a deep shadow: huge, cold, dead.
		for i in 6:
			var y := -r + r * 0.3 * (i + 1)
			var half := sqrt(maxf(r * r - y * y, 0.0))
			ci.draw_line(c + Vector2(-half, y), c + Vector2(half, y), Color(0.5, 0.5, 0.58, 0.55), 5.0 + i * 3.0)
		ci.draw_circle(c + Vector2(r * 0.28, r * 0.18), r * 0.84, Color(0.03, 0.03, 0.05, 0.72))
		ci.draw_arc(c, r, -PI * 0.2, PI * 0.95, 40, Color(1.0, 1.0, 1.0, 0.7), 3.0)
		if ringed:
			var ring := PackedVector2Array()
			for k in 49:
				var a := TAU * k / 48.0
				ring.append(c + Vector2(cos(a) * r * 1.85, sin(a) * r * 0.34).rotated(-0.22))
			ci.draw_polyline(ring, Color(1.0, 1.0, 1.0, 0.55), 3.0)


class _Portals extends _Wrapped:
	func _init() -> void:
		wrap = 4608.0

	func draw_range(ci: CanvasItem, x0: float, x1: float) -> void:
		for spec: Array in [[Vector2(1700.0, -120.0), 110.0], [Vector2(3900.0, 200.0), 70.0]]:
			var c: Vector2 = spec[0]
			if c.x < x0 or c.x >= x1:
				continue
			var r: float = spec[1]
			Neon.soft_light(ci, c, r * 2.4, Color(1.0, 1.0, 1.0, 0.22))
			for i in 4:
				ci.draw_arc(c, r * (1.0 - 0.18 * i), 0.3 * i, TAU - 0.5 + 0.3 * i, 40,
					Color(1.0, 1.0, 1.0, 0.6 - 0.1 * i), 3.0)
			ci.draw_circle(c, r * 0.35, Color(0.0, 0.0, 0.0, 0.9))


## An impossible city: towers rising at the bottom of the view and hanging at
## its top, fogged by distance; blocks float free between the two.
class _Ruins extends _Wrapped:
	var seed_value := 11
	var body := Color(0.2, 0.2, 0.26)
	var rim := 0.35
	var scale_factor := 1.0

	func setup(width: float, seed_number: int, body_color: Color, rim_alpha: float, size_scale: float) -> void:
		wrap = width
		seed_value = seed_number
		body = body_color
		rim = rim_alpha
		scale_factor = size_scale

	func draw_range(ci: CanvasItem, x0: float, x1: float) -> void:
		var rng := RandomNumberGenerator.new()
		rng.seed = seed_value
		var x := 0.0
		while x < wrap:
			var w := rng.randf_range(40.0, 110.0) * scale_factor
			var h := rng.randf_range(90.0, 260.0) * scale_factor
			var hanging := rng.randf() < 0.45
			var spire := rng.randf_range(0.4, 1.2)
			var lamp := rng.randf() < 0.5
			var lamp_at := rng.randf_range(0.2, 0.8)
			var floating := rng.randf() < 0.3
			var fs := rng.randf_range(20.0, 56.0) * scale_factor
			var fp := Vector2(x + rng.randf_range(0.0, 120.0), rng.randf_range(-180.0, 180.0))
			var gap := rng.randf_range(40.0, 160.0) * scale_factor
			if x >= x0 and x < x1:
				_tower(ci, x, w, h, hanging, spire, lamp, lamp_at)
				if floating:
					ci.draw_rect(Rect2(fp, Vector2(fs, fs)), body)
					ci.draw_rect(Rect2(fp, Vector2(fs, fs)), Color(1.0, 1.0, 1.0, rim), false, 1.5)
			x += w + gap

	func _tower(ci: CanvasItem, x: float, w: float, h: float, hanging: bool, spire: float, lamp: bool, lamp_at: float) -> void:
		# Rooted just off screen (the view is about 720 px tall) and reaching in.
		var root := -760.0 if hanging else 760.0
		var tip := root + (h + 420.0) * (1.0 if hanging else -1.0)
		var top := minf(root, tip)
		ci.draw_rect(Rect2(x, top, w, absf(tip - root)), body)
		var dir := 1.0 if hanging else -1.0
		ci.draw_colored_polygon(PackedVector2Array([Vector2(x, tip), Vector2(x + w, tip),
			Vector2(x + w * 0.5, tip + dir * w * spire)]), body)
		ci.draw_line(Vector2(x, tip), Vector2(x + w, tip), Color(1.0, 1.0, 1.0, rim), 2.0)
		ci.draw_line(Vector2(x, tip), Vector2(x, tip - dir * 180.0), Color(1.0, 1.0, 1.0, rim * 0.5), 1.0)
		if lamp:
			var lx := x + lamp_at * w
			ci.draw_line(Vector2(lx, tip - dir * 16.0), Vector2(lx, tip - dir * 150.0), Color(1.0, 1.0, 1.0, rim * 0.6), 2.0)


class _Fog extends Node2D:
	func _draw() -> void:
		for spec: Array in [[-300.0, 220.0, 0.55], [300.0, 220.0, 0.55], [0.0, 120.0, 0.2]]:
			var y: float = spec[0]
			var h: float = spec[1]
			var a: float = spec[2]
			var clear := Color(1.0, 1.0, 1.0, 0.0)
			var thick := Color(1.0, 1.0, 1.0, a)
			draw_polygon(PackedVector2Array([Vector2(-2600.0, y - h), Vector2(2600.0, y - h), Vector2(2600.0, y),
				Vector2(-2600.0, y)]), PackedColorArray([clear, clear, thick, thick]))
			draw_polygon(PackedVector2Array([Vector2(-2600.0, y), Vector2(2600.0, y), Vector2(2600.0, y + h),
				Vector2(-2600.0, y + h)]), PackedColorArray([thick, thick, clear, clear]))


class _Debris extends _Wrapped:
	func _init() -> void:
		wrap = 2560.0

	func draw_range(ci: CanvasItem, x0: float, x1: float) -> void:
		var rng := RandomNumberGenerator.new()
		rng.seed = 505
		for i in 22:
			var p := Vector2(rng.randf() * wrap, rng.randf_range(-330.0, 330.0))
			var r := rng.randf_range(5.0, 14.0)
			if p.x < x0 or p.x >= x1:
				continue
			var rock := GalaxyArt.rock(r, i + 900, 7)
			for k in rock.size():
				rock[k] += p
			ci.draw_colored_polygon(rock, Color(0.12, 0.12, 0.15))
			ci.draw_polyline(GalaxyArt.closed(rock), Color(1.0, 1.0, 1.0, 0.35), 1.0)


## A ring of light bursting out of the player at a flip (world space).
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
		_color = GalaxyArt.state_color(up)
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
		var r := 30.0 + 260.0 * (1.0 - pow(1.0 - k, 3.0))
		Neon.soft_light(self, Vector2.ZERO, 120.0 * (1.0 - k) + 20.0, Color(_color, 0.5 * (1.0 - k)))
		draw_arc(Vector2.ZERO, r, 0.0, TAU, 48, Color(_color, 0.8 * (1.0 - k)), 4.0 * (1.0 - k) + 1.0, true)
		draw_arc(Vector2.ZERO, r * 0.7, 0.0, TAU, 40, Color(1.0, 1.0, 1.0, 0.5 * (1.0 - k)), 2.0, true)


## A brief screen pulse at a flip: a tinted vignette and streaks running
## toward the new floor. Faint (never hides the play) and short.
class _Pulse extends Control:
	const TIME := 0.38
	var _left := 0.0
	var _color := Color.WHITE
	var _up := false

	func _ready() -> void:
		set_anchors_preset(Control.PRESET_FULL_RECT)
		mouse_filter = Control.MOUSE_FILTER_IGNORE

	func fire(up: bool) -> void:
		_up = up
		_color = GalaxyArt.state_color(up)
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
		var edge := Color(_color, 0.28 * fade)
		var clear := Color(_color, 0.0)
		var band := view.y * 0.22
		# Vignette on the top and bottom edges.
		draw_polygon(PackedVector2Array([Vector2.ZERO, Vector2(view.x, 0.0), Vector2(view.x, band), Vector2(0.0, band)]),
			PackedColorArray([edge, edge, clear, clear]))
		draw_polygon(PackedVector2Array([Vector2(0.0, view.y - band), Vector2(view.x, view.y - band), Vector2(view.x, view.y),
			Vector2(0.0, view.y)]), PackedColorArray([clear, clear, edge, edge]))
		# Streaks sweeping down the screen: the new floor is below.
		for i in 7:
			var x := view.x * (0.08 + 0.14 * i)
			var y := view.y * fmod(k * 1.6 + 0.13 * i, 1.0)
			draw_line(Vector2(x, y - 60.0), Vector2(x, y + 60.0), Color(_color, 0.22 * fade), 2.0)
