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

@export_group("Forgiveness")
## Jump still allowed this long after running off a ledge.
@export_range(0.0, 0.25, 0.01, "suffix:s") var coyote_time: float = 0.08
## A tap this long before landing still triggers the jump on landing.
@export_range(0.0, 0.25, 0.01, "suffix:s") var jump_buffer_time: float = 0.12
## Running into a ledge lower than this lifts the player onto it instead of killing.
@export_range(0.0, 24.0, 1.0, "suffix:px") var ledge_assist: float = 10.0


func rise_gravity() -> float:
	return 2.0 * jump_height / (time_to_apex * time_to_apex)


func fall_gravity() -> float:
	return rise_gravity() * fall_gravity_multiplier


## Initial upward speed of a jump (positive number; up is -y in Godot).
func jump_speed() -> float:
	return 2.0 * jump_height / time_to_apex


## Total airtime of a jump that lands at the same height it started from.
func flat_jump_airtime() -> float:
	return time_to_apex + sqrt(2.0 * jump_height / fall_gravity())
