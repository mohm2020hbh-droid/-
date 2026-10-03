package com.carom.game

import android.graphics.Rect
import android.view.MotionEvent
import com.carom.core.audio.AudioCue
import com.carom.core.game.GameSession
import com.carom.core.level.LevelRepository
import com.carom.game.screens.GameHost
import com.carom.game.screens.Haptic
import com.carom.game.screens.PlayScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The three-press restart. Three presses of the restart button in a row make the sounds Tap 1, Tap 2, Tap 3, whatever the ball is doing, and every
 * press puts the attempt back at its start. Nothing else makes them: not a start, a loss, a win, a collision, an automatic retry, or a container.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RestartTapsTest {

    private class Recorder(private val real: GameView) : GameHost by real {
        val sounds = ArrayList<AudioCue>()
        val haptics = ArrayList<Haptic>()
        override fun sound(cue: AudioCue, strength: Double) { sounds += cue }
        override fun haptic(kind: Haptic, strength: Double) { haptics += kind }

        val taps get() = sounds.filter { it in TAPS }

        companion object {
            val TAPS = setOf(AudioCue.RESTART_TAP_1, AudioCue.RESTART_TAP_2, AudioCue.RESTART_TAP_3)
        }
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

    private val all = listOf(AudioCue.RESTART_TAP_1, AudioCue.RESTART_TAP_2, AudioCue.RESTART_TAP_3)

    @Test
    fun threePressesGiveTap1Tap2Tap3InOrderWhateverTheBallIsDoing() {
        for (throwPower in listOf(0.0, 0.001, 0.2, 1.0)) { // at rest, drifting, moving, flying fast
            val (host, screen) = play()
            if (throwPower > 0.0) {
                screen.session.launch(0.0, -1.0, throwPower)
                repeat(5) { screen.update(1 / 60f) }
            }
            pressRestart(screen, 1_000)
            assertEquals("power $throwPower", listOf(AudioCue.RESTART_TAP_1), host.taps)
            pressRestart(screen, 1_250)
            assertEquals(listOf(AudioCue.RESTART_TAP_1, AudioCue.RESTART_TAP_2), host.taps)
            pressRestart(screen, 1_500)
            assertEquals("power $throwPower", all, host.taps)
            assertEquals(GameSession.State.AIMING, screen.session.state)
            assertEquals(0.0, screen.session.ball.speed, 0.0)
            assertTrue("a light touch of feedback as the third completes", host.haptics.contains(Haptic.CLICK))
        }
    }

    @Test
    fun everyPressStillPutsTheAttemptBackAtItsStartAtOnce() {
        val (host, screen) = play()
        screen.session.launch(0.0, -1.0, 0.5)
        repeat(5) { screen.update(1 / 60f) }
        assertEquals(GameSession.State.MOVING, screen.session.state)
        pressRestart(screen, 1_000)
        assertEquals("the first press restarts at once, as it always has", GameSession.State.AIMING, screen.session.state)
        assertEquals(1, screen.session.resetCount)
        pressRestart(screen, 1_250)
        pressRestart(screen, 1_500)
        assertEquals(3, screen.session.resetCount)
        assertEquals(all, host.taps)
    }

    @Test
    fun aFourthPressBeginsANewSetWithTap1() {
        val (host, screen) = play()
        pressRestart(screen, 1_000); pressRestart(screen, 1_250); pressRestart(screen, 1_500)
        pressRestart(screen, 1_750)
        assertEquals(all + AudioCue.RESTART_TAP_1, host.taps)
    }

    @Test
    fun aPauseBetweenPressesStartsTheCountAgain() {
        val (host, screen) = play()
        pressRestart(screen, 1_000)
        pressRestart(screen, 3_000) // too long since the last: the first of a new set
        pressRestart(screen, 3_250)
        assertEquals(listOf(AudioCue.RESTART_TAP_1, AudioCue.RESTART_TAP_1, AudioCue.RESTART_TAP_2), host.taps)
    }

    @Test
    fun theRestartButtonHasNoPressSoundOfItsOwn() {
        val (host, screen) = play()
        pressRestart(screen, 1_000)
        assertEquals("only the first of the three sounds, no button tick on top of it", listOf(AudioCue.RESTART_TAP_1), host.sounds)
    }

    @Test
    fun threeQuickTapsAnywhereOnTheScreenRestartAndSoundTheLastOne() {
        val (host, screen) = play()
        screen.session.launch(0.0, -1.0, 0.5)
        repeat(5) { screen.update(1 / 60f) }
        tap(screen, 900f, 500f, 10_000)
        tap(screen, 900f, 500f, 10_220)
        assertTrue("a lone tap is not a request to restart: no sound", host.taps.isEmpty())
        tap(screen, 900f, 500f, 10_440)
        assertEquals(listOf(AudioCue.RESTART_TAP_3), host.taps)
        assertEquals(GameSession.State.AIMING, screen.session.state)
    }

    @Test
    fun theStartOfALevelIsSilent() {
        val (host, screen) = play()
        repeat(60) { screen.update(1 / 60f) }
        assertTrue(host.taps.isEmpty())
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
        assertTrue(host.taps.isEmpty())
        assertTrue("the collisions themselves sounded as usual", host.sounds.contains(AudioCue.BOUNCE))
    }

    @Test
    fun aWinIsSilentToo() {
        val (host, screen) = play(0) // level 1: straight up scores
        screen.session.launch(0.0, -1.0, 0.6)
        var guard = 0
        while (screen.session.state == GameSession.State.MOVING && guard++ < 3000) screen.update(1 / 60f)
        assertEquals(GameSession.State.WON, screen.session.state)
        repeat(300) { screen.update(1 / 60f) }
        assertTrue(host.taps.isEmpty())
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
        assertTrue(host.taps.isEmpty())
    }
}
