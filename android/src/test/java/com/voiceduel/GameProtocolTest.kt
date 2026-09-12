package com.voiceduel

import com.voiceduel.data.GameProtocol
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The codec must stay byte-compatible with `backend/app/models/messages.py`.
 * Robolectric supplies the real `org.json` implementation.
 */
@RunWith(RobolectricTestRunner::class)
class GameProtocolTest {

    @Test
    fun `audio frame round trips without touching the payload`() {
        val audio = ByteArray(512) { (it % 256).toByte() }

        val decoded = GameProtocol.decodeAudioFrame(GameProtocol.encodeAudioFrame(7, audio))

        requireNotNull(decoded)
        assertEquals(7, decoded.roundNumber)
        assertEquals(GameProtocol.DEFAULT_AUDIO_MIME, decoded.mime)
        assertArrayEquals(audio, decoded.audio)
    }

    @Test
    fun `header length is a four byte big endian prefix`() {
        val frame = GameProtocol.encodeAudioFrame(1, byteArrayOf(9, 9))
        val headerLength = ((frame[0].toInt() and 0xFF) shl 24) or
            ((frame[1].toInt() and 0xFF) shl 16) or
            ((frame[2].toInt() and 0xFF) shl 8) or
            (frame[3].toInt() and 0xFF)

        val header = JSONObject(String(frame, 4, headerLength, Charsets.UTF_8))

        assertEquals(1, header.getInt("round_number"))
        assertEquals(4 + headerLength + 2, frame.size)
    }

    @Test
    fun `empty recordings survive the round trip`() {
        val decoded = GameProtocol.decodeAudioFrame(GameProtocol.encodeAudioFrame(2, ByteArray(0)))

        requireNotNull(decoded)
        assertEquals(0, decoded.audio.size)
    }

    @Test
    fun `malformed frames decode to null instead of throwing`() {
        assertNull(GameProtocol.decodeAudioFrame(ByteArray(0)))
        assertNull(GameProtocol.decodeAudioFrame(byteArrayOf(0, 0)))
        assertNull(GameProtocol.decodeAudioFrame(byteArrayOf(0, 0, 0, 0)))
        assertNull(GameProtocol.decodeAudioFrame(byteArrayOf(0, 0, 0, 32, 1, 2)))
        assertNull(GameProtocol.decodeAudioFrame(byteArrayOf(0, 0, 0, 3, 97, 98, 99)))
    }

    @Test
    fun `messages are encoded as a type payload envelope`() {
        val encoded = GameProtocol.encodeMessage(
            GameProtocol.JOIN_ROOM,
            JSONObject().put("code", "1234"),
        )
        val root = JSONObject(encoded)

        assertEquals(GameProtocol.JOIN_ROOM, root.getString("type"))
        assertEquals("1234", root.getJSONObject("payload").getString("code"))
    }

    @Test
    fun `a message without a payload still decodes`() {
        val message = GameProtocol.decodeMessage("""{"type":"pong"}""")

        requireNotNull(message)
        assertEquals("pong", message.type)
        assertEquals(0, message.payload.length())
    }

    @Test
    fun `garbage text decodes to null`() {
        assertNull(GameProtocol.decodeMessage("not json"))
        assertNull(GameProtocol.decodeMessage("[]"))
        assertNull(GameProtocol.decodeMessage("{}"))
    }
}
