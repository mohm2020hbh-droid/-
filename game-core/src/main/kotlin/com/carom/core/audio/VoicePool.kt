package com.carom.core.audio

/**
 * The bounded pool of sound sources, as a rule that does not depend on any audio API.
 *
 * A request asks for a slot; the pool gives a free one, and when none is free it takes one from a less
 * important sound that is still playing, or refuses. It never grows: at most [capacity] sounds play at once,
 * however many hits arrive, and the important ones (a ball breaking, an explosion, the exit) win over the
 * many small ones (bounces).
 *
 * Time is whatever clock the caller uses, in seconds; a sound holds its slot for the [duration] it was
 * given (or until [release]). Nothing here allocates after construction.
 */
class VoicePool(val capacity: Int) {
    private val until = DoubleArray(capacity)
    private val priority = IntArray(capacity)
    private val started = DoubleArray(capacity)

    /**
     * A slot for a sound of [priority] (higher matters more) lasting [duration] seconds starting at [now], or
     * -1 if the pool is full of sounds that matter at least as much. If another sound had to be dropped for this
     * one, its slot number is written to [stolen] as the return of [lastStolen].
     */
    fun acquire(now: Double, duration: Double, priority: Int): Int {
        lastStolen = -1
        var free = -1
        var weakest = -1
        for (i in 0 until capacity) {
            if (until[i] <= now) {
                if (free < 0) free = i
            } else if (weakest < 0 || this.priority[i] < this.priority[weakest] ||
                (this.priority[i] == this.priority[weakest] && started[i] < started[weakest])
            ) {
                weakest = i
            }
        }
        val slot = when {
            free >= 0 -> free
            weakest >= 0 && this.priority[weakest] < priority -> weakest.also { lastStolen = it }
            else -> return -1
        }
        until[slot] = now + duration
        this.priority[slot] = priority
        started[slot] = now
        return slot
    }

    /** The slot the last successful [acquire] took over from a lower-priority sound, or -1 if it took a free one. */
    var lastStolen = -1
        private set

    /** The sound in [slot] has ended early. */
    fun release(slot: Int) {
        if (slot in 0 until capacity) until[slot] = 0.0
    }

    /** How many slots are in use at [now]. */
    fun busy(now: Double): Int {
        var n = 0
        for (i in 0 until capacity) if (until[i] > now) n++
        return n
    }

    fun clear() {
        until.fill(0.0)
    }

    /** How important each kind of sound is; when the pool is full, the more important one plays. */
    object Priority {
        const val BOUNCE = 1
        const val UI = 2
        const val PORTAL = 4
        const val CLOCK = 5
        const val EXIT_PARTIAL = 6
        const val EXPLOSION = 8
        const val EXIT_COMPLETE = 9
    }
}
