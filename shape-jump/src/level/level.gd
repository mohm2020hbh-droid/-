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

signal shard_collected(shard: Shard)
signal checkpoint_reached(checkpoint: Checkpoint)
signal finish_reached
## An obstacle reported a moment worth a sound (see [method Hazard.cue]).
signal obstacle_cued(kind: StringName, position: Vector2)

## Timed elements must move before the player each physics tick (it rides
## and collides with where they are now), whatever the scene tree order.
const PHYSICS_PRIORITY := -10

@export var data: LevelData
## Falling below this world Y kills the player.
@export var kill_y := 720.0

## Seconds of level time; advances only while [member running].
var clock := 0.0
var running := false

var _timed: Array[Node] = []
var _shards: Array[Shard] = []
var _checkpoints: Array[Checkpoint] = []
var _finish: FinishGate

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
		_apply_time()


## Rewinds all timed elements to [param t] and returns shards collected
## after that moment to the level.
func rewind_to(t: float) -> void:
	clock = t
	_apply_time()
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


func _apply_time() -> void:
	for node in _timed:
		node.apply_time(clock)


func _register(element: Node) -> void:
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
