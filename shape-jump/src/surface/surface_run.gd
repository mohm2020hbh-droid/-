class_name SurfaceRun
extends RefCounted
## World 04's run state (docs/GDD.md §9D), in one place: which surface is the
## floor, what the player is doing about it, and where it comes back after a
## death. The rules themselves live where they act (the gesture in
## [PlayerMotor], the attach check in [SurfaceAttach], gravity in
## [GravityState]); this model reads them every physics tick, names the
## state, and is what the look, the tests and the diagnostics ask.
##
##   GROUND_RUN ──tap, tap──► ATTACHING (target CEILING) ──touch──► CEILING_RUN
##   CEILING_RUN ──tap, tap──► ATTACHING (target GROUND) ──touch──► GROUND_RUN
##   GROUND_RUN / CEILING_RUN ──tap, tap (no surface it can reach)──► same (a failed attach)
##   GROUND_RUN / CEILING_RUN ──off an edge──► AIRBORNE ──► the kill line or a lower floor
##   any ──hit──► DEAD ──fade──► RESPAWNING ──► GROUND_RUN / CEILING_RUN (the checkpoint's surface)
##   any ──finish──► LEVEL_COMPLETE
##
## Gravity only turns on the touch that ends an attach, so while ATTACHING
## the current surface (and gravity) is still the one being left.

signal phase_changed(phase: Phase, previous: Phase)

enum Surface { GROUND, CEILING, NONE }
enum Phase { IDLE, GROUND_RUN, CEILING_RUN, ATTACHING, AIRBORNE, DEAD, RESPAWNING, LEVEL_COMPLETE }
## What the player is doing about surfaces.
enum Action { NONE, ATTACHING }
## The two-tap gesture as the motor sees it (mirrors [enum PlayerMotor.Gesture]).
enum Gesture { IDLE, TAP_PENDING, ATTACH_REQUEST }

var phase: Phase = Phase.IDLE
## The surface that is the floor right now (gravity pulls toward it).
var surface: Surface = Surface.GROUND
## While ATTACHING: the surface being crossed to; otherwise NONE.
var target_surface: Surface = Surface.NONE
var action: Action = Action.NONE
var gesture: Gesture = Gesture.IDLE
## Where the next respawn happens, and on which surface.
var respawn_surface: Surface = Surface.GROUND
var respawn_position := Vector2.ZERO
## Attaches made since the level started, and failed gestures.
var attaches := 0
var fails := 0

var _player: Player
var _gravity: GravityState


func _init(player: Player, gravity: GravityState) -> void:
	_player = player
	_gravity = gravity


static func surface_of(up: bool) -> Surface:
	return Surface.CEILING if up else Surface.GROUND


## The pull of gravity, as a world direction (DOWN on the ground, UP on the ceiling).
func gravity_direction() -> Vector2:
	return Vector2.UP if surface == Surface.CEILING else Vector2.DOWN


## The normal of the surface the player stands on (points into the corridor).
func surface_normal() -> Vector2:
	return -gravity_direction()


func is_attaching() -> bool:
	return action == Action.ATTACHING


## A new gesture would be decided now (on a surface, not crossing).
func can_attach() -> bool:
	return _player.can_attach()


func set_respawn(position: Vector2, up: bool) -> void:
	respawn_position = position
	respawn_surface = surface_of(up)


## A new level: back on its starting surface, nothing counted yet.
func reset(up: bool, spawn: Vector2) -> void:
	attaches = 0
	fails = 0
	surface = surface_of(up)
	target_surface = Surface.NONE
	action = Action.NONE
	gesture = Gesture.IDLE
	set_respawn(spawn, up)
	_set_phase(Phase.IDLE)


## Reads the player and gravity (every physics tick, and after any event).
## [param session_state]: the game's state, for the phases it owns.
func update(session_state: GameSession.State) -> void:
	surface = surface_of(_gravity.up)
	action = Action.ATTACHING if _player.is_attaching() else Action.NONE
	target_surface = surface_of(not _gravity.up) if action == Action.ATTACHING else Surface.NONE
	gesture = _player.motor.gesture as Gesture
	match session_state:
		GameSession.State.COMPLETE:
			_set_phase(Phase.LEVEL_COMPLETE)
			return
		GameSession.State.DYING:
			_set_phase(Phase.DEAD if _player.is_dead() else Phase.RESPAWNING)
			return
		GameSession.State.READY:
			_set_phase(Phase.IDLE)
			return
		GameSession.State.PAUSED:
			return  # Frozen as it was.
	if _player.is_dead():
		_set_phase(Phase.DEAD)
	elif action == Action.ATTACHING:
		_set_phase(Phase.ATTACHING)
	elif _player.is_on_floor():
		_set_phase(Phase.CEILING_RUN if surface == Surface.CEILING else Phase.GROUND_RUN)
	else:
		_set_phase(Phase.AIRBORNE)


func note_attach(_to_up: bool) -> void:
	attaches += 1


func note_fail() -> void:
	fails += 1


func _set_phase(next: Phase) -> void:
	if next == phase:
		return
	var previous := phase
	phase = next
	phase_changed.emit(next, previous)
