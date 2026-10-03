package com.carom.game

import com.carom.core.audio.AudioCue
import com.carom.core.audio.AudioSettings
import com.carom.core.audio.MusicLibrary
import com.carom.core.audio.TrackSpec
import com.carom.game.audio.AudioManager
import com.carom.game.audio.MusicController
import com.carom.game.audio.SoundOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The audio manager is the one door for sound: gains come from the settings, pile-ups are dropped, the music follows the settings at once. */
class AudioManagerTest {

    private class Out : SoundOutput {
        val plays = ArrayList<Pair<AudioCue, Float>>()
        val stopped = ArrayList<AudioCue>()
        var pitchSeen = 1f
        override fun play(cue: AudioCue, volume: Float) { plays += cue to volume }
        override fun stop(cue: AudioCue) { stopped += cue }
        override fun setPitch(scale: Float) { pitchSeen = scale }
        override fun release() {}
    }

    private class Music : MusicController {
        val log = ArrayList<String>()
        var gainSeen = -1f
        var enabledSeen = true
        override fun play(track: TrackSpec, fade: Float) { log += "play ${track.id} $fade" }
        override fun stop(fade: Float) { log += "stop $fade" }
        override fun pause() { log += "pause" }
        override fun resume() { log += "resume" }
        override fun setGain(gain: Float) { gainSeen = gain }
        override fun setEnabled(on: Boolean) { enabledSeen = on }
        override fun setPitch(scale: Float) {}
        override fun beatPulse() = 0.5f
        override fun release() { log += "release" }
    }

    private class Rig {
        val settings = AudioSettings(MapStore())
        val out = Out()
        val music = Music()
        var now = 100.0
        val audio = AudioManager(settings, out, music) { now }
        fun advance(seconds: Double) { now += seconds }
    }

    @Test
    fun aCueIsPlayedAtMasterTimesItsBusTimesItsOwnLevel() {
        val r = Rig()
        r.settings.masterVolume = 0.5
        r.settings.uiVolume = 0.8
        r.audio.play(AudioCue.UI_PRESS)
        assertEquals(1, r.out.plays.size)
        assertEquals((0.5 * 0.8 * AudioCue.UI_PRESS.level).toFloat(), r.out.plays[0].second, 1e-6f)
    }

    @Test
    fun aSwitchedOffBusMakesNoSoundAndNothingReachesTheOutput() {
        val r = Rig()
        r.settings.sfxEnabled = false
        for (cue in AudioCue.entries) { r.audio.play(cue); r.advance(2.0) }
        assertTrue(r.out.plays.isEmpty())
        r.settings.sfxEnabled = true
        r.settings.sfxVolume = 0.0
        r.audio.play(AudioCue.BOUNCE)
        assertTrue("a volume of zero is silence too", r.out.plays.isEmpty())
    }

    @Test
    fun theMusicVolumeLeavesTheEffectsAlone() {
        val r = Rig()
        r.settings.musicVolume = 0.0
        r.audio.play(AudioCue.BOUNCE)
        assertEquals(1, r.out.plays.size)
        assertEquals(1f, r.out.plays[0].second, 1e-6f)
    }

    @Test
    fun theClearPulseAnswersToMasterAndEffectsAndPlaysOnceAtItsOwnLevel() {
        val r = Rig()
        r.settings.musicEnabled = false
        r.settings.musicVolume = 0.0
        r.audio.play(AudioCue.CLEAR_PULSE)
        assertEquals(1f, r.out.plays.single().second, 1e-6f)
        r.advance(2.0)
        r.settings.masterVolume = 0.5
        r.settings.sfxVolume = 0.5
        r.audio.play(AudioCue.CLEAR_PULSE)
        assertEquals(0.25f, r.out.plays.last().second, 1e-6f)
        r.audio.play(AudioCue.CLEAR_PULSE) // a second one at once is a pile-up
        assertEquals(2, r.out.plays.size)
    }

    @Test
    fun aPileUpOfTheSameButtonIsOneSoundButRealCollisionsAllSound() {
        val r = Rig()
        repeat(30) { r.audio.play(AudioCue.UI_PRESS); r.advance(0.001) }
        assertEquals(1, r.out.plays.count { it.first == AudioCue.UI_PRESS })
        val corner = Rig()
        repeat(3) { corner.audio.play(AudioCue.BOUNCE, 0.8); corner.advance(1 / 60.0) }
        assertEquals(3, corner.out.plays.size)
    }

    @Test
    fun aHarderHitIsLouderAndAClockSpeaksAtItsOwnDepth() {
        val r = Rig()
        r.audio.play(AudioCue.BOUNCE, 0.0)
        r.advance(1.0)
        r.audio.play(AudioCue.BOUNCE, 1.0)
        assertEquals(0.7f, r.out.plays[0].second, 1e-6f)
        assertEquals(1.0f, r.out.plays[1].second, 1e-6f)
        r.advance(1.0)
        r.audio.play(AudioCue.CLOCK, 1.0)
        r.advance(1.0)
        r.audio.play(AudioCue.CLOCK, 0.0)
        assertTrue(r.out.plays[2].second > r.out.plays[3].second)
        assertTrue("the clock is its own cue, not the bounce or a button", r.out.plays.map { it.first }.toSet().containsAll(listOf(AudioCue.CLOCK, AudioCue.BOUNCE)))
    }

    @Test
    fun theMusicFollowsTheSettingsTheMomentTheyChange() {
        val r = Rig()
        assertEquals(r.settings.musicGain().toFloat(), r.music.gainSeen, 1e-6f)
        r.settings.musicVolume = 0.3
        assertEquals(0.3f, r.music.gainSeen, 1e-6f)
        r.settings.masterVolume = 0.5
        assertEquals(0.15f, r.music.gainSeen, 1e-6f)
        r.settings.musicEnabled = false
        assertEquals(false, r.music.enabledSeen)
        assertEquals(0f, r.music.gainSeen, 0f)
        r.settings.musicEnabled = true
        assertEquals(true, r.music.enabledSeen)
        assertTrue("a sound-effects switch does not touch the music", run { r.settings.sfxEnabled = false; r.music.enabledSeen })
    }

    @Test
    fun theMusicIsPlayedPausedResumedAndStoppedThroughTheManager() {
        val r = Rig()
        val track = MusicLibrary.trackFor(0, 0)
        r.audio.playMusic(track)
        r.audio.pauseMusic()
        r.audio.resumeMusic()
        r.audio.stopMusic(0.8f)
        assertEquals(listOf("play ${track.id} 1.5", "pause", "resume", "stop 0.8"), r.music.log)
    }

    @Test
    fun theBeatPulseIsSilentWhenTheMusicIs() {
        val r = Rig()
        assertEquals(0.5f, r.audio.beatPulse, 0f)
        r.settings.musicEnabled = false
        assertEquals(0f, r.audio.beatPulse, 0f)
    }

    @Test
    fun stoppingACueReachesTheOutput() {
        val r = Rig()
        r.audio.stop(AudioCue.SUCCESS_SPIN)
        assertEquals(listOf(AudioCue.SUCCESS_SPIN), r.out.stopped)
    }
}
