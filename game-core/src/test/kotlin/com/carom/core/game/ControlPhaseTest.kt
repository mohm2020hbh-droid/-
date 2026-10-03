package com.carom.core.game

import com.carom.core.level.ControlZone
import com.carom.core.level.LevelParser
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Control after the throw: while any part of the ball is still inside the control zone it can be taken again, moved and
 * thrown again; once it is entirely outside, the player's control is over for the attempt and physics alone remains. The
 * zone's line is not a wall. A triple tap starts the attempt over.
 */
class ControlPhaseTest {

    /** A 900×2000 room; the zone is a box near the bottom: x 100..800, y 1400..1900; the ball starts in its middle. */
    private fun level(bounces: Int = 3, extra: String = "") = LevelParser.parse(
        "test",
        """
        {
          "bounces": $bounces, "drag": 0.25,
          "controlZone": {"rect": [100, 1400, 700, 500]},
          "ball": [450, 1650], "goal": [450, 400],
          "obstacles": [ {"type": "wall", "points": [0, 1000, 300, 1000]} $extra ]
        }
        """,
    )

    private fun session(bounces: Int = 3) = GameSession(level(bounces))

    private fun GameSession.stepFor(seconds: Double) {
        repeat((seconds / GameSession.STEP).toInt()) { step() }
    }

    @Test
    fun aStartingBallIsReady() {
        val s = session()
        assertEquals(GameSession.ControlPhase.READY, s.controlPhase)
        assertFalse(s.canRegrab)
    }

    @Test
    fun theZoneLineIsNotAWall() {
        val s = session()
        val zone = s.level.zone as ControlZone.Box
        s.launchAt(0.0, -400.0)
        var sawOutside = false
        repeat(300) {
            s.step()
            if (s.ball.y + s.ball.radius < zone.y) sawOutside = true
            assertEquals("no bounce is spent crossing the line", 3, s.bouncesLeft)
            assertEquals("the ball keeps travelling straight up", 0.0, s.ball.x - 450.0, 1e-6)
        }
        assertTrue(sawOutside)
        assertEquals("speed only falls by drag", true, s.ball.speed > 0.0)
    }

    @Test
    fun aFlyingBallInsideTheZoneCanBeTakenAndThrownAgain() {
        val s = session()
        assertTrue(s.launchAt(0.0, -300.0))
        assertEquals(GameSession.ControlPhase.LAUNCHED, s.controlPhase)
        s.stepFor(0.3)
        assertTrue(s.canRegrab)
        val yWhenTaken = s.ball.y
        assertTrue(s.regrab())
        assertEquals(GameSession.ControlPhase.REGRABBED, s.controlPhase)
        assertEquals(GameSession.State.AIMING, s.state)
        // Held: the game is paused, nothing moves.
        s.advance(0.5)
        assertEquals(yWhenTaken, s.ball.y, 0.0)
        assertEquals(0.0, s.ball.speed, 0.0)
        // Moved a little to the right, then let go moving right: it leaves with that velocity.
        assertTrue(s.placeBall(s.ball.x + 100.0, s.ball.y))
        assertEquals(550.0, s.ball.x, 1e-9)
        assertTrue(s.launchAt(250.0, 0.0))
        assertEquals(GameSession.ControlPhase.RELEASED_AGAIN, s.controlPhase)
        assertEquals(250.0, s.ball.speed, 1e-9)
        assertEquals(1.0, s.ball.dirX, 1e-9)
        assertEquals(2, s.throwCount)
    }

    @Test
    fun takingTheBallBackNeverRefundsABounceOrRewindsTheClock() {
        // A shelf just above the zone: the ball bounces off it while still partly inside the zone and comes back down.
        val shelf = GameSession(
            LevelParser.parse(
                "shelf",
                """
                {"bounces": 3, "drag": 0.25, "controlZone": {"rect": [100, 1400, 700, 500]}, "ball": [450, 1650], "goal": [800, 300],
                 "obstacles": [{"type": "wall", "points": [300, 1330, 600, 1330]}]}
                """,
            ),
        )
        shelf.launchAt(0.0, -900.0)
        var guard = 0
        while (shelf.bouncesLeft == 3 && guard++ < 5000) shelf.step()
        assertEquals(2, shelf.bouncesLeft)
        assertTrue("still partly inside the zone at the hit", shelf.canRegrab)
        val timeAtHit = shelf.gameTime
        assertTrue(shelf.regrab())
        assertEquals("no bounce is given back", 2, shelf.bouncesLeft)
        shelf.launchAt(0.0, -300.0)
        assertEquals(2, shelf.bouncesLeft)
        assertTrue("the clock carries on", shelf.gameTime >= timeAtHit)
    }

    @Test
    fun aBallLetGoAtRestWaitsWhereItWasPut() {
        val s = session()
        s.launchAt(0.0, -200.0)
        s.stepFor(0.2)
        s.regrab()
        s.placeBall(300.0, 1600.0)
        assertFalse("not moving: nothing to throw", s.launchAt(0.0, 0.0))
        s.letGo()
        assertEquals(GameSession.ControlPhase.READY, s.controlPhase)
        assertEquals(GameSession.State.AIMING, s.state)
        assertEquals(300.0, s.ball.x, 1e-9)
        // It can be picked up and thrown again, as before the first throw.
        assertTrue(s.placeBall(320.0, 1600.0))
        assertTrue(s.launchAt(60.0, -20.0))
        assertEquals(hypot(60.0, 20.0), s.ball.speed, 1e-9)
    }

    @Test
    fun aWeakReleaseAfterARegrabHasARealEffect() {
        val s = session()
        s.launchAt(0.0, -400.0)
        s.stepFor(0.1)
        s.regrab()
        assertTrue("a slow movement still throws", s.launchAt(15.0, -10.0))
        assertTrue(s.ball.speed in 17.0..19.0)
    }

    @Test
    fun onceTheBallIsEntirelyOutsideTheZoneControlIsOverForGood() {
        val s = session()
        val zone = s.level.zone as ControlZone.Box
        s.launchAt(0.0, -700.0)
        // While a part of it is still inside the zone the ball can be taken.
        var guard = 0
        while (s.ball.y + s.ball.radius >= zone.y + 5 && guard++ < 5000) s.step()
        assertTrue("just about to be entirely across the line", s.canRegrab)
        while (s.ball.y + s.ball.radius > zone.y - 1 && guard++ < 5000) s.step()
        s.step()
        assertFalse(s.canRegrab)
        assertEquals(GameSession.ControlPhase.OUTSIDE_CONTROL_ZONE, s.controlPhase)
        assertFalse(s.regrab())
        assertEquals(GameSession.State.MOVING, s.state)
    }

    @Test
    fun aBallThatComesBackIsNotTheirsAgain() {
        // A wall high up throws the ball straight back down into the zone.
        val s = GameSession(
            LevelParser.parse(
                "back",
                """
                {"bounces": 3, "drag": 0.0, "controlZone": {"rect": [100, 1400, 700, 500]}, "ball": [450, 1650], "goal": [800, 300],
                 "obstacles": [{"type": "wall", "points": [300, 1000, 600, 1000]}]}
                """,
            ),
        )
        val zone = s.level.zone as ControlZone.Box
        s.launchAt(0.0, -700.0)
        var guard = 0
        while (s.bouncesLeft == 3 && guard++ < 5000) s.step()
        assertEquals(GameSession.ControlPhase.OUTSIDE_CONTROL_ZONE, s.controlPhase)
        while (s.ball.y < zone.y + 100 && guard++ < 20000) s.step()
        assertTrue("the ball is back inside the zone", zone.overlaps(s.ball.x, s.ball.y, s.ball.radius))
        assertFalse("but the player no longer has it", s.canRegrab)
        assertFalse(s.regrab())
    }

    @Test
    fun aTripleTapResetPutsEverythingBack() {
        val s = session(bounces = 3)
        s.placeBall(200.0, 1700.0)
        s.launchAt(0.0, -900.0)
        var guard = 0
        while (s.bouncesLeft == 3 && guard++ < 5000) s.step()
        s.restart()
        assertEquals(GameSession.State.AIMING, s.state)
        assertEquals(GameSession.ControlPhase.READY, s.controlPhase)
        assertEquals(450.0, s.ball.x, 0.0)
        assertEquals(1650.0, s.ball.y, 0.0)
        assertEquals(0.0, s.ball.speed, 0.0)
        assertEquals(3, s.bouncesLeft)
        assertEquals(0, s.throwCount)
        assertEquals(1, s.resetCount)
        // The control is ready again after having been lost.
        assertTrue(s.launchAt(0.0, -300.0))
    }

    @Test
    fun aResetAfterControlWasLostGivesControlBack() {
        val s = session()
        s.launchAt(0.0, -1200.0)
        s.stepFor(1.0)
        assertEquals(GameSession.ControlPhase.OUTSIDE_CONTROL_ZONE, s.controlPhase)
        s.restart()
        s.launchAt(0.0, -100.0)
        assertTrue(s.canRegrab)
        assertNotEquals(GameSession.ControlPhase.OUTSIDE_CONTROL_ZONE, s.controlPhase)
    }

    @Test
    fun aResetWhileHoldingTheBallIsFine() {
        val s = session()
        s.launchAt(0.0, -300.0)
        s.stepFor(0.2)
        s.regrab()
        s.restart()
        assertEquals(GameSession.ControlPhase.READY, s.controlPhase)
        assertEquals(1650.0, s.ball.y, 0.0)
    }

    @Test
    fun theZoneOverlapTestMatchesTheGeometry() {
        val box = ControlZone.Box(100.0, 1400.0, 700.0, 500.0)
        assertTrue(box.overlaps(450.0, 1350.0, 60.0)) // straddling the top line
        assertTrue(box.overlaps(450.0, 1340.0, 60.0)) // just touching
        assertFalse(box.overlaps(450.0, 1339.0, 60.0)) // clear
        assertFalse(box.overlaps(20.0, 1340.0, 60.0)) // clear of the corner (diagonal distance)
        assertTrue(box.overlaps(60.0, 1440.0, 60.0)) // straddling the left line
        val disc = ControlZone.Circle(450.0, 1500.0, 200.0)
        assertTrue(disc.overlaps(450.0, 1259.0, 60.0))
        assertFalse(disc.overlaps(450.0, 1239.0, 60.0))
    }
}
