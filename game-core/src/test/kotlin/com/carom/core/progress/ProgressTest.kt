package com.carom.core.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {

    private class MapStore : KeyValueStore {
        val map = HashMap<String, String>()
        override fun getString(key: String) = map[key]
        override fun putString(key: String, value: String) {
            map[key] = value
        }
    }

    private val ids = listOf("001", "002", "003", "004")

    @Test
    fun onlyTheFirstLevelIsOpenAtStart() {
        val p = Progress(MapStore(), ids)
        assertTrue(p.isUnlocked(0))
        assertFalse(p.isUnlocked(1))
        assertEquals(0, p.currentIndex)
        assertEquals(0, p.completedCount)
    }

    @Test
    fun completingALevelUnlocksTheNextAndSurvivesARestart() {
        val store = MapStore()
        Progress(store, ids).apply {
            markCompleted(0)
            markCompleted(1)
            lastPlayedIndex = 1
        }
        val reloaded = Progress(store, ids)
        assertTrue(reloaded.isCompleted(0))
        assertTrue(reloaded.isCompleted(1))
        assertTrue(reloaded.isUnlocked(2))
        assertFalse(reloaded.isUnlocked(3))
        assertEquals(2, reloaded.currentIndex)
        assertEquals(1, reloaded.lastPlayedIndex)
        assertEquals(2, reloaded.completedCount)
    }

    /** The campaign: six worlds of ten. Only level 1 is open; a world opens when the last level of the one before it is finished. */
    @Test
    fun aWorldOpensOnlyWhenTheWorldBeforeIsFinished() {
        val campaign = (1..60).map { "%03d".format(it) }
        val p = Progress(MapStore(), campaign)
        assertEquals("only level 1 is open", listOf(0), campaign.indices.filter(p::isUnlocked))
        for (world in 0 until 6) {
            val first = world * 10
            assertTrue("world ${world + 1} is open once its first level is", p.isUnlocked(first))
            for (slot in 0 until 10) {
                val level = first + slot
                assertFalse("nothing past level ${level + 1} is open until it is finished", campaign.indices.any { it > level && p.isUnlocked(it) })
                p.markCompleted(level)
                if (slot < 9) assertTrue(p.isUnlocked(level + 1)) else if (world < 5) assertTrue("finishing the last level of world ${world + 1} opens world ${world + 2}", p.isUnlocked(level + 1))
            }
        }
        assertEquals(59, p.highestUnlockedIndex)
        assertEquals(60, p.completedCount)
    }

    @Test
    fun theNextWorldStaysShutWhileOneLevelOfTheWorldIsLeft() {
        val campaign = (1..60).map { "%03d".format(it) }
        val p = Progress(MapStore(), campaign)
        (0 until 9).forEach(p::markCompleted)
        assertTrue(p.isUnlocked(9))
        assertFalse("world 2 is shut while level 10 is not finished", p.isUnlocked(10))
        p.markCompleted(9)
        assertTrue(p.isUnlocked(10))
    }

    @Test
    fun replayingAnOldLevelDoesNotLockAnything() {
        val p = Progress(MapStore(), ids)
        p.markCompleted(0)
        p.markCompleted(1)
        p.markCompleted(0)
        assertEquals(2, p.highestUnlockedIndex)
    }

    @Test
    fun finishingEverythingKeepsTheLastLevelCurrent() {
        val p = Progress(MapStore(), ids)
        ids.indices.forEach(p::markCompleted)
        assertEquals(3, p.highestUnlockedIndex)
        assertEquals(3, p.currentIndex)
    }

    @Test
    fun aLevelInsertedLaterIsPlayableAndBecomesCurrent() {
        val store = MapStore()
        Progress(store, ids).apply { (0..2).forEach(::markCompleted) }
        // An update adds "002b" between 002 and 003.
        val updated = Progress(store, listOf("001", "002", "002b", "003", "004"))
        assertTrue(updated.isUnlocked(2))
        assertFalse(updated.isCompleted(2))
        assertEquals(2, updated.currentIndex)
        assertTrue(updated.isUnlocked(4))
    }

    @Test
    fun failedAttemptsAreCountedPerLevelAndKept() {
        val store = MapStore()
        val progress = Progress(store, listOf("001", "002"))
        assertEquals(0, progress.failCount(0))
        assertEquals(1, progress.recordFail(0))
        assertEquals(2, progress.recordFail(0))
        assertEquals(0, progress.failCount(1))
        assertEquals(2, Progress(store, listOf("001", "002")).failCount(0))
    }

    @Test
    fun settingsPersistAndAreSeparate() {
        val store = MapStore()
        assertTrue(Settings(store).hapticsEnabled)
        assertTrue(Settings(store).soundEnabled)
        Settings(store).hapticsEnabled = false
        assertFalse(Settings(store).hapticsEnabled)
        assertTrue(Settings(store).soundEnabled)
        Settings(store).soundEnabled = false
        assertFalse(Settings(store).soundEnabled)
    }
}
