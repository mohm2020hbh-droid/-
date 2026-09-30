package com.carom.game

import com.carom.core.level.LevelSource
import com.carom.core.progress.KeyValueStore

/** Small purpose-made levels for the flow tests, so they do not depend on how the campaign is designed. */
class MemoryLevels(private val levels: Map<String, String> = DEFAULT) : LevelSource {
    override fun list() = levels.keys.toList()
    override fun read(id: String) = levels.getValue(id)

    companion object {
        val DEFAULT = mapOf(
            // A straight shot: the goal is right above the ball.
            "001" to """{"name": "Straight", "bounces": 3, "ball": [450, 1540], "goal": [450, 460]}""",
            // A wall between the ball and the goal: straight up hits it, then the floor, then the wall again.
            "002" to """{"name": "Wall", "bounces": 2, "ball": [660, 1540], "goal": [660, 460],
                "obstacles": [{"type": "wall", "points": [300, 1000, 900, 1000]}]}""",
            // A straight shot with a hint for a player who keeps failing (straight up scores).
            "003" to """{"name": "Hinted", "bounces": 1, "ball": [450, 1540], "goal": [450, 460],
                "guide": {"afterFails": 10, "angle": 270}}""",
        )
    }
}

class MapStore : KeyValueStore {
    val map = HashMap<String, String>()
    override fun getString(key: String) = map[key]
    override fun putString(key: String, value: String) {
        map[key] = value
    }
}
