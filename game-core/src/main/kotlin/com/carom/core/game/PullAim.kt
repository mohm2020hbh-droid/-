package com.carom.core.game

import kotlin.math.hypot
import kotlin.math.min

/**
 * Hold, pull, let go: the player's control of the ball before a throw, in world coordinates.
 *
 * - **Hold**: the finger is on the ball and the ball follows it around its launch zone. It follows at a limited
 *   speed ([followSpeed]), so a calm movement carries the ball exactly and a hard one leaves it behind. Moving the
 *   ball, resting, moving it again: none of that throws anything.
 * - **Pull**: when the finger gets [detach] away from the ball (a hard pull that outruns it, or a drag that goes out
 *   past the edge of the launch zone, where the ball cannot follow) the ball lets go of the finger and stays
 *   where it is. The pull, from the ball to the finger, is the throw being prepared: its direction is the throw's
 *   direction and its length is the throw's strength ([maxPull] or more is full power). Bringing the finger back
 *   to within [reattach] of the ball cancels the pull and the ball is held again.
 * - **Release**: letting go of a pull at least [minPull] long throws the ball. Letting go in any other state
 *   throws nothing: the ball just stays where it was put. After the throw the ball is physics; it never follows
 *   the finger again.
 *
 * The class knows nothing about walls or the launch zone. The caller tells it where the ball really is on every
 * call and puts the ball at [wantX], [wantY] while [phase] is [Phase.HOLDING], and whatever stops the ball
 * (a wall, the zone's edge) shows up as distance between the ball and the finger.
 *
 * Time is in seconds on one clock. Events carry their own time ([advanceTo]); frames add theirs ([advanceBy]); the
 * clock never runs backwards, so the same time is never counted twice.
 */
class PullAim(
    /** How fast the held ball can follow the finger (world units per second). */
    private val followSpeed: Double,
    /** A finger this far from the ball has pulled away from it. */
    private val detach: Double,
    /** A pull that comes back closer than this is cancelled. */
    private val reattach: Double,
    /** Pulls shorter than this are not throws. */
    private val minPull: Double,
    /** A pull this long is a full-power throw. */
    private val maxPull: Double,
    /** Finger speed at release (world units per second) where the flick boost begins. */
    private val flickStart: Double = Double.POSITIVE_INFINITY,
    /** Finger speed at which the flick boost is complete. */
    private val flickFull: Double = Double.POSITIVE_INFINITY,
    /** Power a fast flick adds on top of the pull's own power. */
    private val flickBoost: Double = 0.25,
) {
    enum class Phase { IDLE, HOLDING, PULLING }

    /** A throw: a unit direction and a power in 0..1. */
    class Throw(val dirX: Double, val dirY: Double, val power: Double)

    var phase = Phase.IDLE
        private set
    val isActive: Boolean get() = phase != Phase.IDLE

    // The finger, and where it wants the ball: the finger plus the way the ball sat under it when it was grabbed.
    private var fingerX = 0.0
    private var fingerY = 0.0
    private var grabX = 0.0
    private var grabY = 0.0
    private var clock = 0.0
    private var ballX = 0.0
    private var ballY = 0.0

    /** Where the ball should be now while it is held: one step from where it is, towards the finger. */
    var wantX = 0.0
        private set
    var wantY = 0.0
        private set

    // The finger's recent positions (a ring buffer), to measure how fast it moves at release.
    private val sampleX = DoubleArray(SAMPLES)
    private val sampleY = DoubleArray(SAMPLES)
    private val sampleT = DoubleArray(SAMPLES)
    private var sampleCount = 0
    private var sampleNext = 0

    /** Starts holding the ball (at [ballX], [ballY]) with the finger at ([x], [y]) at [time]. */
    fun begin(x: Double, y: Double, ballX: Double, ballY: Double, time: Double) {
        phase = Phase.HOLDING
        fingerX = x
        fingerY = y
        grabX = ballX - x
        grabY = ballY - y
        clock = time
        this.ballX = ballX
        this.ballY = ballY
        wantX = ballX
        wantY = ballY
        sampleCount = 0
        sampleNext = 0
        record(x, y, time)
    }

    /** The finger moved to ([x], [y]) at [time]; the ball is at ([ballX], [ballY]). */
    fun move(x: Double, y: Double, time: Double, ballX: Double, ballY: Double) {
        if (!isActive) return
        fingerX = x
        fingerY = y
        record(x, y, time)
        advanceTo(time, ballX, ballY)
    }

    /** A frame lasting [dt] seconds passed with the ball at ([ballX], [ballY]). */
    fun advanceBy(dt: Double, ballX: Double, ballY: Double) = advanceTo(clock + dt, ballX, ballY)

    /** Brings the aim up to [time]: notices a pull, a cancelled pull, and works out where the held ball goes next. */
    fun advanceTo(time: Double, ballX: Double, ballY: Double) {
        if (!isActive) return
        val dt = (time - clock).coerceAtLeast(0.0)
        clock = maxOf(clock, time)
        this.ballX = ballX
        this.ballY = ballY
        wantX = ballX
        wantY = ballY
        val gap = hypot(targetX - ballX, targetY - ballY)
        if (phase == Phase.HOLDING) {
            if (gap >= detach) {
                phase = Phase.PULLING // out of reach: the ball stays, the pull begins
                return
            }
            val step = followSpeed * dt
            if (gap <= step) {
                wantX = targetX
                wantY = targetY
            } else if (gap > 0.0) {
                wantX = ballX + (targetX - ballX) / gap * step
                wantY = ballY + (targetY - ballY) / gap * step
            }
        } else if (gap < reattach) {
            // The finger came back to the ball: the pull is cancelled and the ball is held again, where it is.
            phase = Phase.HOLDING
            grabX = ballX - fingerX
            grabY = ballY - fingerY
        }
    }

    fun cancel() {
        phase = Phase.IDLE
    }

    private val targetX: Double get() = fingerX + grabX
    private val targetY: Double get() = fingerY + grabY

    /** The length of the pull so far (0 unless the ball has let go of the finger). */
    val pullLength: Double get() = if (phase == Phase.PULLING) hypot(targetX - ballX, targetY - ballY) else 0.0

    /** 0..1 from the pull's length alone. */
    val power: Double get() = (pullLength / maxPull).coerceIn(0.0, 1.0)

    /** Whether letting go now would throw the ball. */
    val isThrowReady: Boolean get() = phase == Phase.PULLING && pullLength >= minPull

    /**
     * How fast the finger was moving just before [time], in world units per second: distance over the last
     * [FLICK_WINDOW] seconds. A finger that has come to rest measures 0.
     */
    fun fingerSpeed(time: Double): Double {
        var oldest = -1
        for (k in 1..sampleCount) {
            val i = (sampleNext - k + SAMPLES) % SAMPLES
            if (time - sampleT[i] > FLICK_WINDOW) break
            oldest = i
        }
        if (oldest < 0) return 0.0
        val span = time - sampleT[oldest]
        if (span < MIN_SPAN) return 0.0
        return hypot(fingerX - sampleX[oldest], fingerY - sampleY[oldest]) / span
    }

    /**
     * The finger lifts at ([x], [y]) at [time]. Returns the throw, or null when there was no pull to throw
     * (the ball simply stays where it was put).
     */
    fun release(x: Double, y: Double, time: Double, ballX: Double, ballY: Double): Throw? {
        if (!isActive) return null
        move(x, y, time, ballX, ballY)
        val thrown = isThrowReady
        val length = pullLength
        val dx = targetX - this.ballX
        val dy = targetY - this.ballY
        val boost = flickBoostAt(time)
        phase = Phase.IDLE
        if (!thrown || length <= 0.0) return null
        return Throw(dx / length, dy / length, min(1.0, length / maxPull + flickBoost * boost))
    }

    private fun flickBoostAt(time: Double): Double {
        val speed = fingerSpeed(time)
        return if (speed <= flickStart) 0.0 else ((speed - flickStart) / (flickFull - flickStart)).coerceIn(0.0, 1.0)
    }

    private fun record(x: Double, y: Double, time: Double) {
        sampleX[sampleNext] = x
        sampleY[sampleNext] = y
        sampleT[sampleNext] = time
        sampleNext = (sampleNext + 1) % SAMPLES
        sampleCount = min(sampleCount + 1, SAMPLES)
    }

    private companion object {
        const val SAMPLES = 16

        /** How far back the release speed looks. */
        const val FLICK_WINDOW = 0.08

        /** Shorter spans than this are too noisy to measure a speed from. */
        const val MIN_SPAN = 0.012
    }
}
