package com.carom.game

import com.carom.game.audio.Synth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** The three sounds of the three-press restart are original, short, quiet, and a family: 1 < 2 < 3 in length and weight, never harsh. */
class RestartTapSoundsTest {

    private val sounds = listOf(Synth.restartTap1(), Synth.restartTap2(), Synth.restartTap3())

    private fun seconds(pcm: ShortArray) = pcm.size / Synth.SAMPLE_RATE.toDouble()
    private fun peak(pcm: ShortArray) = pcm.maxOf { abs(it.toInt()) } / 32768.0
    private fun rms(pcm: ShortArray) = sqrt(pcm.sumOf { (it / 32768.0) * (it / 32768.0) } / pcm.size)

    @Test
    fun theyAreShortAndGrowFromOneToThree() {
        val (a, b, c) = sounds
        assertTrue("tap 1 is a blip: ${seconds(a)}", seconds(a) in 0.05..0.2)
        assertTrue("tap 2: ${seconds(b)}", seconds(b) in 0.08..0.25)
        assertTrue("tap 3 is the longest but still short: ${seconds(c)}", seconds(c) in 0.3..0.6)
        assertTrue(seconds(a) < seconds(b) && seconds(b) < seconds(c))
    }

    @Test
    fun theyAreQuietAndTheLastIsTheFullest() {
        for (s in sounds) assertTrue("never loud: peak ${peak(s)}", peak(s) < 0.7)
        val (a, b, c) = sounds
        assertTrue("1 is lighter than 2: ${rms(a)} vs ${rms(b)}", rms(a) < rms(b) * 1.15 || peak(a) < peak(b))
        assertTrue("3 has the most body: ${rms(c)} vs ${rms(a)}", rms(c) > rms(a) * 0.6)
        assertTrue("1 is the lightest of the three (peak)", peak(a) <= peak(b) && peak(b) <= peak(c))
    }

    @Test
    fun theyEndInSilenceAndStartWithoutAClick() {
        for (s in sounds) {
            assertTrue("it ends at nothing", abs(s.last().toInt()) < 40)
            assertTrue("it does not begin with a jump", abs(s.first().toInt()) < 3000)
        }
    }

    @Test
    fun theyAreThreeDifferentSounds() {
        assertTrue(!sounds[0].contentEquals(sounds[1]) && !sounds[1].contentEquals(sounds[2]) && !sounds[0].contentEquals(sounds[2]))
    }

    @Test
    fun theyAreMadeTheSameWayEveryTime() {
        assertTrue(Synth.restartTap1().contentEquals(Synth.restartTap1()))
        assertTrue(Synth.restartTap3().contentEquals(Synth.restartTap3()))
        assertEquals(sounds[2].size, Synth.restartTap3().size)
    }
}
