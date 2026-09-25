extends Node
## Minimal headless test runner (no external addons).
##
##   godot --headless --path shape-jump --fixed-fps 60 res://tests/test_runner.tscn
##   godot ... res://tests/test_runner.tscn -- --filter=playthrough
##
## Discovers tests/unit and tests/integration test_*.gd files (each extends
## [TestCase]), runs every test_* method, prints a summary and exits with
## status 1 if anything failed. Any engine or script error logged while a
## test runs fails that test, so runtime errors can never pass silently.
## --fixed-fps makes physics-driven integration tests run faster than real
## time with identical results.

const TEST_DIRS: Array[String] = ["res://tests/unit", "res://tests/integration"]


## Captures errors (not warnings) reported by the engine while a test runs.
class ErrorCollector extends Logger:
	var _errors := PackedStringArray()
	var _mutex := Mutex.new()

	func _log_error(function: String, file: String, line: int, code: String, rationale: String,
			_editor_notify: bool, error_type: int, _script_backtraces: Array[ScriptBacktrace]) -> void:
		if error_type == ERROR_TYPE_WARNING:
			return
		var what := rationale if not rationale.is_empty() else code
		_mutex.lock()
		_errors.append("engine error: %s (%s:%d in %s)" % [what, file.get_file(), line, function])
		_mutex.unlock()

	func _log_message(_message: String, _error: bool) -> void:
		pass

	func take() -> PackedStringArray:
		_mutex.lock()
		var taken := _errors
		_errors = PackedStringArray()
		_mutex.unlock()
		return taken


func _ready() -> void:
	# Let autoloads finish their _ready before any test touches them.
	await get_tree().process_frame
	var filter := _parse_filter()
	var collector := ErrorCollector.new()
	OS.add_logger(collector)
	var total := 0
	var failed := 0
	var started_ms := Time.get_ticks_msec()
	for path in _discover():
		var script := load(path) as GDScript
		if script == null or not script.can_instantiate():
			# A test file that does not parse must fail the run, not vanish from it.
			total += 1
			failed += 1
			print("  FAIL  %s (script failed to load)" % path.get_file())
			continue
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
			collector.take()
			await test.before_each()
			await test.call(method_name)
			await test.after_each()
			test.failures.append_array(collector.take())
			if test.failures.is_empty():
				print("  PASS  ", test_id)
			else:
				failed += 1
				print("  FAIL  ", test_id)
				for failure in test.failures:
					print("        ", failure)
			test.queue_free()
			await get_tree().process_frame
	OS.remove_logger(collector)
	var seconds := (Time.get_ticks_msec() - started_ms) / 1000.0
	print("\n%d tests, %d failed (%.1fs)" % [total, failed, seconds])
	# Let the audio thread release sounds still playing, so exit is leak-free.
	AudioManager.stop_all()
	for i in 3:
		OS.delay_msec(50)  # Real time: --fixed-fps frames do not wait for the audio thread.
		await get_tree().process_frame
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
