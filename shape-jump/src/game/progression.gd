class_name Progression
## Unlock rules (docs/GDD.md §12): the first level of a world is open, every
## other level opens when the one before it is completed, and completing the
## last level completes the world and unlocks the next one. Everything is
## derived from the saved completion records, so there is no second source
## of truth to fall out of sync.

## Playtest switch: running with `-- --unlock-all` (the web build's test mode
## passes it) opens every level. Results are still saved as usual.
static var unlock_all := "--unlock-all" in OS.get_cmdline_user_args()


static func is_completed(level: LevelData) -> bool:
	return SaveSystem.get_record(level.id).completed


static func is_unlocked(world: WorldData, index: int) -> bool:
	if unlock_all:
		return index >= 0 and index < world.levels.size()
	if index <= 0:
		return index == 0
	return index < world.levels.size() and is_completed(world.levels[index - 1])


## The level a returning player most likely wants: the first one not yet
## completed, or the last one when all are done.
static func furthest_unlocked(world: WorldData) -> int:
	for i in world.levels.size():
		if not is_completed(world.levels[i]):
			return i
	return maxi(world.levels.size() - 1, 0)


static func is_world_completed(world: WorldData) -> bool:
	for level in world.levels:
		if not is_completed(level):
			return false
	return not world.levels.is_empty()
