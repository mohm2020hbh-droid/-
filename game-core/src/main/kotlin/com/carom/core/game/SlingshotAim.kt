package com.carom.core.game

import kotlin.math.hypot

/**
 * Slingshot aiming in world coordinates: press on the ball, pull back, release. The ball is
 * launched opposite to the pull, and power grows with the pull length up to [maxPull].
 *
 * The pull is measured from where the finger first touched, not from the ball's centre, so
 * grabbing the ball slightly off-centre doesn't produce a jump. Pulling past [maxPull] keeps full
 * power but still refines the direction, which gives finer angle control on long pulls.
 */
class SlingshotAim(
    private val maxPull: Double,
    /** Pulls weaker than this are treated as a cancelled shot, so a tap never fires the ball. */
    private val minPower: Double = 0.12,
) {
    var isActive = false
        private set

    private var startX = 0.0
    private var startY = 0.0
    private var currentX = 0.0
    private var currentY = 0.0

    /** Starts aiming if the touch at (x, y) is within [grabRadius] of the ball at (ballX, ballY). */
    fun tryBegin(x: Double, y: Double, ballX: Double, ballY: Double, grabRadius: Double): Boolean {
        if (hypot(x - ballX, y - ballY) > grabRadius) return false
        isActive = true
        startX = x
        startY = y
        currentX = x
        currentY = y
        return true
    }

    fun drag(x: Double, y: Double) {
        if (!isActive) return
        currentX = x
        currentY = y
    }

    fun cancel() {
        isActive = false
    }

    val pullLength: Double get() = hypot(startX - currentX, startY - currentY)

    /** 0..1, proportional to the pull length. */
    val power: Double get() = (pullLength / maxPull).coerceIn(0.0, 1.0)

    /** Launch direction (unit vector, opposite to the pull). Zero until the finger moves. */
    val dirX: Double get() = pullLength.let { if (it > 0.0) (startX - currentX) / it else 0.0 }
    val dirY: Double get() = pullLength.let { if (it > 0.0) (startY - currentY) / it else 0.0 }

    /** Whether releasing now would fire the ball. */
    val isShotReady: Boolean get() = isActive && power >= minPower

    /** Ends aiming and fires [session] if the pull was strong enough. */
    fun release(session: GameSession): Boolean {
        if (!isActive) return false
        isActive = false
        return power >= minPower && session.launch(dirX, dirY, power)
    }
}
