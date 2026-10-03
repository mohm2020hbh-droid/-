package com.carom.game

import android.graphics.Rect
import android.view.MotionEvent
import com.carom.core.game.GameSession
import com.carom.core.level.LevelRepository
import com.carom.game.screens.GameHost
import com.carom.game.screens.PlayScreen
import com.carom.game.screens.Sound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * `low_ball_pulse.wav` has one condition and no other: the ball was very slow when the player began, and the player then pressed restart three
 * times in a row (or tapped the screen three times), so the attempt was put back at its start. Never a loss, an automatic retry, a start, a win,
 * a collision, one or two presses, or three presses while the ball was moving at a normal speed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LowBallPulseTest {

    private class Recorder(private val real: GameView) : GameHost by real {
        val sounds = ArrayList<Sound>()
        override fun sound(kind: Sound, strength: Double) {
            sounds += kind
        }

        val pulses get() = sounds.count { it == Sound.LOW_BALL_PULSE }
    }

    private fun play(index: Int = 1): Pair<Recorder, PlayScreen> {
        val app = GameApp(LevelRepository(MemoryLevels()), MapStore())
        val host = Recorder(GameView(RuntimeEnvironment.getApplication(), app))
        val screen = PlayScreen(host, index, app.levels.load(index))
        screen.layout(1080, 2400, Rect())
        return host to screen
    }

    private fun touch(screen: PlayScreen, action: Int, x: Float, y: Float, time: Long) {
        val e = MotionEvent.obtain(time, time, action, x, y, 0)
        screen.onTouch(e)
        e.recycle()
    }

    private fun tap(screen: PlayScreen, x: Float, y: Float, time: Long) {
        touch(screen, MotionEvent.ACTION_DOWN, x, y, time)
        touch(screen, MotionEvent.ACTION_UP, x, y, time + 50)
    }

    private fun pressRestart(screen: PlayScreen, time: Long) {
        val b = screen.restartButton.bounds
        tap(screen, b.centerX(), b.centerY(), time)
    }

    /** A ball crawling on after a feeble throw. */
    private fun crawl(screen: PlayScreen) {
        screen.session.launch(0.0, -1.0, 0.001) // (launch takes a power: the speed is its square root, so this is 3.8 of the 120 reference units)
        repeat(5) { screen.update(1 / 60f) }
        assertTrue("the ball is very slow", screen.session.isBallVerySlow())
    }

    @Test
    fun theStartOfALevelIsSilent() {
        val (host, screen) = play()
        repeat(60) { screen.update(1 / 60f) }
        assertEquals(0, host.pulses)
    }

    @Test
    fun aLostTryAndTheAutomaticRetryAreSilent() {
        val (host, screen) = play() // level 2: two bounces, straight up and down loses
        screen.session.launch(0.0, -1.0, 1.0)
        var guard = 0
        while (screen.session.state != GameSession.State.FAILED && guard++ < 3000) screen.update(1 / 60f)
        assertEquals(GameSession.State.FAILED, screen.session.state)
        guard = 0
        while (screen.session.state == GameSession.State.FAILED && guard++ < 600) screen.update(1 / 60f)
        assertTrue("the next try began by itself", screen.session.state == GameSession.State.AIMING)
        assertEquals("...without the pulse", 0, host.pulses)
        assertTrue("the collisions themselves sounded as usual", host.sounds.contains(Sound.IMPACT))
    }

    @Test
    fun threePressesWhileTheBallMovesAtANormalSpeedAreSilent() {
        val (host, screen) = play()
        screen.session.launch(0.0, -1.0, 0.5)
        repeat(5) { screen.update(1 / 60f) }
        assertTrue(!screen.session.isBallVerySlow())
        pressRestart(screen, 1_000)
        pressRestart(screen, 1_250)
        pressRestart(screen, 1_500)
        assertEquals(0, host.pulses)
        assertEquals("...though the attempt did start over each time", 3, screen.session.resetCount)
    }

    @Test
    fun threePressesOnABallAtRestBeforeAnyThrowAreSilent() {
        val (host, screen) = play()
        pressRestart(screen, 1_000)
        pressRestart(screen, 1_250)
        pressRestart(screen, 1_500)
        assertEquals(0, host.pulses)
    }

    @Test
    fun aSlowBallAndThreePressesPlayThePulseOnceOnTheThird() {
        val (host, screen) = play()
        crawl(screen)
        pressRestart(screen, 1_000)
        assertEquals("not at the first press", 0, host.pulses)
        pressRestart(screen, 1_250)
        assertEquals("not at the second", 0, host.pulses)
        pressRestart(screen, 1_500)
        assertEquals("at the third, once", 1, host.pulses)
        assertEquals(GameSession.State.AIMING, screen.session.state)
        assertEquals(0.0, screen.session.ball.speed, 0.0)
        pressRestart(screen, 1_750)
        assertEquals("a fourth press starts a new set and plays nothing", 1, host.pulses)
    }

    @Test
    fun aSlowBallAndOnlyOneOrTwoPressesAreSilent() {
        val (host, screen) = play()
        crawl(screen)
        pressRestart(screen, 1_000)
        assertEquals(0, host.pulses)
        crawl(screen)
        pressRestart(screen, 1_250)
        assertEquals(0, host.pulses)
        // ...and a long pause lets the next press begin a new set
        pressRestart(screen, 6_000)
        pressRestart(screen, 6_250)
        assertEquals(0, host.pulses)
    }

    @Test
    fun aBallThatWasNormalWhenAskingAndSlowByTheThirdPressStaysSilent() {
        val (host, screen) = play()
        screen.session.launch(0.0, -1.0, 0.5)
        repeat(5) { screen.update(1 / 60f) }
        pressRestart(screen, 1_000) // the ball is put back; what counts is how it was moving when the player began
        crawl(screen)
        pressRestart(screen, 1_250)
        pressRestart(screen, 1_500)
        assertEquals(0, host.pulses)
    }

    @Test
    fun aSlowBallAndThreeQuickTapsPlayThePulseOnTheThirdTap() {
        val (host, screen) = play()
        crawl(screen)
        tap(screen, 900f, 500f, 10_000)
        tap(screen, 900f, 500f, 10_220)
        assertEquals(0, host.pulses)
        tap(screen, 900f, 500f, 10_440)
        assertEquals(1, host.pulses)
        assertEquals(GameSession.State.AIMING, screen.session.state)
    }

    @Test
    fun threeQuickTapsOnAFastBallAreSilent() {
        val (host, screen) = play()
        screen.session.launch(0.0, -1.0, 0.4)
        repeat(5) { screen.update(1 / 60f) }
        tap(screen, 900f, 500f, 10_000)
        tap(screen, 900f, 500f, 10_220)
        tap(screen, 900f, 500f, 10_440)
        assertEquals(0, host.pulses)
        assertEquals("the attempt did start over", GameSession.State.AIMING, screen.session.state)
    }

    @Test
    fun ballsReleasedFromAContainerAreSilent() {
        val app = GameApp(
            LevelRepository(
                MemoryLevels(
                    mapOf(
                        "001" to """{"name": "Box", "bounces": 3, "ball": [450, 1540], "goal": [450, 460],
                            "elements": [{"kind": "container", "pos": [450, 1000], "scale": [220, 220], "shape": "circle", "value": 2}]}""",
                    ),
                ),
            ),
            MapStore(),
        )
        val host = Recorder(GameView(RuntimeEnvironment.getApplication(), app))
        val screen = PlayScreen(host, 0, app.levels.load(0))
        screen.layout(1080, 2400, Rect())
        screen.session.launch(0.0, -1.0, 0.8)
        var guard = 0
        var most = 0
        while (screen.session.state == GameSession.State.MOVING && guard++ < 600) {
            screen.update(1 / 60f)
            most = maxOf(most, screen.session.balls.count { it.alive })
        }
        assertTrue("the container really released balls ($most at most)", most >= 2)
        assertEquals(0, host.pulses)
    }
}
