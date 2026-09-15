package com.fliperror.core

import kotlin.math.max
import kotlin.math.min

enum class GameState { RUNNING, DEAD, COMPLETE }

enum class DeathCause { NONE, PIT, WALL }

/** Drives the whole simulation. Pure Kotlin: no rendering, no platform types. */
class Game(val level: Level) {

    var state = GameState.RUNNING; private set
    var deathCause = DeathCause.NONE; private set

    // Player state -------------------------------------------------------
    var x = 0.0; private set          // left edge of the drawn square
    var y = 0.0; private set          // bottom edge of the drawn square
    var vy = 0.0; private set
    var grounded = true; private set

    /** Where the run ended, so the renderer can mark the spot. */
    var deathX = 0.0; private set
    var deathY = 0.0; private set

    var attempts = 1; private set
    var elapsed = 0.0; private set
    var stateTime = 0.0; private set
    var bestProgress = 0.0; private set

    private var accumulator = 0.0

    val progress: Double get() = (x / level.finishX).coerceIn(0.0, 1.0)

    /** Hitbox of the player right now (GDD law 3: 10% smaller than the drawing). */
    val hitBox: Box
        get() = Box(
            x + Tuning.PLAYER_INSET, y + Tuning.PLAYER_INSET,
            x + Tuning.PLAYER_SIZE - Tuning.PLAYER_INSET, y + Tuning.PLAYER_SIZE - Tuning.PLAYER_INSET,
        )

    init { reset() }

    fun reset() {
        x = 0.0
        y = level.startY
        vy = 0.0
        grounded = true
        state = GameState.RUNNING
        deathCause = DeathCause.NONE
        elapsed = 0.0
        stateTime = 0.0
        accumulator = 0.0
    }

    /** Death -> retry is a single call; no screens, no loading (GDD 10.3). */
    fun restart() {
        if (progress > bestProgress) bestProgress = progress
        attempts++
        reset()
    }

    /** Advance by real frame time; internally stepped at a fixed rate for determinism. */
    fun update(dt: Double) {
        val clamped = min(dt, Tuning.MAX_FRAME_DT)
        stateTime += clamped
        if (state != GameState.RUNNING) return
        accumulator += clamped
        while (accumulator >= Tuning.FIXED_DT) {
            accumulator -= Tuning.FIXED_DT
            step(Tuning.FIXED_DT)
            if (state != GameState.RUNNING) { accumulator = 0.0; return }
        }
    }

    private fun step(dt: Double) {
        elapsed += dt

        val prevBottom = y
        val prevTop = y + Tuning.PLAYER_SIZE

        // 1. Vertical integration. Asymmetric gravity (GDD 1.4).
        if (!grounded) {
            val g = if (vy > 0) Tuning.GRAVITY_RISE else Tuning.GRAVITY_FALL
            vy = max(vy - g * dt, -Tuning.MAX_FALL_SPEED)
            y += vy * dt
        }

        // 2. Auto-run. The player never controls x (GDD 3).
        x += Tuning.RUN_SPEED * dt

        // 3. Resolve against solids.
        resolveSolids(prevBottom, prevTop)

        // 4. Pit.
        if (y < Tuning.KILL_Y) { die(DeathCause.PIT); return }

        // 5. Finish gate.
        if (x + Tuning.PLAYER_SIZE >= level.finishX) complete()
    }

    private fun resolveSolids(prevBottom: Double, prevTop: Double) {
        val hb = hitBox
        val near = level.solidsNear(hb.x0 - 1.0, hb.x1 + 1.0)
        var landed = false

        for (s in near) {
            if (hb.x1 <= s.x0 || hb.x0 >= s.x1) continue

            // Land on top: crossed the surface while moving down.
            if (vy <= 0.0 && prevBottom >= s.top - 1e-6 && y <= s.top + 1e-6) {
                y = s.top
                vy = 0.0
                grounded = true
                landed = true
            }
            // Bump a ceiling: crossed the underside while moving up. Not lethal.
            else if (vy > 0.0 && prevTop <= s.bottom + 1e-6 && y + Tuning.PLAYER_SIZE >= s.bottom) {
                y = s.bottom - Tuning.PLAYER_SIZE
                vy = 0.0
            }
        }

        // Wall crash: the body is inside a block well below its top surface.
        val hb2 = hitBox
        for (s in near) {
            if (hb2.x1 <= s.x0 || hb2.x0 >= s.x1) continue
            if (hb2.y0 < s.top - Tuning.STEP_TOLERANCE && hb2.y1 > s.bottom) {
                die(DeathCause.WALL); return
            }
        }

        if (!landed && grounded && !hasSupport()) grounded = false
    }

    /** Is there a solid surface directly under the feet right now? */
    private fun hasSupport(): Boolean {
        val hb = hitBox
        return level.solidsNear(hb.x0, hb.x1).any { s ->
            hb.x1 > s.x0 && hb.x0 < s.x1 && y <= s.top + 1e-4 && y >= s.top - 1e-4
        }
    }

    private fun die(cause: DeathCause) {
        if (state != GameState.RUNNING) return
        state = GameState.DEAD
        deathCause = cause
        deathX = x
        deathY = y
        stateTime = 0.0
        if (progress > bestProgress) bestProgress = progress
    }

    private fun complete() {
        state = GameState.COMPLETE
        stateTime = 0.0
        bestProgress = 1.0
    }
}
