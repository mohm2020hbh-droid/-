extends TestCase
## Drives PlayerMotor against a synthetic flat floor at y = 0 (feet position),
## at the real 60 Hz tick, without any physics server.

const DT := 1.0 / 60.0

var config: MovementConfig
var motor: PlayerMotor
var feet_y := 0.0
var on_floor := true


func before_each() -> void:
	config = MovementConfig.new()
	motor = PlayerMotor.new(config)
	motor.running = true
	feet_y = 0.0
	on_floor = true


## One tick against a floor that exists only where [param floor_present] says.
func _step(floor_present: bool = true) -> void:
	var v := motor.begin_tick(on_floor, DT)
	feet_y += v.y * DT
	if floor_present and feet_y >= 0.0:
		feet_y = 0.0
		v.y = 0.0
		on_floor = true
	else:
		on_floor = false
	motor.end_tick(v, DT)


func test_derived_values_match_formulas() -> void:
	assert_near(config.rise_gravity(), 2.0 * 150.0 / (0.36 * 0.36), 0.01)
	assert_near(config.jump_speed(), 2.0 * 150.0 / 0.36, 0.01)
	assert_near(config.fall_gravity(), config.rise_gravity() * 1.3, 0.01)


func test_jump_reaches_configured_height() -> void:
	motor.request_jump()
	var apex := 0.0
	for i in 120:
		_step()
		apex = minf(apex, feet_y)
	assert_near(-apex, config.jump_height, 0.5, "apex height")


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


func test_no_double_jump() -> void:
	motor.request_jump()
	_step()
	assert_true(motor.jumped_this_tick, "first jump")
	for i in 10:
		_step()
	var height_before := feet_y
	motor.request_jump()
	_step()
	assert_false(motor.jumped_this_tick, "second jump in the air must be ignored")
	assert_true(motor.velocity.y > -config.jump_speed() * 0.9, "no upward boost")
	assert_true(feet_y < 0.0 and height_before < 0.0, "still airborne")


func test_coyote_time_allows_late_jump() -> void:
	_step(false)  # ran off the ledge
	_step(false)
	_step(false)  # 3 ticks = 0.05 s < 0.08 s
	motor.request_jump()
	_step(false)
	assert_true(motor.jumped_this_tick, "jump within coyote window")


func test_coyote_time_expires() -> void:
	for i in 8:  # 0.133 s > 0.08 s
		_step(false)
	motor.request_jump()
	_step(false)
	assert_false(motor.jumped_this_tick, "jump after coyote window")


func test_coyote_is_spent_by_jump() -> void:
	_step(false)
	motor.request_jump()
	_step(false)
	assert_true(motor.jumped_this_tick)
	motor.request_jump()
	_step(false)
	assert_false(motor.jumped_this_tick, "coyote cannot give a second jump")


func test_jump_buffer_fires_on_landing() -> void:
	motor.request_jump()
	_step()
	while motor.velocity.y < 0.0 or feet_y < -30.0:
		_step()
	motor.request_jump()  # well before touching the ground
	var jumped_again := false
	for i in 30:
		_step()
		if motor.jumped_this_tick:
			jumped_again = true
			break
	assert_true(jumped_again, "buffered tap jumps on landing")


func test_jump_buffer_expires() -> void:
	motor.request_jump()
	_step()
	while motor.velocity.y < 0.0:
		_step()
	motor.request_jump()  # at apex, ~0.3 s before landing: too early
	var jumped_again := false
	for i in 40:
		_step()
		jumped_again = jumped_again or motor.jumped_this_tick
	assert_false(jumped_again, "stale tap must not jump")


func test_fall_speed_is_capped() -> void:
	for i in 300:
		_step(false)
	assert_near(motor.velocity.y, config.max_fall_speed, 0.001)
