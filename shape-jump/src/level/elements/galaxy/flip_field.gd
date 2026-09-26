@tool
class_name FlipField
extends Node2D
## FLIP FIELD (World 03): a band of space with its own gravity. From its
## left edge to its right edge gravity is [member inside_up]; past the right
## edge it becomes [member outside_up]. Like a gate it spans every height, so
## both changes happen at fixed moments of the level.
##
## [member pit] draws it as a GRAVITY PIT: a short well over a gap that
## throws you to the other surface and lets you go on the far side.
## Origin = on the ground line at the left edge.

@export var length := 256.0:
	set(value):
		length = value
		queue_redraw()
@export var top := -384.0:
	set(value):
		top = value
		queue_redraw()
@export var inside_up := true:
	set(value):
		inside_up = value
		queue_redraw()
@export var outside_up := false
@export var pit := false:
	set(value):
		pit = value
		queue_redraw()

## The drift of the chevrons (px/s), purely visual.
const DRIFT := 90.0
const SPACING := 64.0

var _flow: _Flow


func _ready() -> void:
	z_index = -2
	_flow = _Flow.new()
	add_child(_flow, false, Node.INTERNAL_MODE_FRONT)
	_flow.setup(length, top, inside_up, pit)
	if not Engine.is_editor_hint():
		Level.join(self)


func _enter_tree() -> void:
	if is_node_ready() and not Engine.is_editor_hint():
		Level.join(self)


func _exit_tree() -> void:
	if not Engine.is_editor_hint():
		Level.leave(self)


func gravity_events() -> Array[Vector2]:
	var x := global_position.x
	return [Vector2(x, 1.0 if inside_up else 0.0), Vector2(x + length, 1.0 if outside_up else 0.0)]


## The chevrons drift toward the field's floor (a transform, no redraw).
func apply_time(t: float) -> void:
	var dir := -1.0 if inside_up else 1.0
	_flow.position.y = dir * fposmod(t * DRIFT, SPACING)


func _draw() -> void:
	var color := GalaxyArt.state_color(inside_up)
	var rect := Rect2(0.0, top, length, -top)
	draw_rect(rect, Color(color, 0.07 if not pit else 0.12))
	for x: float in [0.0, length]:
		draw_line(Vector2(x, top), Vector2(x, 0.0), Color(color, 0.7), 2.0)
		Neon.soft_light(self, Vector2(x, top * 0.5), 60.0, Color(color, 0.18))
	if pit:
		# A well: rings narrowing toward the side it throws you to.
		var toward := top if inside_up else 0.0
		for i in 4:
			var k := (i + 1) / 5.0
			var y := lerpf(top * 0.5, toward, k)
			var w := length * (0.5 - 0.1 * i)
			# Domes toward the ceiling (upper half circles) or bowls toward the ground.
			var from := PI if inside_up else 0.0
			draw_arc(Vector2(length * 0.5, y), w * 0.5, from, from + PI, 24,
				Color(color, 0.25 + 0.1 * i), 2.0, true)


## Chevrons in the field's gravity, drawn once over a span one spacing
## taller than the field and slid by the parent (no clipping needed: the
## overhang stays inside the floor and ceiling blocks).
class _Flow extends Node2D:
	var length := 256.0
	var top := -384.0
	var up := true
	var pit := false

	func setup(field_length: float, field_top: float, inside_up: bool, is_pit: bool) -> void:
		length = field_length
		top = field_top
		up = inside_up
		pit = is_pit
		queue_redraw()

	func _draw() -> void:
		var color := Color(GalaxyArt.state_color(up), 0.35)
		var x := FlipField.SPACING * 0.5
		while x < length:
			GalaxyArt.chevrons(self, x, top + 24.0, -24.0, up, color, FlipField.SPACING, 10.0)
			x += FlipField.SPACING * (1.5 if not pit else 1.0)
