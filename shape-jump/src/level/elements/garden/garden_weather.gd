@tool
class_name GardenWeather
extends Node2D
## Scenery weather over a stretch of World 04: drifting leaves or falling
## snow. Nothing here collides; it only thickens the air. It is drawn behind
## every obstacle and the terrain's faces ([constant LAYER]), soft and pale,
## so it can never hide a hazard or be mistaken for one: the dangerous
## leaves ([LeafGlider]) and ice ([FallingRock] with ice) are dark and inked.
## Its particles fall toward the floor of the moment (they turn with a latch).
##
## At most [constant MAX_FLAKES] shapes, redrawn only while on screen.

enum Kind { LEAVES, SNOW }

const MAX_FLAKES := 40
const LAYER := -3

@export var kind: Kind = Kind.LEAVES:
	set(value):
		kind = value
		queue_redraw()
@export var width := 1024.0
@export var ceiling := -320.0
## How thick the air is: 0..1 of [constant MAX_FLAKES].
@export_range(0.0, 1.0, 0.05) var density := 0.5

var _time := 0.0
var _down := 1.0
var _seeds: Array[Vector4] = []


func _ready() -> void:
	z_index = LAYER
	GardenLook.tint(self, &"garden_leaf" if kind == Kind.LEAVES else &"garden_bloom")
	var rng := RandomNumberGenerator.new()
	rng.seed = int(absf(position.x)) + 91
	for i in int(MAX_FLAKES * density):
		# x, phase in the fall, sway phase, size.
		_seeds.append(Vector4(rng.randf() * width, rng.randf(), rng.randf() * TAU, rng.randf_range(0.6, 1.2)))


func _process(delta: float) -> void:
	if Engine.is_editor_hint():
		return
	var camera := get_viewport().get_camera_2d()
	if camera == null:
		return
	var view := camera.get_screen_center_position().x
	if view + 800.0 < global_position.x or view - 800.0 > global_position.x + width:
		return
	_time += delta
	var level := Level.of(self)
	_down = -1.0 if level and level.gravity and level.gravity.up else 1.0
	queue_redraw()


func _draw() -> void:
	if _seeds.is_empty():
		return
	var span := absf(ceiling)
	var speed := 0.16 if kind == Kind.LEAVES else 0.22
	var batch := GardenArt.Batch.new(Color(1.0, 1.0, 1.0, 0.42 if kind == Kind.LEAVES else 0.55))
	for s in _seeds:
		var fall := fposmod(s.y + _time * speed * s.w, 1.0)
		var y := ceiling + span * (fall if _down > 0.0 else 1.0 - fall)
		var x := s.x + sin(_time * 1.3 + s.z) * 26.0 - _time * 30.0 * s.w
		x = fposmod(x, width)
		if kind == Kind.LEAVES:
			batch.poly(GardenArt.leaf(Vector2(x, y), 18.0 * s.w, 8.0 * s.w, _time * 2.0 + s.z, 8), false)
		else:
			batch.discs([Vector3(x, y, 3.2 * s.w)], 8)
	batch.draw(self, Color(0, 0, 0, 0))
