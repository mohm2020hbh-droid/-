package com.carom.core.level

/** Where level files come from: Android assets in the game, a directory in tests and tools. */
interface LevelSource {
    /** File names (without extension) of all levels, in any order. */
    fun list(): List<String>

    /** The JSON text of level [id]. */
    fun read(id: String): String
}

/**
 * The ordered level list. Order comes from the file names — numeric names sort numerically
 * ("9" before "10"), so adding level 031 is just adding `031.json`. Levels are parsed on demand,
 * so the list stays cheap with hundreds of levels.
 */
class LevelRepository(private val source: LevelSource) {

    val ids: List<String> = sortIds(source.list())

    val size: Int get() = ids.size

    private var cachedIndex = -1
    private var cached: LevelData? = null

    fun load(index: Int): LevelData {
        require(index in ids.indices) { "No level at index $index (have $size)" }
        cached?.let { if (cachedIndex == index) return it }
        return LevelParser.parse(ids[index], source.read(ids[index])).also {
            cached = it
            cachedIndex = index
        }
    }

    companion object {
        fun sortIds(ids: Collection<String>): List<String> =
            ids.sortedWith(compareBy<String>({ it.toLongOrNull() ?: Long.MAX_VALUE }, { it }))
    }
}
