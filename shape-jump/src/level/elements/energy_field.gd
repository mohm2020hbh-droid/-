@tool
class_name EnergyField
extends Hazard
## ENERGY FIELD (docs/GDD.md §7): a band of energy that switches on and off
## on a fixed cycle. Drawn as a curtain of crooked crimson strands arcing
## between two clusters of void crystal (World 01's organic look): off, only
## the crystals and a dashed trace of the band remain (harmless); for
## [member warning_time] before switching on the strands flicker (the
## telegraph); on, the curtain fills the band and touching it is deadly.
## Its outer strands run along the band's sides, so its reach reads exactly.
## Give it an [Oscillator] child to move it. Origin = top-left corner.

enum State { OFF, WARNING, ON }

## Room (px) the crystal clusters take at each end of the band.
const CRYSTAL_DEPTH := 14.0

@export var size := Vector2(64, 192):
	set(value):
		size = value.max(Vector2(16, 16))
		_rebuild()
@export_range(0.4, 20.0, 0.05, "suffix:s") var period := 2.0
## Fraction of the cycle during which the field is on (deadly).
@export_range(0.1, 0.9, 0.01) var on_ratio := 0.5
## Starting point within the cycle, 0..1.
@export_range(0.0, 1.0, 0.01) var phase := 0.0
@export_range(0.0, 1.5, 0.05, "suffix:s") var warning_time := 0.35

var _shape_node: CollisionShape2D
var _state: State = State.ON
var _flicker_on := false
var _seed := 0
## The picture, built once per size: the strands (and a second set for the
## flicker), and the crystal shards at both ends.
var _strands := PackedVector2Array()
var _strands_alt := PackedVector2Array()
var _crystals: Array[PackedVector2Array] = []


func _ready() -> void:
	super()
	_seed = OrganicArt.seed_of(position, 6)  # Where it rests: the same picture every attempt.
	_rebuild()
	apply_time(0.0)


func get_rect() -> Rect2:
	return Rect2(Vector2.ZERO, size)


func state_at(t: float) -> State:
	var u := fposmod(t / period + phase, 1.0) * period
	if u < on_ratio * period:
		return State.ON
	if u >= period - warning_time:
		return State.WARNING
	return State.OFF


func is_on() -> bool:
	return _state == State.ON


func apply_time(t: float) -> void:
	if _shape_node == null:
		return
	var state := state_at(t)
	var flicker := state == State.WARNING and fmod(t * 14.0, 1.0) < 0.5
	_shape_node.disabled = state != State.ON
	if state != _state or flicker != _flicker_on:
		_state = state
		_flicker_on = flicker
		queue_redraw()


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _shape_node == null:
		_shape_node = add_hitbox(RectangleShape2D.new())
	_crystals.clear()
	(_shape_node.shape as RectangleShape2D).size = size - Vector2.ONE * HITBOX_INSET * 2.0
	_shape_node.position = size * 0.5
	queue_redraw()


func _build_art() -> void:
	_strands.clear()
	_strands_alt.clear()
	_crystals.clear()
	var count := maxi(int(size.x / 12.0), 2)
	var amp := minf(size.x / count * 0.45, 6.0)
	for i in count:
		var x := lerpf(3.0, size.x - 3.0, float(i) / (count - 1))
		# The outer strands hug the sides; the inner ones wander.
		var wander := amp * (0.3 if i == 0 or i == count - 1 else 1.0)
		OrganicArt.crooked(_strands, Vector2(x, CRYSTAL_DEPTH * 0.5), Vector2(x, size.y - CRYSTAL_DEPTH * 0.5),
			_seed + i * 131, wander, 16.0)
		OrganicArt.crooked(_strands_alt, Vector2(x, CRYSTAL_DEPTH * 0.5), Vector2(x, size.y - CRYSTAL_DEPTH * 0.5),
			_seed + i * 131 + 57, wander, 16.0)
	# Emitters: a few shards grown into each end of the band.
	var shards := clampi(int(size.x / 18.0), 2, 8)
	for end in 2:
		var y := 0.0 if end == 0 else size.y
		var dir := Vector2.DOWN if end == 0 else Vector2.UP
		for i in shards:
			var x := size.x * (i + 0.5) / shards
			var length := CRYSTAL_DEPTH * (0.8 + 0.7 * OrganicArt.rand(_seed, 40 + end * 16 + i))
			_crystals.append(OrganicArt.shard(Vector2(x, y), dir, length, size.x / shards * 1.1, _seed + end * 16 + i))


func _draw() -> void:
	if _crystals.is_empty():
		_build_art()
	var rect := get_rect()
	match _state:
		State.ON:
			draw_rect(rect, Color(Palette.HAZARD, 0.1))
			draw_rect(rect.grow(-5.0), Color(Palette.HAZARD, 0.08))
			draw_multiline(_strands, Color(Palette.HAZARD, 0.3), 5.0)
			draw_multiline(_strands, Color(Palette.HAZARD_CORE, 0.85), 1.5)
			_draw_crystals(Palette.HAZARD_CORE, 1.2)
		State.WARNING when _flicker_on:
			draw_rect(rect, Color(Palette.HAZARD, 0.05))
			draw_multiline(_strands_alt, Color(Palette.HAZARD_CORE, 0.45), 1.5)
			_draw_crystals(Palette.HAZARD_CORE, 1.0)
		_:
			for x: float in [1.0, size.x - 1.0]:
				draw_dashed_line(Vector2(x, CRYSTAL_DEPTH), Vector2(x, size.y - CRYSTAL_DEPTH),
					Color(Palette.NEON_DIM, 0.6), 2.0, 12.0)
			_draw_crystals(Palette.NEON_DIM, 0.4)


func _draw_crystals(rim: Color, glow: float) -> void:
	for crystal in _crystals:
		draw_colored_polygon(crystal, Palette.HAZARD_BODY)
		Neon.polyline(self, crystal, rim, 1.5, glow, true)
