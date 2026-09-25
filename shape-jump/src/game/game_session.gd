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
## A level was loaded (a fresh start, waiting for the first tap).
signal level_loaded

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


@export var world: WorldData
## Level (index in [member world]) opened on start; -1 = the furthest unlocked.
@export var start_level := -1

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
var level_index := 0
var score := ScoreTracker.new()
var progress := ProgressTracker.new()
## Tries at the current level since it was loaded (1 + deaths).
var attempts := 1

var _state_before_pause: State = State.PLAYING
var _respawn: RespawnPoint
var _last_checkpoint: Checkpoint
var _sequence: Tween

@onready var player: Player = %Player
@onready var camera: GameCamera = %GameCamera
@onready var hud: Hud = %Hud
@onready var start_overlay: StartOverlay = %StartOverlay
@onready var pause_menu: PauseMenu = %PauseMenu
@onready var complete_panel: LevelCompletePanel = %LevelCompletePanel
@onready var death_banner: DeathBanner = %DeathBanner
@onready var fade: ScreenFade = %ScreenFade
@onready var _level_slot: Node2D = %LevelSlot
@onready var _tap_input: TapInput = %TapInput


func _ready() -> void:
	player.jumped.connect(_on_player_jumped)
	player.double_jumped.connect(_on_player_double_jumped)
	player.landed.connect(_on_player_landed)
	player.died.connect(_on_player_died)
	score.changed.connect(hud.set_score)
	progress.changed.connect(hud.set_progress)
	hud.pause_pressed.connect(pause)
	pause_menu.resume_pressed.connect(resume)
	pause_menu.restart_pressed.connect(restart_level)
	complete_panel.next_pressed.connect(func() -> void: play_level(level_index + 1))
	complete_panel.retry_pressed.connect(restart_level)
	start_overlay.level_chosen.connect(_on_level_chosen)
	_tap_input.tapped.connect(press_jump)
	_tap_input.pause_requested.connect(pause)
	var first := start_level if start_level >= 0 else Progression.furthest_unlocked(world)
	if Autoplay.requested():
		first = clampi(int(Autoplay.option("--autoplay-from")) - 1, 0, world.levels.size() - 1)
	var resume := Autoplay.option("--resume").split(",")
	if resume.size() == 6:
		first = clampi(int(resume[0]), 0, world.levels.size() - 1)
	load_level(first)
	if resume.size() == 6:
		_resume(int(resume[1]), float(resume[2]), int(resume[3]), {"tiles": int(resume[4]), "shards": int(resume[5])})
	if Autoplay.requested():
		var bot := Autoplay.new()
		bot.game = self
		add_child(bot)


func _physics_process(_delta: float) -> void:
	if state == State.PLAYING:
		score.update_progress(player.global_position.x)
		progress.update(player.global_position.x)


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


func load_level(index: int = level_index) -> void:
	var data := world.get_level(index) if world else null
	if data == null:
		push_error("GameSession: the world has no level %d." % index)
		return
	_kill_sequence()
	if level:
		_level_slot.remove_child(level)
		level.queue_free()
	level_index = index
	attempts = 1
	death_banner.hide()
	level = (load(data.scene_path) as PackedScene).instantiate()
	_level_slot.add_child(level)
	level.shard_collected.connect(_on_shard_collected)
	level.checkpoint_reached.connect(_on_checkpoint_reached)
	level.finish_reached.connect(_on_finish_reached)
	level.obstacle_cued.connect(_on_obstacle_cued)

	player.kill_y = level.kill_y
	player.set_speed_scale(level.data.speed_scale)
	camera.set_kill_line(level.kill_y)
	var spawn := level.get_spawn_feet_position()
	score.reset(spawn.x)
	progress.reset(spawn.x, level.get_finish().global_position.x)
	hud.set_progress(0.0, true)
	var marks: Array[float] = []
	for checkpoint in level.get_checkpoints():
		marks.append(progress.percent_at(checkpoint.global_position.x))
	hud.set_progress_marks(marks)
	_respawn = RespawnPoint.new(spawn, 0.0, score.snapshot())
	_last_checkpoint = null
	player.respawn_at(spawn, false)
	camera.snap_to_target()
	_set_state(State.READY)
	start_overlay.open(world, level_index)
	Events.level_started.emit(level.data.id)
	level_loaded.emit()


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


## Back to the start of this level, waiting for the first tap (that screen
## also lists the world's levels).
func restart_level() -> void:
	play_level(level_index)


## Loads level [param index] (a fresh start, waiting for the first tap) behind
## a quick fade: from the pause menu, the results panel or the level select.
func play_level(index: int) -> void:
	if not Progression.is_unlocked(world, index):
		return
	Events.ui_pressed.emit()
	get_tree().paused = false
	pause_menu.close()
	complete_panel.close()
	load_level(index)
	fade.color.a = 1.0
	_sequence = _new_sequence()
	fade.tween_to(_sequence, 0.0, fade_in_time)


## Where the player will come back after the next death.
func get_respawn_point() -> RespawnPoint:
	return _respawn


## Continues a run in a fresh engine (the web page restarts the engine when
## the browser drops the WebGL context): same level, back at checkpoint
## [param checkpoint] (-1: the start) with the progress, attempts and score
## the run had there.
func _resume(checkpoint: int, percent: float, tries: int, score_state: Dictionary) -> void:
	start_run()
	var cps := level.get_checkpoints()
	if checkpoint >= 0 and checkpoint < cps.size():
		_on_checkpoint_reached(cps[checkpoint])
		_respawn.score = score_state
		_respawn_player()
	attempts = maxi(tries, 1)
	progress.percent = clampf(percent, 0.0, 100.0)
	progress.changed.emit(progress.percent)
	hud.set_progress(progress.percent, true)


## Where a restarted engine would pick this run up (see [method _resume]);
## the web page keeps the latest copy.
func _publish_resume_point() -> void:
	if not OS.has_feature("web"):
		return
	var level_at := level_index
	var checkpoint := level.get_checkpoints().find(_last_checkpoint) if _last_checkpoint else -1
	var saved := _respawn.score if _respawn and checkpoint >= 0 else {"tiles": 0, "shards": 0}
	if state == State.COMPLETE and level_index + 1 < world.levels.size():
		level_at = level_index + 1  # Finished: a restart goes on to the next level.
		checkpoint = -1
		saved = {"tiles": 0, "shards": 0}
	var percent := progress.percent if level_at == level_index else 0.0
	# A property set through the bridge, not eval(): pages may forbid eval.
	var window := JavaScriptBridge.get_interface("window")
	if window:
		window.shapeJumpResume = "%d,%d,%.3f,%d,%d,%d" % [level_at, checkpoint, percent, attempts, saved.tiles, saved.shards]


func _set_state(next: State) -> void:
	state = next
	# The player can only die while actually playing: a hazard reaching it on
	# the start screen, while paused or after the finish must not leave a
	# dead player that the next tap would set running.
	player.invulnerable = state != State.PLAYING
	level.running = state == State.PLAYING or state == State.DYING or state == State.COMPLETE
	hud.set_pause_enabled(state == State.PLAYING or state == State.DYING)
	hud.visible = state != State.READY  # The level select owns the screen.
	state_changed.emit(state)
	_publish_resume_point()


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
	var reached := progress.percent
	progress.penalize()
	death_banner.show_death(attempts, score.get_score(), SaveSystem.get_record(level.data.id).best_score,
		reached, progress.percent)
	attempts += 1
	_sequence = _new_sequence()
	_sequence.tween_interval(death_pause)
	fade.tween_to(_sequence, 1.0, fade_out_time)
	_sequence.tween_callback(_respawn_player)
	_sequence.tween_callback(death_banner.hide_banner)
	fade.tween_to(_sequence, 0.0, fade_in_time)


func _on_level_chosen(index: int) -> void:
	if state == State.READY and index != level_index:
		play_level(index)


func _on_shard_collected(shard: Shard) -> void:
	score.add_shard()
	Events.shard_collected.emit(shard.global_position)


func _on_checkpoint_reached(checkpoint: Checkpoint) -> void:
	_respawn = RespawnPoint.new(respawn_feet_at(checkpoint.global_position), 0.0, score.snapshot())
	_respawn.level_time = time_at(_respawn.feet.x)
	_last_checkpoint = checkpoint
	hud.mark_checkpoint(level.get_checkpoints().find(checkpoint))
	_publish_resume_point()
	Events.checkpoint_reached.emit(checkpoint.global_position)


## Level time at which the player's centre passes [param x] on the first
## run, snapped to a physics tick of that run: a respawn then replays the
## first pass tick for tick (taps are quantised to ticks, so even a fraction
## of a tick of offset could flip a tight jump).
func time_at(x: float) -> float:
	var tick := 1.0 / Engine.physics_ticks_per_second
	var time := (x - level.get_spawn_feet_position().x) / player.get_run_speed()
	return roundf(time / tick) * tick


## Where to respawn for a checkpoint at [param marker]: on its ground, at
## the x the player really had at [method time_at] (within half a tick).
func respawn_feet_at(marker: Vector2) -> Vector2:
	var x := level.get_spawn_feet_position().x + player.get_run_speed() * time_at(marker.x)
	return Vector2(x, marker.y)


## Obstacle sounds only for what the player can see: an off-screen machine
## arming somewhere ahead must not distract or mislead.
func _on_obstacle_cued(kind: StringName, at: Vector2) -> void:
	if state != State.PLAYING or not camera.get_view_rect().grow(32.0).has_point(at):
		return
	match kind:
		&"warning":
			Events.obstacle_warning.emit(at)
		&"slam":
			Events.obstacle_slam.emit(at)


func _on_finish_reached() -> void:
	if state != State.PLAYING:
		return
	_set_state(State.COMPLETE)  # Also makes the player invulnerable.
	progress.complete()
	var result := LevelCompletePanel.Result.new()
	result.score = score.get_score()
	result.is_new_best = SaveSystem.record_result(level.data.id, result.score, score.shards, true)
	result.best = SaveSystem.get_record(level.data.id).best_score
	result.shards = score.shards
	result.total_shards = level.get_shard_count()
	result.attempts = attempts
	var next := world.get_level(level_index + 1)
	result.has_next = next != null
	if next:
		result.unlocked = "%s UNLOCKED" % next.display_name.to_upper()
	else:
		result.title = "WORLD %02d COMPLETE" % world.number
		result.unlocked = "%s UNLOCKED" % world.next_world_name.to_upper()
	Events.level_completed.emit(level.data.id, result.score, score.shards)
	_sequence = _new_sequence()
	_sequence.tween_method(player.set_speed_scale, level.data.speed_scale, 0.0, finish_brake_time) \
		.set_ease(Tween.EASE_OUT).set_trans(Tween.TRANS_CUBIC)
	_sequence.tween_callback(player.set_running.bind(false))
	_sequence.tween_interval(maxf(results_delay - finish_brake_time, 0.0))
	_sequence.tween_callback(complete_panel.open.bind(result))
