package com.carom.core.audio

import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WavFileTest {

    /** A WAV file of 16-bit [frames] (each an array of channel samples), with an unknown chunk before the data. */
    private fun wav(rate: Int, channels: Int, frames: List<IntArray>): ByteArray {
        val out = ByteArrayOutputStream()
        fun i16(v: Int) { out.write(v and 255); out.write(v shr 8 and 255) }
        fun i32(v: Int) { i16(v and 0xFFFF); i16(v ushr 16) }
        val data = frames.size * channels * 2
        out.write("RIFF".toByteArray()); i32(4 + 26 + 8 + 3 + 1 + 8 + data)
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); i32(16); i16(1); i16(channels); i32(rate); i32(rate * channels * 2); i16(channels * 2); i16(16)
        out.write("LIST".toByteArray()); i32(3); out.write(byteArrayOf(1, 2, 3, 0)) // odd size, padded
        out.write("data".toByteArray()); i32(data)
        for (f in frames) for (s in f) i16(s)
        return out.toByteArray()
    }

    @Test
    fun readsMonoSamplesAndKeepsTheRate() {
        val pcm = WavFile.decode(wav(48000, 1, listOf(intArrayOf(0), intArrayOf(1000), intArrayOf(-32768), intArrayOf(32767))))
        assertEquals(48000, pcm.sampleRate)
        assertArrayEquals(shortArrayOf(0, 1000, -32768, 32767), pcm.samples)
    }

    @Test
    fun mixesStereoDownToOne() {
        val pcm = WavFile.decode(wav(44100, 2, listOf(intArrayOf(1000, 3000), intArrayOf(-2000, 0))))
        assertArrayEquals(shortArrayOf(2000, -1000), pcm.samples)
    }

    @Test
    fun theShippedLaunchSoundReads() {
        val levels = File(System.getProperty("levels.dir") ?: "../game/src/main/assets/levels")
        val file = File(levels.parentFile, "sounds/launch_sfx_heartbeat_soft.wav")
        val pcm = WavFile.decode(file.readBytes())
        assertEquals(48000, pcm.sampleRate)
        assertEquals(8640, pcm.samples.size)
        assertTrue(pcm.samples.any { it > 10000 })
    }
}
