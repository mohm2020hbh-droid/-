@tool
class_name GravityEcho
extends Node2D
## GRAVITY ECHO (World 03): a dashed ghost that shows, before a flip, what
## will matter after it: the ledge you will land on, the trap waiting on the
## other surface. It glows while gravity is not yet [member for_up] and fades
## once it is (its job is done). No collision: a picture, never a thing.
## Origin = the top-left of the marked area.

@export var size := Vector2(128, 64):
	set(value):
		size = value
		queue_redraw()
## The gravity in which the marked thing matters.
@export var for_up := true:
	set(value):
		for_up = value
		queue_redraw()
## A hazard ahead (magenta) rather than a place to land (the state colour).
@export var danger := false:
	set(value):
		danger = value
		queue_redraw()


func _ready() -> void:
	z_index = -2
	if not Engine.is_editor_hint():
		Level.join(self)


func _enter_tree() -> void:
	if is_node_ready() and not Engine.is_editor_hint():
		Level.join(self)


func _exit_tree() -> void:
	if not Engine.is_editor_hint():
		Level.leave(self)


func apply_time(t: float) -> void:
	var level := Level.of(self)
	var waiting := level != null and level.gravity_up_at(t) != for_up
	modulate.a = (0.55 + 0.35 * sin(t * 6.0)) if waiting else 0.12


func _draw() -> void:
	var color := GalaxyArt.DANGER if danger else GalaxyArt.state_color(for_up)
	var rect := Rect2(Vector2.ZERO, size)
	draw_rect(rect, Color(color, 0.06))
	Neon.dashed_rect(self, rect, Color(color, 0.8), 2.0, 9.0)
	# A small chevron toward the surface this belongs to.
	var c := rect.get_center()
	var dir := -1.0 if for_up else 1.0
	draw_polyline(PackedVector2Array([c + Vector2(-10.0, -dir * 5.0), c + Vector2(0.0, dir * 7.0),
		c + Vector2(10.0, -dir * 5.0)]), Color(color, 0.9), 2.5, true)
