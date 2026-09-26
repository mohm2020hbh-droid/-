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
	## Gravity there (World 03): true when the ceiling was the floor.
	var gravity_up := false

	func _init(feet_position: Vector2, time: float, score_snapshot: Dictionary) -> void:
		feet = feet_position
		level_time = time
		score = score_snapshot


## Every world, in order (World 01 first).
@export var worlds: Array[WorldData] = []
## World opened on start; -1 = the furthest one unlocked.
@export var start_world := -1
## Level (index in the start world) opened on start; -1 = the furthest unlocked.
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
## The world being played, and its index in [member worlds].
var world: WorldData
var world_index := -1
## The run's gravity: the single source of truth (World 03 turns it; the
## other worlds leave it pulling down). Shared by the level, the player and
## the camera.
var gravity := GravityState.new()
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
@onready var whiteout: WhiteoutVeil = %WhiteoutVeil
@onready var _embers: CPUParticles2D = %Embers
@onready var _embers_ramp_red: Gradient = _embers.color_ramp
var _embers_ramp_mono: Gradient = _make_mono_ramp()


static func _make_mono_ramp() -> Gradient:
	var g := Gradient.new()
	g.colors = PackedColorArray([Color(1, 1, 1, 0.0), Color(1, 1, 1, 0.55), Color(0.8, 0.8, 0.8, 0.0)])
	g.offsets = PackedFloat32Array([0.0, 0.3, 1.0])
	return g


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
	complete_panel.next_pressed.connect(_play_next)
	complete_panel.retry_pressed.connect(restart_level)
	start_overlay.level_chosen.connect(_on_level_chosen)
	start_overlay.world_chosen.connect(_on_world_chosen)
	whiteout.player = player
	player.gravity = gravity
	camera.gravity = gravity
	gravity.flipped.connect(_on_gravity_flipped)
	_tap_input.tapped.connect(press_jump)
	_tap_input.pause_requested.connect(pause)
	# A fixed start level (tests, development) means World 01 unless a start
	# world is given too; otherwise the player's furthest world.
	var w := start_world if start_world >= 0 else (0 if start_level >= 0 else _furthest_world())
	if Autoplay.requested() and Autoplay.option("--autoplay-world") != "":
		w = int(Autoplay.option("--autoplay-world")) - 1
	var resume := Autoplay.option("--resume").split(",")
	if resume.size() == 7:
		w = int(resume[0])
	_use_world(clampi(w, 0, worlds.size() - 1))
	var first := start_level if start_level >= 0 else Progression.furthest_unlocked(world)
	if Autoplay.requested():
		first = clampi(int(Autoplay.option("--autoplay-from")) - 1, 0, world.levels.size() - 1)
	if resume.size() == 7:
		first = clampi(int(resume[1]), 0, world.levels.size() - 1)
	load_level(first)
	if resume.size() == 7:
		_resume(int(resume[2]), float(resume[3]), int(resume[4]), {"tiles": int(resume[5]), "shards": int(resume[6])})
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
	player.kill_top = level.kill_top
	player.set_speed_scale(level.data.speed_scale)
	camera.set_kill_line(level.kill_y, level.kill_top)
	camera.vertical_offset = level.camera_offset
	var spawn := level.get_spawn_feet_position()
	gravity.reset(level.start_gravity_up)
	level.gravity = gravity
	level.set_run_line(spawn.x, player.get_run_speed())
	score.reset(spawn.x)
	progress.reset(spawn.x, level.get_finish().global_position.x)
	hud.set_progress(0.0, true)
	var marks: Array[float] = []
	for checkpoint in level.get_checkpoints():
		marks.append(progress.percent_at(checkpoint.global_position.x))
	hud.set_progress_marks(marks)
	hud.set_progress_milestones(world.progress_milestones)
	_respawn = RespawnPoint.new(spawn, 0.0, score.snapshot())
	_respawn.gravity_up = level.start_gravity_up
	_last_checkpoint = null
	player.respawn_at(spawn, false)
	camera.snap_to_target()
	_set_state(State.READY)
	start_overlay.open(worlds, world_index, level_index)
	whiteout.level = level
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
	var world_at := world_index
	var level_at := level_index
	var checkpoint := level.get_checkpoints().find(_last_checkpoint) if _last_checkpoint else -1
	var saved := _respawn.score if _respawn and checkpoint >= 0 else {"tiles": 0, "shards": 0}
	if state == State.COMPLETE:
		checkpoint = -1  # Finished: a restart goes on to the next level.
		saved = {"tiles": 0, "shards": 0}
		if level_index + 1 < world.levels.size():
			level_at = level_index + 1
		elif world_index + 1 < worlds.size():
			world_at = world_index + 1
			level_at = 0
	var percent := progress.percent if level_at == level_index and world_at == world_index else 0.0
	# A property set through the bridge, not eval(): pages may forbid eval.
	var window := JavaScriptBridge.get_interface("window")
	if window:
		window.shapeJumpResume = "%d,%d,%d,%.3f,%d,%d,%d" % [world_at, level_at, checkpoint, percent, attempts,
			saved.tiles, saved.shards]


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
	# The respawn's gravity is the schedule's at that time (checkpoints sit on
	# steady ground, never at a gate); rewind_to already applied it, this
	# makes it explicit for the player and camera snap below.
	gravity.set_up(_respawn.gravity_up, true)
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


## NEXT on the results: the next level, or the first level of the next world.
func _play_next() -> void:
	if level_index + 1 < world.levels.size():
		play_level(level_index + 1)
	elif world_index + 1 < worlds.size() and Progression.is_world_unlocked(worlds[world_index + 1]):
		play_world(world_index + 1, 0)


## Opens world [param index] at level [param at] (-1: its furthest unlocked
## level), switching the whole look (palette, background, UI) to it.
func play_world(index: int, at := -1) -> void:
	if index < 0 or index >= worlds.size() or not Progression.is_world_unlocked(worlds[index]):
		return
	_use_world(index)
	play_level(at if at >= 0 else Progression.furthest_unlocked(world))


func _furthest_world() -> int:
	var best := 0
	for i in worlds.size():
		if Progression.is_world_unlocked(worlds[i]):
			best = i
	return best


## Makes [param index] the current world and dresses everything in its look.
func _use_world(index: int) -> void:
	world_index = index
	world = worlds[index]
	Palette.use(world.theme)
	if world.background:
		var old := get_node_or_null(^"World/Background")
		if old == null or old.scene_file_path != world.background.resource_path:
			var fresh := world.background.instantiate()
			if old:
				old.get_parent().remove_child(old)
				old.queue_free()
			fresh.name = "Background"
			$World.add_child(fresh)
			$World.move_child(fresh, 0)
	# World 01 keeps the tesseract core; later worlds show the trapped void.
	player.visual.void_style = world.theme != &"red"
	player.fx.refresh_colors()
	UiLook.apply(self, world.theme)
	_embers.color_ramp = _embers_ramp_red if world.theme == &"red" else _embers_ramp_mono
	_embers.modulate = Color.WHITE
	_embers.speed_scale = 1.0
	hud.refresh_look()
	var background := get_node_or_null(^"World/Background")
	if background and background.has_method(&"attach"):
		background.attach(self)  # World 03's look follows gravity.


func _on_world_chosen(index: int) -> void:
	if state == State.READY and index != world_index:
		play_world(index)


func _on_level_chosen(index: int) -> void:
	if state == State.READY and index != level_index:
		play_level(index)


func _on_shard_collected(shard: Shard) -> void:
	score.add_shard()
	Events.shard_collected.emit(shard.global_position)


func _on_checkpoint_reached(checkpoint: Checkpoint) -> void:
	_respawn = RespawnPoint.new(respawn_feet_at(checkpoint.global_position), 0.0, score.snapshot())
	_respawn.level_time = time_at(_respawn.feet.x)
	# From the level's schedule, not the live state: a resumed run (a fresh
	# engine after a lost WebGL context) reaches its checkpoint from the start
	# of the level, with the start's gravity still in place.
	_respawn.gravity_up = level.gravity_up_at(_respawn.level_time)
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
		&"gravity_warning":
			Events.gravity_warning.emit(at)


## A flip in play is heard (restores on respawn and level start are not).
func _on_gravity_flipped(up: bool, instant: bool) -> void:
	if not instant and state == State.PLAYING:
		Events.gravity_flipped.emit(up, player.global_position)


func _on_finish_reached() -> void:
	if state != State.PLAYING:
		return
	_set_state(State.COMPLETE)  # Also makes the player invulnerable.
	progress.complete()
	player.visual.settle()
	var result := LevelCompletePanel.Result.new()
	result.score = score.get_score()
	result.is_new_best = SaveSystem.record_result(level.data.id, result.score, score.shards, true)
	result.best = SaveSystem.get_record(level.data.id).best_score
	result.shards = score.shards
	result.total_shards = level.get_shard_count()
	result.attempts = attempts
	var next := world.get_level(level_index + 1)
	result.has_next = next != null or world_index + 1 < worlds.size()
	if next:
		result.unlocked = "%s UNLOCKED" % next.display_name.to_upper()
	else:
		result.title = "WORLD %02d COMPLETE" % world.number
		result.unlocked = "%s UNLOCKED" % world.next_world_name.to_upper() if world.next_world_name != "" else ""
	Events.level_completed.emit(level.data.id, result.score, score.shards)
	_sequence = _new_sequence()
	_sequence.tween_method(player.set_speed_scale, level.data.speed_scale, 0.0, finish_brake_time) \
		.set_ease(Tween.EASE_OUT).set_trans(Tween.TRANS_CUBIC)
	_sequence.tween_callback(player.set_running.bind(false))
	_sequence.tween_interval(maxf(results_delay - finish_brake_time, 0.0))
	_sequence.tween_callback(complete_panel.open.bind(result))
