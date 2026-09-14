extends Node

## Small pool of one-shot players fed by procedurally generated sounds.

enum Sfx { TAP, WRONG, CORRECT, REVEAL, HINT, SUCCESS, TICK }

const POOL_SIZE := 6
const BUS := "Master"

var _streams: Dictionary = {}
var _players: Array[AudioStreamPlayer] = []
var _next := 0

func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	_streams = {
		Sfx.TAP: SfxSynth.tap(),
		Sfx.WRONG: SfxSynth.wrong(),
		Sfx.CORRECT: SfxSynth.correct(),
		Sfx.REVEAL: SfxSynth.reveal(),
		Sfx.HINT: SfxSynth.hint(),
		Sfx.SUCCESS: SfxSynth.success(),
		Sfx.TICK: SfxSynth.tick(),
	}
	for i in POOL_SIZE:
		var p := AudioStreamPlayer.new()
		p.bus = BUS
		add_child(p)
		_players.append(p)
	_apply_volume()
	SaveManager.settings_changed.connect(_apply_volume)

func _apply_volume() -> void:
	var enabled := bool(SaveManager.get_setting("sfx", true))
	var volume := float(SaveManager.get_setting("volume", 0.8))
	var db := -80.0 if not enabled else linear_to_db(clampf(volume, 0.0, 1.0))
	AudioServer.set_bus_volume_db(AudioServer.get_bus_index(BUS), db)

func play(sfx: Sfx, pitch: float = 1.0) -> void:
	if not bool(SaveManager.get_setting("sfx", true)):
		return
	var player := _players[_next]
	_next = (_next + 1) % _players.size()
	player.stream = _streams[sfx]
	player.pitch_scale = clampf(pitch, 0.5, 2.0)
	player.play()

## Short vibration for meaningful moments only, never for ordinary taps.
func buzz(ms: int = 20) -> void:
	if not bool(SaveManager.get_setting("haptics", true)):
		return
	if OS.has_feature("mobile"):
		Input.vibrate_handheld(ms)
