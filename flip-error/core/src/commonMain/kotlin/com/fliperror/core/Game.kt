package com.fliperror.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

enum class GameState { RUNNING, DEAD, COMPLETE }

enum class DeathCause { NONE, PIT, WALL, SPIKE, CEILING_SPIKE }

/** GDD 2.2: the face is a readability element, not decoration. */
enum class Face { RUN, JUMP, DOUBLE, DEAD }

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

    // Double jump ---------------------------------------------------------
    /** Armed by a real jump only, so stepping off a ledge is still a mistake. */
    private var doubleArmed = false
    private var airTime = 0.0
    private var doubleFaceTimer = 0.0
    /** Increments on every second jump, so the shell can react to one. */
    var doubleJumps = 0; private set

    /** Is a second jump legal this instant? Drives the tap and the UI tell. */
    val canDoubleJump: Boolean
        get() = state == GameState.RUNNING && !grounded && doubleArmed &&
            airTime >= Tuning.DOUBLE_LOCKOUT && vy > Tuning.DOUBLE_MIN_VY

    // Run bookkeeping ------------------------------------------------------
    var deathX = 0.0; private set
    var deathY = 0.0; private set
    var attempts = 1; private set
    var elapsed = 0.0; private set
    var stateTime = 0.0; private set
    var bestProgress = 0.0; private set
    var taps = 0; private set
    /** Spikes cleared with almost nothing to spare. Feedback only. */
    var nearMisses = 0; private set
    private var passEdge = Double.NaN
    private var passGap = Double.MAX_VALUE
    var starsCollected = 0; private set
    private val takenStars = HashSet<Int>()

    /** Which star coins are already in the bag this run. The shell draws the rest. */
    fun starTaken(index: Int) = index in takenStars
    fun takenStarIndices(): Set<Int> = takenStars.toSet()

    private var accumulator = 0.0

    val progress: Double get() = (x / level.finishX).coerceIn(0.0, 1.0)

    /** True once the death effect has played and a tap may restart (GDD 10.3). */
    val canRetry: Boolean get() = state == GameState.DEAD && stateTime >= Tuning.DEATH_EFFECT

    val face: Face
        get() = when {
            state == GameState.DEAD -> Face.DEAD
            doubleFaceTimer > 0.0 -> Face.DOUBLE
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
        doubleArmed = false
        airTime = 0.0
        doubleFaceTimer = 0.0
        doubleJumps = 0
        state = GameState.RUNNING
        deathCause = DeathCause.NONE
        elapsed = 0.0
        stateTime = 0.0
        accumulator = 0.0
        taps = 0
        nearMisses = 0
        passEdge = Double.NaN
        passGap = Double.MAX_VALUE
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
                // The second jump answers the tap itself, not a buffered copy of
                // it: a tap that arrives too early must be spent, or the lockout
                // would just delay a mashed double instead of denying it.
                if (canDoubleJump) doubleJump() else bufferTimer = Tuning.INPUT_BUFFER
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
        if (doubleFaceTimer > 0.0) doubleFaceTimer = max(0.0, doubleFaceTimer - dt)
        if (!grounded) airTime += dt

        // 1. Jump: a buffered tap fires as soon as it legally can.
        if (bufferTimer >= 0.0 && (grounded || coyoteTimer > 0.0)) {
            vy = Tuning.JUMP_VELOCITY
            grounded = false
            coyoteTimer = 0.0
            bufferTimer = -1.0
            airTime = 0.0
            doubleArmed = true
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

        // 2b. Moving air. Vertical only - see Wind for why that is not a choice.
        if (level.winds.isNotEmpty() && !grounded) {
            val hb0 = hitBox
            for (w in level.winds) {
                if (w.covers(hb0.x0, hb0.x1)) {
                    vy = max(vy + w.push * dt, -Tuning.MAX_FALL_SPEED)
                }
            }
        }

        // 3. Auto-run. The player never controls x (GDD 3).
        x += Tuning.RUN_SPEED * dt

        // 4. Resolve against solids.
        resolveSolids(prevBottom, prevTop)

        // 5. Hazards.
        val hb = hitBox
        level.forEachHazardNear(hb.x0, hb.x1) { h ->
            if (h.activeAt(elapsed) && hb.overlaps(h.hitBoxAt(elapsed))) {
                die(if (h.kind == HazardKind.SPIKE_DOWN) DeathCause.CEILING_SPIKE else DeathCause.SPIKE)
                return
            }
        }

        // 5b. Near miss. The tightest moment of a pass is almost always an edge,
        // not the middle, so the gap is tracked across the whole overlap and
        // reported once, when the runner is clear of the hazard.
        var tightest = Double.MAX_VALUE
        var edge = Double.NaN
        level.forEachHazardNear(hb.x0, hb.x1) { h ->
            if (!h.activeAt(elapsed)) return@forEachHazardNear
            val box = h.hitBoxAt(elapsed)
            if (hb.x1 > box.x0 && hb.x0 < box.x1) {
                val gap = if (h.kind == HazardKind.SPIKE_UP) hb.y0 - box.y1 else box.y0 - hb.y1
                if (edge.isNaN() || box.x1 < edge) { edge = box.x1; tightest = gap }
                else if (box.x1 == edge) tightest = min(tightest, gap)
            }
        }
        if (edge.isNaN()) {
            flushPass()
        } else if (passEdge != edge) {
            flushPass()
            passEdge = edge
            passGap = tightest
        } else {
            passGap = min(passGap, tightest)
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
        var landed = false
        // Whether the runner was standing on something when this frame began.
        // Only someone already standing gets carried down by sinking ground.
        val wasStanding = grounded && vy <= 0.0

        val t = elapsed
        level.forEachSolidNear(hb.x0, hb.x1) { s ->
            if (!s.presentAt(t)) return@forEachSolidNear
            if (hb.x1 <= s.x0At(t) || hb.x0 >= s.x1At(t)) return@forEachSolidNear
            val top = s.topAt(t)
            val bottom = s.bottomAt(t)
            // A lift carries, and it carries BOTH ways. Rising, it may push into
            // the runner's feet between two frames, so landing is tested against
            // the surface's own travel rather than a fixed line. Sinking, it used
            // to simply leave: the floor dropped 0.006u in a frame while gravity
            // moved the runner 0.0005u, so the feet hung in the air for the ~26
            // frames it takes to fall as fast as the sand, and the runner spent
            // every descending breath flickering in and out of "grounded" - no
            // coyote time, no armed double jump, and a verifier that saw standing
            // room chopped into five-frame slivers. Ground that sinks under you is
            // still ground you are standing on. Horizontal movers never carry -
            // see Solid.
            val rise = s.offsetY(t) - s.offsetY(t - Tuning.FIXED_DT)
            val sink = if (wasStanding) maxOf(-rise, 0.0) else 0.0
            if (vy <= 0.0 && prevBottom >= top - maxOf(rise, 0.0) - 1e-6 && y <= top + sink + 1e-6) {
                y = top
                vy = 0.0
                if (!grounded) rotationDeg = round(rotationDeg / 90.0) * 90.0
                grounded = true
                landed = true
                doubleArmed = false
                airTime = 0.0
            } else if (vy > 0.0 && prevTop <= bottom + 1e-6 && y + Tuning.PLAYER_SIZE >= bottom) {
                y = bottom - Tuning.PLAYER_SIZE
                vy = 0.0
            }
        }

        // Crashing into the side of a block. Falling into the far wall of a gap
        // is a missed jump, not a wall run - say the thing the player did wrong.
        val hb2 = hitBox
        level.forEachSolidNear(hb2.x0, hb2.x1) { s ->
            if (!s.presentAt(t)) return@forEachSolidNear
            if (hb2.x1 <= s.x0At(t) || hb2.x0 >= s.x1At(t)) return@forEachSolidNear
            if (hb2.y0 < s.topAt(t) - Tuning.STEP_TOLERANCE && hb2.y1 > s.bottomAt(t)) {
                die(if (!grounded && vy < 0.0) DeathCause.PIT else DeathCause.WALL)
                return
            }
        }

        if (!landed && grounded && !hasSupport()) {
            grounded = false
            coyoteTimer = Tuning.COYOTE_TIME
        }
    }

    /** The boost itself. Sets the rise rather than adding to it, so a late
     *  second tap trades away height instead of stacking it. */
    private fun doubleJump() {
        vy = Tuning.DOUBLE_JUMP_VELOCITY
        doubleArmed = false
        doubleJumps++
        doubleFaceTimer = 0.26
        bufferTimer = -1.0
    }

    /** A hazard pass just ended: score it, then forget it. */
    private fun flushPass() {
        if (!passEdge.isNaN() && passGap >= 0.0 && passGap <= Tuning.NEAR_MISS_GAP) nearMisses++
        passEdge = Double.NaN
        passGap = Double.MAX_VALUE
    }

    /** Is there a solid surface directly under the feet right now? */
    private fun hasSupport(): Boolean {
        val hb = hitBox
        val t = elapsed
        var found = false
        level.forEachSolidNear(hb.x0, hb.x1) { s ->
            if (!s.presentAt(t)) return@forEachSolidNear
            if (hb.x1 <= s.x0At(t) || hb.x0 >= s.x1At(t)) return@forEachSolidNear
            if (abs(y - s.topAt(t)) < 1e-3) found = true
        }
        return found
    }

    /**
     * Full simulation state. Used by the level verifier to branch a run
     * without replaying it from the start.
     */
    class Snapshot internal constructor(
        internal val x: Double, internal val y: Double, internal val vy: Double,
        internal val grounded: Boolean, internal val rotationDeg: Double,
        internal val coyoteTimer: Double, internal val bufferTimer: Double,
        internal val elapsed: Double, internal val taps: Int, internal val state: GameState,
        internal val accumulator: Double,
        internal val doubleArmed: Boolean, internal val airTime: Double,
        internal val doubleFaceTimer: Double, internal val doubleJumps: Int,
        internal val nearMisses: Int, internal val passEdge: Double, internal val passGap: Double,
    )

    fun snapshot() = Snapshot(x, y, vy, grounded, rotationDeg, coyoteTimer, bufferTimer, elapsed, taps, state,
        accumulator, doubleArmed, airTime, doubleFaceTimer, doubleJumps, nearMisses, passEdge, passGap)

    fun restore(s: Snapshot) {
        x = s.x; y = s.y; vy = s.vy; grounded = s.grounded; rotationDeg = s.rotationDeg
        coyoteTimer = s.coyoteTimer; bufferTimer = s.bufferTimer
        elapsed = s.elapsed; taps = s.taps; state = s.state; accumulator = s.accumulator
        doubleArmed = s.doubleArmed; airTime = s.airTime
        doubleFaceTimer = s.doubleFaceTimer; doubleJumps = s.doubleJumps
        nearMisses = s.nearMisses; passEdge = s.passEdge; passGap = s.passGap
        deathCause = DeathCause.NONE
        stateTime = 0.0
    }

    /** One fixed physics step, for deterministic offline analysis. */
    fun stepFixed() { if (state == GameState.RUNNING) step(Tuning.FIXED_DT) }

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
