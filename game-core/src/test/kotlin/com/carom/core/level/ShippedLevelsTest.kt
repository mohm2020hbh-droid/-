package com.carom.core.level

import com.carom.core.game.HintRoute
import com.carom.core.tools.LevelPreview
import com.carom.core.tools.ShotSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks every level shipped with the game: it must parse, pass validation and be solvable within
 * its bounce budget with a winning window wide enough to hit with a finger.
 */
class ShippedLevelsTest {

    private class DirectorySource(private val dir: File) : LevelSource {
        override fun list() = dir.listFiles { f -> f.name.endsWith(".json") }!!.map { it.name.removeSuffix(".json") }
        override fun read(id: String) = File(dir, "$id.json").readText()
    }

    private val dir = File(System.getProperty("levels.dir") ?: "../game/src/main/assets/levels")
    private val repository by lazy { LevelRepository(DirectorySource(dir)) }
    private val levels by lazy { (0 until repository.size).map(repository::load) }

    @Test
    fun levelsExist() {
        assertTrue("no levels found in ${dir.absolutePath}", repository.size > 0)
    }

    @Test
    fun levelsFillWholeWorlds() {
        assertEquals("every world must have exactly ${Worlds.SIZE} levels", 0, repository.size % Worlds.SIZE)
    }

    @Test
    fun everyLevelIsValid() {
        val problems = levels.flatMap { level -> LevelValidator.problems(level).map { "${level.id}: $it" } }
        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun everyLevelIsSolvableAndFair() {
        val results = levels.map { ShotSearch.search(it) }
        println(
            String.format(
                "%-5s %-18s %7s %7s %9s %8s %s",
                "id", "name", "budget", "needs", "arc(deg)", "widest", "windows",
            ),
        )
        for (r in results) {
            println(
                String.format(
                    "%-5s %-18s %7d %7s %9.2f %8.2f %d",
                    r.level.id, r.level.name.take(18), r.level.bounces, r.minBounces ?: "-",
                    r.winningArc, r.widestWindow, r.windows.size,
                ),
            )
        }

        System.getProperty("preview.dir")?.let { out ->
            results.chunked(10).forEachIndexed { i, chunk ->
                val entries = chunk.map { r ->
                    r.level to (r.sampleAngle?.let { ShotSearch.fire(r.level, it).path } ?: emptyList())
                }
                LevelPreview.writeSheet(File(out, "levels-${i + 1}.png"), entries)
            }
        }

        for (r in results) {
            assertTrue("level ${r.level.id} has no solution", r.solvable)
            assertTrue(
                "level ${r.level.id} needs a ${r.widestWindow}° window, too precise for touch",
                r.widestWindow >= MIN_FAIR_WINDOW_DEGREES,
            )
        }
    }

    /** A level's path hint must be a throw that really scores, or it would mislead the player. */
    @Test
    fun everyPathHintScores() {
        val wrong = levels.filter { it.guide != null }.mapNotNull { level ->
            val route = HintRoute.plan(level, level.guide!!)
            if (route.scores && route.points.size >= 2) null else "${level.id}: the hinted throw does not score"
        }
        assertEquals(emptyList<String>(), wrong)
        assertTrue("level 23 has a path hint", levels.any { it.id == "023" && it.guide != null })
    }

    /**
     * The player may move the ball around its launch zone before throwing. That may make a shot
     * easier to line up, but it must never skip a bounce the level is built around.
     */
    @Test
    fun movingTheBallAroundItsLaunchZoneNeverSkipsABounce() {
        val skips = levels.mapNotNull { level ->
            val fromStart = ShotSearch.search(level, stepDegrees = 0.5).minBounces ?: return@mapNotNull null
            val fromZone = ShotSearch.minBouncesFromZone(level) ?: return@mapNotNull null
            if (fromZone < fromStart) "${level.id}: $fromStart bounces from the start, $fromZone from its launch zone" else null
        }
        assertEquals(emptyList<String>(), skips)
    }

    private companion object {
        const val MIN_FAIR_WINDOW_DEGREES = 0.3
    }
}
