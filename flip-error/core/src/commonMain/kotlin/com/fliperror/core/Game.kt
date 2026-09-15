package com.fliperror.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

enum class GameState { RUNNING, DEAD, COMPLETE }

enum class DeathCause { NONE, PIT, WALL, SPIKE, CEILING_SPIKE }

/** GDD 2.2: the face is a readability element, not decoration. */
enum class Face { RUN, JUMP, DEAD }

/** Drives the whole simulation. Pure Kotlin: no rendering, no platform types. */
class Game(val level: Level) {

    var state = GameState.RUNNING; private set
    var deathCause = DeathCause.NONE; private set

    // Player state -------------------------------------------------------
    var x = 0.0; private set          // left edge of the drawn square
    var y = 0.0; private set          // bottom edge of the drawn square
    var vy = 0.0; private set
    var grounded = true; private set
    var rotationDeg = 0.0; private set

    // Input forgiveness (GDD 1.4) ----------------------------------------
    private var coyoteTimer = 0.0
    private var bufferTimer = -1.0

    // Run bookkeeping ------------------------------------------------------
    var deathX = 0.0; private set
    var deathY = 0.0; private set
    var attempts = 1; private set
    var elapsed = 0.0; private set
    var stateTime = 0.0; private set
    var bestProgress = 0.0; private set
    var taps = 0; private set
    var starsCollected = 0; private set
    private val takenStars = HashSet<Int>()

    private var accumulator = 0.0

    val progress: Double get() = (x / level.finishX).coerceIn(0.0, 1.0)

    /** True once the death effect has played and a tap may restart (GDD 10.3). */
    val canRetry: Boolean get() = state == GameState.DEAD && stateTime >= Tuning.DEATH_EFFECT

    val face: Face
        get() = when {
            state == GameState.DEAD -> Face.DEAD
            !grounded -> Face.JUMP
            else -> Face.RUN
        }

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
        rotationDeg = 0.0
        coyoteTimer = 0.0
        bufferTimer = -1.0
        state = GameState.RUNNING
        deathCause = DeathCause.NONE
        elapsed = 0.0
        stateTime = 0.0
        accumulator = 0.0
        taps = 0
        starsCollected = 0
        takenStars.clear()
    }

    /** Death -> retry is a single call; no screens, no loading (GDD 10.3). */
    fun restart() {
        if (progress > bestProgress) bestProgress = progress
        attempts++
        reset()
    }

    /**
     * The whole screen is the button (GDD 3.1 / 10.3).
     * While running a tap is a jump; on the death screen any tap retries.
     */
    fun onTap() {
        when (state) {
            GameState.RUNNING -> {
                taps++
                bufferTimer = Tuning.INPUT_BUFFER
            }
            GameState.DEAD -> if (canRetry) restart()
            GameState.COMPLETE -> Unit
        }
    }

    /** Advance by real frame time; internally stepped at a fixed rate for determinism. */
    fun update(dt: Double) {
        // The death-screen timer follows wall clock; only the physics
        // accumulator is clamped, so a hitch cannot spiral the simulation.
        stateTime += dt
        if (state != GameState.RUNNING) return
        val clamped = min(dt, Tuning.MAX_FRAME_DT)
        accumulator += clamped
        while (accumulator >= Tuning.FIXED_DT) {
            accumulator -= Tuning.FIXED_DT
            step(Tuning.FIXED_DT)
            if (state != GameState.RUNNING) { accumulator = 0.0; return }
        }
    }

    private fun step(dt: Double) {
        elapsed += dt

        if (bufferTimer >= 0.0) bufferTimer -= dt
        if (coyoteTimer > 0.0) coyoteTimer = max(0.0, coyoteTimer - dt)

        // 1. Jump: a buffered tap fires as soon as it legally can.
        if (bufferTimer >= 0.0 && (grounded || coyoteTimer > 0.0)) {
            vy = Tuning.JUMP_VELOCITY
            grounded = false
            coyoteTimer = 0.0
            bufferTimer = -1.0
        }

        val prevBottom = y
        val prevTop = y + Tuning.PLAYER_SIZE

        // 2. Vertical integration. Asymmetric gravity (GDD 1.4).
        if (!grounded) {
            val g = if (vy > 0) Tuning.GRAVITY_RISE else Tuning.GRAVITY_FALL
            vy = max(vy - g * dt, -Tuning.MAX_FALL_SPEED)
            y += vy * dt
            // The square turns exactly 90 degrees per jump so the spin reads as timing.
            rotationDeg += 90.0 * dt / Tuning.AIR_TIME
        }

        // 3. Auto-run. The player never controls x (GDD 3).
        x += Tuning.RUN_SPEED * dt

        // 4. Resolve against solids.
        resolveSolids(prevBottom, prevTop)

        // 5. Hazards.
        val hb = hitBox
        for (h in level.hazardsNear(hb.x0, hb.x1)) {
            if (hb.overlaps(h.hitBox)) {
                die(if (h.kind == HazardKind.SPIKE_DOWN) DeathCause.CEILING_SPIKE else DeathCause.SPIKE)
                return
            }
        }

        // 6. Stars.
        level.stars.forEachIndexed { i, s ->
            if (i !in takenStars && hb.overlaps(s.box)) { takenStars += i; starsCollected++ }
        }

        // 7. Pit.
        if (y < Tuning.KILL_Y) { die(DeathCause.PIT); return }

        // 8. Finish gate.
        if (x + Tuning.PLAYER_SIZE >= level.finishX) complete()
    }

    private fun resolveSolids(prevBottom: Double, prevTop: Double) {
        val hb = hitBox
        val near = level.solidsNear(hb.x0 - 1.0, hb.x1 + 1.0)
        var landed = false

        for (s in near) {
            if (hb.x1 <= s.x0 || hb.x0 >= s.x1) continue

            if (vy <= 0.0 && prevBottom >= s.top - 1e-6 && y <= s.top + 1e-6) {
                y = s.top
                vy = 0.0
                if (!grounded) rotationDeg = round(rotationDeg / 90.0) * 90.0
                grounded = true
                landed = true
            } else if (vy > 0.0 && prevTop <= s.bottom + 1e-6 && y + Tuning.PLAYER_SIZE >= s.bottom) {
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

        if (!landed && grounded && !hasSupport()) {
            grounded = false
            coyoteTimer = Tuning.COYOTE_TIME
        }
    }

    /** Is there a solid surface directly under the feet right now? */
    private fun hasSupport(): Boolean {
        val hb = hitBox
        return level.solidsNear(hb.x0, hb.x1).any { s ->
            hb.x1 > s.x0 && hb.x0 < s.x1 && abs(y - s.top) < 1e-4
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
