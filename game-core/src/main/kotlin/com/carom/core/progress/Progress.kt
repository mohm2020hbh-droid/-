package com.carom.core.progress

/** Persistent key/value storage (SharedPreferences on Android, a map in tests). */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

/**
 * Which levels are completed and unlocked. Progress is stored by level id, so inserting or
 * reordering level files later never scrambles a player's save.
 *
 * Unlocking is linear: every level up to one past the furthest completed level is playable.
 */
class Progress(private val store: KeyValueStore, private val levelIds: List<String>) {

    private val completed: MutableSet<String> =
        store.getString(KEY_COMPLETED)?.split(',')?.filter { it.isNotEmpty() }?.toMutableSet() ?: mutableSetOf()

    val levelCount: Int get() = levelIds.size

    val completedCount: Int get() = levelIds.count { it in completed }

    /** Index of the furthest level the player may play. */
    val highestUnlockedIndex: Int
        get() {
            val furthestCompleted = levelIds.indexOfLast { it in completed }
            return (furthestCompleted + 1).coerceAtMost(levelIds.size - 1).coerceAtLeast(0)
        }

    fun isCompleted(index: Int): Boolean = levelIds.getOrNull(index) in completed

    fun isUnlocked(index: Int): Boolean = index in levelIds.indices && index <= highestUnlockedIndex

    /** The level "Play" continues from: the first unlocked level not yet completed, else the last. */
    val currentIndex: Int
        get() = (0..highestUnlockedIndex).firstOrNull { !isCompleted(it) } ?: highestUnlockedIndex

    fun markCompleted(index: Int) {
        val id = levelIds.getOrNull(index) ?: return
        if (completed.add(id)) store.putString(KEY_COMPLETED, completed.sorted().joinToString(","))
    }

    var lastPlayedIndex: Int
        get() = levelIds.indexOf(store.getString(KEY_LAST_PLAYED))
        set(value) {
            levelIds.getOrNull(value)?.let { store.putString(KEY_LAST_PLAYED, it) }
        }

    private companion object {
        const val KEY_COMPLETED = "progress.completed"
        const val KEY_LAST_PLAYED = "progress.lastPlayed"
    }
}

/** Player preferences. */
class Settings(private val store: KeyValueStore) {
    var hapticsEnabled: Boolean
        get() = store.getString(KEY_HAPTICS) != "off"
        set(value) = store.putString(KEY_HAPTICS, if (value) "on" else "off")

    private companion object {
        const val KEY_HAPTICS = "settings.haptics"
    }
}
