package com.carom.core.game

import com.carom.core.level.LevelData
import com.carom.core.math.Vec2
import com.carom.core.physics.Ball
import com.carom.core.physics.CircleTrigger
import com.carom.core.physics.PhysicsWorld
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The rules of one level: launching, counting bounces, winning and failing.
 *
 * Simulation runs at a fixed [STEP] regardless of frame rate, so a given shot always ends the
 * same way on every device. Rendering reads [renderX]/[renderY], which interpolate between steps
 * for smooth motion on 60, 90 and 120 Hz screens.
 */
class GameSession(val level: LevelData) {

    enum class State { AIMING, MOVING, WON, FAILED }

    enum class FailReason {
        /** Hit a wall with no bounces left. */
        OUT_OF_BOUNCES,

        /** Friction stopped the ball before it reached the goal. */
        STOPPED,
    }

    /** Presentation hooks (sound, haptics, effects). The rules never depend on them. */
    interface Listener {
        fun onLaunch() {}
        fun onBounce(x: Double, y: Double, bouncesLeft: Int) {}
        fun onWin(x: Double, y: Double) {}
        fun onFail(reason: FailReason, x: Double, y: Double) {}
    }

    var listener: Listener? = null

    private val world: PhysicsWorld = WorldBuilder.build(level)
    val ball = Ball(level.ballRadius)

    var state = State.AIMING
        private set
    var bouncesLeft = level.bounces
        private set
    val bouncesUsed: Int get() = level.bounces - bouncesLeft
    var failReason: FailReason? = null
        private set

    /** Seconds since launch. */
    var flightTime = 0.0
        private set

    private val currentPath = ArrayList<Vec2>()
    private var previousPath: List<Vec2> = emptyList()

    /** Corners of the current shot: launch point, then every bounce (and the end point once over). */
    val path: List<Vec2> get() = currentPath

    /** The path of the previous attempt, kept after a restart as a reference for the next aim. */
    val lastShotPath: List<Vec2> get() = previousPath

    private var accumulator = 0.0
    private var prevX = 0.0
    private var prevY = 0.0

    val renderX: Double get() = prevX + (ball.x - prevX) * (accumulator / STEP)
    val renderY: Double get() = prevY + (ball.y - prevY) * (accumulator / STEP)

    private val rules = object : PhysicsWorld.Listener {
        override fun onWallContact(x: Double, y: Double, nx: Double, ny: Double): Boolean {
            if (bouncesLeft == 0) {
                finish(State.FAILED, FailReason.OUT_OF_BOUNCES)
                return false
            }
            bouncesLeft--
            currentPath.add(Vec2(x, y))
            listener?.onBounce(x, y, bouncesLeft)
            return true
        }

        override fun onTrigger(trigger: CircleTrigger, x: Double, y: Double): Boolean {
            ball.x = x
            ball.y = y
            finish(State.WON, null)
            return false
        }
    }

    init {
        reset()
    }

    /**
     * Fires the ball in direction (dirX, dirY) with [power] in 0..1. Power maps to speed as a square
     * root so that the distance travelled grows linearly with how far the player pulled.
     */
    fun launch(dirX: Double, dirY: Double, power: Double): Boolean {
        val len = hypot(dirX, dirY)
        if (state != State.AIMING || len == 0.0 || power <= 0.0) return false
        ball.dirX = dirX / len
        ball.dirY = dirY / len
        ball.speed = level.maxSpeed * sqrt(power.coerceAtMost(1.0))
        state = State.MOVING
        currentPath.clear()
        currentPath.add(Vec2(ball.x, ball.y))
        listener?.onLaunch()
        return true
    }

    /** Advances by one frame's worth of time, running as many fixed steps as fit. */
    fun advance(frameSeconds: Double) {
        if (state != State.MOVING) return
        accumulator += min(frameSeconds, MAX_FRAME)
        while (accumulator >= STEP && state == State.MOVING) {
            step()
            accumulator -= STEP
        }
    }

    /** One fixed simulation step. Friction is integrated exactly for constant deceleration. */
    fun step() {
        if (state != State.MOVING) return
        prevX = ball.x
        prevY = ball.y
        val v0 = ball.speed
        val a = level.friction
        val distance: Double
        val v1: Double
        when {
            a <= 0.0 -> {
                distance = v0 * STEP
                v1 = v0
            }
            v0 <= a * STEP -> {
                distance = v0 * v0 / (2.0 * a)
                v1 = 0.0
            }
            else -> {
                distance = v0 * STEP - 0.5 * a * STEP * STEP
                v1 = v0 - a * STEP
            }
        }
        flightTime += STEP
        if (!world.move(ball, distance, rules)) return
        ball.speed = v1
        if (v1 <= 0.0 || flightTime >= MAX_FLIGHT_SECONDS) finish(State.FAILED, FailReason.STOPPED)
    }

    /** Puts everything back to the level's initial state, remembering the last shot's path. */
    fun restart() {
        // A shot cut short mid-flight still leaves its path so far as the reference.
        if (state == State.MOVING) currentPath.add(Vec2(ball.x, ball.y))
        if (currentPath.size > 1) previousPath = ArrayList(currentPath)
        reset()
    }

    private fun reset() {
        ball.place(level.ball.x, level.ball.y)
        ball.dirX = 1.0
        ball.dirY = 0.0
        prevX = ball.x
        prevY = ball.y
        accumulator = 0.0
        state = State.AIMING
        bouncesLeft = level.bounces
        failReason = null
        flightTime = 0.0
        currentPath.clear()
    }

    private fun finish(result: State, reason: FailReason?) {
        state = result
        failReason = reason
        ball.speed = 0.0
        prevX = ball.x
        prevY = ball.y
        accumulator = 0.0
        currentPath.add(Vec2(ball.x, ball.y))
        if (result == State.WON) {
            listener?.onWin(ball.x, ball.y)
        } else {
            listener?.onFail(reason!!, ball.x, ball.y)
        }
    }

    companion object {
        /** Simulation step: 120 Hz. */
        const val STEP = 1.0 / 120.0

        /** Longest frame simulated at once (after a hitch the game slows down rather than skips). */
        const val MAX_FRAME = 0.1

        /** Safety limit for a frictionless level whose ball never reaches a wall or the goal. */
        const val MAX_FLIGHT_SECONDS = 60.0
    }
}
