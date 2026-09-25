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
