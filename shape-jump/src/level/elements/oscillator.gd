@tool
class_name Oscillator
extends Node2D
## Motion component: moves its parent Node2D back and forth along
## [member travel] as a pure function of level time, so every attempt sees
## exactly the same timing (docs/ARCHITECTURE.md §5).
##
## Add it as a child of any element: a [Block] on an AnimatableBody2D becomes
## a moving platform, a [Saw] becomes a moving hazard. The path is previewed
## in the editor.

enum Wave { SINE, LINEAR }

## Offset of the far end of the path, relative to the parent's placed position.
@export var travel := Vector2(0, -128):
	set(value):
		travel = value
		queue_redraw()
@export_range(0.2, 20.0, 0.05, "suffix:s") var period := 2.0
## Starting point within the cycle, 0..1.
@export_range(0.0, 1.0, 0.01) var phase := 0.0
@export var wave: Wave = Wave.SINE

var _parent: Node2D
var _origin := Vector2.ZERO


func _ready() -> void:
	_parent = get_parent() as Node2D
	if _parent:
		_origin = _parent.position
	if not Engine.is_editor_hint():
		add_to_group(&"timed")


## Fraction of [member travel] covered at level time [param t] (0 = origin).
func progress_at(t: float) -> float:
	var cycle := fposmod(t / period + phase, 1.0)
	if wave == Wave.SINE:
		return 0.5 - 0.5 * cos(cycle * TAU)
	return 1.0 - absf(cycle * 2.0 - 1.0)


func offset_at(t: float) -> Vector2:
	return travel * progress_at(t)


func apply_time(t: float) -> void:
	if _parent:
		_parent.position = _origin + offset_at(t)


func _draw() -> void:
	if not Engine.is_editor_hint():
		return
	draw_dashed_line(Vector2.ZERO, travel, Color(Palette.UI_MUTED, 0.8), 2.0, 8.0)
	draw_circle(travel, 6.0, Color(Palette.UI_MUTED, 0.8))
