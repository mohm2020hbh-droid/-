class_name SurfaceLatch
extends RefCounted
## World 04's second tap, as the physics sees it (docs/GDD.md §9D): can the
## player latch to the other surface right now? The [PlayerMotor] owns the
## tap rules; this only answers the question, once per tick, from the real
## collision shapes, and remembers where the latch would land (the camera
## and the latch cue read it).
##
## A latch is possible when, all at once:
## - the player is in the air, not already latching, with its air tap left;
## - moving its body straight toward the other surface (away from the floor
##   it stands on) meets a face within [member MovementConfig.latch_reach]
##   of it: the nearest solid in the way is the target, so a latch can never
##   pass through anything solid;
## - that face looks back at the player (a surface to stand on, not the side
##   of a wall or a corner) and is [code]latchable[/code] (every block unless
##   it says otherwise);
## - the surface is still there where the body arrives: the run carries the
##   player forward while it crosses, so the same check is made again at the
##   arrival x (a ceiling that ends just ahead is not a latch).
## Otherwise the tap is a miss: the body stays where physics puts it.
##
## Mirrored in tools/levelgen/levelgen.py (Level.simulate, latch_probe).

## A face counts as standable when its normal points back at the player
## at least this much.
const FACE_NORMAL := 0.7

## True when a latch started this tick would take hold (see the class notes).
var in_reach := false
## Distance (px) from the body's far side to the target face.
var distance := 0.0
## Where the player's centre will be once it stands on the target surface.
var landing := Vector2.ZERO
## World y of the target face (the cue lights it).
var face_y := 0.0

var _player: Player


func _init(player: Player) -> void:
	_player = player


## Re-reads the world for this tick. [param can_latch]: the player is in the
## air with its air tap left and not latching already.
func probe(can_latch: bool) -> bool:
	in_reach = false
	if not can_latch:
		return false
	var config := _player.config
	var toward := _player.up_direction  # Away from the floor: toward the other surface.
	var from := _player.global_transform
	var hit := _face_toward(from, toward, config.latch_reach)
	if hit == null:
		return false
	distance = hit.get_travel().length()
	var tick := 1.0 / Engine.physics_ticks_per_second
	var ahead := _player.get_run_speed() * tick * config.latch_ticks(distance, tick)
	# Still a floor there on arrival, at about the same height.
	var arrival := _face_toward(from.translated(Vector2(ahead, 0.0)), toward, config.latch_reach)
	if arrival == null or absf(arrival.get_travel().length() - distance) > 2.0:
		return false
	face_y = _player.global_position.y + toward.y * (distance + _player.half_size.y)
	landing = Vector2(_player.global_position.x + ahead, _player.global_position.y + toward.y * distance)
	in_reach = true
	return true


## The standable, latchable face met by moving the body from [param from]
## along [param toward], within [param reach] px; null if none.
func _face_toward(from: Transform2D, toward: Vector2, reach: float) -> KinematicCollision2D:
	var hit := KinematicCollision2D.new()
	if not _player.test_move(from, toward * reach, hit):
		return null
	if hit.get_normal().dot(toward) > -FACE_NORMAL:
		return null
	var surface := hit.get_collider()
	if surface and surface.get(&"latchable") == false:
		return null
	return hit
