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
 * The generator's soft hum means one thing: "I asked to start this level over, completely, by hand" (three quick presses of
 * restart, or three quick taps). Never a loss, a collision, a first start or a ball released from a container.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeneratorSoundTest {

    private class Recorder(private val real: GameView) : GameHost by real {
        val sounds = ArrayList<Sound>()
        override fun sound(kind: Sound, strength: Double) {
            sounds += kind
        }

        val generators get() = sounds.count { it == Sound.GENERATOR }
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

    @Test
    fun theStartOfALevelIsSilent() {
        val (host, screen) = play()
        repeat(60) { screen.update(1 / 60f) }
        assertEquals(0, host.generators)
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
        assertTrue("the ball was put back and the charge was shown", screen.isRespawning)
        assertEquals("...without the generator", 0, host.generators)
        assertTrue("the collisions themselves sounded as usual", host.sounds.contains(Sound.IMPACT))
    }

    @Test
    fun onePressAndTwoPressesOfRestartAreSilentAndTheThirdSoundsOnce() {
        val (host, screen) = play()
        pressRestart(screen, 1_000)
        assertEquals(0, host.generators)
        pressRestart(screen, 1_250)
        assertEquals("not at the first press and not at the second", 0, host.generators)
        screen.session.launch(0.0, -1.0, 0.2)
        repeat(20) { screen.update(1 / 60f) }
        pressRestart(screen, 1_500)
        assertEquals("after the third, and only then", 1, host.generators)
        assertEquals(GameSession.State.AIMING, screen.session.state)
        assertEquals(0.0, screen.session.ball.speed, 0.0)
        assertTrue(screen.isRespawning)
    }

    @Test
    fun slowPressesOfRestartNeverSound() {
        val (host, screen) = play()
        for (k in 0 until 9) pressRestart(screen, 1_000L + 2_000L * k)
        assertEquals(0, host.generators)
        assertEquals("every press still started the attempt over", 9, screen.session.resetCount)
    }

    @Test
    fun threeQuickTapsAnywhereSoundOnTheThirdAndNotBefore() {
        val (host, screen) = play()
        screen.session.launch(0.0, -1.0, 0.3)
        repeat(15) { screen.update(1 / 60f) }
        tap(screen, 900f, 500f, 10_000)
        tap(screen, 900f, 500f, 10_220)
        assertEquals(0, host.generators)
        tap(screen, 900f, 500f, 10_440)
        assertEquals(1, host.generators)
        assertEquals(GameSession.State.AIMING, screen.session.state)
        // and a fourth tap begins a new set: nothing
        tap(screen, 900f, 500f, 10_660)
        assertEquals(1, host.generators)
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
        assertEquals(0, host.generators)
    }
}
