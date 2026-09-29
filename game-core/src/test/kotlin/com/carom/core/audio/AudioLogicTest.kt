package com.carom.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

class AudioLogicTest {

    // ------------------------------------------------------------------ the bounded pool

    @Test
    fun thePoolNeverHoldsMoreThanItsCapacity() {
        val pool = VoicePool(4)
        var granted = 0
        for (i in 0 until 50) if (pool.acquire(0.0 + i * 0.001, 1.0, VoicePool.Priority.BOUNCE) >= 0) granted++
        assertEquals("fifty bounces in a burst, four sources", 4, granted)
        assertEquals(4, pool.busy(0.06))
    }

    @Test
    fun importantSoundsTakeOverFromSmallOnesAndNotTheOtherWayRound() {
        val pool = VoicePool(2)
        val a = pool.acquire(0.0, 1.0, VoicePool.Priority.BOUNCE)
        val b = pool.acquire(0.0, 1.0, VoicePool.Priority.BOUNCE)
        assertTrue(a >= 0 && b >= 0 && a != b)
        val boom = pool.acquire(0.1, 1.0, VoicePool.Priority.EXPLOSION)
        assertTrue("the explosion gets a source by taking one", boom in listOf(a, b))
        assertEquals(boom, pool.lastStolen)
        // Now one bounce and one explosion are playing; another bounce finds nothing to take.
        assertEquals(-1, pool.acquire(0.2, 1.0, VoicePool.Priority.BOUNCE))
        // A second explosion takes the remaining bounce, not the first explosion.
        val second = pool.acquire(0.3, 1.0, VoicePool.Priority.EXPLOSION)
        assertNotEquals(boom, second)
    }

    @Test
    fun aSlotComesBackWhenItsSoundEndsOrIsReleased() {
        val pool = VoicePool(1)
        val s = pool.acquire(0.0, 0.5, VoicePool.Priority.BOUNCE)
        assertEquals(-1, pool.acquire(0.2, 0.5, VoicePool.Priority.BOUNCE))
        assertEquals(s, pool.acquire(0.6, 0.5, VoicePool.Priority.BOUNCE)) // it ended
        pool.release(s)
        assertEquals(s, pool.acquire(0.7, 0.5, VoicePool.Priority.BOUNCE)) // it was let go
    }

    // ------------------------------------------------------------------ beat detection

    /** A kick and a hat every half second (120 bpm) over a quiet noise floor. */
    private fun clickTrack(seconds: Int, rate: Int, bpm: Double): FloatArray {
        val out = FloatArray(seconds * rate)
        val beat = 60.0 / bpm
        var seed = 12345
        for (i in out.indices) {
            seed = seed * 1103515245 + 12345
            out[i] = (((seed ushr 16) and 0x7fff) / 32768f - 0.5f) * 0.02f
        }
        var t = 0.0
        while (t < seconds) {
            val start = (t * rate).toInt()
            for (k in 0 until (0.12 * rate).toInt()) {
                if (start + k >= out.size) break
                val e = exp(-k.toDouble() / rate / 0.04)
                out[start + k] += (0.8 * e * sin(2 * PI * 70.0 * k / rate)).toFloat()
            }
            t += beat
        }
        return out
    }

    @Test
    fun theDetectorFindsTheBeatsOfASteadyPulse() {
        val rate = 22050
        val beats = BeatDetector.detect(clickTrack(8, rate, 120.0), rate)
        assertTrue("about 16 beats in 8 s at 120 bpm, found ${beats.count}", beats.count in 14..17)
        assertEquals(120.0, beats.tempo(), 6.0)
        // and they fall on the beat: within 60 ms of a multiple of half a second
        for (t in beats.times) {
            val off = abs(((t + 0.25) % 0.5) - 0.25)
            assertTrue("beat at $t is $off from the grid", off < 0.06)
        }
    }

    @Test
    fun silenceHasNoBeatsAndThePulseFadesBetweenThem() {
        assertEquals(0, BeatDetector.detect(FloatArray(44100), 22050).count)
        val beats = BeatDetector.Beats(doubleArrayOf(0.5, 1.0), doubleArrayOf(1.0, 1.0))
        assertTrue(BeatDetector.pulse(beats, 0.5, 2.0) > 0.99)
        assertTrue(BeatDetector.pulse(beats, 0.9, 2.0) < BeatDetector.pulse(beats, 0.6, 2.0))
        assertTrue("the last beat of the loop still lights the start of the next", BeatDetector.pulse(beats, 0.0, 2.0) > 0.0)
    }

    // ------------------------------------------------------------------ music folders

    @Test
    fun aLevelTakesItsTrackFromItsWorldsFolderAndHardLevelsFromTheHardcoreOne() {
        val first = MusicLibrary.trackFor(0, 0, false)
        assertEquals("world0", first.folder)
        assertEquals("neighbouring levels share a track", first, MusicLibrary.trackFor(3, 0, false))
        assertEquals("world2", MusicLibrary.trackFor(21, 2, false).folder)
        assertEquals(MusicLibrary.HARDCORE, MusicLibrary.trackFor(21, 2, true).folder)
        assertEquals("worlds past the folders wrap round", "world0", MusicLibrary.trackFor(0, 4, false).folder)
        assertTrue(first.loopSeconds > 5.0)
    }

    @Test
    fun theMusicKeepsPlayingBetweenLevelsOfTheSameTrackAndCrossfadesOtherwise() {
        val a = MusicLibrary.trackFor(0, 0, false)
        val b = MusicLibrary.trackFor(0, 0, true)
        assertTrue(MusicLibrary.change(null, a) is MusicLibrary.Change.Start)
        assertEquals(MusicLibrary.Change.Continue, MusicLibrary.change(a, MusicLibrary.trackFor(2, 0, false)))
        val change = MusicLibrary.change(a, b)
        assertTrue(change is MusicLibrary.Change.Crossfade && change.to == b && change.seconds > 0.5)
    }
}
