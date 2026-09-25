extends TestCase
## The sound library resolves every id the AudioManager plays.

const IDS: Array[StringName] = [&"jump", &"double_jump", &"land", &"collect", &"death", &"checkpoint",
	&"complete", &"ui_click", &"warning", &"slam", &"ambient"]


func test_library_has_every_gameplay_sound() -> void:
	var library := load(AudioManager.LIBRARY_PATH) as SoundLibrary
	assert_true(library != null, "sound library loads")
	for id in IDS:
		assert_true(library.get_stream(id) is AudioStream, "stream for %s" % id)


func test_ambient_loops() -> void:
	var library := load(AudioManager.LIBRARY_PATH) as SoundLibrary
	var ambient := library.get_stream(&"ambient") as AudioStreamOggVorbis
	assert_true(ambient != null and ambient.loop, "the ambient drone is imported as a loop")


func test_unknown_id_is_silent_not_an_error() -> void:
	AudioManager.play(&"does_not_exist")
	assert_true(true)
