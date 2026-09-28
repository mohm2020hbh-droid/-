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

@export_group("Surface Attach (World 04)")
## World 04 has no jump at all: two taps are ONE gesture, the SURFACE ATTACH.
## The second tap must come within this time of the first, or the first is
## forgotten (a lone tap does nothing).
@export_range(0.1, 0.8, 0.01, "suffix:s") var attach_window: float = 0.3
## Farthest surface (px of free space from the body's far side) an attach
## can reach.
@export_range(64.0, 800.0, 1.0, "suffix:px") var attach_reach: float = 360.0
## The crossing: it sets off at this speed toward the other surface...
@export_range(200.0, 3000.0, 10.0, "suffix:px/s") var attach_speed: float = 1000.0
## ...and speeds up at this rate (a pull, not a jump)...
@export_range(0.0, 20000.0, 100.0, "suffix:px/s²") var attach_accel: float = 6000.0
## ...up to this speed.
@export_range(200.0, 4000.0, 10.0, "suffix:px/s") var attach_max_speed: float = 1800.0


## Speed (px/s) of a crossing on its [param tick]-th physics tick (0: the
## tick it starts). The player, its attach check and the level generator all
## use this, so they agree to the tick.
func attach_speed_at(tick: int, dt: float) -> float:
	return minf(attach_speed + attach_accel * tick * dt, attach_max_speed)


## Physics ticks a crossing of [param distance] px takes.
func attach_ticks(distance: float, dt: float) -> int:
	var covered := 0.0
	var ticks := 0
	while covered < distance and ticks < 240:
		covered += attach_speed_at(ticks, dt) * dt
		ticks += 1
	return ticks


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
