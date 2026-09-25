extends TestCase
## Level progress by distance, and the death penalty (docs/GDD.md §11).

var p: ProgressTracker


func before_each() -> void:
	p = ProgressTracker.new()
	p.reset(100.0, 1100.0)


func test_progress_is_distance_from_spawn_to_finish() -> void:
	assert_eq(p.percent, 0.0, "0% at the start")
	assert_near(p.percent_at(350.0), 25.0, 0.001)
	assert_near(p.percent_at(600.0), 50.0, 0.001)
	assert_near(p.percent_at(850.0), 75.0, 0.001)
	assert_near(p.percent_at(1100.0), 100.0, 0.001)
	assert_eq(p.percent_at(40.0), 0.0, "never negative")
	assert_eq(p.percent_at(1500.0), 100.0, "never above 100")


func test_progress_only_rises_while_running() -> void:
	p.update(600.0)
	assert_near(p.percent, 50.0, 0.001)
	p.update(500.0)
	assert_near(p.percent, 50.0, 0.001, "moving back does not lower it")
	p.update(700.0)
	assert_near(p.percent, 60.0, 0.001)


func test_death_takes_25_points_and_never_below_zero() -> void:
	p.update(700.0)  # 60%
	assert_near(p.penalize(), 25.0, 0.001, "25 points taken")
	assert_near(p.percent, 35.0, 0.001, "60% -> 35%")
	p.reset(100.0, 1100.0)
	p.update(300.0)  # 20%
	assert_near(p.penalize(), 20.0, 0.001, "only what there was")
	assert_eq(p.percent, 0.0, "20% -> 0%, not negative")


func test_after_a_penalty_it_rises_again_past_the_lowered_value() -> void:
	p.update(700.0)  # 60%
	p.penalize()     # 35%
	p.update(430.0)  # Respawn at 33%: still 35%.
	assert_near(p.percent, 35.0, 0.001, "no jump back up at the respawn")
	p.update(500.0)  # 40%
	assert_near(p.percent, 40.0, 0.001, "rises once past the lowered value")
	p.update(1100.0)
	assert_near(p.percent, 100.0, 0.001, "and reaches 100% at the finish")


func test_complete_is_exactly_100_and_reports() -> void:
	var seen: Array[float] = []
	p.changed.connect(func(v: float) -> void: seen.append(v))
	p.update(400.0)
	p.complete()
	assert_eq(p.percent, 100.0)
	assert_eq(seen.back(), 100.0, "the HUD hears every change")
