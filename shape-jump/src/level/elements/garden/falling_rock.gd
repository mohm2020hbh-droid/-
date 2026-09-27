@tool
class_name FallingRock
extends GardenHazard
## FALLING ROCK (World 04): a stone breaks loose from the sky side of the
## corridor and falls straight across it, on a fixed rhythm. Never without
## warning: for [member warning_time] its shadow darkens on the surface it
## will hit, dust trickles where it will break loose, a crack opens, and the
## level hears it (the "warning" cue); only then does it fall.
## With the default anchor SKY it falls from the side across from the floor
## you run on (read at the start of each cycle, then kept: a latch mid-fall
## never turns a rock around), so on the ceiling it comes up from below.
## [member ice] makes it an ice shard (the snow sections): same rules.
## Origin = on the ground line at the column's x. (Mirrored in
## tools/levelgen/levelgen.py: FallingRock.)

@export_range(8.0, 64.0, 1.0, "suffix:px") var radius := 24.0
@export_range(0.3, 10.0, 0.05, "suffix:s") var period := 1.8
@export_range(0.0, 1.0, 0.01) var phase := 0.0
@export_range(0.1, 2.0, 0.05, "suffix:s") var warning_time := 0.5
@export var speed := 900.0
@export var ice := false:
	set(value):
		ice = value
		if _stone:
			_stone.setup(radius, ice, int(absf(position.x)))

const HITBOX_SCALE := 0.75

var _stone: _Stone
var _omen: _Omen
var _shape: CollisionShape2D
var _last_into := INF


func _ready() -> void:
	super()
	_omen = _Omen.new()
	add_child(_omen, false, Node.INTERNAL_MODE_FRONT)
	_stone = _Stone.new()
	add_child(_stone, false, Node.INTERNAL_MODE_FRONT)
	ink(_omen)
	ink(_stone)
	_stone.setup(radius, ice, int(absf(position.x)))
	var circle := CircleShape2D.new()
	circle.radius = radius * HITBOX_SCALE
	_shape = add_hitbox(circle)
	apply_time(0.0)


## y (local) of the stone [param into_cycle] s into a cycle falling from the
## ceiling ([param from_ceiling]) or the ground; NAN when it is not falling.
func stone_y(into_cycle: float, from_ceiling: bool) -> float:
	var moving := into_cycle - warning_time
	var travel := absf(ceiling) + radius * 2.0
	if moving < 0.0 or moving * speed > travel:
		return NAN
	var origin := ceiling - radius if from_ceiling else radius
	return origin + (1.0 if from_ceiling else -1.0) * moving * speed


## The world x range (px) it can ever cover (its whole motion).
func x_reach() -> Vector2:
	var x := global_position.x
	return Vector2(x - radius, x + radius)


func apply_time(t: float) -> void:
	if _stone == null:
		return
	var start := GardenHazard.cycle_start(t, period, phase)
	var into_cycle := t - start
	var from_ceiling := on_ceiling_at(start)
	var y := stone_y(into_cycle, from_ceiling)
	var playing := advance_to(t)
	var falling := not is_nan(y)
	_stone.visible = falling
	_shape.position = Vector2(0.0, y) if falling else PARKED
	if falling:
		_stone.position = Vector2(0.0, y)
		_stone.rotation = t * (1.4 if ice else 2.2)
	var warning := into_cycle < warning_time
	var landing := ceiling if not from_ceiling else 0.0
	_omen.visible = warning or falling
	_omen.show_omen(from_ceiling, ceiling, clampf(into_cycle / warning_time, 0.0, 1.0), warning, radius)
	if warning and playing and into_cycle < _last_into:
		cue(&"warning", global_position + Vector2(0.0, landing))
	_last_into = into_cycle


func _draw() -> void:
	# The column: faint dots, always visible.
	var y := ceiling + 12.0
	while y < -8.0:
		draw_circle(Vector2(0.0, y), 1.6, Color(GardenLook.INK_BODY, 0.22))
		y += 22.0


class _Stone extends Node2D:
	var radius := 24.0
	var ice := false
	var seed_value := 1

	func setup(r: float, is_ice: bool, s: int) -> void:
		radius = r
		ice = is_ice
		seed_value = s
		queue_redraw()

	func _draw() -> void:
		if ice:
			# A shard of ice: a long crystal, sharp and outlined (never
			# mistaken for the soft snow drifting behind it).
			var shard := PackedVector2Array([Vector2(0.0, -radius * 1.35), Vector2(radius * 0.8, -radius * 0.2),
				Vector2(radius * 0.55, radius * 1.1), Vector2(-radius * 0.55, radius * 1.1), Vector2(-radius * 0.8, -radius * 0.2)])
			draw_colored_polygon(shard, GardenLook.INK_BODY)
			draw_polyline(GardenArt.closed(shard), GardenLook.INK_RIM, 3.0, true)
			draw_line(Vector2(0.0, -radius * 1.2), Vector2(0.0, radius), Color(GardenLook.INK_RIM, 0.6), 1.5, true)
			draw_line(Vector2(-radius * 0.6, -radius * 0.1), Vector2(radius * 0.6, -radius * 0.1),
				Color(GardenLook.INK_RIM, 0.6), 1.5, true)
			return
		var stone := GardenArt.stone(Vector2.ZERO, radius * 1.05, seed_value + 29)
		draw_colored_polygon(stone, GardenLook.INK_BODY)
		draw_polyline(GardenArt.closed(stone), GardenLook.INK_RIM, 2.5, true)
		# Moss and a crack: a stone of the garden, not a bullet.
		draw_arc(Vector2(-radius * 0.2, -radius * 0.2), radius * 0.55, PI * 1.1, PI * 1.7, 8,
			Color(GardenLook.INK_RIM, 0.6), 2.0, true)
		draw_polyline(PackedVector2Array([Vector2(radius * 0.1, -radius * 0.7), Vector2(radius * 0.25, -radius * 0.1),
			Vector2(radius * 0.05, radius * 0.4)]), Color(GardenLook.INK_RIM, 0.45), 1.5, true)


## The warning, on both ends of the column: dust and a crack where the stone
## breaks loose, a shadow growing where it will land.
class _Omen extends Node2D:
	var _from_ceiling := true
	var _ceiling := -320.0
	var _k := 0.0
	var _warning := true
	var _radius := 24.0

	func show_omen(from_ceiling: bool, ceiling: float, k: float, warning: bool, radius: float) -> void:
		if from_ceiling == _from_ceiling and is_equal_approx(k, _k) and warning == _warning:
			return
		_from_ceiling = from_ceiling
		_ceiling = ceiling
		_k = k
		_warning = warning
		_radius = radius
		queue_redraw()

	func _draw() -> void:
		var source := _ceiling if _from_ceiling else 0.0
		var target := 0.0 if _from_ceiling else _ceiling
		var down := 1.0 if _from_ceiling else -1.0
		# The shadow on the target surface, darker and wider as it comes.
		var w := _radius * (1.2 + 0.8 * _k)
		var shadow := PackedVector2Array()
		for i in 16:
			var a := TAU * i / 16.0
			shadow.append(Vector2(cos(a) * w, target - down * 3.0 + sin(a) * 5.0))
		draw_colored_polygon(shadow, Color(GardenLook.INK_BODY, 0.25 + 0.45 * _k))
		if not _warning:
			return
		# The crack and falling dust at the source.
		var crack := PackedVector2Array([Vector2(-24.0, source), Vector2(-8.0, source + down * 6.0),
			Vector2(0.0, source - down * 3.0), Vector2(10.0, source + down * 7.0), Vector2(24.0, source)])
		draw_polyline(crack, GardenLook.INK_RIM, 3.0, true)
		for i in 5:
			var along := fmod(_k * 3.0 + i * 0.2, 1.0)
			draw_circle(Vector2((i - 2) * 7.0, source + down * (10.0 + along * 70.0)), 2.4,
				Color(GardenLook.INK_BODY, 0.7 * (1.0 - along)))
