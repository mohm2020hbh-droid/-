package com.carom.core.game

/**
 * Why an attempt was put back to its start. Only one of these is the player asking, three times in a row, to begin the level over from scratch;
 * losing a try, a ball released from a container, the first start of a level and a plain press of the button are the others and have no sound of
 * their own. The sound belongs to a decision made here ([RestartSound]) and not to whichever function happens to reset the ball.
 */
enum class RestartKind {
    /** The system started the next try by itself after a loss (a collision, the bounces used up, a wrong way, a stop). */
    AUTOMATIC,

    /** The player pressed the restart button once or twice: the attempt starts over, quietly. */
    MANUAL,

    /** The player asked three times in a row (three quick taps, or three presses of the button): a full manual restart. */
    TRIPLE,
}

/**
 * The one condition that plays the low ball pulse (`low_ball_pulse.wav`): the ball was very slow when the player began asking, and the player
 * completed three presses or taps, so the attempt was put back at its start. Nothing else plays it: not a loss, not an automatic retry, not a
 * start, not a win, not a collision, not one or two presses, and not three presses while the ball is moving at a normal speed.
 */
object RestartSound {
    fun playsLowBallPulse(kind: RestartKind, ballWasVerySlow: Boolean): Boolean = kind == RestartKind.TRIPLE && ballWasVerySlow
}

/**
 * Counts presses of one button: true on the third press that follows the one before within [maxGap] seconds, and the count starts again after it
 * (a fourth press begins a new set). Time is in seconds on one clock.
 */
class PressCounter(private val needed: Int = 3, private val maxGap: Double = 0.7) {
    private var count = 0
    private var last = -1e9

    /** Whether a press at [time] would be the first of a set: nothing counted yet, or too long since the last press. */
    fun startsSet(time: Double): Boolean = count == 0 || time - last > maxGap

    fun press(time: Double): Boolean {
        count = if (time - last > maxGap) 1 else count + 1
        last = time
        if (count >= needed) {
            cancel()
            return true
        }
        return false
    }

    fun cancel() {
        count = 0
        last = -1e9
    }
}
