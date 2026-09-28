class_name SurfaceAttach
extends RefCounted
## World 04's attach check (docs/GDD.md §9D): would an attach started now
## reach a surface it can hold? The [PlayerMotor] owns the gesture; this only
## answers the question, from the real collision shapes, when a gesture asks
## for it, and remembers where the attach would land (the camera and the cue
## read it).
##
## It follows the crossing exactly as the body will make it, tick by tick:
## the run carries the player forward while the pull carries it across
## ([method MovementConfig.attach_speed_at]). An attach is valid when, along
## that path:
## 1. the first thing met is a face (a target surface exists);
## 2. that face looks back at the player: a surface to stand on, not the side
##    of a wall or a corner (the path is not blocked, the normal is right);
## 3. it can be held ([code]latchable[/code] is not false: slick stone
##    cannot);
## 4. it is met within [member MovementConfig.attach_reach] of the body's far
##    side (reachable);
## 5. the body fits all the way there: every step of the path is free until
##    the touch (the destination is clear, the player can occupy it).
## Otherwise the attach fails: nothing moves. Something solid between the
## surfaces is what an attach meets first, so it can never pass through it.
##
## Mirrored in tools/levelgen/levelgen.py (Level.attach_probe).

## A face counts as standable when its normal points back at the player
## at least this much.
const FACE_NORMAL := 0.7

## True when an attach started this tick would take hold.
var in_reach := false
## Distance (px) from the body's far side to the target face.
var distance := 0.0
## Where the player's centre will be once it touches the target surface.
var landing := Vector2.ZERO
## World y of the target face (the cue lights it).
var face_y := 0.0
## Ticks the crossing will take.
var ticks := 0

var _player: Player


func _init(player: Player) -> void:
	_player = player


## Walks the crossing (see the class notes). [param can_attach]: the player
## stands on a surface (or just left one) and is not crossing already.
func probe(can_attach := true) -> bool:
	in_reach = false
	if not can_attach:
		return false
	var config := _player.config
	var toward := _player.up_direction  # Away from the floor: toward the other surface.
	var dt := 1.0 / Engine.physics_ticks_per_second
	var run := _player.get_run_speed() * dt
	var from := _player.global_transform
	var covered := 0.0
	var limit := config.attach_ticks(config.attach_reach, dt) + 1
	for k in limit:
		var step := toward * config.attach_speed_at(k, dt) * dt
		var hit := KinematicCollision2D.new()
		if _player.test_move(from, Vector2(run, 0.0) + step, hit):
			if hit.get_normal().dot(toward) > -FACE_NORMAL:
				return false  # A wall or a corner in the way.
			var surface := hit.get_collider()
			if surface and surface.get(&"latchable") == false:
				return false  # Slick: it cannot be held.
			covered += hit.get_travel().dot(toward)
			if covered > config.attach_reach:
				return false
			distance = covered
			landing = from.origin + hit.get_travel()
			face_y = landing.y + toward.y * _player.half_size.y
			ticks = k + 1
			in_reach = true
			return true
		from = from.translated(Vector2(run, 0.0) + step)
		covered += step.length()
		if covered > config.attach_reach:
			return false
	return false
