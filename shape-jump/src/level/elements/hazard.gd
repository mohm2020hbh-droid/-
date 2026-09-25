@tool
class_name Hazard
extends Area2D
## Base of every deadly obstacle (docs/GDD.md §7). It sits on the hazard
## layer, where only the player's hurtbox looks, and never scans anything
## itself. Like every level element it registers with its Level, so hazards
## with apply_time(t) follow the level clock and rewind with it.

## Hitboxes stay this far inside the drawn shape: touching the glow of an
## edge is forgiven, touching the edge itself is not.
const HITBOX_INSET := 4.0

## Last level time applied, to tell normal play from a rewind (for cues).
var _last_time := 0.0


func _ready() -> void:
	collision_layer = GameConst.LAYER_HAZARD
	collision_mask = 0
	monitoring = false
	# Just behind the level geometry: a block can only hide the part of a
	# hazard inside it, where the player can never be, so nothing reachable is
	# ever hidden, and retracted slabs and pivots vanish into the ground.
	z_index = -1
	if not Engine.is_editor_hint():
		Level.join(self)


func _enter_tree() -> void:
	if is_node_ready() and not Engine.is_editor_hint():
		Level.join(self)  # Re-entering after a reparent.


func _exit_tree() -> void:
	if not Engine.is_editor_hint():
		Level.leave(self)


func _process(_delta: float) -> void:
	if not Engine.is_editor_hint():
		HazardPulse.apply(self, global_position.x * 0.01)


## Adds a generated collision shape (internal: never saved into the scene).
func add_hitbox(shape: Shape2D, at := Vector2.ZERO) -> CollisionShape2D:
	var node := CollisionShape2D.new()
	node.shape = shape
	node.position = at
	add_child(node, false, Node.INTERNAL_MODE_FRONT)
	return node


## True when [param t] continues normal play from the last applied time
## (not a rewind, not the first frame), and records it. Cues (sounds) only
## fire in normal play.
func advance_to(t: float) -> bool:
	var step := t - _last_time
	_last_time = t
	return step > 0.0 and step < 0.1


## Reports a moment worth a sound or a flash (e.g. &"warning", &"slam") to
## the level, which decides whether the player can see it.
func cue(kind: StringName, at: Vector2) -> void:
	var level := Level.of(self)
	if level:
		level.report_cue(kind, at)
