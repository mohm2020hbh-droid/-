package com.carom.game

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.carom.core.game.GameSession
import com.carom.core.level.LevelRepository
import com.carom.core.level.LevelSource
import com.carom.core.progress.KeyValueStore
import com.carom.game.screens.HomeScreen
import com.carom.game.screens.LevelSelectScreen
import com.carom.game.screens.PlayScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

/** Drives the real view with touch events: hold, move, let go, win, save, navigate. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GameFlowTest {

    private class DirectorySource(private val dir: File) : LevelSource {
        override fun list() = dir.listFiles { f -> f.name.endsWith(".json") }!!.map { it.name.removeSuffix(".json") }
        override fun read(id: String) = File(dir, "$id.json").readText()
    }

    private val levelsDir = File(System.getProperty("levels.dir") ?: "src/main/assets/levels")

    /** The real campaign, or the small test levels. */
    private fun newView(store: KeyValueStore, real: Boolean = false): GameView {
        val app = GameApp(LevelRepository(if (real) DirectorySource(levelsDir) else MemoryLevels()), store)
        val view = GameView(RuntimeEnvironment.getApplication(), app)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 1080, 2400)
        return view
    }

    private fun touch(view: GameView, action: Int, x: Float, y: Float, time: Long = SystemClock.uptimeMillis()) {
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

    private fun screenPos(play: PlayScreen) = play.board.x(play.session.ball.x) to play.board.y(play.session.ball.y)

    /**
     * Holds the ball and moves the finger by ([dx], [dy]) pixels at a steady speed of [pxPerSecond] (a touch every 8 ms),
     * then lifts it [restMs] after the last movement. Returns the time of the last event.
     */
    private fun flick(view: GameView, play: PlayScreen, dx: Float, dy: Float, pxPerSecond: Float, restMs: Long = 0, lift: Boolean = true): Long {
        val (bx, by) = screenPos(play)
        val distance = hypot(dx, dy)
        val steps = maxOf(2, (distance / pxPerSecond / 0.008f).toInt())
        val t0 = SystemClock.uptimeMillis()
        touch(view, MotionEvent.ACTION_DOWN, bx, by, t0)
        var t = t0
        for (i in 1..steps) {
            t = t0 + (8L * i)
            touch(view, MotionEvent.ACTION_MOVE, bx + dx * i / steps, by + dy * i / steps, t)
        }
        if (restMs > 0) {
            var rested = 0L
            while (rested < restMs) {
                rested += 8
                touch(view, MotionEvent.ACTION_MOVE, bx + dx, by + dy, t + rested)
            }
            t += restMs
        }
        if (lift) touch(view, MotionEvent.ACTION_UP, bx + dx, by + dy, t)
        return t
    }

    /** The speed the finger had, in world units per second. */
    private fun worldSpeed(play: PlayScreen, pxPerSecond: Float) = pxPerSecond / play.board.scale.toDouble()

    @Test
    fun aFlickSetsTheBallOffWithTheVelocityItHadAndWinningSavesProgress() {
        val store = MapStore()
        val view = newView(store)
        assertTrue(view.currentScreen is HomeScreen)

        view.play(0)
        val play = view.currentScreen as PlayScreen
        flick(view, play, 0f, -300f, 1500f) // straight up, fast
        assertEquals(GameSession.State.MOVING, play.session.state)
        assertTrue(play.session.ball.dirY < -0.999) // up, the way it was moving
        assertEquals(worldSpeed(play, 1500f), play.session.ball.speed, worldSpeed(play, 1500f) * 0.1)

        runFor(view, 2f) // the ball reaches the ring in about a second
        assertEquals(GameSession.State.WON, play.session.state)
        assertTrue(store.map["progress.completed"]!!.split(',').contains("001"))
        // A level that does not end a world moves on by itself 2.5 seconds after the win: no card, nothing to press.
        assertTrue("not yet", view.currentScreen === play)
        runFor(view, 2f)
        assertTrue("on to level 2", view.currentScreen !== play && (view.currentScreen as PlayScreen).index == 1)
    }

    @Test
    fun aSlowMediumAndFastMovementGiveAWeakMediumAndStrongThrow() {
        val speeds = listOf(150f, 600f, 1800f).map { finger ->
            val view = newView(MapStore())
            view.play(0)
            val play = view.currentScreen as PlayScreen
            flick(view, play, 0f, -(finger * 0.25f), finger) // a quarter of a second of movement
            assertEquals(GameSession.State.MOVING, play.session.state)
            val expected = worldSpeed(play, finger)
            assertEquals("finger $finger px/s", expected, play.session.ball.speed, expected * 0.1)
            play.session.ball.speed
        }
        assertTrue("weak < medium < strong: $speeds", speeds[0] < speeds[1] && speeds[1] < speeds[2])
    }

    @Test
    fun aVerySlowMovementStillSetsTheBallOff() {
        val view = newView(MapStore())
        view.play(0)
        val play = view.currentScreen as PlayScreen
        flick(view, play, 0f, -10f, 20f) // 20 px a second: a crawl
        assertEquals("no dead zone: even this moves the ball", GameSession.State.MOVING, play.session.state)
        assertTrue(play.session.ball.speed > 0.0 && play.session.ball.speed < 60.0)
        assertTrue(play.session.ball.dirY < -0.999)
    }

    @Test
    fun aBallThatWasStillWhenTheFingerLiftedStaysWhereItWasPut() {
        val view = newView(MapStore())
        view.play(0)
        val play = view.currentScreen as PlayScreen
        val startY = play.session.ball.y
        flick(view, play, 0f, -120f, 900f, restMs = 200) // moved, then held still for longer than the window
        assertEquals(GameSession.State.AIMING, play.session.state)
        assertEquals("it moved with the finger", startY - 120.0 / play.board.scale, play.session.ball.y, 1.0)
        assertEquals(3, play.session.bouncesLeft)
    }

    @Test
    fun aTapOrATouchThatNeverMovesThrowsNothing() {
        val view = newView(MapStore())
        view.play(0)
        val play = view.currentScreen as PlayScreen
        val (bx, by) = screenPos(play)
        touch(view, MotionEvent.ACTION_DOWN, bx, by)
        touch(view, MotionEvent.ACTION_UP, bx, by)
        assertEquals(GameSession.State.AIMING, play.session.state)
    }

    @Test
    fun theHeldBallGoesWhereTheFingerGoesAndStaysInsideItsZone() {
        val view = newView(MapStore())
        view.play(0)
        val play = view.currentScreen as PlayScreen
        val zone = play.session.level.zone
        val (bx, by) = screenPos(play)
        val t = SystemClock.uptimeMillis()
        touch(view, MotionEvent.ACTION_DOWN, bx, by, t)
        touch(view, MotionEvent.ACTION_MOVE, bx + 60f, by + 20f, t + 8)
        assertEquals("the ball is under the finger", play.board.worldX(bx + 60f), play.session.ball.x, 1e-3)
        assertEquals(play.board.worldY(by + 20f), play.session.ball.y, 1e-3)
        for (i in 1..40) touch(view, MotionEvent.ACTION_MOVE, bx + 60f, by - 30f * i, t + 8 + 8L * i) // far past the top of the zone
        val ball = play.session.ball
        assertTrue("it never leaves the zone", zone.holds(ball.x, ball.y, play.session.level.ballRadius))
        assertEquals("...and rests on its edge", zone.let { (it as com.carom.core.level.ControlZone.Box).y } + play.session.level.ballRadius, ball.y, 1e-3)
        touch(view, MotionEvent.ACTION_UP, bx + 60f, by - 1200f, t + 8 + 8L * 41 + 300) // it had stopped at the edge: nothing thrown
        assertEquals(GameSession.State.AIMING, play.session.state)
    }

    @Test
    fun aTouchOutsideTheZoneDoesNotPickUpTheBallButOneJustOutsideItDoes() {
        val view = newView(MapStore())
        view.play(0)
        val play = view.currentScreen as PlayScreen
        val zone = play.session.level.zone as com.carom.core.level.ControlZone.Box
        val startX = play.session.ball.x
        val startY = play.session.ball.y
        val sx = play.board.x(startX)
        var t = SystemClock.uptimeMillis()

        val far = play.board.y(zone.y - 300.0)
        touch(view, MotionEvent.ACTION_DOWN, sx, far, t)
        touch(view, MotionEvent.ACTION_MOVE, sx, far + 200f, t + 8)
        touch(view, MotionEvent.ACTION_MOVE, sx + 100f, far + 400f, t + 16)
        touch(view, MotionEvent.ACTION_UP, sx + 100f, far + 400f, t + 24)
        assertEquals("a drag that starts far outside does not move the ball", startX, play.session.ball.x, 1e-9)
        assertEquals(startY, play.session.ball.y, 1e-9)
        assertEquals(GameSession.State.AIMING, play.session.state)

        t += 500
        val near = play.board.y(zone.y - 20.0) // a thumb a little outside: the zone is not drawn, so it is forgiving
        touch(view, MotionEvent.ACTION_DOWN, sx, near, t)
        touch(view, MotionEvent.ACTION_MOVE, sx, near + 60f, t + 8)
        assertTrue("a touch just outside still picks the ball up", abs(play.session.ball.y - startY) > 1.0)
        touch(view, MotionEvent.ACTION_CANCEL, sx, near + 60f, t + 16)
    }

    @Test
    fun afterTheReleaseNothingFollowsTheFinger() {
        val view = newView(MapStore())
        view.play(0)
        val play = view.currentScreen as PlayScreen
        flick(view, play, 200f, -100f, 900f)
        assertEquals(GameSession.State.MOVING, play.session.state)
        val dirX = play.session.ball.dirX
        val (bx, by) = screenPos(play)
        val t = SystemClock.uptimeMillis() + 100
        touch(view, MotionEvent.ACTION_DOWN, bx - 300f, by - 300f, t) // a touch while it flies: nothing follows a finger
        touch(view, MotionEvent.ACTION_MOVE, bx - 350f, by - 300f, t + 30)
        touch(view, MotionEvent.ACTION_UP, bx - 350f, by - 300f, t + 60)
        assertEquals(dirX, play.session.ball.dirX, 1e-6)
    }

    @Test
    fun aBounceLowersTheCountInTheBall() {
        val view = newView(MapStore())
        view.play(1)
        val play = view.currentScreen as PlayScreen
        assertEquals(2, play.session.bouncesLeft)
        flick(view, play, 0f, -150f, 500f) // straight up into the wall above the ball, at a moderate speed
        runFor(view, 1.0f)
        assertEquals(1, play.session.bouncesLeft)
    }

    @Test
    fun runningOutOfBouncesBreaksTheBallAndTheNextOneIsReadyAtOnce() {
        val view = newView(MapStore())
        view.play(1)
        val play = view.currentScreen as PlayScreen
        flick(view, play, 0f, -300f, 1800f) // straight up and down between the wall above and the bottom edge
        var guard = 0
        while (play.session.state == GameSession.State.MOVING && guard++ < 2000) play.update(1 / 120f)
        assertEquals(GameSession.State.FAILED, play.session.state)
        assertEquals(GameSession.FailReason.OUT_OF_BOUNCES, play.session.failReason)

        // No result screen and nothing to press: a second later a new ball waits at the start.
        runFor(view, 0.5f)
        assertEquals("not yet", GameSession.State.FAILED, play.session.state)
        runFor(view, 0.7f)
        assertEquals(GameSession.State.AIMING, play.session.state)
        assertEquals(2, play.session.bouncesLeft)
        assertEquals(play.session.level.ball.x, play.session.ball.x, 1e-9)
        assertEquals(play.session.level.ball.y, play.session.ball.y, 1e-9)
        assertEquals(1, view.app.progress.failCount(1))
    }

    @Test
    fun theHintShowsOnlyAfterMoreThanTenLossesAndHidesOnTheNextThrow() {
        val view = newView(MapStore())
        repeat(10) { view.app.progress.recordFail(2) }
        view.play(2)
        val play = view.currentScreen as PlayScreen
        assertFalse(play.isGuideShown) // ten losses are not "more than ten"

        play.session.launch(1.0, 0.0, 1.0) // along the floor: out of bounces on the second edge, the eleventh loss
        runFor(view, 3f)
        assertEquals(GameSession.State.AIMING, play.session.state)
        assertTrue(play.isGuideShown)

        play.session.launch(0.0, -1.0, 1.0) // using it: the hint gets out of the way
        assertFalse(play.isGuideShown)
    }

    @Test
    fun onlyLevelOneIsOpenAtTheStartAndBackReturnsHome() {
        val view = newView(MapStore(), real = true)
        view.showLevels(0)
        val list = view.currentScreen as LevelSelectScreen
        // Level 2 is still locked: tapping it stays on the list.
        val (x2, y2) = list.cellCenter(1)
        touch(view, MotionEvent.ACTION_DOWN, x2, y2)
        touch(view, MotionEvent.ACTION_UP, x2, y2)
        assertTrue(view.currentScreen is LevelSelectScreen)
        // Level 1 opens.
        val (x1, y1) = list.cellCenter(0)
        touch(view, MotionEvent.ACTION_DOWN, x1, y1)
        touch(view, MotionEvent.ACTION_UP, x1, y1)
        assertTrue(view.currentScreen is PlayScreen)
        assertTrue(view.onBack())
        assertTrue(view.currentScreen is LevelSelectScreen)
        assertTrue(view.onBack())
        assertTrue(view.currentScreen is HomeScreen)
        assertEquals(false, view.onBack())
    }

    @Test
    fun theCampaignIsEightWorldsOfTenLevels() {
        val app = GameApp(LevelRepository(DirectorySource(levelsDir)), MapStore())
        assertEquals(80, app.levels.size)
    }

    @Test
    fun everyLevelIsPackagedAsAnAsset() {
        val assets = AssetLevelSource(RuntimeEnvironment.getApplication().assets).list()
        assertEquals(levelsDir.list()!!.count { it.endsWith(".json") }, assets.size)
    }
}
