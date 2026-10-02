package com.carom.core.level

import com.carom.core.game.HintRoute
import com.carom.core.game.GameSession
import com.carom.core.tools.LevelLab
import com.carom.core.tools.LevelPreview
import com.carom.core.tools.ShotSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors

/**
 * Checks every level shipped with the game: the campaign is eight worlds of ten, every level parses, passes validation and
 * is solvable within its bounce budget by a window of throws wide enough to hit with a finger, nothing in the control zone
 * lets the player skip a bounce a level is built around, and the difficulty climbs a step at a time.
 */
class ShippedLevelsTest {

    private class DirectorySource(private val dir: File) : LevelSource {
        override fun list() = dir.listFiles { f -> f.name.endsWith(".json") }!!.map { it.name.removeSuffix(".json") }
        override fun read(id: String) = File(dir, "$id.json").readText()
    }

    private val dir = File(System.getProperty("levels.dir") ?: "../game/src/main/assets/levels")
    private val repository by lazy { LevelRepository(DirectorySource(dir)) }
    private val levels by lazy { (0 until repository.size).map(repository::load) }

    private fun <T> inParallel(work: (LevelData) -> T): List<T> {
        val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors().coerceAtLeast(2))
        try {
            return levels.map { level -> pool.submit<T> { work(level) } }.map { it.get() }
        } finally {
            pool.shutdown()
        }
    }

    @Test
    fun levelsExist() {
        assertTrue("no levels found in ${dir.absolutePath}", repository.size > 0)
    }

    @Test
    fun theCampaignIsEightWorldsOfTenLevels() {
        assertEquals("eight worlds of ${Worlds.SIZE} levels", 8 * Worlds.SIZE, repository.size)
        assertEquals((1..repository.size).map { "%03d".format(it) }, repository.ids)
        assertEquals(8, Worlds.count(repository.size))
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
    fun everyLevelSaysWhatItTeachesAndHowHardItIs() {
        for (level in levels) {
            assertTrue("level ${level.id} has no concept", level.concept.isNotBlank())
            assertTrue("level ${level.id} has no difficulty rating", level.difficulty in 1..10)
        }
    }

    /**
     * The difficulty climbs a step at a time: no jump up of more than one, no fall of more than one, and each world is harder than the last.
     * The campaign has three chapters: worlds 1-6, then world 7 and world 8, which bring in the moving obstacles. Each chapter climbs by itself
     * (a new mechanic is taught from the beginning, so a chapter may start lower than the one before ended), and the campaign still ends on its hardest level.
     */
    @Test
    fun theDifficultyClimbsSmoothlyThroughEachChapter() {
        val d = levels.map { it.difficulty }
        assertEquals("the first level is the easiest", 1, d.first())
        assertEquals("the last level is the hardest", 10, d.last())
        assertEquals("the last level is the hardest of all", d.max(), d.last())
        val chapters = listOf(0 until 6 * Worlds.SIZE, 6 * Worlds.SIZE until 7 * Worlds.SIZE, 7 * Worlds.SIZE until 8 * Worlds.SIZE)
        for (chapter in chapters) {
            for (i in chapter.first + 1..chapter.last) {
                assertTrue("level ${i + 1} (${d[i]}) jumps up from level $i (${d[i - 1]})", d[i] - d[i - 1] <= 1)
                assertTrue("level ${i + 1} (${d[i]}) falls back from level $i (${d[i - 1]})", d[i - 1] - d[i] <= 1)
            }
        }
        for (chapter in chapters.drop(1)) assertTrue("a new chapter starts gently (level ${chapter.first + 1}: ${d[chapter.first]})", d[chapter.first] <= 4)
        val means = d.chunked(Worlds.SIZE).map { it.average() }
        for (w in 1 until 6) assertTrue("world ${w + 1} is not harder than world $w: $means", means[w] > means[w - 1])
        assertTrue("world 8 is not harder than world 7: $means", means[7] > means[6])
    }

    /** Every level names the kind of challenge it is ("Angles: ..."), all ten kinds are used, and none runs on for more than four levels. */
    @Test
    fun theKindsOfChallengeVaryAcrossTheCampaign() {
        val types = levels.map { it.concept.substringBefore(':').trim() }
        for ((i, t) in types.withIndex()) assertTrue("level ${i + 1}: '$t' is not a kind of challenge $CHALLENGES", t in CHALLENGES)
        assertEquals("every kind of challenge is used", CHALLENGES, types.toSet())
        var run = 1
        for (i in 1 until types.size) {
            run = if (types[i] == types[i - 1]) run + 1 else 1
            assertTrue("levels ${i - run + 2}..${i + 1} are all '${types[i]}'", run <= 4)
        }
    }

    @Test
    fun noWorldIsMadeOfOneKindOfChallenge() {
        // Ten levels in a row never repeat one concept.
        for (world in levels.chunked(Worlds.SIZE)) assertEquals("concepts repeat inside a world", world.size, world.map { it.concept }.toSet().size)
    }

    @Test
    fun everyLevelIsSolvableAndFair() {
        // A level built for a gentle throw (a door that opens by itself) has no answer at full speed: it is judged at gentle speeds.
        val reports = inParallel { level ->
            if (GameSession(level).animatesWhileAiming) return@inParallel null   // won on the level clock: see theTimedLevelsCanBeWonInsideTheirWindow
            val full = LevelLab.analyze(level, spacing = 160.0, angleStep = 0.5)
            if (full.solvable) full else LevelLab.analyze(level, spacing = 240.0, angleStep = 1.0, speeds = GENTLE_SPEEDS)
        }.filterNotNull()
        println(String.format("%-4s %-20s %6s %5s %5s %6s %9s %10s", "id", "name", "budget", "needs", "start", "spots", "window", "win.spots"))
        for (r in reports) {
            println(
                String.format(
                    "%-4s %-20s %6d %5s %5s %6d %8.1f° %6d/%d",
                    r.level.id, r.level.name.take(20), r.level.bounces, r.minBounces ?: "-", r.startMin ?: "-", r.spots,
                    r.widestWindow, r.winningSpots, r.spots,
                ),
            )
        }

        System.getProperty("preview.dir")?.let { out ->
            reports.chunked(10).forEachIndexed { i, chunk ->
                val entries = chunk.map { r ->
                    val t = r.bestThrow
                    val path = if (t == null) emptyList() else {
                        val s = com.carom.core.game.GameSession(r.level.copy(ball = t.spot))
                        val rad = Math.toRadians(t.angle)
                        s.launchAt(Math.cos(rad) * r.level.maxSpeed * t.speed, Math.sin(rad) * r.level.maxSpeed * t.speed)
                        while (s.state == com.carom.core.game.GameSession.State.MOVING) s.step()
                        s.path
                    }
                    r.level to path
                }
                LevelPreview.writeSheet(File(out, "levels-${i + 1}.png"), entries)
            }
        }

        for (r in reports) {
            assertTrue("level ${r.level.id} has no solution within ${r.level.bounces} bounces (needs ${r.minBounces})", r.solvable)
            val floor = windowFloor(r.level)
            assertTrue("level ${r.level.id} has a widest window of ${r.widestWindow}°, under the ${floor}° a finger can hit", r.widestWindow >= floor)
            val slack = if (r.level.difficulty <= 2) 3 else 0
            assertTrue(
                "level ${r.level.id} allows ${r.level.bounces} bounces but needs ${r.realMinBounces}: a level's budget is what it needs (a little more only at the start)",
                r.level.bounces - r.realMinBounces!! <= slack,
            )
        }
    }

    /** The narrowest widest-window a level may have: wide for the first taste, narrowing world by world. */
    private fun windowFloor(level: LevelData): Double {
        val number = level.id.toInt()
        if (number <= 3) return 9.0
        return WINDOW_FLOOR[(number - 1) / Worlds.SIZE]
    }

    /**
     * A level whose obstacles run on the level clock is won by choosing the moment as well as the aim, so it carries one winning throw and the span
     * of release times it wins for: that throw is replayed here at the start, middle and end of the span and across the width of its angles, and the span
     * must be long enough for a finger to hit.
     */
    @Test
    fun theTimedLevelsCanBeWonInsideTheirWindow() {
        val timed = levels.filter { GameSession(it).animatesWhileAiming }
        assertEquals("worlds 7 and 8 run on the level clock", 20, timed.size)
        for (level in timed) {
            val t = level.timing
            assertTrue("level ${level.id} says how it is won", t != null)
            t!!
            val minimum = if (level.id.toInt() <= 70) 0.35 else if (level.id.toInt() < 80) 0.25 else 0.2
            assertTrue("level ${level.id}: the timing window is ${t.duration}s, under $minimum", t.duration >= minimum)
            assertTrue("level ${level.id}: the angle window is ${t.width}°, under 3°", t.width >= 3.0)
            val mid = (t.open + t.close) / 2
            val shots = listOf(
                Triple(t.open + 0.02, t.angle + t.width / 2, "start of the window"),
                Triple(mid, t.angle + t.width / 2, "middle"),
                Triple(t.close - 0.02, t.angle + t.width / 2, "end of the window"),
                Triple(mid, t.angle + 0.3, "one edge of the angles"),
                Triple(mid, t.angle + t.width - 0.3, "the other edge"),
            )
            for ((at, angle, what) in shots) {
                val s = GameSession(level.copy(ball = t.from))
                s.warpClock(at)
                val rad = Math.toRadians(angle)
                s.launchAt(Math.cos(rad) * level.maxSpeed * t.speed, Math.sin(rad) * level.maxSpeed * t.speed)
                var n = 0
                while (s.state == GameSession.State.MOVING && n++ < 20000) s.step()
                assertEquals("level ${level.id}, $what (release at ${"%.2f".format(at)}s, ${"%.1f".format(angle)}°)", GameSession.State.WON, s.state)
                assertEquals("level ${level.id} needs all of its ${level.bounces} bounces", 0, s.balls[0].left)
            }
        }
    }

    /** A soft launch: a door that opens by itself is shut for a hard throw and open in time for a gentle one. */
    @Test
    fun theDoorThatOpensByItselfNeedsAGentleThrow() {
        val level = levels.first { it.name == "Patience" }
        val hard = LevelLab.analyze(level, spacing = 240.0, angleStep = 1.0, speeds = listOf(1.0, 0.7, 0.5))
        val gentle = LevelLab.analyze(level, spacing = 240.0, angleStep = 1.0, speeds = listOf(0.12, 0.2))
        assertFalse("a hard throw hits the door while it is shut", hard.solvable)
        assertTrue("a gentle throw arrives when it is open (${gentle.widestWindow}°)", gentle.solvable && gentle.widestWindow >= 3.0)
    }

    /** A strong launch: the hill of a repulsive zone turns a weak throw back, and lets a firm one through. */
    @Test
    fun theHillNeedsAFirmThrow() {
        val level = levels.first { it.name == "Hill" }
        val weak = LevelLab.analyze(level, spacing = 240.0, angleStep = 1.0, speeds = listOf(0.15, 0.3))
        val firm = LevelLab.analyze(level, spacing = 240.0, angleStep = 1.0, speeds = listOf(0.7, 1.0))
        assertFalse("a weak throw is pushed back", weak.solvable)
        assertTrue("a firm throw gets over (${firm.widestWindow}°)", firm.solvable && firm.widestWindow >= 4.0)
    }

    /** A level's path hint must be a throw that really scores, or it would mislead the player. */
    @Test
    fun everyPathHintScores() {
        val wrong = levels.filter { it.guide != null }.mapNotNull { level ->
            val route = HintRoute.plan(level, level.guide!!)
            if (route.scores && route.points.size >= 2) null else "${level.id}: the hinted throw does not score"
        }
        assertEquals(emptyList<String>(), wrong)
    }

    /**
     * The player may move the ball around its control zone before letting it go. That may make a shot easier to line up,
     * but it must never skip a bounce the level is built around: from anywhere the ball can be moved to inside the zone,
     * no full-speed throw may score with fewer bounces than the level's budget (less the slack of the first levels).
     * A way through that needs an aim within [SHORTCUT_WINDOW] degrees is no short cut a finger can take, and is let go.
     */
    @Test
    fun movingTheBallAroundItsControlZoneNeverSkipsABounce() {
        val problems = inParallel { level ->
            val slack = if (level.difficulty <= 2) 3 else 0
            val built = level.bounces - slack
            if (built <= 0 || GameSession(level).animatesWhileAiming) return@inParallel null   // a level on the level clock is searched over release times by the design lab
            val fromZone = ShotSearch.minBouncesFromZone(level, spacing = 80.0, stepDegrees = 1.0, below = built, minWindow = SHORTCUT_WINDOW)
            if (fromZone != null && fromZone < built) "${level.id}: built around $built bounces but $fromZone are enough from somewhere in its control zone" else null
        }
        assertEquals(emptyList<String>(), problems.filterNotNull())
    }

    private companion object {
        /** Levels 4-10, then worlds 2 to 6 (degrees). */
        val WINDOW_FLOOR = doubleArrayOf(5.0, 4.0, 3.5, 3.0, 2.5, 2.0)

        val GENTLE_SPEEDS = listOf(0.12, 0.2, 0.3, 0.45)

        val CHALLENGES = setOf("Positioning", "Angles", "Bounce", "Precision", "Momentum", "Timing", "Forces", "Portal routing", "Multiple balls", "Combination")

        /** A shorter way that needs the aim right to within this many degrees is not one a finger can find. */
        const val SHORTCUT_WINDOW = 2.0
    }
}
