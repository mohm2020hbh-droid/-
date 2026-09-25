extends TestCase
## Guards project-level configuration the gameplay code relies on.


func test_input_actions_exist() -> void:
	assert_true(InputMap.has_action(&"jump"), "jump action")
	assert_true(InputMap.has_action(&"pause"), "pause action")
	var has_mouse := false
	for event in InputMap.action_get_events(&"jump"):
		if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT:
			has_mouse = true
	assert_true(has_mouse, "touch (emulated as left mouse) triggers jump")


func test_audio_buses_exist() -> void:
	assert_true(AudioServer.get_bus_index(&"SFX") > 0, "SFX bus")
	assert_true(AudioServer.get_bus_index(&"Ambient") > 0, "Ambient bus")


func test_fixed_physics_rate() -> void:
	assert_eq(Engine.physics_ticks_per_second, 60)
	assert_true(ProjectSettings.get_setting("physics/common/physics_interpolation"), "interpolation on")
