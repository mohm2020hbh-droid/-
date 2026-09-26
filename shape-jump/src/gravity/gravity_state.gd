class_name GravityState
extends RefCounted
## The direction of gravity for the current run: the single source of truth
## (World 03, docs/GDD.md §9C). The game session owns one; the level writes
## it from its gravity schedule every tick, and the player, camera, world
## look and gravity-aware obstacles only read it or listen to [signal flipped].
##
## DOWN: the ground is the floor (every world before World 03, always).
## UP:   the ceiling is the floor; jumps push toward the ground.
##
## The physics flips in one tick. [member phase] is FLIPPING for
## [constant TRANSITION_TIME] afterwards: the camera turns, the colours
## change, the effects play; nothing in the physics waits for it.

## [param instant]: a restore (level start, respawn, rewind) rather than a
## flip in play: snap, no transition.
signal flipped(up: bool, instant: bool)

enum Phase { NORMAL, FLIPPING }

const TRANSITION_TIME := 0.4

var up := false
var phase := Phase.NORMAL
## Flips in play since the last reset (tests and diagnostics).
var flips := 0

var _transition_left := 0.0


## +1 when gravity pulls down (world y grows), -1 when it pulls up.
func down_sign() -> float:
	return -1.0 if up else 1.0


## The direction a jump pushes (away from the surface the player stands on).
func up_vector() -> Vector2:
	return Vector2.DOWN if up else Vector2.UP


## Sets the direction. A change in play starts a transition; an
## [param instant] one (a restore) does not. The same direction is a no-op,
## so a gate can never flip twice.
func set_up(value: bool, instant := false) -> void:
	if value == up:
		if instant:
			_end_transition()
		return
	up = value
	if instant:
		_end_transition()
	else:
		flips += 1
		phase = Phase.FLIPPING
		_transition_left = TRANSITION_TIME
	flipped.emit(up, instant)


## Back to [param value] at once, telling every listener (a new level).
func reset(value := false) -> void:
	up = value
	flips = 0
	_end_transition()
	flipped.emit(up, true)


## Advances the transition (the level calls this every physics tick).
func advance(delta: float) -> void:
	if phase == Phase.FLIPPING:
		_transition_left -= delta
		if _transition_left <= 0.0:
			_end_transition()


## 0 at the moment of a flip, 1 once the transition is over.
func transition_progress() -> float:
	return 1.0 - _transition_left / TRANSITION_TIME if phase == Phase.FLIPPING else 1.0


func _end_transition() -> void:
	phase = Phase.NORMAL
	_transition_left = 0.0
