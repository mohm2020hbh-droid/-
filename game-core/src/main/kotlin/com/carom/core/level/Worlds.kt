package com.carom.core.level

/**
 * Levels are grouped into worlds of exactly [SIZE] levels: world 0 holds levels 0–9, world 1
 * holds 10–19, and so on. Adding ten level files adds a world.
 */
object Worlds {
    const val SIZE = 10

    fun worldOf(levelIndex: Int): Int = levelIndex / SIZE

    fun count(levelCount: Int): Int = (levelCount + SIZE - 1) / SIZE

    fun firstLevel(world: Int): Int = world * SIZE
}
