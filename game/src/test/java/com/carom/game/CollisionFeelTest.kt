package com.carom.game

import android.graphics.Rect
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
import kotlin.math.hypot

/** What a collision does to the sound, the vibration and the picture, and that nothing else does. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CollisionFeelTest {

    /** The real shell, except that every sound and vibration is written down (with the frame it happened on). */
    private class Recorder(private val real: GameView) : GameHost by real {
        var frame = 0
        val sounds = ArrayList<Pair<Int, AudioCue>>()
        val haptics = ArrayList<Pair<Int, Haptic>>()
        override fun sound(cue: AudioCue, strength: Double) {
            sounds += frame to cue
        }

        override fun haptic(kind: Haptic, strength: Double) {
            haptics += frame to kind
        }
    }

    private class Rig(val host: Recorder, val play: PlayScreen) {
        val impacts get() = host.sounds.filter { it.second == AudioCue.BOUNCE }.map { it.first }

        /** One frame at 60 Hz. */
        fun tick() {
            host.frame++
            play.update(1 / 60f)
        }

        fun run(frames: Int) = repeat(frames) { tick() }
    }

    private fun rig(index: Int): Rig {
        val app = GameApp(LevelRepository(MemoryLevels()), MapStore())
        val view = GameView(RuntimeEnvironment.getApplication(), app)
        val host = Recorder(view)
        val play = PlayScreen(host, index, app.levels.load(index))
        play.layout(1080, 2400, Rect())
        return Rig(host, play)
    }

    @Test
    fun theThrowAndTheFlightMakeNoSoundNoVibrationAndNoShake() {
        val rig = rig(1) // level 2: straight up hits the wall above the ball after about 0.4 s
        val session = rig.play.session
        session.launch(0.0, -1.0, 0.35)
        assertTrue("throwing makes no sound and no vibration", rig.host.sounds.isEmpty() && rig.host.haptics.isEmpty())
        var frames = 0
        while (rig.impacts.isEmpty() && frames++ < 240) {
            rig.tick()
            if (rig.impacts.isEmpty()) {
                assertTrue("nothing at all while the ball flies", rig.host.sounds.isEmpty() && rig.host.haptics.isEmpty())
                assertEquals("...and the picture stands still", 0f, rig.play.shakeOffsetX, 0f)
                assertEquals(0f, rig.play.shakeOffsetY, 0f)
            }
        }
        assertEquals("the first collision is the first thing that sounds", 1, rig.impacts.size)
    }

    @Test
    fun aCollisionSoundsVibratesShakesAndSquashesOnTheSameFrameThenSettlesExactly() {
        val rig = rig(1)
        rig.play.session.launch(0.0, -1.0, 0.35)
        var guard = 0
        while (rig.impacts.isEmpty() && guard++ < 240) rig.tick()
        val hitFrame = rig.impacts.single()
        assertEquals("one pulse of vibration, on the same frame", listOf(hitFrame to Haptic.BOUNCE), rig.host.haptics)
        assertTrue("the shake starts on the very frame", hypot(rig.play.shakeOffsetX, rig.play.shakeOffsetY) > 0f)
        assertTrue("...and so does the squash", rig.play.squashOf(0) > 0f)

        val cap = rig.host.kit.u(10f)
        var peak = 0f
        var settledAt = -1
        for (i in 1..40) {
            rig.tick()
            val offset = hypot(rig.play.shakeOffsetX, rig.play.shakeOffsetY)
            assertTrue("the shake never goes past its cap", offset <= cap + 1e-3f)
            peak = maxOf(peak, offset)
            if (settledAt < 0 && offset == 0f) settledAt = i
            if (settledAt >= 0) assertEquals("once over, the picture stays exactly in place", 0f, offset, 0f)
        }
        assertTrue("it is gone within a blink (well under a quarter of a second)", settledAt in 1..14)
        assertEquals("the squash is over too", 0f, rig.play.squashOf(0), 0f)
        assertEquals("no second sound for the same collision", 1, rig.impacts.size)
    }

    @Test
    fun everyCollisionInAQuickRunHasItsOwnSoundAndPulseButShakesNeverStack() {
        val rig = rig(1)
        val hit = GameSession.Impact(450.0, 300.0, 0.0, 1.0, strength = 0.9)
        val one = rig(1)
        one.play.onBounce(hit, 1)
        one.tick()
        val single = hypot(one.play.shakeOffsetX, one.play.shakeOffsetY)

        repeat(5) { rig.play.onBounce(hit, 1) } // five hits on one frame
        assertEquals(5, rig.impacts.size)
        assertEquals(5, rig.host.haptics.count { it.second == Haptic.BOUNCE })
        rig.tick()
        val stacked = hypot(rig.play.shakeOffsetX, rig.play.shakeOffsetY)
        assertTrue("five hits shake no harder than one ($stacked vs $single)", stacked <= rig.host.kit.u(10f) && stacked <= single * 1.7f + 1f)
        // A run of hits over a few frames: the picture is always within the cap, and when it ends it is back exactly.
        for (i in 0 until 12) {
            rig.play.onBounce(hit, 1)
            rig.tick()
            assertTrue(hypot(rig.play.shakeOffsetX, rig.play.shakeOffsetY) <= rig.host.kit.u(10f) + 1e-3f)
        }
        rig.run(30)
        assertEquals(0f, rig.play.shakeOffsetX, 0f)
        assertEquals(0f, rig.play.shakeOffsetY, 0f)
        assertEquals(17, rig.impacts.size)
    }

    @Test
    fun theHitThatBreaksTheBallSoundsToo() {
        val rig = rig(1) // two bounces: straight up and down hits three times
        rig.play.session.launch(0.0, -1.0, 1.0)
        var guard = 0
        while (rig.play.session.state == GameSession.State.MOVING && guard++ < 2000) rig.tick()
        assertEquals(GameSession.State.FAILED, rig.play.session.state)
        assertEquals("two counted bounces and the fatal one each sound", 3, rig.impacts.size)
        assertEquals(listOf(Haptic.BOUNCE, Haptic.BOUNCE, Haptic.BREAK), rig.host.haptics.map { it.second })
        assertTrue("the break follows the last knock", rig.host.sounds.last().second == AudioCue.FAIL_BREAK)
        assertFalse("nothing sounded before the first hit", rig.host.sounds.first().first == 0)
    }

    @Test
    fun theFeedbackDoesNotTouchThePhysics() {
        val plain = GameSession(rig(1).play.session.level)
        val felt = rig(1)
        plain.launch(0.3, -1.0, 0.8)
        felt.play.session.launch(0.3, -1.0, 0.8)
        var frames = 0
        var bounces = 0
        while (plain.state == GameSession.State.MOVING && felt.play.session.state == GameSession.State.MOVING && frames++ < 600) {
            plain.advance(1 / 60.0)
            felt.tick()
            assertEquals(plain.ball.x, felt.play.session.ball.x, 1e-12)
            assertEquals(plain.ball.y, felt.play.session.ball.y, 1e-12)
            assertEquals(plain.bouncesLeft, felt.play.session.bouncesLeft)
            bounces = felt.impacts.size
        }
        assertTrue("the shot really did hit things on the way", bounces >= 2)
        assertEquals(plain.state, felt.play.session.state)
    }
}
