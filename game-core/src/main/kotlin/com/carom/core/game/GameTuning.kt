package com.carom.core.game

/**
 * Every number the reference game's behaviour is built on, in one place and all adjustable.
 *
 * The reference measures speeds and forces in its own units ("ref units"). Here they are converted to
 * this game's world with [refToWorld]: the reference's top speed ([maxSpeedRef]) is the level's top
 * speed, so `1 ref unit = level.maxSpeed / maxSpeedRef` world units, whatever the level's size.
 */
data class GameTuning(
    // ---- the ball: a rigid body without gravity or spin ----
    /** The ball never falls. (Kept as a value so a level type with gravity is possible; the engine applies none.) */
    val gravityScale: Double = 0.0,
    val mass: Double = 1.0,
    /** Linear drag: the velocity decays as e^(−drag·t), so a free ball loses speed smoothly and never stops dead. */
    val drag: Double = 0.25,
    val angularDrag: Double = 0.0,
    /** The ball's rotation is frozen: only the picture spins, nothing physical does. */
    val freezeRotation: Boolean = true,

    // ---- touch control: swipe → delta × sensibility → impulse ----
    /**
     * How a touch acts on the ball before the throw: [ControlMode.HOLD] (hold the ball, move it, pull away, let go)
     * or [ControlMode.SWIPE] (a swipe anywhere is the impulse). Once the ball is flying, only a ball in a touch zone
     * can be pushed again, and that is always by a swipe.
     */
    val controlMode: ControlMode = ControlMode.HOLD,
    val touchSensibility: Double = 0.5,
    /** A touch that moves less than this (in dp) is not a swipe. */
    val swipeTolerance: Double = 4.0,
    /** A touch that ends within this many seconds without moving is a tap. */
    val tapThreshold: Double = 0.4,
    /** A second tap within this many seconds of the first makes a double tap. */
    val doubleTapWait: Double = 0.2,
    /** Taps closer together than this are one bounce of the finger, not two taps. */
    val tapWait: Double = 0.1,

    // ---- hold and pull (in dp, like the swipe) ----
    /** How fast a held ball can follow the finger; a finger that moves faster than this pulls away from the ball. */
    val pullFollowSpeed: Double = 700.0,
    /** A finger this far from the ball has pulled away from it: the pull (the throw being prepared) begins. */
    val pullDetach: Double = 30.0,
    /** A pull brought back closer than this to the ball is cancelled. */
    val pullReattach: Double = 16.0,
    /** Letting go of a pull shorter than this throws nothing. */
    val pullMin: Double = 30.0,
    /** A pull this long is a full-power throw. */
    val pullMax: Double = 110.0,
    /** Finger speeds (dp per second) at release where a flick starts and stops adding power, and how much it adds. */
    val flickStart: Double = 900.0,
    val flickFull: Double = 2600.0,
    val flickBoost: Double = 0.25,

    // ---- speed ----
    /** The top speed in ref units. It caps the ball's speed whatever pushes it: swipes, boosters, fields, portals. */
    val maxSpeedRef: Double = 120.0,

    // ---- collisions ----
    /** Two contacts with the same wall closer together than this (seconds of game time) are one collision. */
    val debounceTime: Double = 0.05,
    /** Two contacts closer together than this (world units) within twice [debounceTime] are one collision (a corner). */
    val debounceTravel: Double = 1.5,

    // ---- ball death by standing still ----
    /** At or under this speed (ref units), outside a touch zone, the ball is dead. */
    val stopSpeedRef: Double = 0.1,
    /** Between [stopSpeedRef] and this speed the ball fades out before it dies. */
    val fadeSpeedRef: Double = 1.1,

    // ---- force zones ----
    /** Booster: acceleration = direction × force × this (ref units per second²). */
    val boosterGain: Double = 100.0,
    /** Attractive and repulsive zones: acceleration = direction × force × this. */
    val fieldGain: Double = 1.0,
    /** Slower zone: velocity = lerp(velocity, 0, force × dt × this). */
    val slowerGain: Double = 5.0,

    // ---- portals ----
    /** After a jump, portals ignore the ball for at least this long (seconds of game time), and until it has left the exit portal. */
    val portalCooldown: Double = 0.25,

    // ---- slow motion ----
    /** Game time runs at this fraction of real time inside a slow-motion zone (1/8). */
    val slowMoScale: Double = 0.125,
    /** All sound plays at this pitch (playback speed) during slow motion. */
    val slowMoPitch: Double = 0.33,

    // ---- flow ----
    /** From the last ball lost to the automatic retry. */
    val retryDelay: Double = 1.0,
    /** From the win to the next level. */
    val nextLevelDelay: Double = 2.5,

    /** Balls that can exist at once (extra balls come from a pool of this size; nothing is created during play). */
    val maxBalls: Int = 12,
) {
    /** World units per ref unit for a level whose top speed is [maxSpeed]. */
    fun refToWorld(maxSpeed: Double): Double = maxSpeed / maxSpeedRef

    enum class ControlMode {
        /** Touch → drag → delta × sensibility → impulse. The ball does not go to the finger. */
        SWIPE,

        /** Hold the ball and move it round its launch zone; pull away from it and let go to throw it ([PullAim]). */
        HOLD,
    }

    companion object {
        val DEFAULT = GameTuning()

        /** Game seconds per simulation step at normal speed: 120 Hz. */
        const val STEP = 1.0 / 120.0
    }
}
