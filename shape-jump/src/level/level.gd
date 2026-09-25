class_name Level
extends Node2D
## Root of every level scene (docs/ARCHITECTURE.md §5).
##
## Owns the level clock. Every node in the "timed" group below this level
## is a pure function of that clock (apply_time(t)), so rewinding the clock
## to a checkpoint's time restores the exact same obstacle timing. The level
## never touches the player: it reports shards, checkpoints and the finish
## through signals.
##
## Required children: a Marker2D named "SpawnPoint" (player feet position)
## and one FinishGate.

signal shard_collected(shard: Shard)
signal checkpoint_reached(checkpoint: Checkpoint)
signal finish_reached

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

@onready var spawn_point: Marker2D = $SpawnPoint


func _ready() -> void:
	if data == null:
		data = LevelData.new()
	for node in get_tree().get_nodes_in_group(&"timed"):
		if is_ancestor_of(node):
			_timed.append(node)
	for node in get_tree().get_nodes_in_group(&"shards"):
		if is_ancestor_of(node):
			var shard := node as Shard
			_shards.append(shard)
			shard.collected.connect(_on_shard_collected)
	for node in get_tree().get_nodes_in_group(&"checkpoints"):
		if is_ancestor_of(node):
			var checkpoint := node as Checkpoint
			_checkpoints.append(checkpoint)
			checkpoint.reached.connect(_on_checkpoint_reached)
	_checkpoints.sort_custom(func(a: Checkpoint, b: Checkpoint) -> bool: return a.global_position.x < b.global_position.x)
	_finish = _find_finish(self)
	if _finish:
		_finish.reached.connect(func() -> void: finish_reached.emit())
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
		if node is Oscillator:
			# Teleported, not moved: do not draw an interpolated streak.
			(node as Oscillator).reset_parent_interpolation()
	for shard in _shards:
		if shard.is_collected and shard.collected_at > t:
			shard.restore()


func get_spawn_feet_position() -> Vector2:
	return spawn_point.global_position


func get_shard_count() -> int:
	return _shards.size()


func get_checkpoints() -> Array[Checkpoint]:
	return _checkpoints


func get_finish() -> FinishGate:
	return _finish


func get_timed_elements() -> Array[Node]:
	return _timed


func _apply_time() -> void:
	for node in _timed:
		node.apply_time(clock)


func _on_shard_collected(shard: Shard) -> void:
	shard.collected_at = clock
	shard_collected.emit(shard)


func _on_checkpoint_reached(checkpoint: Checkpoint) -> void:
	checkpoint.level_time = clock
	checkpoint_reached.emit(checkpoint)


func _find_finish(root: Node) -> FinishGate:
	for child in root.get_children():
		if child is FinishGate:
			return child
		var nested := _find_finish(child)
		if nested:
			return nested
	return null
