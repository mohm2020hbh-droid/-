package com.fliperror.core

/**
 * All gameplay numbers live here, sourced from docs/FLIP_ERROR_GDD.md section 1.4.
 * Nothing outside this object is allowed to hard-code a gameplay constant.
 *
 * World unit = one player width. Y is up, y = 0 is the base ground surface.
 */
object Tuning {

    // --- Player ---------------------------------------------------------
    const val PLAYER_SIZE = 1.0
    /** GDD: player hitbox is 10% smaller than the drawn shape. */
    const val PLAYER_HITBOX_SCALE = 0.90
    /** GDD: hazard hitbox is 15% smaller than the drawn shape. */
    const val HAZARD_HITBOX_SCALE = 0.85

    // --- Run ------------------------------------------------------------
    const val RUN_SPEED = 9.5            // units / second

    // --- Jump -----------------------------------------------------------
    const val JUMP_APEX = 2.6            // units
    const val RISE_TIME = 0.22           // seconds
    const val FALL_TIME = 0.30           // seconds

    val GRAVITY_RISE = 2.0 * JUMP_APEX / (RISE_TIME * RISE_TIME)   // 107.44 u/s^2
    val GRAVITY_FALL = 2.0 * JUMP_APEX / (FALL_TIME * FALL_TIME)   //  57.78 u/s^2
    val JUMP_VELOCITY = GRAVITY_RISE * RISE_TIME                   //  23.64 u/s
    val AIR_TIME = RISE_TIME + FALL_TIME                           //   0.52 s
    val JUMP_DISTANCE = RUN_SPEED * AIR_TIME                       //   4.94 u

    const val MAX_FALL_SPEED = 45.0

    // --- Double jump -----------------------------------------------------
    //
    // A second tap in the air is a tool, not a parachute. Three limits keep it
    // honest: it must be armed by a real jump (walking off a ledge does not arm
    // it), it cannot fire in the first moments of the jump, so mashing buys
    // nothing, and it closes once the runner is committed to falling, which is
    // also what leaves the landing input buffer intact.
    /** Height the second jump adds, measured from wherever it fires. */
    const val DOUBLE_JUMP_APEX = 2.0
    /** Dead time after take-off. Mashing two taps together is not a super jump. */
    const val DOUBLE_LOCKOUT = 0.080
    /** Once falling faster than this the window is shut and taps buffer for the landing. */
    const val DOUBLE_MIN_VY = -8.0

    val DOUBLE_JUMP_VELOCITY = kotlin.math.sqrt(2.0 * GRAVITY_RISE * DOUBLE_JUMP_APEX)  // 20.73 u/s
    /** How long the window stays open, counted from take-off. */
    val DOUBLE_WINDOW_END = RISE_TIME + (-DOUBLE_MIN_VY) / GRAVITY_FALL                 // 0.358 s

    // --- Forgiveness (hard but fair) ------------------------------------
    const val COYOTE_TIME = 0.060        // GDD 1.4
    const val INPUT_BUFFER = 0.090       // GDD 1.4
    /** Vertical slack before the side of a block counts as a wall crash. */
    const val STEP_TOLERANCE = 0.12

    // --- Simulation -----------------------------------------------------
    /** Fixed physics step. Small enough that nothing tunnels at max fall speed. */
    const val FIXED_DT = 1.0 / 240.0
    /** Never simulate more than this much wall-clock in one frame (spiral-of-death guard). */
    const val MAX_FRAME_DT = 0.10

    /**
     * Clearance over a hazard that still counts as "that was close".
     *
     * Measured, not guessed. On LEVEL 1's verified line the runner passes its
     * ground spikes with 0.054u or 0.110u to spare, and its ceiling corridor
     * with 1.425u; see NearMissTest, which fails if that spread ever moves. A
     * threshold between the two tight groups is what makes the cue mean
     * something: it marks the hair's-breadth passes and ignores the rest.
     */
    const val NEAR_MISS_GAP = 0.08

    // --- Death / restart -------------------------------------------------
    /** GDD 2.3: the whole death effect is 0.25s, then the screen is ready. */
    const val DEATH_EFFECT = 0.25
    /** Falling below this is a pit death. */
    const val KILL_Y = -8.0

    // --- Rhythm ----------------------------------------------------------
    const val BPM = 140.0
    val BEAT = 60.0 / BPM                    // 0.428571 s
    val BAR = 4.0 * BEAT                     // 1.714286 s
    val BEAT_UNITS = RUN_SPEED * BEAT        // 4.0714 u
    val BAR_UNITS = 4.0 * BEAT_UNITS         // 16.2857 u

    // --- Derived helpers --------------------------------------------------
    val PLAYER_HITBOX = PLAYER_SIZE * PLAYER_HITBOX_SCALE
    val PLAYER_INSET = (PLAYER_SIZE - PLAYER_HITBOX) / 2.0
}
