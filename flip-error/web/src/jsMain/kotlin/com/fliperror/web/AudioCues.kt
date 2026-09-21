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
                // --- THE ABYSS ----------------------------------------------
                //
                // World 3 has no recordings of its own: the pack predates it, and
                // inventing sounds is what the pack-only rule exists to stop. What
                // it has instead is seven short hazard SFX whose names say where
                // they were first used rather than what they sound like - see the
                // alias table in AudioMap, which is the whole of the reuse.
                //
                // An arm coming up out of the floor, on the edge, not the state.
                Look.TENTACLE -> {
                    edge(key + "/warm", hz.warmAt(t) > 0.0) {
                        Audio.hazardCue(AudioMap.ABYSS_JELLY_WARN, vol * 0.55)
                    }
                    edge(key + "/up", hz.activeAt(t)) {
                        Audio.hazardCue(AudioMap.ABYSS_ARM, vol)
                    }
                }
                // A jelly has the same two moments a beam has, and for the same
                // reason: the swell is a warning and the open is a fact.
                Look.JELLY -> {
                    edge(key + "/warm", hz.warmAt(t) > 0.0) {
                        Audio.hazardCue(AudioMap.ABYSS_JELLY_WARN, vol * 0.8)
                    }
                    edge(key + "/open", hz.activeAt(t)) {
                        Audio.hazardCue(AudioMap.ABYSS_JELLY_OPEN, vol)
                    }
                }
                // A split bubble is the one abyss event with a moment in the
                // middle of it: the parent stops being there, and that is when it
                // is heard. activeAt is false from the split onward, so the edge
                // is taken on the way DOWN - hence the negation.
                Look.BUBBLE -> if (hz.pulses) {
                    edge(key + "/split", !hz.activeAt(t)) {
                        Audio.hazardCue(AudioMap.ABYSS_SPLIT, vol)
                    }
                } else {
                    // A still or chasing bubble does not switch; the edge is the
                    // runner coming within earshot of it, said once per pass.
                    edge(key, ahead < earshot * 0.5) {
                        Audio.hazardCue(AudioMap.ABYSS_SWELL, vol * 0.5)
                    }
                }
                // The three that travel or swing: announced on approach, once.
                Look.WAVE -> edge(key, ahead < earshot * 0.55) {
                    Audio.hazardCue(AudioMap.ABYSS_SWELL, vol * 0.85)
                }
                Look.RING -> edge(key, ahead < earshot * 0.5) {
                    Audio.hazardCue(AudioMap.ABYSS_RING, vol * 0.8)
                }
                Look.CRYSTAL -> edge(key, ahead < earshot * 0.45) {
                    Audio.hazardCue(AudioMap.ABYSS_CRYSTAL, vol * 0.6)
                }
                // --- CLOCKWORK ------------------------------------------------
                //
                // The machine's parts are mostly MOTION rather than blink, so
                // there is no activeAt to take an edge from. What there is
                // instead is a stroke: a ram, a gate and a press are near the
                // end of their travel or they are not, and that is the moment
                // worth a sound. [atStroke] answers it from the hazard's own
                // resting box and reach, so a part that is moved or resized in a
                // level file keeps its cue without anybody remembering to.
                Look.PISTON, Look.CRUSHER -> {
                    edge(key + "/warn", atStroke(hz, t, 0.55)) {
                        Audio.hazardCue(AudioMap.MACHINE_WARN, vol * 0.55)
                    }
                    edge(key + "/hit", atStroke(hz, t, 0.92)) {
                        Audio.hazardCue(AudioMap.MACHINE_IMPACT, vol)
                    }
                }
                Look.SHUTTER -> edge(key + "/shut", atStroke(hz, t, 0.85)) {
                    Audio.hazardCue(AudioMap.MACHINE_GATE, vol * 0.85)
                }
                // A vent has the two moments a beam has, for the same reason.
                Look.STEAM -> {
                    edge(key + "/warm", hz.warmAt(t) > 0.0) {
                        Audio.hazardCue(AudioMap.MACHINE_WARN, vol * 0.6)
                    }
                    edge(key + "/up", hz.activeAt(t)) {
                        Audio.hazardCue(AudioMap.MACHINE_STEAM, vol)
                    }
                }
                Look.RAIL -> {
                    edge(key + "/warm", hz.warmAt(t) > 0.0) {
                        Audio.hazardCue(AudioMap.MACHINE_WARN, vol * 0.7)
                    }
                    edge(key + "/live", hz.activeAt(t)) {
                        Audio.hazardCue(AudioMap.MACHINE_LIVE, vol * 0.9)
                    }
                }
                Look.BOLT -> if (hz.pulses) {
                    edge(key, hz.activeAt(t)) { Audio.hazardCue(AudioMap.MACHINE_IMPACT, vol) }
                } else Unit
                // Teeth and weights do not switch; the edge is the runner coming
                // within earshot, said once per pass.
                Look.GEAR, Look.CYLINDER -> edge(key, ahead < earshot * 0.45) {
                    Audio.hazardCue(AudioMap.MACHINE_GEAR, vol * 0.7)
                }
                Look.CHAIN -> edge(key, ahead < earshot * 0.5) {
                    Audio.hazardCue(AudioMap.MACHINE_CHAIN, vol * 0.8)
                }
                // --- OVERGROWTH ----------------------------------------------
                //
                // The forest's parts mostly switch rather than travel, so their
                // edges are the ones a blink gives: swelling, then open. A
                // flower gets both because both are moments the player reads -
                // the throat brightening is the warning and the snap is the
                // fact - and the rest get the one that matters.
                Look.FLOWER -> {
                    edge(key + "/warm", hz.warmAt(t) > 0.0) {
                        Audio.hazardCue(AudioMap.FOREST_WARN, vol * 0.75)
                    }
                    edge(key + "/snap", hz.activeAt(t)) {
                        Audio.hazardCue(AudioMap.FOREST_SNAP, vol)
                    }
                }
                Look.ROOT -> {
                    edge(key + "/warm", hz.warmAt(t) > 0.0) {
                        Audio.hazardCue(AudioMap.FOREST_WARN, vol * 0.5)
                    }
                    // A wall of roots is many boxes on one blink, so its voice is
                    // the wall's rather than each root's - keyed on the blink's
                    // own phase, which every box in it shares.
                    edge(key + "/up", hz.activeAt(t)) {
                        Audio.hazardCue(
                            if (hz.kind == com.fliperror.core.HazardKind.SPIKE_DOWN)
                                AudioMap.FOREST_WALL else AudioMap.FOREST_ROOT, vol * 0.85)
                    }
                }
                Look.THORN -> {
                    edge(key + "/warm", hz.warmAt(t) > 0.0) {
                        Audio.hazardCue(AudioMap.FOREST_WARN, vol * 0.6)
                    }
                    edge(key + "/burst", hz.activeAt(t)) {
                        Audio.hazardCue(AudioMap.FOREST_IMPACT, vol * 0.9)
                    }
                }
                Look.PULSE -> {
                    edge(key + "/warm", hz.warmAt(t) > 0.0) {
                        Audio.hazardCue(AudioMap.FOREST_WARN, vol * 0.5)
                    }
                    edge(key + "/beat", hz.activeAt(t)) {
                        Audio.hazardCue(AudioMap.FOREST_SNAP, vol * 0.7)
                    }
                }
                Look.SEED -> if (hz.pulses) {
                    edge(key, hz.activeAt(t)) { Audio.hazardCue(AudioMap.FOREST_IMPACT, vol) }
                } else if (hz.moves) {
                    edge(key, ahead < earshot * 0.5) {
                        Audio.hazardCue(AudioMap.FOREST_SWEEP, vol * 0.7)
                    }
                } else Unit
                // A bloom grows rather than switching, so there is no edge to
                // take from a blink - the runner coming within earshot is it.
                Look.BLOOM -> edge(key, ahead < earshot * 0.5) {
                    Audio.hazardCue(AudioMap.FOREST_BLOOM, vol * 0.8)
                }
                Look.VINE -> edge(key, ahead < earshot * 0.45) {
                    Audio.hazardCue(AudioMap.FOREST_SWEEP, vol * 0.6)
                }
                Look.SPORE -> edge(key, ahead < earshot * 0.5) {
                    Audio.hazardCue(AudioMap.FOREST_SWEEP, vol * 0.45)
                }
                // An orb and a drifting shard are quiet. Everything in front of
                // the runner having a voice is the same as nothing having one.
                Look.SPIKE, Look.RELIC, Look.ORB, Look.SHARD -> Unit
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

    /**
     * Is this part [how] of the way to the end of its stroke, and heading there?
     *
     * A ram, a gate and a press are pure motion - there is no on/off to watch -
     * so the sound has to come off the travel itself. The reach is the hazard's
     * own, and the direction matters: a part on its way back up is not an
     * impact, and firing on both halves of the cycle is how a machine ends up
     * clattering twice per stroke.
     */
    private fun atStroke(hz: com.fliperror.core.Hazard, t: Double, how: Double): Boolean {
        val m = hz.motion ?: return false
        if (m.reachY <= 0.0) return false
        val now = m.offsetY(t)
        val was = m.offsetY(t - 1.0 / 60.0)
        val down = hz.kind == com.fliperror.core.HazardKind.SPIKE_DOWN
        // Down-hanging parts travel to -reach; floor parts travel to +reach.
        val toward = if (down) -m.reachY else m.reachY
        val progress = now / toward
        val closing = if (down) now < was else now > was
        return closing && progress >= how
    }

    /** Fire [onRise] the first frame [now] becomes true, and not again until it
     *  has been false. The whole reason this class does not scream. */
    private inline fun edge(key: String, now: Boolean, onRise: () -> Unit) {
        val before = was[key] ?: false
        if (now && !before) onRise()
        was[key] = now
    }
}
