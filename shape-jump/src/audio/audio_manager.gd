extends Node
## Plays sound effects in response to [Events]. The only place that knows
## which sound belongs to which gameplay event.

const LIBRARY_PATH := "res://assets/audio/sound_library.tres"
const POOL_SIZE := 8
const SFX_BUS := &"SFX"
const AMBIENT_BUS := &"Ambient"

var _library: SoundLibrary
var _pool: Array[AudioStreamPlayer] = []
var _next_voice := 0
var _ambient: AudioStreamPlayer


func _ready() -> void:
	# UI sounds must still play while the game is paused.
	process_mode = Node.PROCESS_MODE_ALWAYS
	if ResourceLoader.exists(LIBRARY_PATH):
		_library = load(LIBRARY_PATH) as SoundLibrary
	for i in POOL_SIZE:
		var voice := AudioStreamPlayer.new()
		voice.bus = SFX_BUS
		add_child(voice)
		_pool.append(voice)
	_ambient = AudioStreamPlayer.new()
	_ambient.bus = AMBIENT_BUS
	add_child(_ambient)

	Events.player_jumped.connect(func(_p: Vector2) -> void: play(&"jump", 0.06))
	Events.player_landed.connect(_on_player_landed)
	Events.player_died.connect(func(_p: Vector2, _c: StringName) -> void: play(&"death"))
	Events.shard_collected.connect(func(_p: Vector2) -> void: play(&"collect", 0.04))
	Events.checkpoint_reached.connect(func(_p: Vector2) -> void: play(&"checkpoint"))
	Events.level_completed.connect(func(_id: StringName, _s: int, _n: int) -> void: play(&"complete"))
	Events.ui_pressed.connect(func() -> void: play(&"ui_click"))


## Plays [param id] on the next free (or oldest) voice. [param pitch_jitter]
## adds small random pitch variation so repeated sounds do not feel robotic.
func play(id: StringName, pitch_jitter: float = 0.0, volume_db: float = 0.0) -> void:
	if _library == null:
		return
	var stream := _library.get_stream(id)
	if stream == null:
		return
	var voice := _pool[_next_voice]
	_next_voice = (_next_voice + 1) % POOL_SIZE
	voice.stream = stream
	voice.volume_db = volume_db
	voice.pitch_scale = 1.0 + randf_range(-pitch_jitter, pitch_jitter)
	voice.play()


func play_ambient(stream: AudioStream, volume_db: float = -12.0) -> void:
	_ambient.stream = stream
	_ambient.volume_db = volume_db
	_ambient.play()


func stop_ambient() -> void:
	_ambient.stop()


func _on_player_landed(_position: Vector2, impact_speed: float) -> void:
	# Soft landings from small drops should barely be heard.
	var loudness := clampf(impact_speed / 1200.0, 0.25, 1.0)
	play(&"land", 0.05, linear_to_db(loudness))
