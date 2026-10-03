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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The three-press restart. With a very slow ball: press 1 -> Tap 1, press 2 -> Tap 2, press 3 -> Tap 3 and the ball goes back to its start.
 * In every other case these sounds are not played: not at a start, a loss, a win, a collision, an automatic retry, a ball moving at a normal
 * speed, or anything but the restart button's presses.
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

    /** A ball crawling on after a feeble throw (3.8 of the 120 reference units). */
    private fun crawl(screen: PlayScreen) {
        screen.session.launch(0.0, -1.0, 0.001)
        repeat(5) { screen.update(1 / 60f) }
        assertTrue("the ball is very slow", screen.session.isBallVerySlow())
    }

    @Test
    fun aVerySlowBallGivesTap1ThenTap2ThenTap3AndIsPutBackOnTheThird() {
        val (host, screen) = play()
        crawl(screen)
        val before = screen.session.resetCount
        pressRestart(screen, 1_000)
        assertEquals(listOf(AudioCue.RESTART_TAP_1), host.taps)
        assertEquals("the first press does not touch the ball", GameSession.State.MOVING, screen.session.state)
        pressRestart(screen, 1_250)
        assertEquals(listOf(AudioCue.RESTART_TAP_1, AudioCue.RESTART_TAP_2), host.taps)
        assertEquals("nor does the second", GameSession.State.MOVING, screen.session.state)
        assertEquals(before, screen.session.resetCount)
        pressRestart(screen, 1_500)
        assertEquals(listOf(AudioCue.RESTART_TAP_1, AudioCue.RESTART_TAP_2, AudioCue.RESTART_TAP_3), host.taps)
        assertEquals("the third puts the ball back at its start", GameSession.State.AIMING, screen.session.state)
        assertEquals(before + 1, screen.session.resetCount)
        assertEquals(0.0, screen.session.ball.speed, 0.0)
        assertTrue("charging again", screen.isRespawning)
        assertTrue("a light touch of feedback on the third", host.haptics.contains(Haptic.CLICK))
    }

    @Test
    fun aFourthPressBeginsANewSetAndAtRestItIsSilent() {
        val (host, screen) = play()
        crawl(screen)
        pressRestart(screen, 1_000); pressRestart(screen, 1_250); pressRestart(screen, 1_500)
        assertEquals(3, host.taps.size)
        pressRestart(screen, 1_750) // the ball is at its start now: nothing slow about it
        assertEquals("no fourth sound", 3, host.taps.size)
    }

    @Test
    fun theRestartButtonHasNoPressSoundOfItsOwn() {
        val (host, screen) = play()
        pressRestart(screen, 1_000)
        assertTrue("silent at a normal speed", host.sounds.isEmpty())
    }

    @Test
    fun withTheBallAtANormalSpeedEveryPressRestartsAtOnceAndNothingSounds() {
        val (host, screen) = play()
        screen.session.launch(0.0, -1.0, 0.5)
        repeat(5) { screen.update(1 / 60f) }
        assertFalse(screen.session.isBallVerySlow())
        pressRestart(screen, 1_000)
        assertEquals("the first press restarts at once, as it always has", GameSession.State.AIMING, screen.session.state)
        pressRestart(screen, 1_250)
        pressRestart(screen, 1_500)
        assertTrue(host.taps.isEmpty())
        assertEquals(3, screen.session.resetCount)
    }

    @Test
    fun aBallAtRestBeforeAnyThrowIsSilent() {
        val (host, screen) = play()
        pressRestart(screen, 1_000); pressRestart(screen, 1_250); pressRestart(screen, 1_500)
        assertTrue(host.taps.isEmpty())
    }

    @Test
    fun theSpeedCountsAtTheFirstPressNotAtTheLater() {
        val (host, screen) = play()
        screen.session.launch(0.0, -1.0, 0.5) // normal when the player starts
        repeat(5) { screen.update(1 / 60f) }
        pressRestart(screen, 1_000) // restarts at once
        crawl(screen) // slow now, but the set began at a normal speed
        pressRestart(screen, 1_250)
        pressRestart(screen, 1_500)
        assertTrue(host.taps.isEmpty())
    }

    @Test
    fun aPauseBetweenPressesStartsTheCountAgain() {
        val (host, screen) = play()
        crawl(screen)
        pressRestart(screen, 1_000)
        pressRestart(screen, 3_000) // too long since the last: this is the first of a new set
        assertEquals(listOf(AudioCue.RESTART_TAP_1, AudioCue.RESTART_TAP_1), host.taps)
        assertEquals("the ball was never touched", GameSession.State.MOVING, screen.session.state)
    }

    @Test
    fun threeQuickTapsAnywhereRestartInSilence() {
        val (host, screen) = play()
        crawl(screen)
        tap(screen, 900f, 500f, 10_000)
        tap(screen, 900f, 500f, 10_220)
        tap(screen, 900f, 500f, 10_440)
        assertTrue("only the restart button's presses have these sounds", host.taps.isEmpty())
        assertEquals("...but the attempt does start over", GameSession.State.AIMING, screen.session.state)
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
