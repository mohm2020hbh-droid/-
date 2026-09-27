@tool
class_name Waterfall
extends GardenHazard
## WATERFALL (World 04): a column of water pouring across the corridor, and
## the world's clearest sign of which way is down: it always pours toward
## the floor of the moment (the ground while you run on the ground; toward
## the ceiling while you run there: it rises "from below").
##
## Most waterfalls are scenery. A [member deadly] one swells into a torrent
## on a fixed rhythm: a harmless thread while calm, foam gathering at its
## source as a warning, then a torrent [member width] wide that kills, then
## calm again (the Timeline STEPS curve). Pass it while it is calm, or on the
## surface it does not reach ([member length] shorter than the corridor).
## Its source is the sky side of the moment (anchor SKY) unless anchored.
## Origin = on the ground line at the column's centre. (Mirrored in
## tools/levelgen/levelgen.py: Waterfall.)

@export var width := 70.0:
	set(value):
		width = value
		queue_redraw()
## How far the water reaches from its source (px); the whole corridor when
## at least as long as it.
@export var length := 1000.0:
	set(value):
		length = value
		queue_redraw()
@export var deadly := false
@export_range(0.3, 10.0, 0.05, "suffix:s") var period := 2.4
@export_range(0.0, 0.95, 0.01) var hold_ratio := 0.5
@export_range(0.0, 1.0, 0.01) var phase := 0.0
@export_range(0.0, 1.0, 0.05, "suffix:s") var warning_time := 0.4

## A calm waterfall is a thread this wide; only above twice the inset is it deadly.
const THREAD := 7.0
## Stripe spacing and speed of the flowing water (visual).
const STRIPE := 34.0
const FLOW := 360.0

var _shape: CollisionShape2D
var _box: RectangleShape2D
var _flow := 0.0
var _source_up := true
var _was_warning := false
var _time := 0.0


func _ready() -> void:
	super()
	if deadly:
		_box = RectangleShape2D.new()
		_shape = add_hitbox(_box)
	apply_time(0.0)


## 0 calm .. 1 torrent at [param t] (always 0 for scenery).
func flow_at(t: float) -> float:
	return Timeline.steps(fposmod(t / period + phase, 1.0), hold_ratio) if deadly else 0.0


## World y range (local) the water covers from a source on the ceiling or not.
func span(source_up: bool) -> Vector2:
	var reach := minf(length, absf(ceiling))
	return Vector2(ceiling, ceiling + reach) if source_up else Vector2(-reach, 0.0)


## The world x range (px) it can ever cover (its whole motion).
func x_reach() -> Vector2:
	var x := global_position.x
	return Vector2(x - width * 0.5, x + width * 0.5)


func apply_time(t: float) -> void:
	var start := GardenHazard.cycle_start(t, period, phase)
	_source_up = on_ceiling_at(start)
	var cycle := fposmod(t / period + phase, 1.0)
	_flow = flow_at(t)
	var playing := advance_to(t)
	var warning := deadly and Timeline.steps_warning(cycle, hold_ratio, period, warning_time) and _flow < 0.5
	if playing and warning and not _was_warning:
		cue(&"warning", global_position + Vector2(0.0, ceiling if _source_up else 0.0))
	_was_warning = warning
	queue_redraw()
	if _shape == null:
		return
	var w := width * _flow
	if w <= HITBOX_INSET * 2.0 + THREAD:
		_shape.position = PARKED
		return
	var range_y := span(_source_up)
	_box.size = Vector2(w - HITBOX_INSET * 2.0, range_y.y - range_y.x - HITBOX_INSET * 2.0)
	_shape.position = Vector2(0.0, (range_y.x + range_y.y) * 0.5)


func _process(delta: float) -> void:
	super(delta)
	_time += delta
	# Only the water on screen moves (a few lines per frame).
	var camera := get_viewport().get_camera_2d() if not Engine.is_editor_hint() else null
	if camera == null or absf(camera.get_screen_center_position().x - global_position.x) < 1100.0:
		queue_redraw()


func _draw() -> void:
	var range_y := span(_source_up)
	var w := maxf(width * _flow, THREAD) if deadly else width
	var down := 1.0 if _source_up else -1.0  # Pours away from its source.
	var body := Color(GardenLook.INK_BODY, 0.9) if deadly else Color(GardenLook.INK_BODY, 0.28)
	var rim := GardenLook.INK_RIM if deadly else Color(GardenLook.INK_RIM, 0.55)
	var rect := Rect2(-w * 0.5, range_y.x, w, range_y.y - range_y.x)
	draw_rect(rect, body)
	draw_line(Vector2(-w * 0.5, range_y.x), Vector2(-w * 0.5, range_y.y), rim, 2.0)
	draw_line(Vector2(w * 0.5, range_y.x), Vector2(w * 0.5, range_y.y), rim, 2.0)
	# Stripes streaming toward the floor of the moment.
	var lines := PackedVector2Array()
	var offset := fposmod(_time * FLOW, STRIPE)
	var top := range_y.x
	var height := range_y.y - range_y.x
	var columns := maxi(int(w / 14.0), 1)
	for c in columns:
		var x := -w * 0.5 + w * (c + 0.5) / columns
		var y := fposmod(offset * down + c * 11.0, STRIPE)
		while y < height:
			var a := top + y
			lines.append_array([Vector2(x, a), Vector2(x, minf(a + STRIPE * 0.45, range_y.y))])
			y += STRIPE
	if not lines.is_empty():
		draw_multiline(lines, Color(rim, 0.6), 1.5)
	# Foam where it lands, and a gathering glow at the source before a torrent.
	var land := range_y.y if _source_up else range_y.x
	var source := range_y.x if _source_up else range_y.y
	for i in 3:
		draw_circle(Vector2((i - 1) * w * 0.35, land), 6.0 + 4.0 * _flow, Color(rim, 0.7))
	if deadly and _flow < 0.5 and Timeline.steps_warning(fposmod(_last_time / period + phase, 1.0), hold_ratio,
			period, warning_time):
		Neon.soft_light(self, Vector2(0.0, source), width * 1.3, Color(GardenLook.INK_RIM, 0.55))
		for i in 4:
			draw_circle(Vector2((i - 1.5) * width * 0.3, source + down * 8.0), 7.0, GardenLook.INK_BODY)
