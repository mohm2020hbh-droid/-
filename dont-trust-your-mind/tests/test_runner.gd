extends Node

## Headless test entry point.
##   godot --headless res://tests/test_runner.tscn
## Exits non-zero on any failure so CI and local runs both fail loudly.

const SUITES := [
	preload("res://tests/suites/content_validation.gd"),
	preload("res://tests/suites/session_rules.gd"),
	preload("res://tests/suites/localization.gd"),
	preload("res://tests/suites/save_system.gd"),
	preload("res://tests/suites/rendering.gd"),
	preload("res://tests/suites/playthrough.gd"),
	preload("res://tests/suites/responsive.gd"),
	preload("res://tests/suites/persistence.gd"),
]

func _ready() -> void:
	await get_tree().process_frame
	var total := 0
	var failed := 0
	var started := Time.get_ticks_msec()

	for suite_script in SUITES:
		var suite: TestSuite = suite_script.new()
		add_child(suite)
		print("\n▶ %s" % suite.suite_name())
		await suite.run()
		total += suite.assertions
		failed += suite.failures.size()
		for message in suite.failures:
			print("   ✗ %s" % message)
		if suite.failures.is_empty():
			print("   ✓ %d checks passed" % suite.assertions)
		suite.queue_free()

	var ms := Time.get_ticks_msec() - started
	print("\n%s  %d checks, %d failed, %d ms" % [
		"PASS" if failed == 0 else "FAIL", total, failed, ms])
	get_tree().quit(0 if failed == 0 else 1)
