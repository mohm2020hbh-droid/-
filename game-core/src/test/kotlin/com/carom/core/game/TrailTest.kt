package com.carom.core.game

import com.carom.core.level.LevelData
import com.carom.core.level.LevelParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/** The drawn line is the real flight: straight where nothing bends it, curved where a well, a hill or a booster does. */
class TrailTest {

    private fun level(elements: String, bounces: Int = 3, extra: String = ""): LevelData = LevelParser.parse(
        "t",
        """{"size": [4000, 2000], "bounces": $bounces, "ball": [200, 1000], "goal": [3900, 100], "launchZone": 0 $extra,
           "obstacles": [], "elements": [$elements]}""",
    )

    private fun GameSession.run(steps: Int) { repeat(steps) { if (state == GameSession.State.MOVING) step() } }

    @Test
    fun aFreeFlightIsOneStraightSegment() {
        val s = GameSession(level(""))
        s.applyImpulse(1500.0, 120.0)
        s.run(120)
        assertEquals("launch only: the end is added by the picture", 1, s.trail.size)
        assertEquals(s.path.size, s.trail.size)
    }

    @Test
    fun aWellBendsTheLineAndItFollowsTheBall() {
        val well = """{"kind": "attractive", "pos": [1400, 1250], "scale": [900, 900], "force": 300}"""
        val s = GameSession(level(well, extra = ""","drag":0"""))
        s.applyImpulse(1500.0, 0.0)
        val flown = ArrayList<Pair<Double, Double>>()
        repeat(200) { if (s.state == GameSession.State.MOVING) { s.step(); flown.add(s.ball.x to s.ball.y) } }
        assertTrue("the trail has points where the flight turned: ${s.trail.size}", s.trail.size > 8)
        // every point of the trail is a place the ball really was
        for (p in s.trail.drop(1).filter { t -> s.path.none { it.x == t.x && it.y == t.y } }) assertTrue("(${p.x}, ${p.y}) is on the ball's path", flown.any { hypot(it.first - p.x, it.second - p.y) < 1e-6 })
        val last = s.trail.last()
        // the line is not the chord: some point is well off the straight line from the launch to the ball
        val a = s.trail.first()
        val b = s.trail.last()
        val dx = b.x - a.x
        val dy = b.y - a.y
        val off = s.trail.maxOf { abs((it.x - a.x) * dy - (it.y - a.y) * dx) / hypot(dx, dy) }
        assertTrue("the line leaves the chord by ${off.toInt()} units", off > 40.0)
        assertTrue("the corners stay few while the trail is dense", s.trail.size > 3 * s.path.size)
    }

    @Test
    fun aBoosterAndAHillBendItToo() {
        val booster = """{"kind": "booster", "pos": [1400, 1000], "scale": [600, 600], "rotation": 90, "force": 6}"""
        val s = GameSession(level(booster, extra = ""","drag":0"""))
        s.applyImpulse(1500.0, 0.0)
        s.run(300)
        assertTrue(s.trail.size > 3)
        val hill = """{"kind": "repulsive", "pos": [1400, 1100], "scale": [800, 800], "force": 100}"""
        val h = GameSession(level(hill, extra = ""","drag":0"""))
        h.applyImpulse(1500.0, 0.0)
        h.run(300)
        assertTrue(h.trail.size > 3)
    }

    @Test
    fun bouncesAreCornersInBothLists() {
        val s = GameSession(level("", extra = ""","drag":0"""))
        s.applyImpulse(0.0, -2000.0)
        s.run(240)
        assertTrue(s.path.size >= 2)
        for (p in s.path) assertTrue("a corner is in the trail", s.trail.any { it.x == p.x && it.y == p.y })
    }
}
