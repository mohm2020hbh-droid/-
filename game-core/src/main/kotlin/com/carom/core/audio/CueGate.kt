package com.carom.core.audio

/**
 * Lets a cue through unless it has already played [AudioCue.burst] times in the last [AudioCue.window] seconds. A stutter, a double tap or a
 * pile-up of the same sound is dropped; real events that follow each other quickly (the three hits of a corner, two balls hitting at once)
 * all sound, because a cue's burst is as large as the real thing needs. Time is in seconds on any one clock.
 */
class CueGate {
    private val plays = Array(AudioCue.entries.size) { DoubleArray(AudioCue.entries[it].burst) { -1e9 } }
    private val next = IntArray(AudioCue.entries.size)

    /** Whether [cue] may sound at [now]; if it may, the play is counted. */
    fun allow(cue: AudioCue, now: Double): Boolean {
        val ring = plays[cue.ordinal]
        val i = next[cue.ordinal]
        // the oldest play in the ring is the one that decides: if it is still inside the window, the burst is full
        if (now - ring[i] < cue.window) return false
        ring[i] = now
        next[cue.ordinal] = (i + 1) % ring.size
        return true
    }

    fun reset() {
        for (r in plays) r.fill(-1e9)
        next.fill(0)
    }
}
