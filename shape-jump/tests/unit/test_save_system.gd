extends TestCase

const TEST_PATH := "user://test_save.cfg"

var _original_path: String


func before_each() -> void:
	_original_path = SaveSystem.save_path
	SaveSystem.save_path = TEST_PATH
	DirAccess.remove_absolute(ProjectSettings.globalize_path(TEST_PATH))
	SaveSystem.load_from_disk()


func after_each() -> void:
	DirAccess.remove_absolute(ProjectSettings.globalize_path(TEST_PATH))
	SaveSystem.save_path = _original_path
	SaveSystem.load_from_disk()


func test_empty_record_defaults() -> void:
	var rec := SaveSystem.get_record(&"nope")
	assert_eq(rec.best_score, 0)
	assert_eq(rec.best_shards, 0)
	assert_eq(rec.completed, false)


func test_record_persists_across_reload() -> void:
	assert_true(SaveSystem.record_result(&"level_x", 1200, 7, true), "first result is a new best")
	SaveSystem.load_from_disk()
	var rec := SaveSystem.get_record(&"level_x")
	assert_eq(rec.best_score, 1200)
	assert_eq(rec.best_shards, 7)
	assert_eq(rec.completed, true)


func test_best_values_never_decrease() -> void:
	SaveSystem.record_result(&"level_x", 1200, 7, true)
	assert_false(SaveSystem.record_result(&"level_x", 900, 3, false), "lower score is not a best")
	var rec := SaveSystem.get_record(&"level_x")
	assert_eq(rec.best_score, 1200)
	assert_eq(rec.best_shards, 7)
	assert_eq(rec.completed, true)


func test_writes_leave_no_temp_file_behind() -> void:
	SaveSystem.record_result(&"level_x", 10, 1, false)
	assert_true(FileAccess.file_exists(TEST_PATH), "save written")
	assert_false(FileAccess.file_exists(TEST_PATH + ".tmp"), "temp file renamed into place")


func test_recovers_from_an_interrupted_write() -> void:
	SaveSystem.record_result(&"level_x", 777, 3, true)
	# Simulate a crash between writing the temp file and the rename: the
	# temp holds the new data, the main file is garbage.
	DirAccess.copy_absolute(ProjectSettings.globalize_path(TEST_PATH), ProjectSettings.globalize_path(TEST_PATH + ".tmp"))
	var broken := FileAccess.open(TEST_PATH, FileAccess.WRITE)
	broken.store_string("[half-written")
	broken.close()
	expect_engine_error("ConfigFile parse error")
	SaveSystem.load_from_disk()
	assert_eq(SaveSystem.get_record(&"level_x").best_score, 777, "progress recovered")
	DirAccess.remove_absolute(ProjectSettings.globalize_path(TEST_PATH + ".tmp"))
