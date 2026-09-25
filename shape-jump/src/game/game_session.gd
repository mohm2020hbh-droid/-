class_name GameSession
extends Node
## Game state machine and the single mediator of the gameplay scene
## (docs/ARCHITECTURE.md §4).
##
## Signals come up from Player, Level and UI; calls go down to them. It is
## also the only emitter of the global [Events] bus. Input is routed here so
## a test bot can drive the game through the same entry points.

signal state_changed(new_state: State)

enum State { READY, PLAYING, PAUSED, DYING, COMPLETE }

const DEATH_PAUSE := 0.45
const FADE_OUT := 0.16
const FADE_IN := 0.24
const DEATH_SHAKE := 0.85
const FINISH_BRAKE_TIME := 0.7
const RESULTS_DELAY := 0.8

@export var level_scene: PackedScene

var state: State = State.READY
var level: Level
var score := ScoreTracker.new()

var _state_before_pause: State = State.PLAYING
var _respawn_feet := Vector2.ZERO
var _respawn_time := 0.0
var _respawn_score := {}
var _sequence: Tween

@onready var player: Player = %Player
@onready var camera: GameCamera = %GameCamera
@onready var hud: Hud = %Hud
@onready var start_overlay: StartOverlay = %StartOverlay
@onready var pause_menu: PauseMenu = %PauseMenu
@onready var complete_panel: LevelCompletePanel = %LevelCompletePanel
@onready var fade: ScreenFade = %ScreenFade
@onready var _level_slot: Node2D = %LevelSlot


func _ready() -> void:
	player.jumped.connect(_on_player_jumped)
	player.landed.connect(_on_player_landed)
	player.died.connect(_on_player_died)
	score.changed.connect(hud.set_score)
	hud.pause_pressed.connect(pause)
	pause_menu.resume_pressed.connect(resume)
	pause_menu.restart_pressed.connect(restart_level)
	complete_panel.play_again_pressed.connect(restart_level)
	load_level()


func _physics_process(_delta: float) -> void:
	if state == State.PLAYING:
		score.update_progress(player.global_position.x)


func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed(&"pause"):
		get_viewport().set_input_as_handled()
		pause()
	elif event.is_action_pressed(&"jump"):
		get_viewport().set_input_as_handled()
		press_jump()


func _notification(what: int) -> void:
	# Mobile: leaving the app (call, home button) must never cost a life.
	if what == NOTIFICATION_APPLICATION_FOCUS_OUT or what == NOTIFICATION_APPLICATION_PAUSED:
		pause()


## The single "tap" entry point (touch, mouse, keyboard or test bot).
func press_jump() -> void:
	match state:
		State.READY:
			start_run()
		State.PLAYING:
			player.request_jump()


func load_level() -> void:
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
	camera.bottom_limit = level.kill_y
	_respawn_feet = level.get_spawn_feet_position()
	_respawn_time = 0.0
	score.reset(_respawn_feet.x)
	_respawn_score = score.snapshot()
	hud.set_score(0, 0)
	player.respawn_at(_respawn_feet, false)
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
	if state != State.PLAYING and state != State.DYING:
		return
	_state_before_pause = state
	_set_state(State.PAUSED)
	get_tree().paused = true
	pause_menu.open()
	Events.ui_pressed.emit()


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
	fade.tween_to(_sequence, 0.0, FADE_IN)


func _set_state(next: State) -> void:
	state = next
	level.running = state == State.PLAYING or state == State.DYING or state == State.COMPLETE
	hud.set_pause_enabled(state == State.PLAYING or state == State.DYING)
	state_changed.emit(state)


func _respawn() -> void:
	level.rewind_to(_respawn_time)
	score.restore(_respawn_score)
	player.respawn_at(_respawn_feet, true)
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


func _on_player_jumped() -> void:
	Events.player_jumped.emit(player.global_position)


func _on_player_landed(impact_speed: float) -> void:
	Events.player_landed.emit(player.global_position, impact_speed)


func _on_player_died(cause: StringName) -> void:
	if state != State.PLAYING:
		return
	_set_state(State.DYING)
	camera.shake(DEATH_SHAKE)
	Events.player_died.emit(player.global_position, cause)
	_sequence = _new_sequence()
	_sequence.tween_interval(DEATH_PAUSE)
	fade.tween_to(_sequence, 1.0, FADE_OUT)
	_sequence.tween_callback(_respawn)
	fade.tween_to(_sequence, 0.0, FADE_IN)


func _on_shard_collected(shard: Shard) -> void:
	score.add_shard()
	Events.shard_collected.emit(shard.global_position)


func _on_checkpoint_reached(checkpoint: Checkpoint) -> void:
	# The level time at which the player's centre is exactly on the checkpoint,
	# so a respawn there sees the same obstacle timing as the first pass.
	var speed := player.config.run_speed * level.data.speed_scale
	checkpoint.level_time = level.clock + (checkpoint.global_position.x - player.global_position.x) / speed
	_respawn_feet = checkpoint.global_position
	_respawn_time = checkpoint.level_time
	_respawn_score = score.snapshot()
	Events.checkpoint_reached.emit(checkpoint.global_position)


func _on_finish_reached() -> void:
	if state != State.PLAYING:
		return
	_set_state(State.COMPLETE)
	var final_score := score.get_score()
	var is_new_best := SaveSystem.record_result(level.data.id, final_score, score.shards, true)
	var best: int = SaveSystem.get_record(level.data.id).best_score
	Events.level_completed.emit(level.data.id, final_score, score.shards)
	_sequence = _new_sequence()
	_sequence.tween_method(player.set_speed_scale, level.data.speed_scale, 0.0, FINISH_BRAKE_TIME) \
		.set_ease(Tween.EASE_OUT).set_trans(Tween.TRANS_CUBIC)
	_sequence.tween_callback(player.set_running.bind(false))
	_sequence.tween_interval(maxf(RESULTS_DELAY - FINISH_BRAKE_TIME, 0.0))
	_sequence.tween_callback(complete_panel.open.bind(
		final_score, best, is_new_best, score.shards, level.get_shard_count()))
