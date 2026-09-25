extends TestCase
## Drives PlayerMotor against a synthetic flat floor at y = 0 (feet position),
## at the real 60 Hz tick, without any physics server.

const DT := 1.0 / 60.0
const NONE := PlayerMotor.Jump.NONE
const GROUND := PlayerMotor.Jump.GROUND
const AIR := PlayerMotor.Jump.AIR

var config: MovementConfig
var motor: PlayerMotor
var feet_y := 0.0
var on_floor := true


func before_each() -> void:
	config = MovementConfig.new()
	motor = PlayerMotor.new(config)
	motor.reset(true)
	motor.running = true
	feet_y = 0.0
	on_floor = true


## One tick against a floor that exists only where [param floor_present] says.
## Returns the jump that started during the tick.
func _step(floor_present: bool = true) -> PlayerMotor.Jump:
	var v := motor.begin_tick(on_floor, DT)
	feet_y += v.y * DT
	if floor_present and feet_y >= 0.0:
		feet_y = 0.0
		v.y = 0.0
		on_floor = true
	else:
		on_floor = false
	motor.end_tick(v, DT, on_floor)
	return motor.jump_this_tick


## Ticks until the motor starts rising no more (the apex); returns its height.
func _rise_to_apex() -> float:
	var apex := feet_y
	while motor.velocity.y < 0.0:
		_step()
		apex = minf(apex, feet_y)
	return -apex


func _land() -> void:
	for i in 300:
		if on_floor:
			return
		_step()


func test_derived_values_match_formulas() -> void:
	assert_near(config.rise_gravity(), 2.0 * 150.0 / (0.36 * 0.36), 0.01)
	assert_near(config.jump_speed(), 2.0 * 150.0 / 0.36, 0.01)
	assert_near(config.fall_gravity(), config.rise_gravity() * 1.3, 0.01)
	assert_near(config.double_jump_speed(), config.jump_speed(), 0.01, "same height -> same take-off speed")
	assert_near(config.max_jump_height(), 300.0, 0.001)


func test_jump_reaches_configured_height() -> void:
	motor.request_jump()
	assert_eq(_step(), GROUND)
	assert_near(_rise_to_apex(), config.jump_height, 0.5, "apex height")


func test_airtime_matches_design_metric() -> void:
	motor.request_jump()
	_step()
	var ticks := 1
	while not on_floor and ticks < 200:
		_step()
		ticks += 1
	assert_near(ticks * DT, config.flat_jump_airtime(), DT * 1.5, "airtime")


func test_run_speed_and_scale() -> void:
	_step()
	assert_eq(motor.velocity.x, 520.0)
	motor.speed_scale = 1.5
	_step()
	assert_eq(motor.velocity.x, 780.0)
	motor.running = false
	_step()
	assert_eq(motor.velocity.x, 0.0)


func test_double_jump_at_the_apex_doubles_the_height() -> void:
	motor.request_jump()
	_step()
	var first_apex := _rise_to_apex()
	motor.request_jump()
	assert_eq(_step(), AIR, "a tap in the air is the double jump")
	var total_apex := _rise_to_apex()
	assert_near(first_apex, 150.0, 0.5)
	assert_near(total_apex, config.max_jump_height(), 1.0, "jump + double jump at the apex")


func test_double_jump_height_is_the_same_rising_or_falling() -> void:
	motor.request_jump()
	_step()
	while motor.velocity.y < 300.0:  # Past the apex, falling.
		_step()
	assert_true(feet_y < -100.0, "still well above the floor")
	var start := -feet_y
	motor.request_jump()
	assert_eq(_step(), AIR)
	assert_near(_rise_to_apex() - start, config.double_jump_height, 1.0, "the double jump resets the fall")


func test_there_is_never_a_third_jump() -> void:
	motor.request_jump()
	_step()
	for i in 10:
		_step()
	motor.request_jump()
	assert_eq(_step(), AIR)
	for i in 10:
		_step()
	var speed_before := motor.velocity.y
	for i in 5:  # Hammer the button.
		motor.request_jump()
		assert_eq(_step(), NONE, "no jump left in the air")
	assert_true(motor.velocity.y > speed_before, "no upward boost, still decelerating")
	assert_eq(motor.air_jumps_left, 0)


func test_double_jump_comes_back_after_landing() -> void:
	motor.request_jump()
	_step()
	motor.request_jump()
	_step()
	assert_eq(motor.air_jumps_left, 0, "spent")
	_land()
	assert_eq(motor.air_jumps_left, 1, "restored on landing")
	motor.request_jump()
	assert_eq(_step(), GROUND)
	motor.request_jump()
	assert_eq(_step(), AIR, "double jump available again")


func test_taps_in_one_tick_become_jumps_on_consecutive_ticks() -> void:
	# A slow frame can deliver two taps before one physics tick: both count,
	# exactly as they would at a high frame rate.
	motor.request_jump()
	motor.request_jump()
	assert_eq(_step(), GROUND)
	assert_eq(_step(), AIR)
	assert_eq(_step(), NONE)


func test_a_third_simultaneous_tap_does_not_fire_in_the_air() -> void:
	for i in 3:
		motor.request_jump()
	assert_eq(_step(), GROUND)
	assert_eq(_step(), AIR)
	var jumps := 0
	for i in 200:
		if _step() != NONE:
			jumps += 1
	assert_eq(jumps, 0, "the third tap expired in the buffer long before the landing")


func test_coyote_time_allows_late_ground_jump() -> void:
	_step(false)  # ran off the ledge
	_step(false)
	_step(false)  # 3 ticks = 0.05 s < 0.08 s
	motor.request_jump()
	assert_eq(_step(false), GROUND, "jump within coyote window")
	assert_eq(motor.air_jumps_left, 1, "the double jump is still there")


func test_after_coyote_time_a_tap_is_the_double_jump() -> void:
	for i in 8:  # 0.133 s > 0.08 s
		_step(false)
	motor.request_jump()
	assert_eq(_step(false), AIR, "fell off the edge: only the double jump is left")
	motor.request_jump()
	assert_eq(_step(false), NONE, "and nothing after it")


func test_coyote_is_spent_by_the_ground_jump() -> void:
	_step(false)
	motor.request_jump()
	assert_eq(_step(false), GROUND)
	motor.request_jump()
	assert_eq(_step(false), AIR, "coyote cannot give a second ground jump")
	motor.request_jump()
	assert_eq(_step(false), NONE, "two jumps at most")


func test_jump_buffer_fires_on_landing_when_no_jump_is_left() -> void:
	motor.request_jump()
	_step()
	motor.request_jump()
	_step()  # Double jump spent.
	while motor.velocity.y < 0.0 or feet_y < -30.0:
		_step()
	motor.request_jump()  # Shortly before touching the ground.
	var landing_jump: PlayerMotor.Jump = NONE
	for i in 30:
		landing_jump = _step()
		if landing_jump != NONE:
			break
	assert_eq(landing_jump, GROUND, "buffered tap jumps on landing")


func test_mashing_before_a_landing_is_one_jump() -> void:
	motor.request_jump()
	_step()
	motor.request_jump()
	_step()
	while motor.velocity.y < 0.0 or feet_y < -40.0:
		_step()
	var jumps: Array[int] = []
	for i in 30:
		if i < 3:
			motor.request_jump()  # Three quick taps before touching down.
		var jump := _step()
		if jump != NONE:
			jumps.append(jump)
	assert_eq(jumps, [GROUND] as Array[int], "one ground jump, no wasted double jump")
	assert_eq(motor.air_jumps_left, 1, "the double jump is still available")


func test_jump_buffer_expires() -> void:
	motor.request_jump()
	_step()
	motor.request_jump()
	_step()
	while motor.velocity.y < 0.0:
		_step()
	motor.request_jump()  # At the apex, far above the ground: too early.
	var jumped_again := false
	for i in 60:
		jumped_again = jumped_again or _step() != NONE
	assert_false(jumped_again, "stale tap must not jump")


func test_a_tap_just_before_landing_uses_the_double_jump() -> void:
	# The rule is simple and visible: a tap in the air is the double jump.
	motor.request_jump()
	_step()
	while motor.velocity.y < 0.0 or feet_y < -20.0:
		_step()
	motor.request_jump()
	assert_eq(_step(), AIR)


func test_without_air_jumps_a_tap_in_the_air_only_buffers() -> void:
	config.air_jumps = 0
	motor.reset(true)
	motor.request_jump()
	_step()
	for i in 10:
		_step()
	motor.request_jump()
	assert_eq(_step(), NONE)


func test_reset_forgets_pending_taps() -> void:
	motor.request_jump()
	motor.request_jump()
	motor.reset(true)
	assert_eq(_step(), NONE)
	assert_eq(_step(), NONE)


func test_fall_speed_is_capped() -> void:
	for i in 300:
		_step(false)
	assert_near(motor.velocity.y, config.max_fall_speed, 0.001)
