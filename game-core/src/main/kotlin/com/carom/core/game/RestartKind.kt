package com.carom.core.game

/**
 * Why an attempt was put back to its start. Only one of these is the player asking, three times in a row, to begin the
 * level over from scratch, and only that one has the generator's soft hum ([chargesGenerator]); losing a try, a ball
 * released from a container, the first start of a level and a plain press of the button never do. The sound belongs to
 * this decision and not to whatever function happens to reset the ball.
 */
enum class RestartKind(val chargesGenerator: Boolean) {
    /** The system started the next try by itself after a loss (a collision, the bounces used up, a wrong way, a stop). */
    AUTOMATIC(false),

    /** The player pressed the restart button once or twice: the attempt starts over, quietly. */
    MANUAL(false),

    /** The player asked three times in a row (three quick taps, or three presses of the button): a full manual restart. */
    TRIPLE(true),
}

/**
 * Counts presses of one button: true on the third press that follows the one before within [maxGap] seconds, and the
 * count starts again after it (a fourth press begins a new set). Time is in seconds on one clock.
 */
class PressCounter(private val needed: Int = 3, private val maxGap: Double = 0.7) {
    private var count = 0
    private var last = -1e9

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
