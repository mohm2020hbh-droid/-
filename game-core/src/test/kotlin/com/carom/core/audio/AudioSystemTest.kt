package com.carom.core.audio

import com.carom.core.progress.KeyValueStore
import com.carom.core.progress.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The central audio rules: what each sound is, how loud it plays for the player's settings, and how often it may repeat. */
class AudioSystemTest {

    private class MapStore : KeyValueStore {
        val map = HashMap<String, String>()
        override fun getString(key: String) = map[key]
        override fun putString(key: String, value: String) { map[key] = value }
    }

    // ---------------------------------------------------------------- settings

    @Test
    fun theDefaultsAreBalancedAndEverythingIsOn() {
        val a = AudioSettings(MapStore())
        assertEquals(AudioSettings.DEFAULT_MASTER, a.masterVolume, 0.0)
        assertEquals(AudioSettings.DEFAULT_MUSIC, a.musicVolume, 0.0)
        assertEquals(AudioSettings.DEFAULT_SFX, a.sfxVolume, 0.0)
        assertEquals(AudioSettings.DEFAULT_UI, a.uiVolume, 0.0)
        assertTrue(a.musicEnabled && a.sfxEnabled)
        assertTrue("the music sits under the effects", a.musicVolume < a.sfxVolume)
        assertTrue("the interface sits under the effects", a.uiVolume < a.sfxVolume)
    }

    @Test
    fun everyChoiceIsSavedAndComesBackAfterARestart() {
        val store = MapStore()
        val a = AudioSettings(store)
        a.masterVolume = 0.55
        a.musicVolume = 0.2
        a.sfxVolume = 0.9
        a.uiVolume = 0.35
        a.musicEnabled = false
        a.sfxEnabled = false
        val again = AudioSettings(store) // the game was closed and opened
        assertEquals(0.55, again.masterVolume, 1e-9)
        assertEquals(0.2, again.musicVolume, 1e-9)
        assertEquals(0.9, again.sfxVolume, 1e-9)
        assertEquals(0.35, again.uiVolume, 1e-9)
        assertFalse(again.musicEnabled)
        assertFalse(again.sfxEnabled)
        again.musicEnabled = true
        assertTrue(AudioSettings(store).musicEnabled)
        assertFalse("the other switch stays as it was", AudioSettings(store).sfxEnabled)
    }

    @Test
    fun vibrationIsItsOwnSwitchAndSurvivesARestartToo() {
        val store = MapStore()
        Settings(store).hapticsEnabled = false
        assertFalse(Settings(store).hapticsEnabled)
        assertTrue("sound is untouched by it", Settings(store).audio.sfxEnabled)
        Settings(store).hapticsEnabled = true
        assertTrue(Settings(store).hapticsEnabled)
    }

    @Test
    fun volumesStayBetweenZeroAndOne() {
        val a = AudioSettings(MapStore())
        a.masterVolume = 7.0
        assertEquals(1.0, a.masterVolume, 0.0)
        a.masterVolume = -3.0
        assertEquals(0.0, a.masterVolume, 0.0)
        a.sfxVolume = Double.NaN
        assertEquals(0.0, a.sfxVolume, 0.0)
    }

    @Test
    fun theOldSingleSoundSwitchStillCountsUntilTheNewOnesAreSet() {
        val store = MapStore().also { it.putString("settings.sound", "off") }
        val a = AudioSettings(store)
        assertFalse(a.musicEnabled)
        assertFalse(a.sfxEnabled)
        a.musicEnabled = true
        assertTrue(a.musicEnabled)
        assertFalse("only the one that was set changes", a.sfxEnabled)
    }

    @Test
    fun aChangeIsAnnouncedSoThatWhatPlaysFollowsAtOnce() {
        val a = AudioSettings(MapStore())
        var told = 0
        a.onChange = { told++ }
        a.musicVolume = 0.4
        a.musicVolume = 0.4 // the same value again is no change
        a.sfxEnabled = false
        assertEquals(2, told)
    }

    // ---------------------------------------------------------------- gains

    @Test
    fun masterControlsEverythingAndEachBusOnlyItself() {
        val a = AudioSettings(MapStore())
        val bounce = a.gain(AudioCue.BOUNCE)
        val button = a.gain(AudioCue.UI_PRESS)
        val music = a.musicGain()
        a.musicVolume = 0.0
        assertEquals("music volume leaves the effects alone", bounce, a.gain(AudioCue.BOUNCE), 1e-12)
        assertEquals(button, a.gain(AudioCue.UI_PRESS), 1e-12)
        assertEquals(0.0, a.musicGain(), 0.0)
        a.musicVolume = AudioSettings.DEFAULT_MUSIC
        a.sfxVolume = 0.5
        assertEquals(bounce / 2, a.gain(AudioCue.BOUNCE), 1e-12)
        assertEquals("sfx volume leaves the buttons alone", button, a.gain(AudioCue.UI_PRESS), 1e-12)
        assertEquals("...and the music", music, a.musicGain(), 1e-12)
        a.sfxVolume = 1.0
        a.uiVolume = 0.0
        assertEquals(0.0, a.gain(AudioCue.UI_PRESS), 0.0)
        assertEquals(bounce, a.gain(AudioCue.BOUNCE), 1e-12)
        a.uiVolume = AudioSettings.DEFAULT_UI
        a.masterVolume = 0.5
        assertEquals(bounce / 2, a.gain(AudioCue.BOUNCE), 1e-12)
        assertEquals(button / 2, a.gain(AudioCue.UI_PRESS), 1e-12)
        assertEquals(music / 2, a.musicGain(), 1e-12)
        a.masterVolume = 0.0
        for (cue in AudioCue.entries) assertEquals("$cue", 0.0, a.gain(cue), 0.0)
    }

    @Test
    fun theSwitchesSilenceTheirOwnBusOnly() {
        val a = AudioSettings(MapStore())
        a.musicEnabled = false
        assertEquals(0.0, a.musicGain(), 0.0)
        assertTrue(a.gain(AudioCue.BOUNCE) > 0.0)
        a.musicEnabled = true
        a.sfxEnabled = false
        for (cue in AudioCue.entries) assertEquals("$cue", 0.0, a.gain(cue), 0.0)
        assertTrue("the music plays on", a.musicGain() > 0.0)
    }

    @Test
    fun theClearPulseAnswersToMasterAndSfxAndNeverToMusic() {
        val a = AudioSettings(MapStore())
        val full = a.gain(AudioCue.CLEAR_PULSE)
        a.musicVolume = 0.0
        a.musicEnabled = false
        assertEquals(full, a.gain(AudioCue.CLEAR_PULSE), 0.0)
        a.sfxVolume = 0.25
        assertEquals(full * 0.25, a.gain(AudioCue.CLEAR_PULSE), 1e-12)
        a.sfxVolume = 1.0
        a.masterVolume = 0.5
        assertEquals(full * 0.5, a.gain(AudioCue.CLEAR_PULSE), 1e-12)
        a.sfxEnabled = false
        assertEquals(0.0, a.gain(AudioCue.CLEAR_PULSE), 0.0)
    }

    // ---------------------------------------------------------------- the cues

    @Test
    fun theBalanceAtTheDefaults() {
        val a = AudioSettings(MapStore())
        val bounce = a.gain(AudioCue.BOUNCE)
        for (cue in AudioCue.entries.filter { it.bus == AudioBus.UI }) {
            assertTrue("$cue must sit under a collision", a.gain(cue) < bounce * 0.5)
        }
        assertTrue("the music sits well under a collision", a.musicGain() < bounce * 0.8)
        assertTrue("the pulse is clear: not quieter than half a collision", a.gain(AudioCue.CLEAR_PULSE) >= bounce * 0.5)
        assertTrue("...and no louder than one", a.gain(AudioCue.CLEAR_PULSE) <= bounce)
        for (cue in AudioCue.entries) assertTrue("$cue", a.gain(cue) in 0.0..1.0)
    }

    @Test
    fun aCollisionOutranksAnInterfaceSound() {
        assertTrue(AudioCue.BOUNCE.priority > AudioCue.UI_PRESS.priority)
        for (cue in AudioCue.entries.filter { it.bus == AudioBus.UI }) assertTrue("$cue", AudioCue.BOUNCE.priority > cue.priority)
        for (cue in AudioCue.entries.filter { it.category == AudioCategory.GAMEPLAY || it.category == AudioCategory.POWER_UP }) {
            assertTrue("$cue must not be below the buttons", cue.priority > VoicePool.Priority.UI)
        }
    }

    @Test
    fun everySoundBelongsToOneBusAndOneCategoryThatAgree() {
        for (cue in AudioCue.entries) {
            when (cue.category) {
                AudioCategory.UI -> assertEquals("$cue", AudioBus.UI, cue.bus)
                AudioCategory.MUSIC -> assertEquals("$cue", AudioBus.MUSIC, cue.bus)
                else -> assertEquals("$cue", AudioBus.SFX, cue.bus)
            }
        }
        assertEquals(AudioCategory.POWER_UP, AudioCue.CLOCK.category)
        assertEquals(AudioCategory.FEEDBACK, AudioCue.CLEAR_PULSE.category)
        assertEquals(AudioCategory.GAMEPLAY, AudioCue.BOUNCE.category)
        assertTrue("the clock has a cue of its own, not shared with the bounce, the pulse or the buttons", AudioCue.entries.count { it == AudioCue.CLOCK } == 1)
    }

    // ---------------------------------------------------------------- the gate

    @Test
    fun theSameCueIsNotStackedIntoABuzz() {
        val g = CueGate()
        var heard = 0
        for (i in 0 until 40) if (g.allow(AudioCue.UI_PRESS, i * 0.001)) heard++ // forty taps in forty milliseconds
        assertEquals(1, heard)
        assertTrue("a moment later it sounds again", g.allow(AudioCue.UI_PRESS, 0.5))
    }

    @Test
    fun theClearPulseSoundsOncePerSecondAtMost() {
        val g = CueGate()
        assertTrue(g.allow(AudioCue.CLEAR_PULSE, 10.0))
        assertFalse(g.allow(AudioCue.CLEAR_PULSE, 10.4))
        assertFalse(g.allow(AudioCue.CLEAR_PULSE, 10.99))
        assertTrue(g.allow(AudioCue.CLEAR_PULSE, 11.01))
    }

    @Test
    fun realConsecutiveCollisionsAllSoundAndOnlyAPileUpIsDropped() {
        val g = CueGate()
        // the three hits of a corner, a frame apart, and two balls hitting on one frame
        assertTrue(g.allow(AudioCue.BOUNCE, 1.000))
        assertTrue(g.allow(AudioCue.BOUNCE, 1.017))
        assertTrue(g.allow(AudioCue.BOUNCE, 1.033))
        assertTrue(g.allow(AudioCue.BOUNCE, 1.033))
        // a fifth within the same 50 ms is a pile-up
        assertFalse(g.allow(AudioCue.BOUNCE, 1.040))
        // ...but a collision a moment later is a new one
        assertTrue(g.allow(AudioCue.BOUNCE, 1.080))
        // steady play, one every 100 ms for a second: every one sounds
        val steady = CueGate()
        var heard = 0
        for (i in 0 until 10) if (steady.allow(AudioCue.BOUNCE, 5.0 + i * 0.1)) heard++
        assertEquals(10, heard)
    }

    @Test
    fun cuesDoNotGetInEachOthersWay() {
        val g = CueGate()
        assertTrue(g.allow(AudioCue.UI_PRESS, 1.0))
        assertTrue(g.allow(AudioCue.BOUNCE, 1.0))
        assertTrue(g.allow(AudioCue.CLEAR_PULSE, 1.0))
        assertTrue(g.allow(AudioCue.CLOCK, 1.0))
    }

    @Test
    fun resetForgetsEverythingPlayed() {
        val g = CueGate()
        g.allow(AudioCue.CLEAR_PULSE, 5.0)
        g.reset()
        assertTrue(g.allow(AudioCue.CLEAR_PULSE, 5.0))
    }
}
