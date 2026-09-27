class_name SurfaceRun
extends RefCounted
## World 04's run state (docs/GDD.md §9D), in one place: which surface is the
## floor, what the player is doing about it, and where it comes back after a
## death. The rules themselves live where they act (the tap rules in
## [PlayerMotor], the latch check in [SurfaceLatch], gravity in
## [GravityState]); this model reads them every physics tick, names the
## phase, and is what the look, the tests and the diagnostics ask.
##
##   GROUND_RUN ──tap──► AIRBORNE ──tap (in reach)──► LATCHING ──touch──► CEILING_RUN
##   CEILING_RUN ──tap──► RELEASE ──tap (in reach)──► LATCHING ──touch──► GROUND_RUN
##   AIRBORNE / RELEASE ──tap (out of reach)──► (same phase, air tap spent) ──land──► back on its floor
##   any ──hit──► DEAD ──fade──► RESPAWNING ──► GROUND_RUN / CEILING_RUN (the checkpoint's surface)
##   any ──finish──► LEVEL_COMPLETE
##
## RELEASE is the ceiling's AIRBORNE: the player let go of the ceiling and
## flies toward the ground; gravity still pulls it back up unless the second
## tap latches to the ground.

signal phase_changed(phase: Phase, previous: Phase)

enum Surface { GROUND, CEILING }
enum Phase { IDLE, GROUND_RUN, CEILING_RUN, AIRBORNE, RELEASE, LATCHING, DEAD, RESPAWNING, LEVEL_COMPLETE }

var phase: Phase = Phase.IDLE
## The surface that is the floor right now (gravity pulls toward it).
var surface: Surface = Surface.GROUND
## While LATCHING: the surface being crossed to (the floor already).
var target_surface: Surface = Surface.GROUND
## Where the next respawn happens, and on which surface.
var respawn_surface: Surface = Surface.GROUND
var respawn_position := Vector2.ZERO
## Latches made since the level started (misses are not latches).
var latches := 0
var misses := 0

var _player: Player
var _gravity: GravityState


func _init(player: Player, gravity: GravityState) -> void:
	_player = player
	_gravity = gravity


static func surface_of(up: bool) -> Surface:
	return Surface.CEILING if up else Surface.GROUND


## The pull of gravity, as a world direction.
func gravity_direction() -> Vector2:
	return Vector2.UP if surface == Surface.CEILING else Vector2.DOWN


## The normal of the surface the player stands on (points into the corridor).
func surface_normal() -> Vector2:
	return -gravity_direction()


func is_airborne() -> bool:
	return phase == Phase.AIRBORNE or phase == Phase.RELEASE or phase == Phase.LATCHING


func is_latching() -> bool:
	return phase == Phase.LATCHING


## The second tap (the latch) is still there to use.
func has_second_tap() -> bool:
	return _player.has_double_jump() and not _player.is_latching()


func set_respawn(position: Vector2, up: bool) -> void:
	respawn_position = position
	respawn_surface = surface_of(up)


## A new level: back on its starting surface, nothing counted yet.
func reset(up: bool, spawn: Vector2) -> void:
	latches = 0
	misses = 0
	surface = surface_of(up)
	target_surface = surface
	set_respawn(spawn, up)
	_set_phase(Phase.IDLE)


## Reads the player and gravity (every physics tick, and after any event).
## [param session_state]: the game's state, for the phases it owns.
func update(session_state: GameSession.State) -> void:
	surface = surface_of(_gravity.up)
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
	elif _player.is_latching():
		target_surface = surface
		_set_phase(Phase.LATCHING)
	elif _player.is_on_floor():
		_set_phase(Phase.CEILING_RUN if surface == Surface.CEILING else Phase.GROUND_RUN)
	elif surface == Surface.CEILING:
		_set_phase(Phase.RELEASE)
	else:
		_set_phase(Phase.AIRBORNE)


func note_latch() -> void:
	latches += 1


func note_miss() -> void:
	misses += 1


func _set_phase(next: Phase) -> void:
	if next == phase:
		return
	var previous := phase
	phase = next
	phase_changed.emit(next, previous)
