@tool
class_name WhiteoutZone
extends Node2D
## WHITEOUT (docs/GDD.md §7, World 02): while the player runs between this
## node's x and x + [member length], the screen floods white on a fixed
## rhythm: [member warning_time] of pulsing glare (the telegraph), then
## [member flash_time] of near-total white, then a short fade. Only secondary
## geometry drowns: the WhiteoutVeil redraws every hazard and every real
## ledge in black on top of the white, and the player stays above it, so a
## whiteout never hides anything that can kill. It changes nothing in the
## physics. Origin = start of the zone (x); y unused.

@export_range(64.0, 20000.0, 1.0, "suffix:px") var length := 1024.0:
	set(value):
		length = value
		queue_redraw()
@export_range(0.5, 20.0, 0.05, "suffix:s") var period := 2.4
@export_range(0.1, 5.0, 0.05, "suffix:s") var flash_time := 0.9
@export_range(0.0, 2.0, 0.05, "suffix:s") var warning_time := 0.45
@export_range(0.0, 1.0, 0.01) var phase := 0.0

const FADE := 0.3


func _ready() -> void:
	add_to_group(&"whiteout_zones")


## Whiteout strength 0..1 at level time [param t] for a player at [param x].
func intensity(t: float, x: float) -> float:
	if x < global_position.x or x > global_position.x + length:
		return 0.0
	var u := fposmod(t / period + phase, 1.0) * period
	if u < warning_time:
		return 0.18 + 0.14 * sin(u * 40.0)  # Glare pulses: it is coming.
	u -= warning_time
	if u < flash_time:
		return minf(u / 0.08, 1.0)
	u -= flash_time
	if u < FADE:
		return 1.0 - u / FADE
	return 0.0


func _draw() -> void:
	if Engine.is_editor_hint():
		draw_rect(Rect2(0, -720, length, 900), Color(1, 1, 1, 0.08))
