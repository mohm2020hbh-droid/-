extends TestCase
## One tap per finger press, whatever the device (docs/GDD.md §4): a long
## press, a release, a key repeat or the mouse click emulated from a touch
## never count as another tap, so the second tap is always a real second tap.


func _key(pressed: bool, echo := false) -> InputEventKey:
	var key := InputEventKey.new()
	key.keycode = KEY_SPACE
	key.physical_keycode = KEY_SPACE
	key.pressed = pressed
	key.echo = echo
	return key


func test_a_touch_counts_once_on_press_never_on_release() -> void:
	var touch := InputEventScreenTouch.new()
	touch.pressed = true
	assert_true(TapInput.is_tap(touch), "the press is the tap")
	touch.pressed = false
	assert_false(TapInput.is_tap(touch), "the release is not")


func test_the_mouse_click_emulated_from_a_touch_is_ignored() -> void:
	var click := InputEventMouseButton.new()
	click.button_index = MOUSE_BUTTON_LEFT
	click.pressed = true
	click.device = InputEvent.DEVICE_ID_EMULATION
	assert_false(TapInput.is_tap(click), "already counted as the touch")


func test_a_held_key_is_one_tap_its_repeats_and_release_are_not() -> void:
	assert_true(TapInput.is_tap(_key(true)), "the press")
	assert_false(TapInput.is_tap(_key(true, true)), "a long press repeats: not a tap")
	assert_false(TapInput.is_tap(_key(false)), "the release: not a tap")
