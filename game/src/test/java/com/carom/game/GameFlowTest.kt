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
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 2400, 1080)
        return view
    }

    private fun touch(view: GameView, action: Int, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        val e = MotionEvent.obtain(t, t, action, x, y, 0)
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

    @Test
    fun pullingBackFromTheBallFiresItAndWinningSavesProgress() {
        val store = MapStore()
        val view = newView(store)
        assertTrue(view.currentScreen is HomeScreen)

        view.play(0)
        val play = view.currentScreen as PlayScreen
        val bx = play.board.x(play.session.ball.x)
        val by = play.board.y(play.session.ball.y)
        // Level 1 is a straight shot: pull left, release, the ball flies right into the goal.
        touch(view, MotionEvent.ACTION_DOWN, bx, by)
        touch(view, MotionEvent.ACTION_MOVE, bx - 200f, by)
        touch(view, MotionEvent.ACTION_MOVE, bx - 400f, by)
        touch(view, MotionEvent.ACTION_UP, bx - 400f, by)
        assertEquals(GameSession.State.MOVING, play.session.state)

        runFor(view, 4f)
        assertEquals(GameSession.State.WON, play.session.state)
        assertTrue(store.map["progress.completed"]!!.split(',').contains("001"))
        assertEquals(1, GameApp(LevelRepository(DirectorySource(levelsDir)), store).progress.currentIndex)
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
        assertTrue(view.currentScreen is LevelSelectScreen)
        // Level 2 (second cell of the first row) is still locked: tapping it stays on the list.
        touch(view, MotionEvent.ACTION_DOWN, 915f, 297f)
        touch(view, MotionEvent.ACTION_UP, 915f, 297f)
        assertTrue(view.currentScreen is LevelSelectScreen)
        // Level 1 opens.
        touch(view, MotionEvent.ACTION_DOWN, 635f, 297f)
        touch(view, MotionEvent.ACTION_UP, 635f, 297f)
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
