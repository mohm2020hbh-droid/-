class_name TestSuite
extends Node

## Tiny assertion base class. Deliberately minimal: the value is in the tests,
## not in the framework.

var assertions := 0
var failures: PackedStringArray = PackedStringArray()

func suite_name() -> String:
	return "suite"

func run() -> void:
	pass

func check(condition: bool, message: String) -> void:
	assertions += 1
	if not condition:
		failures.append(message)

func equal(actual, expected, message: String) -> void:
	assertions += 1
	if actual != expected:
		failures.append("%s (got %s, expected %s)" % [message, actual, expected])

func near(actual: float, expected: float, message: String, tolerance := 0.01) -> void:
	assertions += 1
	if absf(actual - expected) > tolerance:
		failures.append("%s (got %f, expected %f)" % [message, actual, expected])
