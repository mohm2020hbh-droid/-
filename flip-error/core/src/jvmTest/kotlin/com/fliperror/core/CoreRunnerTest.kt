package com.fliperror.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun flat(len: Double = 200.0) = Level(
    id = 0, name = "TEST", subtitle = "", bpm = 140.0,
    solids = listOf(Solid(-10.0, len, 0.0)),
    hazards = emptyList(), stars = emptyList(), finishX = len,
)

private fun withPit() = Level(
    id = 0, name = "PIT", subtitle = "", bpm = 140.0,
    solids = listOf(Solid(-10.0, 30.0, 0.0), Solid(60.0, 200.0, 0.0)),
    hazards = emptyList(), stars = emptyList(), finishX = 200.0,
)

private fun withStep() = Level(
    id = 0, name = "STEP", subtitle = "", bpm = 140.0,
    solids = listOf(Solid(-10.0, 30.0, 0.0), Solid(30.0, 200.0, 4.0)),
    hazards = emptyList(), stars = emptyList(), finishX = 200.0,
)

/** Advance [seconds] of simulated time in 60fps frames. */
private fun Game.run(seconds: Double, frame: Double = 1.0 / 60.0) {
    val frames = kotlin.math.round(seconds / frame).toInt()
    repeat(frames) { update(frame) }
}

class CoreRunnerTest {

    @Test fun `auto run moves forward at the tuned speed`() {
        val g = Game(flat())
        g.run(1.0)
        assertTrue(abs(g.x - Tuning.RUN_SPEED) < 0.05, "expected ~9.5 after 1s, got ${g.x}")
    }

    @Test fun `player never leaves the ground on flat terrain`() {
        val g = Game(flat())
        repeat(600) {
            g.update(1.0 / 60.0)
            assertEquals(0.0, g.y, 1e-9, "player drifted off the floor")
            assertTrue(g.grounded)
        }
    }

    @Test fun `falling into a pit is a pit death`() {
        val g = Game(withPit())
        g.run(6.0)
        assertEquals(GameState.DEAD, g.state)
        assertEquals(DeathCause.PIT, g.deathCause)
        assertTrue(g.deathX in 29.0..35.0, "death should be at the pit edge, was ${g.deathX}")
    }

    @Test fun `running into a wall is a wall death, not a silent stop`() {
        val g = Game(withStep())
        g.run(6.0)
        assertEquals(GameState.DEAD, g.state)
        assertEquals(DeathCause.WALL, g.deathCause)
    }

    @Test fun `restart is instant and resets the run but keeps history`() {
        val g = Game(withPit())
        g.run(6.0)
        val reached = g.progress
        g.restart()
        assertEquals(GameState.RUNNING, g.state)
        assertEquals(0.0, g.x, 1e-9)
        assertEquals(0.0, g.y, 1e-9)
        assertEquals(2, g.attempts)
        assertTrue(g.bestProgress >= reached - 1e-9, "best progress must be remembered")
    }

    @Test fun `simulation is frame rate independent`() {
        val a = Game(flat()); a.run(3.0, 1.0 / 60.0)
        val b = Game(flat()); b.run(3.0, 1.0 / 144.0)
        assertTrue(abs(a.x - b.x) < 0.05, "60fps gave ${a.x}, 144fps gave ${b.x}")
    }

    @Test fun `reaching the end completes the level`() {
        val g = Game(flat(60.0))
        g.run(10.0)
        assertEquals(GameState.COMPLETE, g.state)
    }
}
