package com.carom.core.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** `clear_pulse.wav` is used exactly as the player supplied it: same bytes, short, clear but not harsh; and the file it replaced is gone. */
class ClearPulseSoundTest {

    private val sounds = File(File(System.getProperty("levels.dir") ?: "../game/src/main/assets/levels").parentFile, "sounds")
    private val file = File(sounds, "clear_pulse.wav")

    @Test
    fun theFileIsTheOneThatWasSupplied() {
        assertTrue("missing: ${file.absolutePath}", file.exists())
        val sha = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals("the pulse must be the supplied file, byte for byte", SUPPLIED_SHA256, sha)
    }

    @Test
    fun theEarlierPulseFileIsNotShippedAnymore() {
        assertFalse(File(sounds, "low_ball_pulse.wav").exists())
    }

    @Test
    fun itIsAShortClearMonoSample() {
        val b = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(ByteArray(4).also { b.get(0, it) }))
        assertEquals("WAVE", String(ByteArray(4).also { b.get(8, it) }))
        assertEquals("PCM", 1, b.getShort(20).toInt())
        assertEquals("mono", 1, b.getShort(22).toInt())
        assertEquals("44.1 kHz", 44100, b.getInt(24))
        assertEquals("16 bit", 16, b.getShort(34).toInt())
        val bytes = b.getInt(40)
        val seconds = bytes / 2 / 44100.0
        assertTrue("short: $seconds s", seconds in 0.1..0.6)
        var peak = 0
        for (i in 0 until bytes / 2) peak = maxOf(peak, kotlin.math.abs(b.getShort(44 + 2 * i).toInt()))
        assertTrue("audible: peak ${peak / 32768.0}", peak / 32768.0 > 0.1)
        assertTrue("not harsh: peak ${peak / 32768.0}", peak / 32768.0 < 0.6)
    }

    private companion object {
        const val SUPPLIED_SHA256 = "d7575abd48810a1e9cffddea4f2787b7f60854cbaa49854cb738f17f6135cbcc"
    }
}
