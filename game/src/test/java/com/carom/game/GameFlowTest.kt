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

    /** Holds the ball and strokes straight up by [dragPx], letting go mid-stroke, so it flies straight up. */
    private fun dragUpAndRelease(view: GameView, play: PlayScreen, dragPx: Float) {
        val bx = play.board.x(play.session.ball.x)
        val by = play.board.y(play.session.ball.y)
        touch(view, MotionEvent.ACTION_DOWN, bx, by)
        touch(view, MotionEvent.ACTION_MOVE, bx, by - dragPx / 2)
        touch(view, MotionEvent.ACTION_MOVE, bx, by - dragPx)
        touch(view, MotionEvent.ACTION_UP, bx, by - dragPx)
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

        runFor(view, 4f)
        assertEquals(GameSession.State.WON, play.session.state)
        assertTrue(store.map["progress.completed"]!!.split(',').contains("001"))
        assertEquals(1, GameApp(LevelRepository(DirectorySource(levelsDir)), store).progress.currentIndex)
    }

    @Test
    fun aBounceLowersTheCountInTheBall() {
        val view = newView(MapStore())
        view.play(1)
        val play = view.currentScreen as PlayScreen
        assertEquals(2, play.session.bouncesLeft)
        // Straight up from level 2's start hits the wall above the ball.
        dragUpAndRelease(view, play, 400f)
        runFor(view, 0.35f) // first wall at ~0.21 s, the next one at ~0.57 s
        assertEquals(1, play.session.bouncesLeft)
    }

    @Test
    fun theBallFollowsTheFingerAndStaysPutWhenLetGoWithoutAThrow() {
        val view = newView(MapStore())
        view.play(0)
        val play = view.currentScreen as PlayScreen
        val startX = play.session.ball.x
        val bx = play.board.x(startX)
        val by = play.board.y(play.session.ball.y)
        val t = SystemClock.uptimeMillis()
        touch(view, MotionEvent.ACTION_DOWN, bx, by, t)
        touch(view, MotionEvent.ACTION_MOVE, bx + 50f, by, t + 30)
        touch(view, MotionEvent.ACTION_MOVE, bx + 100f, by, t + 60)
        assertEquals(startX + 100f / play.board.scale, play.session.ball.x, 0.5) // carried to the right
        // Held still for a moment, then lifted: no throw, the ball stays where it was put.
        touch(view, MotionEvent.ACTION_MOVE, bx + 100f, by, t + 400)
        touch(view, MotionEvent.ACTION_UP, bx + 100f, by, t + 420)
        assertEquals(GameSession.State.AIMING, play.session.state)
        assertEquals(startX + 100f / play.board.scale, play.session.ball.x, 0.5)
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

        // No result screen and nothing to press: a moment later a new ball waits at the start.
        runFor(view, 1f)
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

        play.session.launch(0.0, 1.0, 0.005) // a throw so weak it stops: the eleventh loss
        runFor(view, 1f)
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
