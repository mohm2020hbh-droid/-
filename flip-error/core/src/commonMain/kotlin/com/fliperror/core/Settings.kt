package com.fliperror.core

/**
 * The switches a player is allowed to throw. None of them touch difficulty:
 * effects can be turned down for a weak phone and colours can be turned up for
 * a colour-blind one, but the jump arc, the speed and the hitboxes are the same
 * game for everyone.
 */
enum class Lang { EN, AR }

class Settings private constructor(
    var music: Boolean,
    var sfx: Boolean,
    var vibration: Boolean,
    var reduceEffects: Boolean,
    var colorblind: Boolean,
    var lang: Lang,
) {
    constructor() : this(true, true, true, false, false, Lang.EN)

    fun serialize() = "s1|${b(music)}${b(sfx)}${b(vibration)}${b(reduceEffects)}${b(colorblind)}|${lang.name}"

    private fun b(v: Boolean) = if (v) "1" else "0"

    companion object {
        fun parse(raw: String?): Settings {
            val fresh = Settings()
            if (raw.isNullOrBlank()) return fresh
            val parts = raw.split('|')
            if (parts.size < 3 || parts[0] != "s1") return fresh
            val f = parts[1]
            if (f.length >= 5) {
                fresh.music = f[0] == '1'
                fresh.sfx = f[1] == '1'
                fresh.vibration = f[2] == '1'
                fresh.reduceEffects = f[3] == '1'
                fresh.colorblind = f[4] == '1'
            }
            fresh.lang = Lang.entries.firstOrNull { it.name == parts[2] } ?: Lang.EN
            return fresh
        }
    }
}
