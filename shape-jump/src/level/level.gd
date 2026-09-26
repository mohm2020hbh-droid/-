class_name Level
extends Node2D
## Root of every level scene (docs/ARCHITECTURE.md §5).
##
## Owns the level clock. Every timed element below this level is a pure
## function of that clock (apply_time(t)), so rewinding the clock to a
## checkpoint's time restores the exact same obstacle timing. The level never
## touches the player: it reports shards, checkpoints and the finish through
## signals.
##
## Elements register themselves when they enter a level and unregister when
## they leave ([method join] / [method leave]), so obstacles spawned or freed
## at runtime are handled like the ones placed in the editor.
##
## Required children: a Marker2D named "SpawnPoint" (player feet position)
## and one FinishGate.
##
## Gravity (World 03): elements with gravity_events() (gravity gates, flip
## fields) turn gravity at fixed x positions. The player's x is a straight
## line in level time, so gravity is a pure function of the clock too
## ([method gravity_up_at]): it rewinds with everything else, and a respawn
## restores it exactly. The level writes it into [member gravity] each tick.

signal shard_collected(shard: Shard)
signal checkpoint_reached(checkpoint: Checkpoint)
signal finish_reached
## An obstacle reported a moment worth a sound (see [method Hazard.cue]).
signal obstacle_cued(kind: StringName, position: Vector2)

## Timed elements must move before the player each physics tick (it rides
## and collides with where they are now), whatever the scene tree order.
const PHYSICS_PRIORITY := -10
## A gate at x turns gravity on the tick the player's centre reaches it.
const GRAVITY_EPSILON := 0.001

@export var data: LevelData
## Falling below this world Y kills the player.
@export var kill_y := 720.0
## Rising above this world Y kills the player (when the ceiling is the floor).
@export var kill_top := -1.0e7
## Gravity when the level starts (true: the ceiling is the floor).
@export var start_gravity_up := false
## Where the view's centre sits, in px away from the floor from the player
## (the camera's vertical offset; a corridor level centres its corridor).
@export var camera_offset := -90.0

## Seconds of level time; advances only while [member running].
var clock := 0.0
var running := false
## The run's gravity, written from the schedule each tick (set by the game).
var gravity: GravityState

var _timed: Array[Node] = []
var _shards: Array[Shard] = []
var _checkpoints: Array[Checkpoint] = []
var _finish: FinishGate
var _gravity_switches: Array[Node] = []
## Every gravity change in x order: Vector2(x, 1.0 for up / 0.0 for down).
var _gravity_events: Array[Vector2] = []
var _gravity_dirty := true
## The player's run: x = _run_origin + _run_speed * clock.
var _run_origin := 0.0
var _run_speed := 0.0

@onready var spawn_point: Marker2D = get_node_or_null(^"SpawnPoint")


## Registers [param element] with the level it belongs to (if any). Level
## elements call this when they are ready or re-enter the tree.
static func join(element: Node) -> void:
	var level := of(element)
	if level:
		level._register(element)


## Unregisters [param element]; level elements call this from _exit_tree.
static func leave(element: Node) -> void:
	var level := of(element)
	if level:
		level._unregister(element)


func _ready() -> void:
	# Runs after every child registered itself (children are ready first).
	process_physics_priority = PHYSICS_PRIORITY
	if data == null:
		data = LevelData.new()
	if spawn_point == null:
		push_error("Level '%s' has no Marker2D named SpawnPoint; the player will spawn at the origin." % name)
	if _finish == null:
		push_error("Level '%s' has no FinishGate; it can never be completed." % name)
	_apply_time()


func _physics_process(delta: float) -> void:
	if running:
		clock += delta
		if gravity:
			gravity.advance(delta)
		_apply_time()


## Rewinds all timed elements to [param t] and returns shards collected
## after that moment to the level.
func rewind_to(t: float) -> void:
	clock = t
	_apply_time(true)
	for node in _timed:
		# Teleported, not moved: do not draw an interpolated streak.
		if node is Oscillator:
			(node as Oscillator).reset_parent_interpolation()
		elif node is CanvasItem:
			node.reset_physics_interpolation()
	for shard in _shards:
		if shard.is_collected and shard.collected_at > t:
			shard.restore()


func get_spawn_feet_position() -> Vector2:
	return spawn_point.global_position if spawn_point else global_position


func get_shard_count() -> int:
	return _shards.size()


func get_checkpoints() -> Array[Checkpoint]:
	return _checkpoints


func get_finish() -> FinishGate:
	return _finish


func get_timed_elements() -> Array[Node]:
	return _timed


func report_cue(kind: StringName, at: Vector2) -> void:
	obstacle_cued.emit(kind, at)


## Tells the level where the player's centre is at every moment of the run
## (x = [param origin_x] + [param speed] * clock), which places its gravity
## changes in time.
func set_run_line(origin_x: float, speed: float) -> void:
	_run_origin = origin_x
	_run_speed = speed
	_apply_time(true)


## The player's centre x at level time [param t].
func run_x_at(t: float) -> float:
	return _run_origin + _run_speed * t


## True when gravity pulls up at level time [param t] (a pure function of
## the clock: the gates are crossed at fixed times).
func gravity_up_at(t: float) -> bool:
	if _gravity_dirty:
		_rebuild_gravity_events()
	var x := run_x_at(t)
	var up := start_gravity_up
	for event in _gravity_events:
		if x < event.x - GRAVITY_EPSILON:
			break
		up = event.y > 0.5
	return up


## Level time of the last real gravity change at or before [param t]
## (-INF: none yet). Gravity-aware machines (mines) move from there.
func last_gravity_change(t: float) -> float:
	if _gravity_dirty:
		_rebuild_gravity_events()
	var x := run_x_at(t)
	var up := start_gravity_up
	var last := -INF
	for event in _gravity_events:
		if x < event.x - GRAVITY_EPSILON:
			break
		if (event.y > 0.5) != up:
			up = event.y > 0.5
			last = time_at_x(event.x)
	return last


## The next real gravity change after [param t]: 1 (to up), 0 (to down),
## or -1 (none left).
func next_gravity_change(t: float) -> int:
	if _gravity_dirty:
		_rebuild_gravity_events()
	var x := run_x_at(t)
	var up := gravity_up_at(t)
	for event in _gravity_events:
		if x < event.x - GRAVITY_EPSILON and (event.y > 0.5) != up:
			return 1 if event.y > 0.5 else 0
	return -1


## Level time at which the player's centre reaches [param x].
func time_at_x(x: float) -> float:
	return (x - _run_origin) / _run_speed if _run_speed > 0.0 else 0.0


func has_gravity_changes() -> bool:
	return not _gravity_switches.is_empty()


## x of every gravity change, in order, with the state after it.
func get_gravity_events() -> Array[Vector2]:
	if _gravity_dirty:
		_rebuild_gravity_events()
	return _gravity_events


func _rebuild_gravity_events() -> void:
	_gravity_events.clear()
	for node in _gravity_switches:
		for event: Vector2 in node.gravity_events():
			_gravity_events.append(event)
	_gravity_events.sort_custom(func(a: Vector2, b: Vector2) -> bool: return a.x < b.x)
	_gravity_dirty = false


func _apply_time(instant := false) -> void:
	if gravity:
		gravity.set_up(gravity_up_at(clock), instant)
	for node in _timed:
		node.apply_time(clock)


func _register(element: Node) -> void:
	if element.has_method(&"gravity_events") and not element in _gravity_switches:
		_gravity_switches.append(element)
		_gravity_dirty = true
	if element.has_method(&"apply_time") and not element in _timed:
		_timed.append(element)
		if is_node_ready():
			element.apply_time(clock)  # Spawned mid-run: start in phase.
	if element is Shard and not element in _shards:
		_shards.append(element)
		element.collected.connect(_on_shard_collected)
	elif element is Checkpoint and not element in _checkpoints:
		_checkpoints.append(element)
		element.reached.connect(_on_checkpoint_reached)
		_checkpoints.sort_custom(func(a: Checkpoint, b: Checkpoint) -> bool:
			return a.global_position.x < b.global_position.x)
	elif element is FinishGate:
		if _finish and _finish != element:
			push_warning("Level '%s' has more than one FinishGate; using the last one." % name)
		_finish = element
		if not _finish.reached.is_connected(_on_finish_reached):
			_finish.reached.connect(_on_finish_reached)


func _unregister(element: Node) -> void:
	_timed.erase(element)
	if _gravity_switches.has(element):
		_gravity_switches.erase(element)
		_gravity_dirty = true
	if element is Shard:
		_shards.erase(element)
		if element.collected.is_connected(_on_shard_collected):
			element.collected.disconnect(_on_shard_collected)
	elif element is Checkpoint:
		_checkpoints.erase(element)
		if element.reached.is_connected(_on_checkpoint_reached):
			element.reached.disconnect(_on_checkpoint_reached)
	elif element == _finish:
		_finish = null


## The Level [param element] belongs to, or null.
static func of(element: Node) -> Level:
	var node := element.get_parent()
	while node and not node is Level:
		node = node.get_parent()
	return node as Level


func _on_shard_collected(shard: Shard) -> void:
	shard.collected_at = clock
	shard_collected.emit(shard)


func _on_checkpoint_reached(checkpoint: Checkpoint) -> void:
	checkpoint_reached.emit(checkpoint)


func _on_finish_reached() -> void:
	finish_reached.emit()
