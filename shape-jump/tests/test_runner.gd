extends Node
## Minimal headless test runner (no external addons).
##
##   godot --headless --path shape-jump --fixed-fps 60 res://tests/test_runner.tscn
##   godot ... res://tests/test_runner.tscn -- --filter=playthrough
##
## Discovers tests/unit and tests/integration test_*.gd files (each extends
## [TestCase]), runs every test_* method, prints a summary and exits with
## status 1 if anything failed. --fixed-fps makes physics-driven integration
## tests run faster than real time with identical results.

const TEST_DIRS: Array[String] = ["res://tests/unit", "res://tests/integration"]


func _ready() -> void:
	# Let autoloads finish their _ready before any test touches them.
	await get_tree().process_frame
	var filter := _parse_filter()
	var total := 0
	var failed := 0
	var started_ms := Time.get_ticks_msec()
	for path in _discover():
		var script: GDScript = load(path)
		for method in script.get_script_method_list():
			var method_name: String = method.name
			if not method_name.begins_with("test_"):
				continue
			var test_id := "%s:%s" % [path.get_file().get_basename(), method_name]
			if not filter.is_empty() and not test_id.contains(filter):
				continue
			total += 1
			var test: TestCase = script.new()
			add_child(test)
			await test.before_each()
			await test.call(method_name)
			await test.after_each()
			if test.failures.is_empty():
				print("  PASS  ", test_id)
			else:
				failed += 1
				print("  FAIL  ", test_id)
				for failure in test.failures:
					print("        ", failure)
			test.queue_free()
			await get_tree().process_frame
	var seconds := (Time.get_ticks_msec() - started_ms) / 1000.0
	print("\n%d tests, %d failed (%.1fs)" % [total, failed, seconds])
	get_tree().quit(1 if failed > 0 or total == 0 else 0)


func _discover() -> PackedStringArray:
	var paths := PackedStringArray()
	for dir_path in TEST_DIRS:
		for file in DirAccess.get_files_at(dir_path):
			if file.begins_with("test_") and file.ends_with(".gd"):
				paths.append(dir_path.path_join(file))
	paths.sort()
	return paths


func _parse_filter() -> String:
	for arg in OS.get_cmdline_user_args():
		if arg.begins_with("--filter="):
			return arg.trim_prefix("--filter=")
	return ""
