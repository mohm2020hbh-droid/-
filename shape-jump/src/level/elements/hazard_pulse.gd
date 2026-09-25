class_name HazardPulse
## Shared "alive" brightness pulse for hazards: cheap modulate animation,
## no redraw. Hazards call [method apply] from _process.

const SPEED := 5.0
const DEPTH := 0.18


static func apply(item: CanvasItem, time_offset: float) -> void:
	var t := Time.get_ticks_msec() / 1000.0 + time_offset
	var k := 1.0 - DEPTH * (0.5 + 0.5 * sin(t * SPEED))
	item.modulate = Color(k, k, k, 1.0)
