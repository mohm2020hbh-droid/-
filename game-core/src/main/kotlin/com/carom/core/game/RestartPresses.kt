package com.carom.core.game

import com.carom.core.audio.AudioCue

/**
 * The feedback of the three-press restart. Pressing restart three times in a row, at any speed of the ball, counts 1 -> 2 -> 3 and each press has a
 * sound of its own ([AudioCue.RESTART_TAP_1], [AudioCue.RESTART_TAP_2], [AudioCue.RESTART_TAP_3]). A press still restarts the attempt at once, as it
 * always has; the sounds are only the feedback of how far the three have got.
 *
 * Asked only by the restart button's presses (and the third sound by the three quick taps on the screen): not by a loss, an automatic retry, a start,
 * a win, a collision, or a container releasing balls.
 */
object RestartSound {
    /** The sound of press number [step] (1..3) of a set of presses; nothing for any other number. */
    fun cueFor(step: Int): AudioCue? = when (step) {
        1 -> AudioCue.RESTART_TAP_1
        2 -> AudioCue.RESTART_TAP_2
        3 -> AudioCue.RESTART_TAP_3
        else -> null
    }
}

/**
 * Counts presses of one button: true on the third press that follows the one before within [maxGap] seconds, and the count starts again after it
 * (a fourth press begins a new set). Time is in seconds on one clock.
 */
class PressCounter(private val needed: Int = 3, private val maxGap: Double = 0.7) {
    private var count = 0
    private var last = -1e9

    /** The number of the latest press in its set (1, 2 or 3), also right after the third one that ends the set. */
    var step = 0
        private set

    fun press(time: Double): Boolean {
        count = if (time - last > maxGap) 1 else count + 1
        last = time
        step = count
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
