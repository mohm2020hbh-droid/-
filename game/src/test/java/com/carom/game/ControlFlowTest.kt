package com.carom.game

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.carom.core.game.GameSession
import com.carom.core.level.LevelRepository
import com.carom.game.screens.PlayScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.hypot

/**
 * Control after the throw, with a real touch stream: the ball can be taken again while any part of it is inside the
 * control zone, and not once it is entirely outside; three quick taps anywhere start the attempt over.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ControlFlowTest {

    /** A wall high above the ball's start throws a straight shot back down through the zone. */
    private val shelf = mapOf(
        "001" to """{"name": "Shelf", "bounces": 3, "ball": [660, 1540], "goal": [150, 300],
            "obstacles": [{"type": "wall", "points": [300, 600, 900, 600]}]}""",
    )

    private fun newPlay(level: Int = 0, levels: Map<String, String> = MemoryLevels.DEFAULT): Pair<GameView, PlayScreen> {
        val app = GameApp(LevelRepository(MemoryLevels(levels)), MapStore())
        val view = GameView(RuntimeEnvironment.getApplication(), app)
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1080, 2400)
        view.play(level)
        return view to (view.currentScreen as PlayScreen)
    }

    private fun touch(view: GameView, action: Int, x: Float, y: Float, time: Long) {
        val e = MotionEvent.obtain(time, time, action, x, y, 0)
        view.dispatchTouchEvent(e)
        e.recycle()
    }

    private fun runFor(view: GameView, seconds: Float) {
        var t = 0f
        while (t < seconds) {
            view.currentScreen.update(1 / 60f)
            t += 1 / 60f
        }
    }

    private fun ballAt(play: PlayScreen) = play.board.x(play.session.ball.x) to play.board.y(play.session.ball.y)

    /** A finger takes the ball at its place and moves by (dx, dy) px at pxPerSecond (a touch every 8 ms), then lifts at the end. Returns the last event time. */
    private fun drag(view: GameView, play: PlayScreen, dx: Float, dy: Float, pxPerSecond: Float, t0: Long = SystemClock.uptimeMillis()): Long {
        val (bx, by) = ballAt(play)
        val steps = maxOf(2, (hypot(dx, dy) / pxPerSecond / 0.008f).toInt())
        touch(view, MotionEvent.ACTION_DOWN, bx, by, t0)
        var t = t0
        for (i in 1..steps) {
            t = t0 + 8L * i
            touch(view, MotionEvent.ACTION_MOVE, bx + dx * i / steps, by + dy * i / steps, t)
        }
        touch(view, MotionEvent.ACTION_UP, bx + dx, by + dy, t)
        return t
    }

    @Test
    fun aSlowBallStillInsideTheZoneCanBeTakenBackMovedAndThrownAgain() {
        val (view, play) = newPlay()
        val s = play.session
        assertEquals(GameSession.ControlPhase.READY, s.controlPhase)
        drag(view, play, 0f, -40f, 200f) // a gentle throw straight up
        assertEquals(GameSession.State.MOVING, s.state)
        assertEquals(GameSession.ControlPhase.LAUNCHED, s.controlPhase)
        runFor(view, 0.5f)
        assertTrue("still inside the zone", s.canRegrab)

        // Take hold of it where it is, without a jump.
        val (bx, by) = ballAt(play)
        val yBefore = s.ball.y
        val t = SystemClock.uptimeMillis() + 1000
        touch(view, MotionEvent.ACTION_DOWN, bx, by, t)
        assertEquals(GameSession.ControlPhase.REGRABBED, s.controlPhase)
        assertEquals("the game waits while the ball is held", yBefore, s.ball.y, 1e-9)
        runFor(view, 0.5f)
        assertEquals(yBefore, s.ball.y, 1e-9)

        // Move it sideways, smoothly: it follows the finger, and leaves with the speed of that movement.
        val startX = s.ball.x
        for (i in 1..30) touch(view, MotionEvent.ACTION_MOVE, bx - 6f * i, by, t + 8L * i)
        assertEquals("it follows the finger", startX - 180.0 / play.board.scale, s.ball.x, 1.0)
        touch(view, MotionEvent.ACTION_UP, bx - 180f, by, t + 8L * 30)
        assertEquals(GameSession.State.MOVING, s.state)
        assertEquals(GameSession.ControlPhase.RELEASED_AGAIN, s.controlPhase)
        assertTrue("it leaves to the left", s.ball.dirX < -0.99)
        val expected = 750.0 / play.board.scale // 6 px per 8 ms
        assertEquals(expected, s.ball.speed, expected * 0.1)
        assertEquals("nothing was refunded", 3, s.bouncesLeft)
    }

    @Test
    fun aBallLetGoAtRestAfterBeingTakenBackWaitsForTheNextThrow() {
        val (view, play) = newPlay()
        val s = play.session
        drag(view, play, 0f, -40f, 200f)
        runFor(view, 0.3f)
        val (bx, by) = ballAt(play)
        val t = SystemClock.uptimeMillis() + 1000
        touch(view, MotionEvent.ACTION_DOWN, bx, by, t)
        for (i in 1..10) touch(view, MotionEvent.ACTION_MOVE, bx + 5f * i, by, t + 8L * i)
        for (i in 11..60) touch(view, MotionEvent.ACTION_MOVE, bx + 50f, by, t + 8L * i) // holds still
        touch(view, MotionEvent.ACTION_UP, bx + 50f, by, t + 8L * 61)
        assertEquals(GameSession.State.AIMING, s.state)
        assertEquals(GameSession.ControlPhase.READY, s.controlPhase)
        // ...and is thrown as at the start.
        drag(view, play, 0f, -100f, 600f, t0 = t + 2000)
        assertEquals(GameSession.State.MOVING, s.state)
    }

    @Test
    fun aBallEntirelyOutsideTheZoneCannotBeTakenEvenWhenItComesBack() {
        val (view, play) = newPlay(levels = shelf)
        val s = play.session
        drag(view, play, 0f, -300f, 1500f) // fast, straight up, out of the zone
        runFor(view, 0.4f)
        assertEquals(GameSession.State.MOVING, s.state)
        assertEquals(GameSession.ControlPhase.OUTSIDE_CONTROL_ZONE, s.controlPhase)
        val t = SystemClock.uptimeMillis() + 5000
        // Later the ball is back over the zone (bounced off the shelf), and still not the player's.
        var guard = 0
        while (s.ball.dirY <= 0.0 && guard++ < 600) runFor(view, 1 / 60f)
        while (s.ball.y < 1200.0 && s.state == GameSession.State.MOVING && guard++ < 1200) runFor(view, 1 / 60f)
        assertTrue("the ball is back inside the zone", play.session.level.zone.overlaps(s.ball.x, s.ball.y, s.level.ballRadius))
        assertEquals(GameSession.ControlPhase.OUTSIDE_CONTROL_ZONE, s.controlPhase)
        val dirX = s.ball.dirX
        val dirY = s.ball.dirY
        val speed = s.ball.speed
        val (bx, by) = ballAt(play)
        touch(view, MotionEvent.ACTION_DOWN, bx, by, t)
        touch(view, MotionEvent.ACTION_MOVE, bx + 100f, by, t + 16)
        touch(view, MotionEvent.ACTION_UP, bx + 100f, by, t + 32)
        assertEquals(GameSession.State.MOVING, s.state)
        assertEquals("the ball is unaffected", dirX, s.ball.dirX, 1e-9)
        assertEquals(dirY, s.ball.dirY, 1e-9)
        assertEquals(speed, s.ball.speed, 1e-9)
    }

    @Test
    fun threeQuickTapsAnywhereStartTheAttemptOver() {
        val (view, play) = newPlay()
        val s = play.session
        drag(view, play, 0f, -200f, 1200f)
        runFor(view, 0.4f)
        assertEquals(GameSession.State.MOVING, s.state)
        val t = SystemClock.uptimeMillis() + 10_000
        for (k in 0 until 3) {
            touch(view, MotionEvent.ACTION_DOWN, 900f, 500f, t + 220L * k)
            touch(view, MotionEvent.ACTION_UP, 900f, 500f, t + 220L * k + 60)
        }
        assertEquals(GameSession.State.AIMING, s.state)
        assertEquals(GameSession.ControlPhase.READY, s.controlPhase)
        assertEquals(450.0, s.ball.x, 0.0)
        assertEquals(1540.0, s.ball.y, 0.0)
        assertEquals(0.0, s.ball.speed, 0.0)
        assertEquals(3, s.bouncesLeft)
        assertEquals(1, s.resetCount)
    }

    @Test
    fun twoTapsAndAThrowAreNotATripleTap() {
        val (view, play) = newPlay()
        val s = play.session
        val (bx, by) = ballAt(play)
        val t = SystemClock.uptimeMillis() + 20_000
        for (k in 0 until 2) {
            touch(view, MotionEvent.ACTION_DOWN, bx, by, t + 200L * k)
            touch(view, MotionEvent.ACTION_UP, bx, by, t + 200L * k + 50)
        }
        drag(view, play, 0f, -100f, 800f, t0 = t + 420)
        assertEquals(GameSession.State.MOVING, s.state)
        assertEquals(0, s.resetCount)
    }

    @Test
    fun aSlowTouchSequenceIsNotATripleTap() {
        val (view, play) = newPlay()
        val s = play.session
        drag(view, play, 0f, -200f, 1200f)
        runFor(view, 0.2f)
        val t = SystemClock.uptimeMillis() + 30_000
        for (k in 0 until 3) {
            touch(view, MotionEvent.ACTION_DOWN, 900f, 500f, t + 900L * k)
            touch(view, MotionEvent.ACTION_UP, 900f, 500f, t + 900L * k + 60)
        }
        assertEquals(0, s.resetCount)
    }
}
