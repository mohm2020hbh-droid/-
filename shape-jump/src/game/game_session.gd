class_name GameSession
extends Node
## Game state machine and the single mediator of the gameplay scene
## (docs/ARCHITECTURE.md §4).
##
## Signals come up from Player, Level and UI; calls go down to them. It is
## also the only emitter of the global [Events] bus. Input is routed here so
## a test bot can drive the game through the same entry points; device
## quirks (multi-touch, duplicate events) are handled by [TapInput].

signal state_changed(new_state: State)

enum State { READY, PLAYING, PAUSED, DYING, COMPLETE }


## Where and when the player comes back after a death.
class RespawnPoint:
	var feet := Vector2.ZERO
	## Level time at which the player's centre was exactly at [member feet].
	var level_time := 0.0
	var score := {}

	func _init(feet_position: Vector2, time: float, score_snapshot: Dictionary) -> void:
		feet = feet_position
		level_time = time
		score = score_snapshot


@export var level_scene: PackedScene

@export_group("Death & Respawn Feel")
## Time the shatter plays before the screen fades.
@export_range(0.0, 2.0, 0.05, "suffix:s") var death_pause := 0.45
@export_range(0.0, 1.0, 0.01, "suffix:s") var fade_out_time := 0.16
@export_range(0.0, 1.0, 0.01, "suffix:s") var fade_in_time := 0.24
## Camera shake on death, 0..1 trauma.
@export_range(0.0, 1.0, 0.05) var death_shake := 0.85
@export_group("Finish Feel")
@export_range(0.0, 3.0, 0.05, "suffix:s") var finish_brake_time := 0.7
@export_range(0.0, 3.0, 0.05, "suffix:s") var results_delay := 0.8

var state: State = State.READY
var level: Level
var score := ScoreTracker.new()

var _state_before_pause: State = State.PLAYING
var _respawn: RespawnPoint
var _sequence: Tween

@onready var player: Player = %Player
@onready var camera: GameCamera = %GameCamera
@onready var hud: Hud = %Hud
@onready var start_overlay: StartOverlay = %StartOverlay
@onready var pause_menu: PauseMenu = %PauseMenu
@onready var complete_panel: LevelCompletePanel = %LevelCompletePanel
@onready var fade: ScreenFade = %ScreenFade
@onready var _level_slot: Node2D = %LevelSlot
@onready var _tap_input: TapInput = %TapInput


func _ready() -> void:
	player.jumped.connect(_on_player_jumped)
	player.double_jumped.connect(_on_player_double_jumped)
	player.landed.connect(_on_player_landed)
	player.died.connect(_on_player_died)
	score.changed.connect(hud.set_score)
	hud.pause_pressed.connect(pause)
	pause_menu.resume_pressed.connect(resume)
	pause_menu.restart_pressed.connect(restart_level)
	complete_panel.play_again_pressed.connect(restart_level)
	_tap_input.tapped.connect(press_jump)
	_tap_input.pause_requested.connect(pause)
	load_level()


func _physics_process(_delta: float) -> void:
	if state == State.PLAYING:
		score.update_progress(player.global_position.x)


func _notification(what: int) -> void:
	match what:
		NOTIFICATION_APPLICATION_FOCUS_OUT, NOTIFICATION_APPLICATION_PAUSED:
			# Mobile: leaving the app (call, home button) must never cost a life.
			_pause(false)
		NOTIFICATION_WM_GO_BACK_REQUEST:
			_on_back_requested()


## The single "tap" entry point (TapInput or a test bot).
func press_jump() -> void:
	match state:
		State.READY:
			start_run()
		State.PLAYING:
			player.request_jump()


func load_level() -> void:
	if level_scene == null:
		push_error("GameSession: no level_scene assigned.")
		return
	_kill_sequence()
	if level:
		_level_slot.remove_child(level)
		level.queue_free()
	level = level_scene.instantiate()
	_level_slot.add_child(level)
	level.shard_collected.connect(_on_shard_collected)
	level.checkpoint_reached.connect(_on_checkpoint_reached)
	level.finish_reached.connect(_on_finish_reached)

	player.kill_y = level.kill_y
	player.set_speed_scale(level.data.speed_scale)
	camera.set_kill_line(level.kill_y)
	var spawn := level.get_spawn_feet_position()
	score.reset(spawn.x)
	_respawn = RespawnPoint.new(spawn, 0.0, score.snapshot())
	player.respawn_at(spawn, false)
	camera.snap_to_target()
	_set_state(State.READY)
	start_overlay.open(level.data.display_name, SaveSystem.get_record(level.data.id).best_score)
	Events.level_started.emit(level.data.id)


func start_run() -> void:
	if state != State.READY:
		return
	start_overlay.close()
	player.set_running(true)
	_set_state(State.PLAYING)


func pause() -> void:
	_pause(true)


func _pause(user_initiated: bool) -> void:
	if state != State.PLAYING and state != State.DYING:
		return
	_state_before_pause = state
	_set_state(State.PAUSED)
	get_tree().paused = true
	pause_menu.open()
	if user_initiated:
		Events.ui_pressed.emit()  # No click sound when the OS paused us.


func resume() -> void:
	if state != State.PAUSED:
		return
	pause_menu.close()
	get_tree().paused = false
	# A pause during the death animation resumes it; the respawn follows.
	_set_state(_state_before_pause)
	Events.ui_pressed.emit()


func restart_level() -> void:
	Events.ui_pressed.emit()
	get_tree().paused = false
	pause_menu.close()
	complete_panel.close()
	load_level()
	fade.color.a = 1.0
	_sequence = _new_sequence()
	fade.tween_to(_sequence, 0.0, fade_in_time)


## Where the player will come back after the next death.
func get_respawn_point() -> RespawnPoint:
	return _respawn


func _set_state(next: State) -> void:
	state = next
	# The player can only die while actually playing: a hazard reaching it on
	# the start screen, while paused or after the finish must not leave a
	# dead player that the next tap would set running.
	player.invulnerable = state != State.PLAYING
	level.running = state == State.PLAYING or state == State.DYING or state == State.COMPLETE
	hud.set_pause_enabled(state == State.PLAYING or state == State.DYING)
	state_changed.emit(state)


func _respawn_player() -> void:
	level.rewind_to(_respawn.level_time)
	score.restore(_respawn.score)
	player.respawn_at(_respawn.feet, true)
	camera.snap_to_target()
	_set_state(State.PLAYING)
	Events.player_respawned.emit(player.global_position)


func _new_sequence() -> Tween:
	_kill_sequence()
	return create_tween()


func _kill_sequence() -> void:
	if _sequence and _sequence.is_valid():
		_sequence.kill()
	_sequence = null


## Android Back (also the edge-swipe gesture): never quit in the middle of
## a run. Playing -> pause, paused -> resume, menus -> leave the game.
func _on_back_requested() -> void:
	match state:
		State.PLAYING, State.DYING:
			pause()
		State.PAUSED:
			resume()
		_:
			get_tree().quit()


func _on_player_jumped() -> void:
	Events.player_jumped.emit(player.global_position)


func _on_player_double_jumped() -> void:
	Events.player_double_jumped.emit(player.global_position)


func _on_player_landed(impact_speed: float) -> void:
	Events.player_landed.emit(player.global_position, impact_speed)


func _on_player_died(cause: StringName) -> void:
	if state != State.PLAYING:
		return
	_set_state(State.DYING)
	camera.shake(death_shake)
	Events.player_died.emit(player.global_position, cause)
	_sequence = _new_sequence()
	_sequence.tween_interval(death_pause)
	fade.tween_to(_sequence, 1.0, fade_out_time)
	_sequence.tween_callback(_respawn_player)
	fade.tween_to(_sequence, 0.0, fade_in_time)


func _on_shard_collected(shard: Shard) -> void:
	score.add_shard()
	Events.shard_collected.emit(shard.global_position)


func _on_checkpoint_reached(checkpoint: Checkpoint) -> void:
	# The level time at which the player's centre is exactly on the checkpoint,
	# so a respawn there sees the same obstacle timing as the first pass.
	var feet := checkpoint.global_position
	var time := level.clock + (feet.x - player.global_position.x) / player.get_run_speed()
	_respawn = RespawnPoint.new(feet, time, score.snapshot())
	Events.checkpoint_reached.emit(feet)


func _on_finish_reached() -> void:
	if state != State.PLAYING:
		return
	_set_state(State.COMPLETE)  # Also makes the player invulnerable.
	var final_score := score.get_score()
	var is_new_best := SaveSystem.record_result(level.data.id, final_score, score.shards, true)
	var best: int = SaveSystem.get_record(level.data.id).best_score
	Events.level_completed.emit(level.data.id, final_score, score.shards)
	_sequence = _new_sequence()
	_sequence.tween_method(player.set_speed_scale, level.data.speed_scale, 0.0, finish_brake_time) \
		.set_ease(Tween.EASE_OUT).set_trans(Tween.TRANS_CUBIC)
	_sequence.tween_callback(player.set_running.bind(false))
	_sequence.tween_interval(maxf(results_delay - finish_brake_time, 0.0))
	_sequence.tween_callback(complete_panel.open.bind(
		final_score, best, is_new_best, score.shards, level.get_shard_count()))
