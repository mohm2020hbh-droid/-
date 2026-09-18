package com.fliperror.core

/**
 * The switches a player is allowed to throw. None of them touch difficulty:
 * effects can be turned down for a weak phone and colours can be turned up for
 * a colour-blind one, but the jump arc, the speed and the hitboxes are the same
 * game for everyone.
 */
enum class Lang { EN, AR }

class Settings private constructor(
    /** Everything, including the player's own cues. 0 is a muted game. */
    var master: Int,
    /** Jump, land, death, coins - the sounds the player's own hands make. */
    var sfx: Int,
    /**
     * The room: wind, rumble, hum, and the things happening out of sight.
     *
     * There is no MUSIC control here because there is no music - see Audio. A
     * switch for a thing that does not exist is worse than no switch at all: it
     * tells the player the game has a soundtrack they failed to hear.
     */
    var ambience: Int,
    var vibration: Boolean,
    var reduceEffects: Boolean,
    var colorblind: Boolean,
    var lang: Lang,
    /** Testing only, and off by default: opens every level regardless of progress.
     *  It unlocks doors, it does not touch the game behind them. */
    var unlockAll: Boolean = false,
    /** Testing only: every cosmetic wearable without paying for it. The prices
     *  and the economy are untouched underneath - see Progress.tryOn. */
    var tryAllCosmetics: Boolean = false,
) {
    constructor() : this(FULL, FULL, FULL, true, false, false, Lang.EN, false, false)

    /** Volumes are stored in tenths: a slider a person can actually land on. */
    val masterGain get() = master / 10.0
    val sfxGain get() = sfx / 10.0
    val ambienceGain get() = ambience / 10.0

    fun serialize() =
        "s2|${b(vibration)}${b(reduceEffects)}${b(colorblind)}${b(unlockAll)}${b(tryAllCosmetics)}" +
            "|$master,$sfx,$ambience|${lang.name}"

    private fun b(v: Boolean) = if (v) "1" else "0"

    companion object {
        const val FULL = 10

        private fun vol(s: String?) = s?.toIntOrNull()?.coerceIn(0, FULL) ?: FULL

        fun parse(raw: String?): Settings {
            val fresh = Settings()
            if (raw.isNullOrBlank()) return fresh
            val parts = raw.split('|')
            when {
                // The current shape.
                parts.size >= 4 && parts[0] == "s2" -> {
                    val f = parts[1]
                    if (f.length >= 3) {
                        fresh.vibration = f[0] == '1'
                        fresh.reduceEffects = f[1] == '1'
                        fresh.colorblind = f[2] == '1'
                        if (f.length >= 4) fresh.unlockAll = f[3] == '1'
                        if (f.length >= 5) fresh.tryAllCosmetics = f[4] == '1'
                    }
                    val v = parts[2].split(',')
                    fresh.master = vol(v.getOrNull(0))
                    fresh.sfx = vol(v.getOrNull(1))
                    fresh.ambience = vol(v.getOrNull(2))
                    fresh.lang = Lang.entries.firstOrNull { it.name == parts[3] } ?: Lang.EN
                }
                // What players who have already been here have saved. The old MUSIC
                // switch has nothing to map onto - the game it controlled is gone -
                // so it is dropped, and everything that still means something is kept.
                parts.size >= 3 && parts[0] == "s1" -> {
                    val f = parts[1]
                    if (f.length >= 5) {
                        fresh.sfx = if (f[1] == '1') FULL else 0
                        fresh.vibration = f[2] == '1'
                        fresh.reduceEffects = f[3] == '1'
                        fresh.colorblind = f[4] == '1'
                        if (f.length >= 6) fresh.unlockAll = f[5] == '1'
                    }
                    fresh.lang = Lang.entries.firstOrNull { it.name == parts[2] } ?: Lang.EN
                }
            }
            return fresh
        }
    }
}
