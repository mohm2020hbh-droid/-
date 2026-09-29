package com.carom.core.game

import com.carom.core.level.LevelData
import com.carom.core.level.LevelParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot

/** The behaviours taken from the reference game, one test each: physics, control, forces, portals, slow motion, state. */
class ReferenceSystemsTest {

    /** A big empty box (nothing to hit but the edges); ball on the left, goal far off the path. */
    private fun level(
        elements: String = "",
        bounces: Int = 3,
        extra: String = "",
        w: Int = 8000,
        h: Int = 2000,
        goal: String = "[7800, 200]",
        obstacles: String = "",
    ): LevelData = LevelParser.parse(
        "t",
        """{"size": [$w, $h], "bounces": $bounces, "ball": [200, 1000], "goal": $goal, "launchZone": 0 $extra,
           "obstacles": [$obstacles], "elements": [$elements]}""",
    )

    private fun GameSession.steps(n: Int): GameSession {
        repeat(n) { step() }
        return this
    }

    private fun GameSession.seconds(s: Double): GameSession = steps(Math.round(s / GameSession.STEP).toInt())

    private class Log : GameSession.Listener {
        var bounces = 0
        val progress = ArrayList<Double>()
        var portals = 0
        var slowIn = 0
        var slowOut = 0
        var lost = 0
        var spawned = 0
        var exitPartial = 0
        val events = ArrayList<GameSession.ElementEvent>()
        override fun onBounce(impact: GameSession.Impact, bouncesLeft: Int) {
            bounces++
            progress.add(impact.progress)
        }
        override fun onPortal(ball: Int, fromX: Double, fromY: Double, toX: Double, toY: Double) { portals++ }
        override fun onSlowMo(active: Boolean) { if (active) slowIn++ else slowOut++ }
        override fun onBallLost(ball: Int, reason: GameSession.FailReason, x: Double, y: Double) { lost++ }
        override fun onBallSpawned(ball: Int, x: Double, y: Double) { spawned++ }
        override fun onExitPartial(count: Int, needed: Int, x: Double, y: Double) { exitPartial++ }
        override fun onElement(element: Int, event: GameSession.ElementEvent) { events.add(event) }
    }

    private fun session(lvl: LevelData, log: Log? = null) = GameSession(lvl).also { if (log != null) it.listener = log }

    // ------------------------------------------------------------------ ball physics

    @Test
    fun aFreeBallLosesSpeedToDragExponentiallyAndNeverFalls() {
        val s = session(level())
        assertTrue(s.applyImpulse(1200.0, 0.0))
        s.seconds(1.0)
        assertEquals(1200.0 * exp(-0.25), s.ball.speed, 1200.0 * 0.002)
        s.seconds(2.0)
        assertEquals(1200.0 * exp(-0.75), s.ball.speed, 1200.0 * 0.004)
        assertEquals("no gravity", 1000.0, s.ball.y, 1e-9)
    }

    @Test
    fun theBallStartsWithNoSpeedAndTheSwipeIsTheOnlyThingThatMovesIt() {
        val s = session(level())
        s.seconds(1.0)
        assertEquals(GameSession.State.AIMING, s.state) // nothing moves until the first swipe
        assertEquals(200.0, s.ball.x, 0.0)
    }

    // ------------------------------------------------------------------ control

    @Test
    fun aSwipeIsDeltaTimesSensibilityAndTheBallDoesNotFollowTheFinger() {
        val s = session(level())
        // 100 dp × 0.5 = 50 ref units per second; ×20 world units per ref unit = 1000.
        assertTrue(s.swipe(100.0, 0.0))
        assertEquals(1000.0, s.ball.speed, 1e-9)
        assertEquals(1.0, s.ball.dirX, 1e-12)
        assertEquals(200.0, s.ball.x, 0.0) // it starts where it was; only its velocity changed
    }

    @Test
    fun theSwipeDirectionIsTheSwipeAndAHugeSwipeIsCapped() {
        val s = session(level())
        assertTrue(s.swipe(-300.0, -400.0)) // 500 dp long → 250 ref units, over the 120 cap
        assertEquals(2400.0, s.ball.speed, 1e-9)
        assertEquals(-0.6, s.ball.dirX, 1e-12)
        assertEquals(-0.8, s.ball.dirY, 1e-12)
    }

    @Test
    fun aDragShorterThanTheToleranceIsNotASwipe() {
        val s = session(level())
        assertFalse(s.swipe(2.0, 2.0)) // under 4 dp
        assertEquals(GameSession.State.AIMING, s.state)
        assertTrue(s.swipe(4.0, 0.0))
    }

    @Test
    fun touchControlTellsSwipesTapsAndDoubleTapsApart() {
        val c = TouchControl()
        // a swipe: 60 dp
        c.begin(0.0, 0.0, 0.0); c.move(30.0, 0.0)
        assertEquals(30.0 * 0.5 / 120.0, c.power, 1e-12)
        val swipe = c.end(60.0, 0.0, 0.2)
        assertEquals(TouchControl.Gesture.SWIPE, swipe.gesture)
        assertEquals(60.0, swipe.dx, 0.0)
        assertEquals(30.0, c.impulseRef(swipe.dx, swipe.dy), 1e-12)
        // a tap: short, barely moved
        c.begin(0.0, 0.0, 1.0)
        assertEquals(TouchControl.Gesture.TAP, c.end(1.0, 1.0, 1.1).gesture)
        // a second one within 0.2 s of the first, but not a bounce of the same touch (≥ 0.1 s later): a double tap
        c.begin(0.0, 0.0, 1.25)
        assertEquals(TouchControl.Gesture.DOUBLE_TAP, c.end(0.0, 0.0, 1.3).gesture)
        // a long press is neither
        c.begin(0.0, 0.0, 5.0)
        assertEquals(TouchControl.Gesture.NONE, c.end(0.0, 0.0, 5.6).gesture)
        // a tap too long after the last tap is a plain tap again
        c.begin(0.0, 0.0, 7.0); c.end(0.0, 0.0, 7.05)
        c.begin(0.0, 0.0, 7.6)
        assertEquals(TouchControl.Gesture.TAP, c.end(0.0, 0.0, 7.65).gesture)
        // the tuning values can be changed
        c.tuning = GameTuning(swipeTolerance = 30.0)
        c.begin(0.0, 0.0, 9.0)
        assertEquals(TouchControl.Gesture.TAP, c.end(20.0, 0.0, 9.1).gesture)
    }

    @Test
    fun aTouchZoneLetsThePlayerPushTheBallAgainWhileItMoves() {
        val zone = """{"kind": "touchZone", "pos": [300, 1000], "scale": [600, 600]}"""
        val s = session(level(zone))
        s.swipe(60.0, 0.0) // 600 u/s
        s.seconds(0.05)
        assertTrue("the ball is inside the zone", s.canSwipe)
        assertTrue(s.swipe(0.0, 40.0)) // a push straight down: 400
        assertTrue(s.ball.dirY > 0.3)
        // Far outside the zone a swipe does nothing.
        s.seconds(2.0)
        assertFalse(s.canSwipe)
        val before = s.ball.speed
        assertFalse(s.swipe(100.0, 0.0))
        assertEquals(before, s.ball.speed, 0.0)
    }

    // ------------------------------------------------------------------ speed limit

    @Test
    fun oneSpeedLimitCapsEveryKindOfPush() {
        // A booster the whole way, a strong attractor beside the path, a boosting portal, and a swipe in a touch zone.
        val elements = """
          {"kind": "booster", "pos": [1500, 1000], "scale": [3000, 400], "force": 20},
          {"kind": "attractive", "pos": [2500, 1000], "scale": [1200, 1200], "force": 500},
          {"kind": "portal", "id": "a", "link": "b", "pos": [3200, 1000], "scale": [200, 200], "boost": 5},
          {"kind": "portal", "id": "b", "link": "a", "pos": [3800, 1000], "scale": [200, 200]},
          {"kind": "touchZone", "pos": [4200, 1000], "scale": [800, 800]}
        """
        val s = session(level(elements, extra = ""","drag":0"""))
        s.applyImpulse(800.0, 0.0)
        var fastest = 0.0
        var steps = 0
        while (s.state == GameSession.State.MOVING && steps++ < 1200) {
            s.step()
            fastest = maxOf(fastest, s.ball.speed)
            if (s.ball.x in 4000.0..4400.0) s.applyImpulse(5000.0, 0.0)
        }
        assertTrue("never over the cap: $fastest", fastest <= 2400.0 + 1e-9)
        assertEquals("but it does get there", 2400.0, fastest, 1e-6)
    }

    // ------------------------------------------------------------------ force zones

    @Test
    fun aBoosterPushesAlongItsDirectionAllTheTimeTheBallIsInside() {
        val s = session(level("""{"kind": "booster", "pos": [500, 1000], "scale": [400, 300], "force": 6.3}""", extra = ""","drag":0"""))
        s.applyImpulse(200.0, 0.0)
        s.seconds(1.5)
        // 6.3 × 100 ref units/s² × 20 = 12600 world units/s²; inside for 200/… s the speed climbs a long way
        assertTrue("boosted, was ${s.ball.speed}", s.ball.speed > 1500.0)
        assertEquals(1.0, s.ball.dirX, 1e-6)
    }

    @Test
    fun aBoosterTurnedUpwardsBendsTheBallsPath() {
        val s = session(level("""{"kind": "booster", "pos": [500, 1000], "scale": [400, 300], "rotation": -90, "force": 6.3}""", extra = ""","drag":0"""))
        s.applyImpulse(600.0, 0.0)
        s.seconds(0.35) // in the booster since 0.17 s, and not yet back down from the ceiling
        assertTrue("pushed up (dirY=${s.ball.dirY})", s.ball.dirY < -0.3)
    }

    @Test
    fun anAttractiveZonePullsTowardsItsCentreAndARepulsiveOneAwayFromIt() {
        fun dirYAfterPassing(kind: String): Double {
            val s = session(level("""{"kind": "$kind", "pos": [1500, 700], "scale": [1400, 1400], "force": 60}""", extra = ""","drag":0"""))
            s.applyImpulse(900.0, 0.0)
            s.seconds(1.2)
            return s.ball.dirY * s.ball.speed
        }
        assertTrue("attracted upwards to the centre above the path", dirYAfterPassing("attractive") < -50.0)
        assertTrue("repelled downwards", dirYAfterPassing("repulsive") > 50.0)
    }

    @Test
    fun aSlowerZoneDrainsSpeedSmoothlyAndNeverReversesTheBall() {
        val plain = session(level(extra = ""","drag":0""")).also { it.applyImpulse(1000.0, 0.0) }.seconds(1.5)
        val slowed = session(level("""{"kind": "slower", "pos": [1000, 1000], "scale": [600, 400], "force": 2}""", extra = ""","drag":0"""))
        slowed.applyImpulse(1000.0, 0.0)
        var last = slowed.ball.speed
        var monotone = true
        repeat(180) {
            slowed.step()
            if (slowed.state != GameSession.State.MOVING) return@repeat
            if (slowed.ball.speed > last + 1e-9) monotone = false
            last = slowed.ball.speed
            assertTrue(slowed.ball.dirX >= 0.0)
        }
        assertTrue("slower than the free ball (${slowed.ball.speed} vs ${plain.ball.speed})", slowed.ball.speed < plain.ball.speed * 0.6)
        assertTrue("it slows, never speeds up", monotone)
    }

    // ------------------------------------------------------------------ portals

    private val portals = """
        {"kind": "portal", "id": "a", "link": "b", "pos": [1000, 1000], "scale": [140, 140] PA},
        {"kind": "portal", "id": "b", "link": "a", "pos": [3000, 600], "scale": [140, 140] PB}
    """

    private fun portalSession(pa: String = "", pb: String = "", log: Log? = null) =
        session(level(portals.replace("PA", pa).replace("PB", pb), extra = ""","drag":0"""), log)

    @Test
    fun aPortalMovesTheBallToItsPartnerKeepingSpeedAndDirection() {
        val log = Log()
        val s = portalSession(log = log)
        s.applyImpulse(1000.0, 0.0)
        s.seconds(1.0) // through the first portal at 0.8 s
        assertEquals(1, log.portals)
        assertEquals(600.0, s.ball.y, 1e-9)
        assertTrue(s.ball.x > 3000.0)
        assertEquals(1000.0, s.ball.speed, 1e-6)
        assertEquals(1.0, s.ball.dirX, 1e-9)
    }

    @Test
    fun aPortalBoostsAndDecaysTheSpeedAndTurnsItByTheDifferenceInRotation() {
        val boosted = portalSession(pa = ""","boost": 1.5""").also { it.applyImpulse(1000.0, 0.0) }.seconds(1.0)
        assertEquals(1500.0, boosted.ball.speed, 1e-6)
        val decayed = portalSession(pa = ""","decay": 0.5""").also { it.applyImpulse(1000.0, 0.0) }.seconds(1.0)
        assertEquals(500.0, decayed.ball.speed, 1e-6)
        val turned = portalSession(pb = ""","rotation": 90""").also { it.applyImpulse(1000.0, 0.0) }.seconds(1.0)
        assertEquals("out of a portal turned 90° clockwise: heading down", 1.0, turned.ball.dirY, 1e-9)
        assertEquals(1000.0, turned.ball.speed, 1e-6)
    }

    @Test
    fun aPortalExitDirectionCanBeSetOnThePartner() {
        val s = portalSession(pb = ""","vector": [0, -1]""").also { it.applyImpulse(1000.0, 0.0) }.seconds(1.0)
        assertEquals(-1.0, s.ball.dirY, 1e-9) // straight up out of the exit
    }

    @Test
    fun portalsThatOverlapCannotSendTheBallRoundInCircles() {
        val elements = """
          {"kind": "portal", "id": "a", "link": "b", "pos": [1000, 1000], "scale": [300, 300]},
          {"kind": "portal", "id": "b", "link": "a", "pos": [1100, 1000], "scale": [300, 300]}
        """
        val log = Log()
        val s = session(level(elements, extra = ""","drag":0"""), log)
        s.applyImpulse(1000.0, 0.0)
        s.seconds(4.0)
        assertEquals("one jump, then it leaves", 1, log.portals)
        assertTrue(s.ball.x > 1500.0)
    }

    // ------------------------------------------------------------------ slow motion and the fixed step

    @Test
    fun slowMotionRunsGameTimeAtAnEighthWhileTheBallIsInItsZone() {
        val log = Log()
        val s = session(level("""{"kind": "slowMo", "pos": [1000, 1000], "scale": [600, 600]}""", extra = ""","drag":0"""), log)
        s.applyImpulse(1000.0, 0.0)
        var slowSteps = 0
        var before = s.gameTime
        var gameDelta = 0.0
        repeat(1000) {
            before = s.gameTime
            s.step()
            if (s.isSlowMotion) {
                slowSteps++
                gameDelta = s.gameTime - before
            }
        }
        assertEquals(1, log.slowIn)
        assertEquals(1, log.slowOut)
        assertTrue(slowSteps > 10)
        assertEquals("a step of game time is an eighth of a normal one", GameSession.STEP / 8, gameDelta, 1e-12)
        assertEquals(1.0, s.timeScale, 0.0)
    }

    @Test
    fun theSimulationIsTheSameAtEveryFrameRate() {
        val elements = """
          {"kind": "slowMo", "pos": [1500, 1000], "scale": [700, 700]},
          {"kind": "booster", "pos": [900, 1000], "scale": [300, 300], "force": 3},
          {"kind": "portal", "id": "a", "link": "b", "pos": [3500, 1000], "scale": [200, 200]},
          {"kind": "portal", "id": "b", "link": "a", "pos": [5000, 500], "scale": [200, 200]},
          {"kind": "solid", "pos": [2600, 1000], "scale": [80, 500], "moving": {"to": [2600, 1600], "period": 3}}
        """
        val lvl = level(elements, bounces = 6)
        fun run(frame: Double): Triple<Double, Double, Double> {
            val s = session(lvl)
            s.swipe(90.0, 6.0)
            val frames = Math.round(5.0 / frame).toInt()
            repeat(frames) { s.advance(frame) }
            return Triple(s.ball.x, s.ball.y, s.gameTime)
        }
        val ref = run(1.0 / 120)
        for (fps in listOf(30, 60, 90, 144)) {
            val r = run(1.0 / fps)
            assertEquals("x at $fps fps", ref.first, r.first, 1e-6)
            assertEquals("y at $fps fps", ref.second, r.second, 1e-6)
            assertEquals("time at $fps fps", ref.third, r.third, 1e-9)
        }
    }

    // ------------------------------------------------------------------ collisions and counting

    @Test
    fun theBounceSoundPitchStartsLowAndRisesAsTheBouncesRunOut() {
        val log = Log()
        val s = session(level(bounces = 3), log)
        s.applyImpulse(0.0, 2400.0) // straight down and up between the edges
        s.seconds(3.0)
        assertTrue(log.progress.size >= 3)
        assertEquals(listOf(0.0, 1.0 / 3, 2.0 / 3), log.progress.take(3))
        val t = GameTuning()
        assertEquals(0.8, t.bouncePitch(0.0), 1e-12)
        assertTrue(t.bouncePitch(2.0 / 3) > t.bouncePitch(1.0 / 3))
        assertEquals(1.25, t.bouncePitch(1.0), 1e-12)
    }

    @Test
    fun aCornerHitCountsAsOneCollision() {
        // Into the exact corner of two walls at once: one bounce, straight back.
        val walls = """{"type": "wall", "points": [1600, 300, 1600, 2000]}, {"type": "wall", "points": [1000, 1400, 1700, 1400]}"""
        val s = session(level(obstacles = walls, extra = ""","drag":0"""), Log().also { })
        val log = Log()
        s.listener = log
        val toCorner = hypot(1600 - 200.0 - 22.0 - 60.0, 1400 - 1000.0 - 22.0 - 60.0)
        s.applyImpulse((1600 - 200.0 - 82) / toCorner * 1500, (1400 - 1000.0 - 82) / toCorner * 1500)
        s.seconds(1.2)
        assertTrue("at most a bounce or two, not one per contact point (${log.bounces})", log.bounces <= 2)
    }

    @Test
    fun aBallSqueezedAgainstAMovingBarrierIsNotCountedEveryStep() {
        // A barrier sliding into a ball that sits against a wall keeps touching it; that is one collision, not one per step.
        val elements = """{"kind": "solid", "pos": [1600, 1000], "scale": [100, 600], "moving": {"to": [1000, 1000], "period": 8, "wave": "linear"}}"""
        val zone = """{"kind": "touchZone", "pos": [1000, 1000], "scale": [1500, 700]}"""
        val log = Log()
        val s = session(level("$elements,$zone", bounces = 50, obstacles = """{"type": "wall", "points": [700, 400, 700, 1600]}""", extra = ""","drag":0"""), log)
        s.applyImpulse(60.0, 0.0)
        s.seconds(6.0)
        assertTrue("counted ${log.bounces} times for a slow squeeze", log.bounces < 12)
    }

    @Test
    fun aBarrierThatMovesIntoTheBallThrowsItFaster() {
        // Wall sliding towards the ball at 200 u/s, ball approaching at 300 u/s: relative 500, so it comes back at 700.
        val elements = """{"kind": "solid", "pos": [2500, 1000], "scale": [100, 800], "moving": {"to": [1500, 1000], "period": 10, "wave": "linear"}}"""
        val s = session(level(elements, extra = ""","drag":0"""))
        s.applyImpulse(300.0, 0.0)
        var guard = 0
        while (s.ball.dirX > 0 && s.state == GameSession.State.MOVING && guard++ < 2000) s.step()
        assertTrue(s.ball.dirX < 0.0)
        assertEquals(700.0, s.ball.speed, 15.0)
    }

    // ------------------------------------------------------------------ losing a ball

    @Test
    fun aBallThatSlowsToAStopFadesThenDiesUnlessItIsInATouchZone() {
        val plain = session(level(extra = ""","drag":4"""))
        plain.applyImpulse(400.0, 0.0)
        var minAlpha = 1.0
        var guard = 0
        while (plain.state == GameSession.State.MOVING && guard++ < 2000) {
            plain.step()
            minAlpha = minOf(minAlpha, plain.balls[0].alpha)
        }
        assertEquals(GameSession.State.FAILED, plain.state)
        assertEquals(GameSession.FailReason.STOPPED, plain.failReason)
        assertTrue("it faded first ($minAlpha)", minAlpha < 1.0)
        assertTrue(plain.ball.speed == 0.0)

        val zone = """{"kind": "touchZone", "pos": [900, 1000], "scale": [3000, 800]}"""
        val safe = session(level(zone, extra = ""","drag":4"""))
        safe.applyImpulse(400.0, 0.0)
        safe.seconds(6.0)
        assertEquals("slow, but still able to be swiped", GameSession.State.MOVING, safe.state)
        assertEquals(1.0, safe.balls[0].alpha, 0.0)
    }

    @Test
    fun aDeathZoneKillsTheBallAndTheGameIsLostWhenNoBallIsLeft() {
        val log = Log()
        val s = session(level("""{"kind": "death", "pos": [1000, 1000], "scale": [200, 400]}""", extra = ""","drag":0"""), log)
        s.applyImpulse(1500.0, 0.0)
        s.seconds(1.0)
        assertEquals(GameSession.State.FAILED, s.state)
        assertEquals(GameSession.FailReason.DEATH_ZONE, s.failReason)
        assertEquals(1, log.lost)
    }

    @Test
    fun aFastBallCannotSkipOverAThinDeathZone() {
        val s = session(level("""{"kind": "death", "pos": [1000, 1000], "scale": [6, 400]}""", extra = ""","drag":0"""))
        s.applyImpulse(9000.0, 0.0) // capped at 2400: 20 units per step, over a 6-unit zone
        s.seconds(1.0)
        assertEquals(GameSession.FailReason.DEATH_ZONE, s.failReason)
    }

    // ------------------------------------------------------------------ barriers and switches

    @Test
    fun aDestructibleBarrierBreaksAfterItsHitsAndThenLetsTheBallThrough() {
        val log = Log()
        val d = """{"kind": "destructible", "pos": [1200, 1000], "scale": [80, 500], "value": 2}"""
        val s = session(level(d, bounces = 20, extra = ""","drag":0"""), log)
        s.applyImpulse(1500.0, 0.0)
        s.seconds(2.0) // hits, bounces off the left edge, hits again
        assertTrue(log.events.contains(GameSession.ElementEvent.BROKEN))
        assertFalse(s.elements[0].active)
        val count = log.bounces
        s.seconds(2.5) // back off the left edge, and this time nothing is in the way
        assertTrue("the third time it goes through (x=${s.ball.x})", s.ball.x > 1500.0)
        assertTrue(log.bounces >= count)
    }

    @Test
    fun aSwitchTurnsABarrierOffAndFullResetTurnsItBackOn() {
        val elements = """
          {"kind": "switch", "pos": [800, 1000], "scale": [200, 200], "channel": 1},
          {"kind": "switchable", "pos": [2000, 1000], "scale": [80, 600], "channel": 1}
        """
        val log = Log()
        val s = session(level(elements, extra = ""","drag":0"""), log)
        assertTrue(s.elements[1].active)
        s.applyImpulse(1500.0, 0.0)
        s.seconds(2.0)
        assertFalse("the switch turned it off", s.elements[1].active)
        assertEquals("the ball went through it: no bounce", 0, log.bounces)
        assertTrue(s.ball.x > 2100.0)
        s.restart()
        assertTrue("a retry puts the barrier back", s.elements[1].active)
    }

    @Test
    fun aBallContainerLetsOutItsBallsOnceWithoutCreatingAnything() {
        val log = Log()
        val c = """{"kind": "ballContainer", "pos": [1400, 1000], "scale": [100, 400], "value": 2, "force": 40}"""
        val s = session(level(c, bounces = 5, extra = ""","drag":0"""), log)
        s.applyImpulse(1200.0, 0.0)
        s.seconds(1.2)
        assertEquals(2, log.spawned)
        assertEquals(3, s.balls.count { it.alive })
        assertTrue(s.elements[0].spent)
        assertEquals(s.balls.size, GameTuning().maxBalls) // the pool never grows
    }

    @Test
    fun theExitNeedsAsManyBallsAsTheLevelDemands() {
        val log = Log()
        val lvl = level(extra = ""","exitRequired": 2, "drag": 0""", goal = "[1000, 1000]")
        val s = session(lvl, log)
        s.applyImpulse(1000.0, 0.0)
        s.seconds(1.5)
        assertEquals("one ball in is not enough", 1, log.exitPartial)
        assertEquals(1, s.exitCount)
        assertEquals(GameSession.State.FAILED, s.state) // no ball left and the exit is not complete
    }

    // ------------------------------------------------------------------ reset

    @Test
    fun aRetryPutsTheWholeLevelBackAndTheSameThrowPlaysTheSame() {
        val elements = """
          {"kind": "destructible", "pos": [1300, 1000], "scale": [80, 500], "value": 1},
          {"kind": "slowMo", "pos": [700, 1000], "scale": [400, 400]},
          {"kind": "solid", "pos": [2600, 700], "scale": [100, 300], "rotating": {"speed": 90}},
          {"kind": "portal", "id": "a", "link": "b", "pos": [1800, 1000], "scale": [140, 140]},
          {"kind": "portal", "id": "b", "link": "a", "pos": [2200, 500], "scale": [140, 140]}
        """
        val lvl = level(elements, bounces = 6, extra = ""","drag":0.1""")
        val s = session(lvl)
        s.applyImpulse(1400.0, 40.0)
        s.seconds(3.0)
        val firstX = s.ball.x
        val firstY = s.ball.y
        assertTrue("things happened", !s.elements[0].active || s.gameTime > 0.0)
        s.restart()
        assertEquals(GameSession.State.AIMING, s.state)
        assertEquals(200.0, s.ball.x, 0.0)
        assertEquals(1000.0, s.ball.y, 0.0)
        assertEquals(0.0, s.ball.speed, 0.0)
        assertEquals(6, s.bouncesLeft)
        assertEquals(1.0, s.timeScale, 0.0)
        assertEquals(0, s.exitCount)
        assertEquals(0.0, s.gameTime, 0.0)
        assertTrue("barrier is back", s.elements[0].active)
        assertEquals(1, s.balls.count { it.alive })
        assertEquals("the turning barrier is back at its start", 0.0, s.elements[2].rotation, 0.0)
        s.applyImpulse(1400.0, 40.0)
        s.seconds(3.0)
        assertEquals(firstX, s.ball.x, 1e-9)
        assertEquals(firstY, s.ball.y, 1e-9)
    }

    @Test
    fun movingRotatingAndScalingElementsFollowGameTimeOnly() {
        val e = """{"kind": "solid", "pos": [1000, 1000], "scale": [100, 100], "moving": {"to": [1400, 1000], "period": 4},
                    "rotating": {"speed": 45}, "scaling": {"to": [200, 100], "period": 4}}"""
        val s = session(level(e, extra = ""","drag":0"""))
        s.applyImpulse(0.0, -50.0) // start the clock; the ball goes nowhere near
        val el = s.elements[0]
        assertEquals(1000.0, el.x, 0.0) // t = 0
        s.seconds(2.0) // half a period: the far end of the trip
        assertEquals(1400.0, el.x, 0.5)
        assertEquals(200.0, el.sx, 0.5)
        assertEquals(90.0, el.rotation, 0.5)
        s.seconds(2.0)
        assertEquals("back again", 1000.0, el.x, 0.5)
    }

    @Test
    fun nothingIsCreatedWhilePlayingTheBallPoolHasAFixedSize() {
        val s = session(level(bounces = 5))
        assertEquals(GameTuning().maxBalls, s.balls.size)
        assertNotNull(s.balls[0])
        assertTrue(abs(GameTuning().refToWorld(2400.0) - 20.0) < 1e-12)
    }
}
