class_name MovementConfig
extends Resource
## Every tunable number of the player's movement (docs/GDD.md §6).
##
## The jump is authored as a height and a time-to-apex; gravity and jump
## velocity are derived from them, so designers tune feel in intuitive units
## and level metrics (gap widths, step heights) stay predictable.

@export_group("Run")
@export_range(100.0, 1200.0, 10.0, "suffix:px/s") var run_speed: float = 520.0

@export_group("Jump")
## Height of the jump arc, measured at the player's feet.
@export_range(32.0, 400.0, 1.0, "suffix:px") var jump_height: float = 150.0
@export_range(0.15, 0.8, 0.01, "suffix:s") var time_to_apex: float = 0.36
## Extra gravity while descending: snappier, heavier landings.
@export_range(1.0, 3.0, 0.05) var fall_gravity_multiplier: float = 1.3
@export_range(200.0, 3000.0, 10.0, "suffix:px/s") var max_fall_speed: float = 1400.0

@export_group("Double Jump")
## Jumps allowed in the air before landing again. Capped at 1: the game has
## exactly two jumps (ground + double), never a third.
@export_range(0, 1) var air_jumps: int = 1
## Height of the double jump, measured from where it starts. It resets the
## vertical speed, so it is the same whether used rising or falling.
@export_range(32.0, 400.0, 1.0, "suffix:px") var double_jump_height: float = 150.0

@export_group("Forgiveness")
## Jump still allowed this long after running off a ledge.
@export_range(0.0, 0.25, 0.01, "suffix:s") var coyote_time: float = 0.08
## A tap this long before landing still triggers the jump on landing.
@export_range(0.0, 0.25, 0.01, "suffix:s") var jump_buffer_time: float = 0.12
## Running into a ledge lower than this lifts the player onto it instead of killing.
@export_range(0.0, 24.0, 1.0, "suffix:px") var ledge_assist: float = 10.0
## In the air, grazing the underside corner of an overhang by less than this
## ducks the player under it instead of killing.
@export_range(0.0, 16.0, 1.0, "suffix:px") var head_clip_assist: float = 6.0

@export_group("Surface Latch (World 04)")
## The second tap of World 04 does not jump again: it latches to the other
## surface if that surface is within this distance of the body's far side.
@export_range(32.0, 400.0, 1.0, "suffix:px") var latch_reach: float = 160.0
## Speed at which a latch sets off toward the other surface (it then
## accelerates with the fall gravity until it lands there).
@export_range(200.0, 2000.0, 10.0, "suffix:px/s") var latch_speed: float = 1000.0


func rise_gravity() -> float:
	return 2.0 * jump_height / (time_to_apex * time_to_apex)


func fall_gravity() -> float:
	return rise_gravity() * fall_gravity_multiplier


## Initial upward speed of a jump (positive number; up is -y in Godot).
func jump_speed() -> float:
	return 2.0 * jump_height / time_to_apex


## Initial upward speed of a double jump (positive number).
func double_jump_speed() -> float:
	return sqrt(2.0 * rise_gravity() * double_jump_height)


## Highest the feet can get above the take-off point: double jump at the apex.
func max_jump_height() -> float:
	return jump_height + (double_jump_height if air_jumps > 0 else 0.0)


## Total airtime of a jump that lands at the same height it started from.
func flat_jump_airtime() -> float:
	return time_to_apex + sqrt(2.0 * jump_height / fall_gravity())


## Physics ticks a latch takes to cover [param distance] px (World 04): the
## motor's own integration (half the gravity before the move, half after),
## so the player, its latch check and the level generator agree to the tick.
func latch_ticks(distance: float, tick: float) -> int:
	var covered := 0.0
	var speed := latch_speed
	var ticks := 0
	while covered < distance and ticks < 120:
		speed = minf(speed + fall_gravity() * tick * 0.5, max_fall_speed)
		covered += speed * tick
		speed = minf(speed + fall_gravity() * tick * 0.5, max_fall_speed)
		ticks += 1
	return ticks
