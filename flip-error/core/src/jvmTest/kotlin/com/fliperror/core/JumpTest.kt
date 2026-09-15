package com.fliperror.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun flat(len: Double = 400.0, hazards: List<Hazard> = emptyList()) = Level(
    id = 0, name = "T", subtitle = "", bpm = 140.0,
    solids = listOf(Solid(-10.0, len, 0.0)),
    hazards = hazards, stars = emptyList(), finishX = len,
)

private fun ledge(edge: Double) = Level(
    id = 0, name = "L", subtitle = "", bpm = 140.0,
    solids = listOf(Solid(-10.0, edge, 0.0)),
    hazards = emptyList(), stars = emptyList(), finishX = 400.0,
)

private fun Game.frames(n: Int, fps: Double = 240.0) = repeat(n) { update(1.0 / fps) }

class JumpTest {

    @Test fun `jump arc matches the tuned apex, airtime and distance`() {
        val g = Game(flat())
        g.onTap()
        var apex = 0.0
        val x0 = g.x
        var t = 0.0
        val dt = 1.0 / 240.0
        // leave the ground
        g.update(dt); t += dt
        while (!g.grounded && t < 2.0) {
            apex = maxOf(apex, g.y)
            g.update(dt); t += dt
        }
        assertTrue(abs(apex - Tuning.JUMP_APEX) < 0.08, "apex ${apex} should be ~${Tuning.JUMP_APEX}")
        assertTrue(abs(t - Tuning.AIR_TIME) < 0.02, "airtime ${t} should be ~${Tuning.AIR_TIME}")
        val dist = g.x - x0
        assertTrue(abs(dist - Tuning.JUMP_DISTANCE) < 0.2, "distance ${dist} should be ~${Tuning.JUMP_DISTANCE}")
    }

    @Test fun `a tap while airborne does not give a second jump`() {
        val g = Game(flat())
        g.onTap(); g.frames(30)
        val yBefore = g.y
        g.onTap(); g.frames(2)
        assertTrue(g.y < yBefore + 0.2, "double jump must not exist in this slice")
    }

    @Test fun `coyote time forgives a tap just after the ledge`() {
        val g = Game(ledge(30.0))
        // run until the moment support is lost
        while (g.grounded && g.state == GameState.RUNNING) g.update(1.0 / 240.0)
        g.frames(10)                      // ~42ms after the ledge, inside the 60ms window
        g.onTap(); g.frames(2)
        assertTrue(g.vy > 0.0, "coyote jump should have fired, vy=${g.vy}")
    }

    @Test fun `coyote time expires`() {
        val g = Game(ledge(30.0))
        while (g.grounded && g.state == GameState.RUNNING) g.update(1.0 / 240.0)
        g.frames(30)                      // 125ms after the ledge, well past 60ms
        g.onTap(); g.frames(2)
        assertTrue(g.vy < 0.0, "jump must not fire outside the coyote window")
    }

    @Test fun `input buffer fires a slightly early tap on landing`() {
        val g = Game(flat())
        g.onTap()
        // fall until ~50ms before touchdown, then tap early
        var t = 0.0
        val dt = 1.0 / 240.0
        while (t < Tuning.AIR_TIME - 0.05) { g.update(dt); t += dt }
        assertTrue(!g.grounded)
        g.onTap()
        while (t < Tuning.AIR_TIME + 0.02) { g.update(dt); t += dt }
        assertTrue(g.vy > 0.0, "buffered tap should re-launch immediately on landing, vy=${g.vy}")
    }

    @Test fun `a spike kills and reports why`() {
        val spike = Hazard(HazardKind.SPIKE_UP, 20.0, 21.0, 0.0, 1.0)
        val g = Game(flat(hazards = listOf(spike)))
        g.frames(2000)
        assertEquals(GameState.DEAD, g.state)
        assertEquals(DeathCause.SPIKE, g.deathCause)
        assertTrue(abs(g.deathX - 19.2) < 1.2, "death should be at the spike, was ${g.deathX}")
    }

    @Test fun `a well timed jump clears a spike`() {
        val spike = Hazard(HazardKind.SPIKE_UP, 20.0, 21.0, 0.0, 1.0)
        val g = Game(flat(hazards = listOf(spike)))
        val dt = 1.0 / 240.0
        while (g.x < 17.6) g.update(dt)
        g.onTap()
        repeat(400) { g.update(dt) }
        assertEquals(GameState.RUNNING, g.state, "cause=${g.deathCause} x=${g.deathX}")
        assertTrue(g.x > 25.0)
    }

    @Test fun `ceiling spikes kill from below and report their own cause`() {
        val spike = Hazard(HazardKind.SPIKE_DOWN, 20.0, 21.0, 2.2, 3.2)
        val g = Game(flat(hazards = listOf(spike)))
        val dt = 1.0 / 240.0
        while (g.x < 18.0) g.update(dt)
        g.onTap()
        repeat(200) { g.update(dt) }
        assertEquals(DeathCause.CEILING_SPIKE, g.deathCause)
    }

    @Test fun `face reflects run, jump and death`() {
        val spike = Hazard(HazardKind.SPIKE_UP, 20.0, 21.0, 0.0, 1.0)
        val g = Game(flat(hazards = listOf(spike)))
        assertEquals(Face.RUN, g.face)
        g.onTap(); g.frames(20)
        assertEquals(Face.JUMP, g.face)
        g.frames(2000)
        assertEquals(Face.DEAD, g.face)
    }

    @Test fun `tap retries only after the death effect has played`() {
        val spike = Hazard(HazardKind.SPIKE_UP, 20.0, 21.0, 0.0, 1.0)
        val g = Game(flat(hazards = listOf(spike)))
        while (g.state == GameState.RUNNING) g.update(1.0 / 240.0)
        assertEquals(GameState.DEAD, g.state)
        g.onTap()
        assertEquals(GameState.DEAD, g.state, "tap during the 0.25s effect must not retry")
        g.update(Tuning.DEATH_EFFECT + 0.01)
        g.onTap()
        assertEquals(GameState.RUNNING, g.state)
        assertEquals(2, g.attempts)
    }

    @Test fun `the square snaps to a right angle on landing`() {
        val g = Game(flat())
        g.onTap()
        g.frames(300)
        assertTrue(g.grounded)
        assertEquals(0.0, g.rotationDeg % 90.0, 1e-6, "rotation ${g.rotationDeg} must snap")
    }
}
