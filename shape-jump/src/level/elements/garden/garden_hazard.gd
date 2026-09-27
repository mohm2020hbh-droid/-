@tool
class_name GardenHazard
extends Hazard
## Base of World 04's obstacles (THE INVERTED GARDEN, docs/GDD.md §9D).
##
## Every one lives in the corridor between the ground (the origin's y) and
## the ceiling ([member ceiling] px above it, negative) and is anchored to a
## side of it:
##   GROUND / CEILING  always that surface (roots of the earth, hanging vines);
##   FLOOR             the surface the player runs on, as of the start of
##                     each cycle (a flower that opens where you are);
##   SKY               the side across from it, as of the start of each
##                     cycle (a rock falls from the sky onto your floor).
## The side is read from the level's surface log ([method Level.surface_up_at])
## once per cycle and kept for the whole cycle, so what the warning showed is
## what happens, a latch mid-cycle never makes an obstacle jump across the
## corridor, and the element stays a pure function of the clock and the log
## (a respawn replays it exactly).
##
## Drawn in ink ([GardenArt], [GardenLook]): dark bodies, bright rims, tinted
## per colour state through the node group [code]garden_ink[/code].

enum Anchor { GROUND, CEILING, FLOOR, SKY }

@export var anchor: Anchor = Anchor.GROUND:
	set(value):
		anchor = value
		queue_redraw()
## The ceiling's underside, relative to the origin on the ground line (px,
## negative: up).
@export var ceiling := -320.0:
	set(value):
		ceiling = value
		queue_redraw()


func _ready() -> void:
	super()
	GardenLook.tint(self, &"garden_ink")


## Adds [param item] (a drawing child) to the ink role.
func ink(item: CanvasItem) -> void:
	GardenLook.tint(item, &"garden_ink")


## Level time at which the cycle containing [param t] started.
static func cycle_start(t: float, period: float, phase: float) -> float:
	return (floorf(t / period + phase) - phase) * period


## True when this element sits on the ceiling for a cycle that started at
## [param start] (see the class notes).
func on_ceiling_at(start: float) -> bool:
	match anchor:
		Anchor.GROUND:
			return false
		Anchor.CEILING:
			return true
	var level := Level.of(self)
	var floor_up := level.surface_up_at(start) if level and not Engine.is_editor_hint() else false
	return floor_up if anchor == Anchor.FLOOR else not floor_up


## y (local) of the ground or the ceiling.
func surface_y(on_ceiling: bool) -> float:
	return ceiling if on_ceiling else 0.0


## +1 from the ceiling into the corridor (down), -1 from the ground (up).
static func into(on_ceiling: bool) -> float:
	return 1.0 if on_ceiling else -1.0


## Collision shapes are parked here while an element is harmless.
const PARKED := Vector2(0.0, 100000.0)
