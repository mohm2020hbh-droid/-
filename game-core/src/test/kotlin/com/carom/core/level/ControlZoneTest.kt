package com.carom.core.level

import com.carom.core.game.GameSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The control zone: its shapes, how a level names one, and that it works as a real boundary. */
class ControlZoneTest {

    private val out = DoubleArray(2)

    private fun level(zone: String?, ball: String = "[500, 1500]", goal: String = "[500, 300]") = LevelParser.parse(
        "z",
        """{"bounces": 1, "ball": $ball, "goal": $goal ${zone?.let { ", $it" } ?: ""}}""",
    )

    @Test
    fun aBoxKeepsTheWholeBallInsideItsLine() {
        val box = ControlZone.Box(100.0, 1000.0, 700.0, 600.0)
        box.nearestCentre(0.0, 0.0, 60.0, out) // far up-left: the corner, one radius in from both edges
        assertEquals(160.0, out[0], 1e-9)
        assertEquals(1060.0, out[1], 1e-9)
        box.nearestCentre(2000.0, 5000.0, 60.0, out)
        assertEquals(740.0, out[0], 1e-9)
        assertEquals(1540.0, out[1], 1e-9)
        box.nearestCentre(400.0, 1200.0, 60.0, out) // inside: unchanged
        assertEquals(400.0, out[0], 1e-9)
        assertEquals(1200.0, out[1], 1e-9)
        assertTrue(box.holds(160.0, 1060.0, 60.0))
        assertFalse(box.holds(159.0, 1060.0, 60.0))
        assertEquals(0.0, box.distanceFromCentres(400.0, 1200.0, 60.0), 1e-9)
        assertEquals(60.0, box.distanceFromCentres(160.0, 1000.0, 60.0), 1e-9) // 60 above the top centre line
    }

    @Test
    fun aCircleKeepsTheWholeBallInsideItsLine() {
        val circle = ControlZone.Circle(450.0, 1500.0, 260.0)
        circle.nearestCentre(450.0, 0.0, 60.0, out) // straight up: 200 from the centre (260 − 60)
        assertEquals(450.0, out[0], 1e-9)
        assertEquals(1300.0, out[1], 1e-9)
        assertTrue(circle.holds(450.0, 1300.0, 60.0))
        assertFalse(circle.holds(450.0, 1299.0, 60.0))
        assertEquals(800.0, circle.distanceFromCentres(450.0, 500.0, 60.0), 1e-9) // 1000 from the middle, 200 of it allowed
    }

    @Test
    fun aTouchNearTheLineStillCounts() {
        val box = ControlZone.Box(0.0, 1000.0, 900.0, 1000.0)
        assertTrue(box.reaches(450.0, 1100.0, 0.0))
        assertFalse(box.reaches(450.0, 950.0, 0.0))
        assertTrue(box.reaches(450.0, 950.0, 60.0))
    }

    @Test
    fun aLevelNamesItsZoneInOneOfThreeForms() {
        assertEquals(ControlZone.Box(0.0, 1280.0, 900.0, 720.0), level("\"controlZone\": {\"top\": 1280}").zone)
        assertEquals(ControlZone.Box(100.0, 1200.0, 700.0, 800.0), level("\"controlZone\": {\"rect\": [100, 1200, 700, 800]}").zone)
        assertEquals(ControlZone.Circle(500.0, 1500.0, 320.0), level("\"controlZone\": {\"circle\": [500, 1500, 320]}").zone)
    }

    @Test
    fun theOlderLaunchZoneMeansADiscRoundTheStart() {
        val zone = level("\"launchZone\": 130").zone
        assertEquals(ControlZone.Circle(500.0, 1500.0, 190.0), zone) // 130 for the centre, plus the ball's own radius
        zone.nearestCentre(500.0, 0.0, 60.0, out)
        assertEquals(1370.0, out[1], 1e-9)
    }

    @Test
    fun aLevelWithoutAZoneGetsTheStandardBand() {
        assertEquals(ControlZone.Box(0.0, 1100.0, 900.0, 900.0), level(null).zone) // 400 above the start, down to the bottom edge
        assertEquals(ControlZone.Box(0.0, 0.0, 900.0, 2000.0), level(null, ball = "[500, 100]", goal = "[500, 1800]").zone)
    }

    @Test
    fun badZonesAreRefused() {
        for (bad in listOf(
            "\"controlZone\": {}", "\"controlZone\": {\"top\": 1, \"rect\": [0, 0, 1, 1]}", "\"controlZone\": {\"rect\": [0, 0, 0, 100]}",
            "\"controlZone\": {\"circle\": [0, 0, -5]}", "\"controlZone\": {\"top\": 2500}", "\"controlZone\": 5", "\"controlZone\": {\"rect\": [1, 2, 3]}",
        )) {
            try {
                level(bad)
                fail("accepted $bad")
            } catch (expected: LevelFormatException) {
                assertNotNull(expected.message)
            }
        }
    }

    @Test
    fun theValidatorKeepsTheGoalOutOfReachAndTheBallInside() {
        assertEquals(emptyList<String>(), LevelValidator.problems(level("\"controlZone\": {\"top\": 1300}")))
        // A band that starts above the goal lets the ball be put into it.
        assertTrue(LevelValidator.problems(level("\"controlZone\": {\"top\": 200}")).any { "reaches the goal" in it })
        // The ball's start has to be inside its own zone.
        assertTrue(LevelValidator.problems(level("\"controlZone\": {\"rect\": [0, 1000, 300, 600]}")).any { "outside its control zone" in it })
        // A disc that only just misses the goal is fine.
        assertEquals(emptyList<String>(), LevelValidator.problems(level("\"controlZone\": {\"circle\": [500, 1500, 300]}")))
    }

    @Test
    fun theZoneIsARealBoundaryTheBallCannotLeave() {
        val band = GameSession(level("\"controlZone\": {\"top\": 1300}"))
        assertTrue(band.placeBall(500.0, 0.0)) // dragged far past the dashed line
        assertEquals(1300.0 + band.level.ballRadius, band.ball.y, 1e-3) // it stops with its edge on the line
        assertTrue(band.placeBall(-500.0, 5000.0)) // and across the whole width of the band, to the corners
        assertEquals(band.level.ballRadius, band.ball.x, 1e-3)
        assertEquals(band.level.height - band.level.ballRadius, band.ball.y, 1e-3)

        val box = GameSession(level("\"controlZone\": {\"rect\": [300, 1300, 400, 500]}"))
        box.placeBall(0.0, 0.0)
        assertEquals(360.0, box.ball.x, 1e-3)
        assertEquals(1360.0, box.ball.y, 1e-3)
        box.placeBall(5000.0, 5000.0)
        assertEquals(640.0, box.ball.x, 1e-3)
        assertEquals(1740.0, box.ball.y, 1e-3)

        val disc = GameSession(level("\"controlZone\": {\"circle\": [500, 1500, 260]}"))
        disc.placeBall(500.0, 0.0)
        assertEquals(1300.0, disc.ball.y, 1e-3)
    }
}
