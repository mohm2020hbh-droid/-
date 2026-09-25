extends Node
## Global signal bus for cross-cutting gameplay events.
##
## Only [GameSession] emits these. Systems that merely react to gameplay
## (audio today; haptics or analytics later) listen here so the gameplay code
## never needs to know they exist.

@warning_ignore_start("unused_signal")
signal level_started(level_id: StringName)
signal player_jumped(position: Vector2)
signal player_double_jumped(position: Vector2)
signal player_landed(position: Vector2, impact_speed: float)
signal player_died(position: Vector2, cause: StringName)
signal player_respawned(position: Vector2)
signal shard_collected(position: Vector2)
signal checkpoint_reached(position: Vector2)
## An obstacle on screen is about to strike (e.g. a crush block arming).
signal obstacle_warning(position: Vector2)
## A heavy obstacle on screen hit home (e.g. a crush block closing).
signal obstacle_slam(position: Vector2)
signal level_completed(level_id: StringName, score: int, shards: int)
signal ui_pressed
@warning_ignore_restore("unused_signal")
