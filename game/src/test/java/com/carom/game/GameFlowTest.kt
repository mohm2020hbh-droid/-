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

/** Drives the real view with touch events: aim, launch, win, save, navigate. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GameFlowTest {

    private class MapStore : KeyValueStore {
        val map = HashMap<String, String>()
        override fun getString(key: String) = map[key]
        override fun putString(key: String, value: String) {
            map[key] = value
        }
    }

    private class DirectorySource(private val dir: File) : LevelSource {
        override fun list() = dir.listFiles { f -> f.name.endsWith(".json") }!!.map { it.name.removeSuffix(".json") }
        override fun read(id: String) = File(dir, "$id.json").readText()
    }

    private val levelsDir = File(System.getProperty("levels.dir") ?: "src/main/assets/levels")

    private fun newView(store: KeyValueStore): GameView {
        val app = GameApp(LevelRepository(DirectorySource(levelsDir)), store)
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

    /** Swipes straight up by [dragPx] from the ball: touch, drag, let go. The swipe is the impulse. */
    private fun dragUpAndRelease(view: GameView, play: PlayScreen, dragPx: Float) {
        val bx = play.board.x(play.session.ball.x)
        val by = play.board.y(play.session.ball.y)
        val t = SystemClock.uptimeMillis()
        touch(view, MotionEvent.ACTION_DOWN, bx, by, t)
        touch(view, MotionEvent.ACTION_MOVE, bx, by - dragPx / 2, t + 40)
        touch(view, MotionEvent.ACTION_MOVE, bx, by - dragPx, t + 80)
        touch(view, MotionEvent.ACTION_UP, bx, by - dragPx, t + 90)
    }

    @Test
    fun draggingTheBallFiresItThatWayAndWinningSavesProgress() {
        val store = MapStore()
        val view = newView(store)
        assertTrue(view.currentScreen is HomeScreen)

        view.play(0)
        val play = view.currentScreen as PlayScreen
        // Level 1 is a straight shot: the goal is right above the ball.
        dragUpAndRelease(view, play, 400f)
        assertEquals(GameSession.State.MOVING, play.session.state)
        assertTrue(play.session.ball.dirY < -0.999) // up, the way it was dragged

        runFor(view, 2f) // the ball reaches the ring in about a second
        assertEquals(GameSession.State.WON, play.session.state)
        assertTrue(store.map["progress.completed"]!!.split(',').contains("001"))
        assertEquals(1, GameApp(LevelRepository(DirectorySource(levelsDir)), store).progress.currentIndex)
        // A level that does not end a world moves on by itself 2.5 seconds after the win: no card, nothing to press.
        assertTrue("not yet", view.currentScreen === play)
        runFor(view, 2f)
        assertTrue("on to level 2", view.currentScreen !== play && (view.currentScreen as PlayScreen).index == 1)
    }

    @Test
    fun aBounceLowersTheCountInTheBall() {
        val view = newView(MapStore())
        view.play(1)
        val play = view.currentScreen as PlayScreen
        assertEquals(2, play.session.bouncesLeft)
        // Straight up from level 2's start hits the wall above the ball (a 400 px swipe is a bit over half speed).
        dragUpAndRelease(view, play, 400f)
        runFor(view, 0.6f) // first wall at ~0.4 s, the next one at ~1.0 s
        assertEquals(1, play.session.bouncesLeft)
    }

    @Test
    fun theBallStaysWhereItIsWhileTheFingerMovesAndASwipeIsAnImpulse() {
        val view = newView(MapStore())
        view.play(0)
        val play = view.currentScreen as PlayScreen
        val startX = play.session.ball.x
        val bx = play.board.x(startX)
        val by = play.board.y(play.session.ball.y)
        val t = SystemClock.uptimeMillis()
        touch(view, MotionEvent.ACTION_DOWN, bx, by, t)
        touch(view, MotionEvent.ACTION_MOVE, bx + 150f, by, t + 30)
        assertEquals("the ball does not follow the finger", startX, play.session.ball.x, 1e-9)
        // Let go after a drag of 150 px: 150 / 3 = 50 dp × 0.5 = 25 ref units × 20 = 500 units per second.
        touch(view, MotionEvent.ACTION_UP, bx + 150f, by, t + 60)
        assertEquals(GameSession.State.MOVING, play.session.state)
        assertEquals(500.0, play.session.ball.speed, 1.0)
        assertEquals(1.0, play.session.ball.dirX, 1e-9)
    }

    @Test
    fun aTapOrAShortDragDoesNotThrow() {
        val view = newView(MapStore())
        view.play(0)
        val play = view.currentScreen as PlayScreen
        val bx = play.board.x(play.session.ball.x)
        val by = play.board.y(play.session.ball.y)
        touch(view, MotionEvent.ACTION_DOWN, bx, by)
        touch(view, MotionEvent.ACTION_UP, bx + 6f, by + 4f) // about 2 dp: under the 4 dp swipe tolerance
        assertEquals(GameSession.State.AIMING, play.session.state)
    }

    @Test
    fun runningOutOfBouncesBreaksTheBallAndTheNextOneIsReadyAtOnce() {
        val store = MapStore()
        val view = newView(store)
        view.play(1)
        val play = view.currentScreen as PlayScreen
        // Straight up and down between the wall above and the bottom edge: two bounces, then it breaks.
        dragUpAndRelease(view, play, 400f)
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
    fun theHintForLevel23ShowsOnlyAfterMoreThanTenLossesAndHidesOnTheNextThrow() {
        val view = newView(MapStore())
        repeat(10) { view.app.progress.recordFail(22) }
        view.play(22)
        val play = view.currentScreen as PlayScreen
        assertEquals("023", play.session.level.id)
        assertFalse(play.isGuideShown) // ten losses are not "more than ten"

        play.session.launch(1.0, 0.0, 1.0) // along the floor: out of bounces on the third edge, the eleventh loss
        runFor(view, 3f)
        assertEquals(GameSession.State.AIMING, play.session.state)
        assertTrue(play.isGuideShown)

        play.session.launch(0.0, -1.0, 1.0) // using it: the hint gets out of the way
        assertFalse(play.isGuideShown)
    }

    @Test
    fun aTapOnTheBallDoesNotFire() {
        val view = newView(MapStore())
        view.play(0)
        val play = view.currentScreen as PlayScreen
        val bx = play.board.x(play.session.ball.x)
        val by = play.board.y(play.session.ball.y)
        touch(view, MotionEvent.ACTION_DOWN, bx, by)
        touch(view, MotionEvent.ACTION_UP, bx + 3f, by)
        assertEquals(GameSession.State.AIMING, play.session.state)
    }

    @Test
    fun lockedLevelsCannotBeOpenedAndBackReturnsHome() {
        val view = newView(MapStore())
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
    fun everyLevelIsPackagedAsAnAsset() {
        val assets = AssetLevelSource(RuntimeEnvironment.getApplication().assets).list()
        assertEquals(levelsDir.list()!!.count { it.endsWith(".json") }, assets.size)
    }
}
