class_name TestCase
extends Node
## Base class for tests run by tests/test_runner.gd.
## Every method whose name starts with "test_" is run on a fresh instance
## that is added to the scene tree, so integration tests may await frames.

var failures: PackedStringArray = []


## Called before each test method. May be a coroutine.
func before_each() -> void:
	pass


## Called after each test method. May be a coroutine.
func after_each() -> void:
	pass


func fail(message: String) -> void:
	failures.append(message)


func assert_true(condition: bool, message: String = "") -> void:
	if not condition:
		fail("expected true" + _suffix(message))


func assert_false(condition: bool, message: String = "") -> void:
	if condition:
		fail("expected false" + _suffix(message))


func assert_eq(actual: Variant, expected: Variant, message: String = "") -> void:
	if typeof(actual) != typeof(expected) or actual != expected:
		fail("expected %s, got %s%s" % [var_to_str(expected), var_to_str(actual), _suffix(message)])


func assert_near(actual: float, expected: float, tolerance: float, message: String = "") -> void:
	if absf(actual - expected) > tolerance:
		fail("expected %.4f ± %.4f, got %.4f%s" % [expected, tolerance, actual, _suffix(message)])


func _suffix(message: String) -> String:
	return "" if message.is_empty() else " — " + message
