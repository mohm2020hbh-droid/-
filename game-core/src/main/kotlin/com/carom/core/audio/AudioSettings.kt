package com.carom.core.audio

import com.carom.core.progress.KeyValueStore

/**
 * The player's audio choices, kept in one place and saved as they are changed: four volumes (Master over Music, Sfx and Ui, each 0..1) and the
 * switches for the music and for the sound effects (the effects switch covers the interface sounds too). Vibration is the game's other feedback
 * and has its own switch in [com.carom.core.progress.Settings].
 *
 * Every sound's gain is worked out here: master x its bus's volume x the cue's own level, and nothing at all when its bus is switched off.
 * [onChange] is told after every change so what is playing (the music) follows at once.
 */
class AudioSettings(private val store: KeyValueStore) {

    var onChange: (() -> Unit)? = null

    var masterVolume: Double
        get() = read(KEY_MASTER, DEFAULT_MASTER)
        set(value) = write(KEY_MASTER, value)

    var musicVolume: Double
        get() = read(KEY_MUSIC_VOLUME, DEFAULT_MUSIC)
        set(value) = write(KEY_MUSIC_VOLUME, value)

    var sfxVolume: Double
        get() = read(KEY_SFX_VOLUME, DEFAULT_SFX)
        set(value) = write(KEY_SFX_VOLUME, value)

    var uiVolume: Double
        get() = read(KEY_UI_VOLUME, DEFAULT_UI)
        set(value) = write(KEY_UI_VOLUME, value)

    var musicEnabled: Boolean
        get() = flag(KEY_MUSIC_ON)
        set(value) = writeFlag(KEY_MUSIC_ON, value)

    var sfxEnabled: Boolean
        get() = flag(KEY_SFX_ON)
        set(value) = writeFlag(KEY_SFX_ON, value)

    fun volumeOf(bus: AudioBus): Double = when (bus) {
        AudioBus.MUSIC -> musicVolume
        AudioBus.SFX -> sfxVolume
        AudioBus.UI -> uiVolume
    }

    fun isEnabled(bus: AudioBus): Boolean = if (bus == AudioBus.MUSIC) musicEnabled else sfxEnabled

    /** How loud [cue] plays now, 0..1: nothing if its bus is off, otherwise master x bus volume x the cue's level. */
    fun gain(cue: AudioCue): Double = if (isEnabled(cue.bus)) masterVolume * volumeOf(cue.bus) * cue.level else 0.0

    /** How loud the music plays now relative to its own level, 0..1: master x music volume, or nothing when the music is off. */
    fun musicGain(): Double = if (musicEnabled) masterVolume * musicVolume else 0.0

    private fun read(key: String, default: Double): Double = (store.getString(key)?.toDoubleOrNull() ?: default).coerceIn(0.0, 1.0)

    private fun write(key: String, value: Double) {
        val v = if (value.isNaN()) 0.0 else value.coerceIn(0.0, 1.0)
        if (store.getString(key)?.toDoubleOrNull() == v) return
        store.putString(key, v.toString())
        onChange?.invoke()
    }

    /** A switch is on unless it was saved as off; the single sound switch of earlier versions still counts until the player sets the new ones. */
    private fun flag(key: String): Boolean = (store.getString(key) ?: store.getString(KEY_LEGACY_SOUND)) != "off"

    private fun writeFlag(key: String, value: Boolean) {
        if (flag(key) == value && store.getString(key) != null) return
        store.putString(key, if (value) "on" else "off")
        onChange?.invoke()
    }

    companion object {
        const val DEFAULT_MASTER = 1.0
        const val DEFAULT_MUSIC = 0.7
        const val DEFAULT_SFX = 1.0
        const val DEFAULT_UI = 0.7

        private const val KEY_MASTER = "audio.master"
        private const val KEY_MUSIC_VOLUME = "audio.music"
        private const val KEY_SFX_VOLUME = "audio.sfx"
        private const val KEY_UI_VOLUME = "audio.ui"
        private const val KEY_MUSIC_ON = "audio.musicOn"
        private const val KEY_SFX_ON = "audio.sfxOn"
        private const val KEY_LEGACY_SOUND = "settings.sound"
    }
}
