package com.fliperror.web

import com.fliperror.core.Game
import com.fliperror.core.Look
import com.fliperror.core.Surface

/**
 * The level's own voice: every World 2 obstacle announced by the recording that
 * belongs to it, at the moment it matters.
 *
 * This exists because sound in this game is not decoration, it is information.
 * A beam that charges silently is a beam the player has to be already looking at;
 * a beam that winds up audibly can be heard coming while the eyes are somewhere
 * else, which on a level with three things moving at once is the difference
 * between reading it and guessing.
 *
 * Two rules keep it from becoming noise:
 *
 *  EDGES, NOT STATES. Every cue fires on a TRANSITION - a beam beginning to
 *  charge, a geyser erupting, a bridge starting to fade - and each one is
 *  remembered so it cannot retrigger while the condition holds. Polling a state
 *  every frame and playing a sound each time it is true is how an engine ends up
 *  screaming.
 *
 *  ONLY WHAT IS NEAR. Nothing more than a screen ahead is audible, and volume
 *  falls off with distance, so the ear is told about the thing being run at
 *  rather than about the whole level at once.
 */
class AudioCues {

    /** What each watched thing was doing last frame, keyed by its own position. */
    private val was = HashMap<String, Boolean>()
    private var lastWindZone = -1
    private var lastX = 0.0

    /** How far ahead a thing can be and still be worth hearing, in world units. */
    private val earshot = 26.0

    fun reset() {
        was.clear()
        lastWindZone = -1
        lastX = 0.0
    }

    /** Fired once per frame. Cheap: it only looks at what is near the runner. */
    fun frame(g: Game) {
        if (!Audio.samplesReady) return
        // A retry rewinds x; forget everything rather than carry stale edges over
        // into a run that has not happened yet.
        if (g.x < lastX - 1.0) reset()
        lastX = g.x

        val t = g.elapsed
        val level = g.level

        level.forEachHazardNear(g.x - 2.0, g.x + earshot) { hz ->
            val box = hz.drawBoxAt(t)
            val ahead = box.x0 - g.x
            if (ahead < -2.0 || ahead > earshot) return@forEachHazardNear
            // Near things are loud, far things are hints.
            val near = (1.0 - (ahead.coerceAtLeast(0.0) / earshot)).coerceIn(0.0, 1.0)
            val vol = 0.25 + 0.65 * near * near
            val key = "${hz.look}@${hz.x0}"

            when (hz.look) {
                // A beam has two moments and they are different sounds: the wind-up,
                // which is a warning, and the strike, which is a fact.
                Look.LASER -> {
                    edge(key + "/warm", hz.warmAt(t) > 0.0) {
                        Audio.hazardCue(AudioMap.LASER_CHARGE, vol * 0.9)
                    }
                    edge(key + "/on", hz.activeAt(t)) {
                        Audio.hazardCue(AudioMap.LASER_BLAST, vol)
                    }
                }
                Look.GEYSER -> edge(key, hz.activeAt(t)) {
                    Audio.hazardCue(AudioMap.SAND_GEYSER, vol)
                }
                Look.RUIN -> edge(key, hz.activeAt(t)) {
                    Audio.hazardCue(AudioMap.FALLING_RUIN, vol)
                }
                // A crest and a boulder do not switch on, so the edge is the runner
                // coming within earshot of one - said once per pass, on approach.
                Look.SAND_WAVE, Look.BOULDER -> edge(key, ahead < earshot * 0.55) {
                    Audio.hazardCue(AudioMap.SAND_WAVE, vol * 0.8)
                }
                // The abyss has no recordings of its own - the pack predates it -
                // and inventing sounds for it is what the pack-only rule forbids.
                // Its obstacles are read rather than heard, which is what the world
                // is about anyway.
                Look.SPIKE, Look.RELIC, Look.BUBBLE, Look.ORB,
                Look.TENTACLE, Look.WALL, Look.MINE, Look.CURRENT -> Unit
            }
        }

        // A span that is about to stop being floor says so while the runner is
        // still standing on it. strengthAt falls to zero as it goes, so the cue
        // lands during the fade rather than after the drop.
        level.forEachSolidNear(g.x - 2.0, g.x + earshot) { sd ->
            val blink = sd.blink ?: return@forEachSolidNear
            if (sd.surface != Surface.BRIDGE && sd.surface != Surface.MIRAGE) return@forEachSolidNear
            val ahead = sd.x0At(t) - g.x
            if (ahead > earshot) return@forEachSolidNear
            val going = blink.solidAt(t) && blink.strengthAt(t) < 0.45
            edge("bridge@${sd.x0}/${(t / 4.0).toInt()}", going) {
                Audio.hazardCue(AudioMap.COLLAPSE_BRIDGE, 0.55)
            }
        }

        // Wind is a place rather than an event: it speaks when the runner enters
        // the column, and stays quiet however long they are inside it.
        val zone = level.winds.indexOfFirst { it.covers(g.x, g.x + 1.0) }
        if (zone != lastWindZone) {
            lastWindZone = zone
            if (zone >= 0) Audio.hazardCue(AudioMap.WIND_BLAST, 0.7)
        }
    }

    /** Fire [onRise] the first frame [now] becomes true, and not again until it
     *  has been false. The whole reason this class does not scream. */
    private inline fun edge(key: String, now: Boolean, onRise: () -> Unit) {
        val before = was[key] ?: false
        if (now && !before) onRise()
        was[key] = now
    }
}
