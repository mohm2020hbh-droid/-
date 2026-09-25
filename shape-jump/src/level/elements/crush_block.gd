@tool
class_name CrushBlock
extends Hazard
## CRUSH BLOCK (docs/GDD.md §7): a heavy toothed block that waits, arms
## (shakes, heats up, a warning sounds), slams along [member travel], holds
## shut, then slowly withdraws. Works from any side: down from a ceiling, up
## from a pit, or sideways. Its destination is always marked with a dashed
## outline that brightens while it arms.
##
## Origin = top-left corner of the block in its open position. Crush blocks
## draw just behind the level geometry, so a ceiling or the ground hides the
## part that is retracted into it.

enum Phase { OPEN, ARMING, SLAM, CLOSED, RETURN }

const SHAKE_AMPLITUDE := 2.5

@export var size := Vector2(128, 192):
	set(value):
		size = value.max(Vector2(16, 16))
		_rebuild()
## From the open to the closed position.
@export var travel := Vector2(0, 192):
	set(value):
		travel = value
		_rebuild()
@export_range(0.5, 20.0, 0.05, "suffix:s") var period := 2.6
## Starting point within the cycle, 0..1.
@export_range(0.0, 1.0, 0.01) var phase := 0.0
@export_range(0.0, 2.0, 0.05, "suffix:s") var warning_time := 0.45
@export_range(0.02, 1.0, 0.01, "suffix:s") var slam_time := 0.1
@export_range(0.0, 5.0, 0.05, "suffix:s") var closed_time := 0.35
@export_range(0.05, 5.0, 0.05, "suffix:s") var return_time := 0.55

var _head: SlabArt
var _shape: CollisionShape2D
var _sparks: CPUParticles2D
var _phase_now: Phase = Phase.OPEN


func _ready() -> void:
	super()
	_rebuild()
	apply_time(0.0)


func _get_configuration_warnings() -> PackedStringArray:
	if get_open_time() < 0.0:
		return ["warning + slam + closed + return time is longer than the period."]
	return []


## Time spent fully open in each cycle.
func get_open_time() -> float:
	return period - warning_time - slam_time - closed_time - return_time


## Seconds into the cycle at level time [param t].
func cycle_time(t: float) -> float:
	return fposmod(t + phase * period, period)


func phase_at(t: float) -> Phase:
	var u := cycle_time(t)
	var open := get_open_time()
	if u < open:
		return Phase.OPEN
	if u < open + warning_time:
		return Phase.ARMING
	if u < open + warning_time + slam_time:
		return Phase.SLAM
	if u < open + warning_time + slam_time + closed_time:
		return Phase.CLOSED
	return Phase.RETURN


## 0 = open, 1 = closed.
func closure_at(t: float) -> float:
	var u := cycle_time(t)
	var slam_start := get_open_time() + warning_time
	match phase_at(t):
		Phase.SLAM:
			var k := (u - slam_start) / slam_time
			return k * k  # Accelerates into the hit.
		Phase.CLOSED:
			return 1.0
		Phase.RETURN:
			var k := (u - slam_start - slam_time - closed_time) / return_time
			return 1.0 - smoothstep(0.0, 1.0, k)
	return 0.0


func apply_time(t: float) -> void:
	if _head == null:
		return
	var offset := travel * closure_at(t)
	var now := phase_at(t)
	var shake := Vector2.ZERO
	if now == Phase.ARMING:
		# Deterministic tremble across the direction of travel.
		shake = travel.orthogonal().normalized() * sin(cycle_time(t) * 95.0) * SHAKE_AMPLITUDE
	_head.position = offset + shake
	_head.self_modulate = Color(1.7, 1.7, 1.7) if now == Phase.ARMING and fmod(t * 8.0, 1.0) < 0.5 else Color.WHITE
	_shape.position = offset + size * 0.5
	if now != _phase_now:
		var playing := advance_to(t)
		if playing and now == Phase.ARMING:
			cue(&"warning", global_position + offset + size * 0.5)
		elif playing and now == Phase.CLOSED:
			_sparks.restart()
			cue(&"slam", _sparks.global_position)
		_phase_now = now
		queue_redraw()
	else:
		advance_to(t)


func _rebuild() -> void:
	if not is_inside_tree():
		return
	if _head == null:
		_head = SlabArt.new()
		add_child(_head, false, Node.INTERNAL_MODE_FRONT)
		_shape = add_hitbox(RectangleShape2D.new())
		_sparks = _make_sparks()
		add_child(_sparks, false, Node.INTERNAL_MODE_FRONT)
	var leading := HazardArt.leading_face(travel)
	_head.rect = Rect2(Vector2.ZERO, size)
	_head.hot_face = leading
	_head.with_teeth = true
	(_shape.shape as RectangleShape2D).size = size - Vector2.ONE * HITBOX_INSET * 2.0
	# Sparks fly from the middle of the leading face at the closed position.
	var face := HazardArt.face_of(Rect2(travel, size), leading)
	_sparks.position = (face[0] + face[1]) * 0.5
	_sparks.direction = -travel.normalized() if travel != Vector2.ZERO else Vector2.UP
	_sparks.emission_rect_extents = Vector2(size.x * 0.4, 2.0) if absf(travel.y) >= absf(travel.x) else Vector2(2.0, size.y * 0.4)
	update_configuration_warnings()
	queue_redraw()


func _make_sparks() -> CPUParticles2D:
	var sparks := CPUParticles2D.new()
	sparks.emitting = false
	sparks.one_shot = true
	sparks.explosiveness = 1.0
	sparks.amount = 18
	sparks.lifetime = 0.45
	sparks.emission_shape = CPUParticles2D.EMISSION_SHAPE_RECTANGLE
	sparks.spread = 70.0
	sparks.gravity = Vector2(0, 700)
	sparks.initial_velocity_min = 120.0
	sparks.initial_velocity_max = 360.0
	sparks.scale_amount_min = 2.0
	sparks.scale_amount_max = 4.0
	sparks.color = Palette.HAZARD_CORE
	return sparks


func _draw() -> void:
	# The destination, always visible, brighter while arming or shut.
	var hot := _phase_now == Phase.ARMING or _phase_now == Phase.SLAM or _phase_now == Phase.CLOSED
	var target := Rect2(travel, size)
	draw_rect(target, Color(Palette.HAZARD, 0.14 if hot else 0.05))
	Neon.dashed_rect(self, target, Color(Palette.HAZARD, 0.85 if hot else 0.35), 2.0, 10.0)
