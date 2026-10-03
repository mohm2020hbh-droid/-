package com.carom.core.audio

/**
 * A piece of music described as a recipe: tempo, key, and a seed for its variations. The platform turns a recipe
 * into audio (there are no music files); the same recipe always gives the same piece.
 */
data class TrackSpec(
    val id: String,
    /** The folder it lives in: its world's. */
    val folder: String,
    val bpm: Double,
    /** MIDI note of the key's root (57 = A3). */
    val root: Int,
    val minor: Boolean,
    val seed: Int,
    /** 0..1: how busy and bright it is. */
    val intensity: Double,
    /** Length of the loop in bars of four beats. */
    val bars: Int = 8,
) {
    val loopSeconds: Double get() = bars * 4 * 60.0 / bpm
}

/**
 * The music folders and how a level picks its track: each world has a folder of tracks, and a level within a folder
 * takes its track by position, so neighbouring levels of a world share one and the music carries on between them.
 */
object MusicLibrary {
    private fun world(n: Int, tracks: List<TrackSpec>) = "world$n" to tracks

    val folders: Map<String, List<TrackSpec>> = mapOf(
        world(0, listOf(
            TrackSpec("w0a", "world0", 84.0, 57, true, 11, 0.25),
            TrackSpec("w0b", "world0", 90.0, 60, false, 12, 0.3),
        )),
        world(1, listOf(
            TrackSpec("w1a", "world1", 92.0, 55, true, 21, 0.35),
            TrackSpec("w1b", "world1", 98.0, 59, true, 22, 0.4),
        )),
        world(2, listOf(
            TrackSpec("w2a", "world2", 100.0, 52, true, 31, 0.45),
            TrackSpec("w2b", "world2", 104.0, 57, false, 32, 0.5),
        )),
        world(3, listOf(
            TrackSpec("w3a", "world3", 108.0, 50, true, 41, 0.55),
            TrackSpec("w3b", "world3", 112.0, 55, true, 42, 0.6),
        )),
        world(4, listOf(
            TrackSpec("w4a", "world4", 116.0, 53, true, 51, 0.65),
            TrackSpec("w4b", "world4", 120.0, 58, false, 52, 0.7),
        )),
        world(5, listOf(
            TrackSpec("w5a", "world5", 124.0, 51, true, 61, 0.75),
            TrackSpec("w5b", "world5", 128.0, 56, true, 62, 0.8),
        )),
        world(6, listOf(
            TrackSpec("w6a", "world6", 132.0, 55, true, 71, 0.82),
            TrackSpec("w6b", "world6", 136.0, 59, false, 72, 0.86),
        )),
        world(7, listOf(
            TrackSpec("w7a", "world7", 138.0, 52, true, 81, 0.9),
            TrackSpec("w7b", "world7", 142.0, 57, true, 82, 0.95),
        )),
    )

    /** Tracks per position: the number of levels in a row that share a track before the next one takes over. */
    private const val LEVELS_PER_TRACK = 5

    /** The track for the level at [levelIndex] (0-based) in world [world] (0-based); a world beyond the folders wraps round. */
    fun trackFor(levelIndex: Int, world: Int): TrackSpec {
        val folder = folders.getValue("world${Math.floorMod(world, WORLD_FOLDERS)}")
        return folder[(levelIndex / LEVELS_PER_TRACK) % folder.size]
    }

    private const val WORLD_FOLDERS = 8

    /** What to do with the music when a level starts: nothing if the same track is playing, otherwise crossfade. */
    sealed interface Change {
        object Continue : Change
        data class Crossfade(val to: TrackSpec, val seconds: Double) : Change
        data class Start(val track: TrackSpec, val fadeInSeconds: Double) : Change
    }

    fun change(playing: TrackSpec?, wanted: TrackSpec, crossfade: Double = 1.5): Change = when {
        playing == null -> Change.Start(wanted, crossfade)
        playing.id == wanted.id -> Change.Continue
        else -> Change.Crossfade(wanted, crossfade)
    }
}
