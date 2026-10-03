package com.carom.core.game

import com.carom.core.audio.AudioCue

/**
 * The feedback of the three-press restart. When the player starts pressing restart while the ball is very slow, the three presses count 1 -> 2 -> 3
 * and each has a sound of its own ([AudioCue.RESTART_TAP_1], [AudioCue.RESTART_TAP_2], [AudioCue.RESTART_TAP_3]); the ball is put back at its start
 * on the third, not before. In any other case these sounds are not played and a press restarts the attempt at once, as it always has.
 *
 * This is the only place that decides it, and it is asked only by the restart button's presses: not by a loss, an automatic retry, a start, a win,
 * a collision, or a container releasing balls.
 */
object RestartSound {
    /** The sound of press number [step] (1..3) in a set that began while the ball was very slow ([ballWasVerySlow]); nothing otherwise. */
    fun cueFor(step: Int, ballWasVerySlow: Boolean): AudioCue? = if (!ballWasVerySlow) null else when (step) {
        1 -> AudioCue.RESTART_TAP_1
        2 -> AudioCue.RESTART_TAP_2
        3 -> AudioCue.RESTART_TAP_3
        else -> null
    }

    /** Whether press number [step] puts the ball back at its start now: always, except presses 1 and 2 of a set begun with a very slow ball. */
    fun resetsNow(step: Int, ballWasVerySlow: Boolean): Boolean = !ballWasVerySlow || step >= 3
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

    /** Whether a press at [time] would be the first of a set: nothing counted yet, or too long since the last press. */
    fun startsSet(time: Double): Boolean = count == 0 || time - last > maxGap

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
